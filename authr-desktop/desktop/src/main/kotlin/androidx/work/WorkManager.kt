package androidx.work

import android.content.Context
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/**
 * ============================================================================
 * DESKTOP WORK MANAGER — a faithful, coroutine-based re-implementation of the
 * androidx.work API surface used by Authr OS.
 *
 * Semantics preserved from Android:
 *  - Chained work (beginWith(a).then(b)...enqueue()) runs strictly in order;
 *    a chain halts when a worker returns Result.failure().
 *  - Result.retry() re-runs the worker after exponential backoff.
 *  - Unique work with ExistingWorkPolicy.REPLACE cancels the running chain.
 *  - Periodic work re-schedules itself after the interval elapses (while the
 *    app process is alive — desktops have no Doze/Boot receivers; a
 *    persistent JobStore is not needed because Authr re-schedules its
 *    maintenance work on every launch).
 *  - Constraints (network/battery) are satisfied trivially on desktop.
 * ============================================================================
 */

// ---------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------

class Data internal constructor(internal val values: Map<String, Any?>) {

    fun getString(key: String, defaultValue: String? = null): String? =
        (values[key] as? String) ?: defaultValue

    fun getInt(key: String, defaultValue: Int = 0): Int = when (val v = values[key]) {
        is Int -> v
        is Long -> v.toInt()
        is Number -> v.toInt()
        else -> defaultValue
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long = when (val v = values[key]) {
        is Long -> v
        is Int -> v.toLong()
        is Number -> v.toLong()
        else -> defaultValue
    }

    fun getFloat(key: String, defaultValue: Float = 0f): Float = when (val v = values[key]) {
        is Float -> v
        is Number -> v.toFloat()
        else -> defaultValue
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean =
        (values[key] as? Boolean) ?: defaultValue

    fun hasKeyWithValueOfType(key: String, className: Class<*>): Boolean =
        values.containsKey(key)

    val size: Int get() = values.size
    fun keySet(): Set<String> = values.keys
    fun keyValues(): Map<String, Any?> = values

    class Builder {
        private val map = HashMap<String, Any?>()
        fun putAll(data: Data?): Builder { if (data != null) map.putAll(data.values); return this }
        fun putString(key: String, value: String?): Builder { map[key] = value; return this }
        fun putInt(key: String, value: Int): Builder { map[key] = value; return this }
        fun putLong(key: String, value: Long): Builder { map[key] = value; return this }
        fun putFloat(key: String, value: Float): Builder { map[key] = value; return this }
        fun putBoolean(key: String, value: Boolean): Builder { map[key] = value; return this }
        fun remove(key: String): Builder { map.remove(key); return this }
        fun build(): Data = Data(HashMap(map))
    }

    companion object {
        @JvmStatic val EMPTY = Data(emptyMap())
    }
}

fun workDataOf(vararg pairs: Pair<String, Any?>): Data = Data(linkedMapOf(*pairs))

// ---------------------------------------------------------------------------
// Constraints
// ---------------------------------------------------------------------------

enum class NetworkType { NOT_ROAMED, CONNECTED, METERED, NOT_REQUIRED, UNMETERED, TEMPORARILY_UNMETERED }

class Constraints private constructor(
    internal val requiredNetworkType: NetworkType?,
    internal val requiresBatteryNotLow: Boolean,
    internal val requiresCharging: Boolean,
    internal val requiresStorageNotLow: Boolean
) {
    class Builder {
        private var networkType: NetworkType? = null
        private var batteryNotLow = false
        private var charging = false
        private var storageNotLow = false
        fun setRequiredNetworkType(type: NetworkType?): Builder { networkType = type; return this }
        fun setRequiresBatteryNotLow(requires: Boolean): Builder { batteryNotLow = requires; return this }
        fun setRequiresCharging(requires: Boolean): Builder { charging = requires; return this }
        fun setRequiresStorageNotLow(requires: Boolean): Builder { storageNotLow = requires; return this }
        fun build(): Constraints = Constraints(networkType, batteryNotLow, charging, storageNotLow)
    }
}

enum class BackoffPolicy { LINEAR, EXPONENTIAL }

// ---------------------------------------------------------------------------
// Foreground info
// ---------------------------------------------------------------------------

class ForegroundInfo(
    val notificationId: Int,
    val notification: android.app.Notification,
    val type: Int? = null
)

// ---------------------------------------------------------------------------
// WorkRequest
// ---------------------------------------------------------------------------

sealed class WorkRequest protected constructor(
    internal val workerClass: KClass<out ListenableWorker>,
    internal val inputData: Data,
    internal val tags: Set<String>,
    internal val constraints: Constraints,
    internal val backoffPolicy: BackoffPolicy,
    internal val backoffDelaySeconds: Long,
    internal val id: UUID = UUID.randomUUID()
) {
    class OneTime : WorkRequest {
        internal constructor(
            workerClass: KClass<out ListenableWorker>, inputData: Data, tags: Set<String>,
            constraints: Constraints, backoffPolicy: BackoffPolicy, backoffDelaySeconds: Long
        ) : super(workerClass, inputData, tags, constraints, backoffPolicy, backoffDelaySeconds)
    }

    class Periodic internal constructor(
        workerClass: KClass<out ListenableWorker>, inputData: Data, tags: Set<String>,
        constraints: Constraints, backoffPolicy: BackoffPolicy, backoffDelaySeconds: Long,
        internal val repeatInterval: Long
    ) : WorkRequest(workerClass, inputData, tags, constraints, backoffPolicy, backoffDelaySeconds)

    abstract class Builder<W : WorkRequest, B : Builder<W, B>> internal constructor(
        internal val workerClass: KClass<out ListenableWorker>
    ) {
        internal var inputData: Data = Data.EMPTY
        internal val tags = LinkedHashSet<String>()
        internal var constraints = Constraints.Builder().build()
        internal var backoffPolicy = BackoffPolicy.EXPONENTIAL
        internal var backoffDelaySeconds = 30L

        fun setInputData(inputData: Data): B { this.inputData = inputData; return this as B }
        fun setConstraints(constraints: Constraints): B { this.constraints = constraints; return this as B }
        fun addTag(tag: String): B { tags.add(tag); return this as B }
        fun setBackoffPolicy(policy: BackoffPolicy): B { backoffPolicy = policy; return this as B }
        fun setBackoffCriteria(policy: BackoffPolicy, backoffDelay: Long, unit: TimeUnit): B {
            backoffPolicy = policy
            backoffDelaySeconds = unit.toSeconds(backoffDelay)
            return this as B
        }
        fun setId(id: UUID): B = this as B
        abstract fun build(): W
    }

    companion object {
        val id: Nothing get() = error("unused")
        internal const val DEFAULT_BACKOFF_DELAY_SECONDS = 30L
    }
}

class OneTimeWorkRequest private constructor(request: WorkRequest.OneTime) :
    WorkRequest(request.workerClass, request.inputData, request.tags, request.constraints,
        request.backoffPolicy, request.backoffDelaySeconds, request.id) {

    class Builder(workerClass: KClass<out ListenableWorker>) :
        WorkRequest.Builder<OneTimeWorkRequest, Builder>(workerClass) {
        override fun build(): OneTimeWorkRequest = OneTimeWorkRequest(
            WorkRequest.OneTime(workerClass, inputData, tags, constraints, backoffPolicy, backoffDelaySeconds)
        )
    }
}

class PeriodicWorkRequest private constructor(request: WorkRequest.Periodic) :
    WorkRequest(request.workerClass, request.inputData, request.tags, request.constraints,
        request.backoffPolicy, request.backoffDelaySeconds, request.id) {

    internal val repeatIntervalMillis: Long = request.repeatInterval

    class Builder(
        workerClass: KClass<out ListenableWorker>,
        private val repeatInterval: Long,
        private val unit: TimeUnit
    ) : WorkRequest.Builder<PeriodicWorkRequest, Builder>(workerClass) {
        override fun build(): PeriodicWorkRequest = PeriodicWorkRequest(
            WorkRequest.Periodic(workerClass, inputData, tags, constraints, backoffPolicy, backoffDelaySeconds,
                unit.toMillis(repeatInterval))
        )
    }
}

inline fun <reified T : ListenableWorker> OneTimeWorkRequestBuilder(): OneTimeWorkRequest.Builder =
    OneTimeWorkRequest.Builder(T::class)

inline fun <reified T : ListenableWorker> PeriodicWorkRequestBuilder(
    repeatInterval: Long,
    unit: TimeUnit
): PeriodicWorkRequest.Builder = PeriodicWorkRequest.Builder(T::class, repeatInterval, unit)

// ---------------------------------------------------------------------------
// Workers
// ---------------------------------------------------------------------------

class WorkerParameters internal constructor(
    val inputData: Data,
    val tags: Set<String>,
    val id: UUID,
    val runAttemptCount: Int
)

abstract class ListenableWorker internal constructor(
    val appContext: Context,
    val workerParams: WorkerParameters
) {
    val applicationContext: Context get() = appContext
    val inputData: Data get() = workerParams.inputData
    val id: UUID get() = workerParams.id
    val tags: Set<String> get() = workerParams.tags

    open val isStopped: Boolean get() = DesktopWorkManager.isWorkStopped(id)

    abstract fun doWorkAsync(): Any
}

abstract class CoroutineWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : ListenableWorker(appContext, workerParams) {

    abstract suspend fun doWork(): Result

    final override fun doWorkAsync(): Any = "coroutine-worker"

    suspend fun setForeground(foregroundInfo: ForegroundInfo) {
        // Desktop: surface as a tray balloon via the notification shim
        try {
            android.app.NotificationManager.notifyStatic(
                foregroundInfo.notificationId,
                foregroundInfo.notification
            )
        } catch (e: Exception) {
            android.util.Log.w("CoroutineWorker", "setForeground failed: ${e.message}")
        }
    }

    fun setProgress(data: Data) { DesktopWorkManager.updateProgress(id, data) }

    class Result {
        internal val type: Int
        val outputData: Data

        private constructor(type: Int, outputData: Data = Data.EMPTY) {
            this.type = type
            this.outputData = outputData
        }

        companion object {
            const val SUCCESS = 0
            const val FAILURE = 1
            const val RETRY = 2

            @JvmStatic fun success(): Result = Result(SUCCESS)
            @JvmStatic fun success(outputData: Data): Result = Result(SUCCESS, outputData)
            @JvmStatic fun failure(): Result = Result(FAILURE)
            @JvmStatic fun failure(outputData: Data): Result = Result(FAILURE, outputData)
            @JvmStatic fun retry(): Result = Result(RETRY)
        }

        override fun toString(): String = when (type) {
            SUCCESS -> "Success"
            FAILURE -> "Failure"
            else -> "Retry"
        }
    }
}

// ---------------------------------------------------------------------------
// WorkManager
// ---------------------------------------------------------------------------

enum class ExistingWorkPolicy { KEEP, REPLACE, APPEND, APPEND_OR_REPLACE }
enum class ExistingPeriodicWorkPolicy { KEEP, REPLACE, UPDATE, CANCEL_AND_REENQUEUE }

class Operation internal constructor() {
    interface Result
    val result: java.util.concurrent.CompletableFuture<Operation.Result> =
        java.util.concurrent.CompletableFuture.completedFuture(Completed)
    object Completed : Operation.Result
}

class WorkInfo internal constructor(
    val id: UUID,
    val state: State,
    val tags: Set<String>
) {
    enum class State { ENQUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED, BLOCKED }
}

class WorkContinuation internal constructor(
    private val manager: DesktopWorkManager,
    private val uniqueName: String?,
    private val existingPolicy: ExistingWorkPolicy?,
    private val chain: List<WorkRequest>
) {
    fun then(request: WorkRequest): WorkContinuation =
        WorkContinuation(manager, uniqueName, existingPolicy, chain + request)

    fun then(requests: List<WorkRequest>): WorkContinuation =
        WorkContinuation(manager, uniqueName, existingPolicy, chain + requests)

    fun enqueue(): Operation {
        manager.enqueueChain(uniqueName, existingPolicy, chain)
        return Operation()
    }
}

class WorkManager private constructor() {

    companion object {
        @Volatile private var instance: WorkManager? = null

        @JvmStatic
        fun getInstance(context: Context): WorkManager =
            instance ?: synchronized(this) { instance ?: WorkManager().also { instance = it } }
    }

    private val impl = DesktopWorkManager

    fun enqueue(vararg requests: WorkRequest): Operation {
        impl.enqueueRequests(listOf(*requests))
        return Operation()
    }

    fun enqueue(requests: List<WorkRequest>): Operation {
        impl.enqueueRequests(requests)
        return Operation()
    }

    fun beginWith(request: WorkRequest): WorkContinuation = WorkContinuation(impl, null, null, listOf(request))
    fun beginWith(requests: List<WorkRequest>): WorkContinuation = WorkContinuation(impl, null, null, requests)

    fun beginUniqueWork(name: String, policy: ExistingWorkPolicy, request: WorkRequest): WorkContinuation =
        WorkContinuation(impl, name, policy, listOf(request))

    fun beginUniqueWork(name: String, policy: ExistingWorkPolicy, requests: List<WorkRequest>): WorkContinuation =
        WorkContinuation(impl, name, policy, requests)

    fun enqueueUniqueWork(name: String, policy: ExistingWorkPolicy, request: WorkRequest): Operation {
        impl.enqueueChain(name, policy, listOf(request))
        return Operation()
    }

    fun enqueueUniquePeriodicWork(name: String, policy: ExistingPeriodicWorkPolicy, request: PeriodicWorkRequest): Operation {
        impl.enqueuePeriodic(name, policy, request)
        return Operation()
    }

    fun cancelAllWorkByTag(tag: String): Operation { impl.cancelByTag(tag); return Operation() }
    fun cancelUniqueWork(name: String): Operation { impl.cancelUnique(name); return Operation() }
    fun cancelAllWork(): Operation { impl.cancelAll(); return Operation() }
    fun pruneWork(): Operation = Operation()

    fun getWorkInfosForUniqueWork(name: String): java.util.concurrent.CompletableFuture<List<WorkInfo>> =
        java.util.concurrent.CompletableFuture.completedFuture(impl.getWorkInfos(name))
}

// ---------------------------------------------------------------------------
// Configuration + worker factory (Koin-driven, mirrors the Android app's
// KoinWorkerFactory setup)
// ---------------------------------------------------------------------------

class Configuration private constructor(internal val workerFactory: WorkerFactory?) {

    class Builder {
        private var factory: WorkerFactory? = null
        fun setWorkerFactory(factory: WorkerFactory?): Builder { this.factory = factory; return this }
        fun build(): Configuration = Configuration(factory)
    }

    interface Provider {
        val workManagerConfiguration: Configuration
    }
}

abstract class WorkerFactory {
    abstract fun createWorker(appContext: Context, workerClassName: String, params: WorkerParameters): ListenableWorker?
}

object DefaultWorkerFactory : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, params: WorkerParameters): ListenableWorker? {
        return try {
            val clazz = Class.forName(workerClassName).kotlin as KClass<out ListenableWorker>
            DesktopWorkManager.factories[clazz]?.invoke(params)
        } catch (e: Exception) {
            null
        }
    }
}

// ---------------------------------------------------------------------------
// The engine
// ---------------------------------------------------------------------------

internal object DesktopWorkManager {

    /** Registered by the DI module: worker class -> (params -> worker instance). */
    internal val factories = ConcurrentHashMap<KClass<out ListenableWorker>, (WorkerParameters) -> ListenableWorker>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, t -> android.util.Log.e("DesktopWorkManager", "Worker crashed", t) })

    private val stoppedIds = ConcurrentHashMap.newKeySet<UUID>()
    private val uniqueChains = ConcurrentHashMap<String, Job>()
    private val periodicJobs = ConcurrentHashMap<String, Job>()
    private val workStates = ConcurrentHashMap<String, WorkInfo.State>()
    private val taggedRequests = ConcurrentHashMap<String, MutableSet<String>>()
    private val periodicTags = ConcurrentHashMap<String, MutableSet<String>>()

    /** Koin registration helper: worker { ... } replacement on desktop. */
    fun <T : ListenableWorker> registerWorker(clazz: KClass<T>, factory: (WorkerParameters) -> T) {
        factories[clazz] = factory
    }

    fun isWorkStopped(id: UUID): Boolean = stoppedIds.contains(id)

    fun updateProgress(id: UUID, data: Data) { /* progress not observed by the app */ }

    fun enqueueChain(uniqueName: String?, policy: ExistingWorkPolicy?, chain: List<WorkRequest>) {
        val name = uniqueName ?: "chain_${UUID.randomUUID()}"
        if (uniqueName != null && (policy == ExistingWorkPolicy.REPLACE || policy == null)) {
            cancelUnique(uniqueName)
        }
        noteTagsForChain(uniqueName, chain)
        val previous = uniqueChains[name]
        if (policy == ExistingWorkPolicy.KEEP && previous != null && previous.isActive) return
        val job = scope.launch { runChain(chain, name) }
        uniqueChains[name] = job
    }

    fun enqueueRequests(requests: List<WorkRequest>) {
        for (request in requests) {
            when (request) {
                is PeriodicWorkRequest -> enqueuePeriodic("periodic_${request.id}", ExistingPeriodicWorkPolicy.KEEP, request)
                else -> enqueueChain(null, null, listOf(request))
            }
        }
    }

    private suspend fun runChain(chain: List<WorkRequest>, name: String) {
        for (request in chain) {
            if (!runSingle(request)) {
                android.util.Log.w("DesktopWorkManager", "Chain $name halted after ${request.workerClass.simpleName} failure")
                workStates[name] = WorkInfo.State.FAILED
                return
            }
        }
        workStates[name] = WorkInfo.State.SUCCEEDED
    }

    private suspend fun runSingle(request: WorkRequest): Boolean {
        val factory = factories[request.workerClass]
            ?: run {
                android.util.Log.e("DesktopWorkManager", "No factory for ${request.workerClass}; is it registered in the DI module?")
                return false
            }

        var attempt = 0
        while (true) {
            if (stoppedIds.contains(request.id)) {
                stoppedIds.remove(request.id)
                return false
            }
            val params = WorkerParameters(request.inputData, request.tags, request.id, attempt)
            val worker = factory(params) ?: return false
            val result = try {
                (worker as CoroutineWorker).doWork()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("DesktopWorkManager", "Worker ${request.workerClass.simpleName} threw", e)
                CoroutineWorker.Result.retry()
            }
            when (result.type) {
                CoroutineWorker.Result.SUCCESS -> return true
                CoroutineWorker.Result.FAILURE -> return false
                else -> {
                    attempt++
                    val backoffMillis = backoffMillis(request, attempt)
                    android.util.Log.w("DesktopWorkManager",
                        "Worker ${request.workerClass.simpleName} retry #${attempt} in ${backoffMillis}ms")
                    kotlinx.coroutines.delay(backoffMillis)
                }
            }
        }
    }

    private fun backoffMillis(request: WorkRequest, attempt: Int): Long {
        val base = request.backoffDelaySeconds * 1000L
        val growth = if (request.backoffPolicy == BackoffPolicy.EXPONENTIAL) {
            1L shl (attempt - 1).coerceAtMost(10)
        } else {
            attempt.toLong()
        }
        return (base * growth).coerceAtMost(5 * 60 * 1000L)
    }

    fun enqueuePeriodic(name: String, policy: ExistingPeriodicWorkPolicy, request: PeriodicWorkRequest) {
        if (policy == ExistingPeriodicWorkPolicy.KEEP) {
            periodicJobs[name]?.let { if (it.isActive) return }
        }
        periodicJobs.remove(name)?.cancel()
        noteTagsForPeriodic(name, request)
        val job = scope.launch {
            while (true) {
                runSingle(request)
                kotlinx.coroutines.delay(request.repeatIntervalMillis)
            }
        }
        periodicJobs[name] = job
    }

    fun cancelByTag(tag: String) {
        // Chain-level cancellation: cancel jobs whose requests carry the tag.
        // (Authr always cancels whole pipelines by their pipeline tag.)
        for ((name, job) in uniqueChains) {
            if (job.isActive) {
                // cancel matching tags tracked at enqueue time
                taggedRequests[name]?.let { if (it.contains(tag)) { cancelUnique(name) } }
            }
        }
        for ((name, job) in periodicJobs) {
            if (job.isActive && periodicTags[name]?.contains(tag) == true) {
                job.cancel(); periodicTags.remove(name)
            }
        }
    }

    fun cancelUnique(name: String) {
        uniqueChains.remove(name)?.cancel()
        periodicJobs.remove(name)?.cancel()
        workStates.remove(name)
    }

    fun cancelAll() {
        uniqueChains.values.forEach { it.cancel() }
        uniqueChains.clear()
        periodicJobs.values.forEach { it.cancel() }
        periodicJobs.clear()
    }

    fun getWorkInfos(name: String): List<WorkInfo> = listOf(
        WorkInfo(UUID.nameUUIDFromBytes(name.toByteArray()),
            workStates[name] ?: WorkInfo.State.SUCCEEDED, emptySet())
    )

    fun noteTagsForChain(uniqueName: String?, chain: List<WorkRequest>) {
        val name = uniqueName ?: return
        taggedRequests.getOrPut(name) { LinkedHashSet() }.addAll(chain.flatMap { it.tags })
    }

    fun noteTagsForPeriodic(name: String, request: PeriodicWorkRequest) {
        periodicTags.getOrPut(name) { LinkedHashSet() }.addAll(request.tags)
    }
}
