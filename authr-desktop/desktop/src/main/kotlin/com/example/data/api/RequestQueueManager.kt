package com.example.data.api

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object RequestQueueManager {

    enum class Priority { HIGH, NORMAL, LOW }

    data class QueueRequest<T>(
        val id: String,
        val agentKey: String,
        val prompt: String,
        val systemInstruction: String?,
        val temperature: Float?,
        val priority: Priority,
        val block: suspend () -> T,
        val deferred: CompletableDeferred<T>,
        val timestamp: Long = System.currentTimeMillis()
    ) : Comparable<QueueRequest<*>> {
        override fun compareTo(other: QueueRequest<*>): Int {
            if (this.priority != other.priority) {
                return this.priority.ordinal.compareTo(other.priority.ordinal)
            }
            return this.timestamp.compareTo(other.timestamp)
        }
    }

    private val queueScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Monitoring StateFlows for UI
    private val _queueLength = MutableStateFlow(0)
    val queueLength: StateFlow<Int> = _queueLength.asStateFlow()

    private val _runningRequests = MutableStateFlow(0)
    val runningRequests: StateFlow<Int> = _runningRequests.asStateFlow()

    private val _waitingRequests = MutableStateFlow(0)
    val waitingRequests: StateFlow<Int> = _waitingRequests.asStateFlow()

    private val _failedRequests = MutableStateFlow(0)
    val failedRequests: StateFlow<Int> = _failedRequests.asStateFlow()

    private val _retryCountdown = MutableStateFlow(0)
    val retryCountdown: StateFlow<Int> = _retryCountdown.asStateFlow()

    private val _averageRequestDurationMs = MutableStateFlow(0L)
    val averageRequestDurationMs: StateFlow<Long> = _averageRequestDurationMs.asStateFlow()

    private val totalRequestsCount = AtomicInteger(0)
    private val totalDurationSumMs = AtomicLong(0)

    // Configuration limits
    var concurrencyLimit = 2
        set(value) {
            field = value
            semaphore = Semaphore(value)
        }

    var requestsPerMinuteLimit = 15
        set(value) {
            field = value
            rateLimiter = RateLimiter(value)
        }

    private var semaphore = Semaphore(concurrencyLimit)
    private var rateLimiter = RateLimiter(requestsPerMinuteLimit)

    // Pending persistence map
    private val serializedRequests = ConcurrentHashMap<String, String>()

    init {
        Log.d("RequestQueueManager", "RequestQueueManager initialized.")
    }

    class RateLimiter(private val limitPerMinute: Int) {
        private val timestamps = mutableListOf<Long>()

        suspend fun acquire() {
            while (true) {
                val now = System.currentTimeMillis()
                synchronized(this) {
                    timestamps.removeAll { it < now - 60000 }
                    if (timestamps.size < limitPerMinute) {
                        timestamps.add(now)
                        return
                    }
                }
                delay(200)
            }
        }
    }

    fun initialize(context: Context) {
        loadQueueFromPersistence(context)
    }

    private fun loadQueueFromPersistence(context: Context) {
        val sharedPrefs = context.getSharedPreferences("studio_settings", Context.MODE_PRIVATE)
        val jsonStr = sharedPrefs.getString("offline_queue_data", "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id", UUID.randomUUID().toString())
                serializedRequests[id] = obj.toString()
            }
            _waitingRequests.value = serializedRequests.size
            _queueLength.value = serializedRequests.size
            Log.d("RequestQueueManager", "Loaded ${serializedRequests.size} requests from persistence.")
        } catch (e: Exception) {
            Log.e("RequestQueueManager", "Error parsing persistence queue: ${e.message}")
        }
    }

    private fun saveQueueToPersistence(context: Context) {
        val sharedPrefs = context.getSharedPreferences("studio_settings", Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        serializedRequests.values.forEach {
            try {
                jsonArray.put(JSONObject(it))
            } catch (e: Exception) {
                // ignore
            }
        }
        sharedPrefs.edit().putString("offline_queue_data", jsonArray.toString()).apply()
    }

    private fun persistRequest(context: Context, id: String, agentKey: String, prompt: String, systemInstruction: String?, temperature: Float?, priority: Priority) {
        try {
            val obj = JSONObject().apply {
                put("id", id)
                put("agentKey", agentKey)
                put("prompt", prompt)
                put("systemInstruction", systemInstruction)
                put("temperature", if (temperature != null) temperature.toDouble() else org.json.JSONObject.NULL)
                put("priority", priority.name)
                put("timestamp", System.currentTimeMillis())
            }
            serializedRequests[id] = obj.toString()
            saveQueueToPersistence(context)
        } catch (e: Exception) {
            Log.e("RequestQueueManager", "Failed to serialize: ${e.message}")
        }
    }

    private fun removeRequestFromPersistence(context: Context, id: String) {
        serializedRequests.remove(id)
        saveQueueToPersistence(context)
    }

    fun adjustConcurrencyDynamically(context: Context) {
        val sharedPrefs = context.getSharedPreferences("studio_settings", Context.MODE_PRIVATE)
        val isAutoOptimizeEnabled = sharedPrefs.getBoolean("auto_optimize", false)
        if (!isAutoOptimizeEnabled) {
            return
        }

        try {
            // 1. Check Battery status
            val batteryStatus: android.content.Intent? = context.registerReceiver(
                null,
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            )
            val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level >= 0 && scale > 0) (level * 100f / scale) else 100f
            val isCharging = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) == android.os.BatteryManager.BATTERY_STATUS_CHARGING

            // 2. Check Network status
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val activeNetwork = connectivityManager?.activeNetwork
            val networkCapabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)
            val isWifi = networkCapabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ?: false

            // 3. Check Thermal state (Android 10+ / API 29+)
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            val isThermalThrottling = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                (powerManager?.currentThermalStatus ?: 0) >= android.os.PowerManager.THERMAL_STATUS_MODERATE
            } else {
                false
            }

            // Calculate optimal concurrency (1 to 4)
            var optimalConcurrency = 2 // Default

            if (isCharging) {
                optimalConcurrency = if (isWifi) 4 else 3
            } else {
                if (batteryPct < 20) {
                    optimalConcurrency = 1 // Eco mode
                } else if (batteryPct < 50) {
                    optimalConcurrency = 2
                } else {
                    optimalConcurrency = if (isWifi) 3 else 2
                }
            }

            if (isThermalThrottling) {
                optimalConcurrency = (optimalConcurrency - 1).coerceAtLeast(1) // Throttle back
            }

            if (concurrencyLimit != optimalConcurrency) {
                Log.d("RequestQueueManager", "Optimizing concurrency dynamically from $concurrencyLimit to $optimalConcurrency (Battery: $batteryPct%, Charging: $isCharging, WiFi: $isWifi, ThermalThrottled: $isThermalThrottling)")
                concurrencyLimit = optimalConcurrency
            }
        } catch (e: Exception) {
            Log.e("RequestQueueManager", "Error adjusting dynamic concurrency: ${e.message}")
        }
    }

    suspend fun <T> enqueueRequest(
        context: Context,
        agentKey: String,
        prompt: String,
        systemInstruction: String?,
        temperature: Float?,
        priority: Priority = Priority.NORMAL,
        block: suspend () -> T
    ): T {
        val deferred = CompletableDeferred<T>()
        val id = UUID.randomUUID().toString()
        val request = QueueRequest(id, agentKey, prompt, systemInstruction, temperature, priority, block, deferred)

        synchronized(this) {
            _queueLength.value++
            _waitingRequests.value++
        }

        persistRequest(context, id, agentKey, prompt, systemInstruction, temperature, priority)

        queueScope.launch {
            executeRequestWithRetry(context, request)
        }

        return deferred.await()
    }

    private suspend fun <T> executeRequestWithRetry(context: Context, request: QueueRequest<T>) {
        var attempt = 0
        var delayMs = 1500L
        val startTime = System.currentTimeMillis()

        adjustConcurrencyDynamically(context)
        semaphore.withPermit {
            rateLimiter.acquire()

            synchronized(this) {
                _waitingRequests.value = (_waitingRequests.value - 1).coerceAtLeast(0)
                _runningRequests.value++
            }

            while (true) {
                try {
                    val result = request.block()
                    val duration = System.currentTimeMillis() - startTime

                    val count = totalRequestsCount.incrementAndGet()
                    val totalDuration = totalDurationSumMs.addAndGet(duration)
                    _averageRequestDurationMs.value = totalDuration / count

                    removeRequestFromPersistence(context, request.id)
                    request.deferred.complete(result)
                    break
                } catch (e: Exception) {
                    val is429 = e.message?.contains("429") == true
                    val isNetworkError = e is IOException || e is SocketTimeoutException || e.message?.contains("timeout") == true

                    if (is429 || isNetworkError) {
                        attempt++
                        if (attempt > 4) {
                            synchronized(this) {
                                _failedRequests.value++
                            }
                            removeRequestFromPersistence(context, request.id)
                            request.deferred.completeExceptionally(e)
                            break
                        }

                        val retryAfterSec = if (is429) 12 else (delayMs / 1000).toInt()
                        delayMs *= 2

                        for (sec in retryAfterSec downTo 1) {
                            _retryCountdown.value = sec
                            delay(1000)
                        }
                        _retryCountdown.value = 0
                    } else {
                        synchronized(this) {
                            _failedRequests.value++
                        }
                        removeRequestFromPersistence(context, request.id)
                        request.deferred.completeExceptionally(e)
                        break
                    }
                }
            }

            synchronized(this) {
                _runningRequests.value = (_runningRequests.value - 1).coerceAtLeast(0)
                _queueLength.value = (_queueLength.value - 1).coerceAtLeast(0)
            }
        }
    }
}
