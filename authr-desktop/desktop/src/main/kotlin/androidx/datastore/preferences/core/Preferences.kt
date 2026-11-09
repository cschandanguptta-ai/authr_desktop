package androidx.datastore.preferences.core

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Desktop replacement for androidx.datastore.preferences (Preferences DataStore).
 * File-backed, atomic writes, one instance per name per process — same
 * observable semantics as the Android library.
 */
sealed class Preferences {
    internal abstract val values: Map<Key<*>, Any>

    operator fun <T> get(key: Key<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return values[key] as T?
    }

    fun asMap(): Map<Key<*>, Any> = values

    override fun equals(other: Any?): Boolean = other is Preferences && other.values == values
    override fun hashCode(): Int = values.hashCode()

    val isEmpty: Boolean get() = values.isEmpty()

    fun toMutablePreferences(): MutablePreferences = MutablePreferences(LinkedHashMap(values))

    fun toPreferences(): Preferences = this

    class Key<T> internal constructor(val name: String, internal val typeTag: String) {
        override fun toString(): String = name
        override fun equals(other: Any?): Boolean = other is Key<*> && other.name == name && other.typeTag == typeTag
        override fun hashCode(): Int = name.hashCode() * 31 + typeTag.hashCode()
    }

    companion object {
        fun from(vararg pairs: Pair<Key<*>, Any>): Preferences =
            MutablePreferences(LinkedHashMap(mapOf(*pairs)))
    }
}

class MutablePreferences internal constructor(
    internal val backing: LinkedHashMap<Key<*>, Any>
) : Preferences() {
    override val values: Map<Key<*>, Any> get() = backing

    operator fun <T> set(key: Key<T>, value: T) {
        requireNotNull(value) { "null values are not supported; use remove(key)" }
        @Suppress("UNCHECKED_CAST")
        backing[key] = value as Any
    }

    fun <T> remove(key: Key<T>) { backing.remove(key) }

    fun clear() { backing.clear() }

    operator fun contains(key: Key<*>): Boolean = backing.containsKey(key)
}

fun booleanPreferencesKey(name: String): Preferences.Key<Boolean> = Preferences.Key(name, "b")
fun stringPreferencesKey(name: String): Preferences.Key<String> = Preferences.Key(name, "s")
fun intPreferencesKey(name: String): Preferences.Key<Int> = Preferences.Key(name, "i")
fun longPreferencesKey(name: String): Preferences.Key<Long> = Preferences.Key(name, "l")
fun floatPreferencesKey(name: String): Preferences.Key<Float> = Preferences.Key(name, "f")
fun doublePreferencesKey(name: String): Preferences.Key<Double> = Preferences.Key(name, "d")
fun stringSetPreferencesKey(name: String): Preferences.Key<Set<String>> = Preferences.Key(name, "ss")

fun preferencesOf(vararg pairs: Pair<Preferences.Key<*>, Any>): Preferences =
    MutablePreferences(LinkedHashMap(mapOf(*pairs)))

fun emptyPreferences(): Preferences = MutablePreferences(LinkedHashMap())

suspend fun DataStore<Preferences>.edit(transform: suspend (MutablePreferences) -> Unit): Preferences {
    return updateData { current ->
        val mutable = current.toMutablePreferences()
        transform(mutable)
        mutable
    }
}

/**
 * File-backed Preferences DataStore. Keys serialize as "typeTag:name".
 */
internal class FilePreferencesDataStore(private val file: File) : DataStore<Preferences> {

    private val mutex = Mutex()
    private val state = MutableStateFlow(load())

    private fun load(): Preferences {
        return try {
            if (file.exists()) {
                val root = org.json.JSONObject(file.readText(Charsets.UTF_8))
                val map = LinkedHashMap<Preferences.Key<*>, Any>()
                for (serializedKey in root.keys()) {
                    val idx = serializedKey.indexOf(':')
                    if (idx <= 0) continue
                    val tag = serializedKey.substring(0, idx)
                    val name = serializedKey.substring(idx + 1)
                    val key: Preferences.Key<*> = when (tag) {
                        "b" -> booleanPreferencesKey(name)
                        "s" -> stringPreferencesKey(name)
                        "i" -> intPreferencesKey(name)
                        "l" -> longPreferencesKey(name)
                        "f" -> floatPreferencesKey(name)
                        "d" -> doublePreferencesKey(name)
                        "ss" -> stringSetPreferencesKey(name)
                        else -> continue
                    }
                    val raw = root.get(serializedKey)
                    map[key] = when (tag) {
                        "b" -> raw.toString().toBoolean()
                        "i" -> (raw as? Number)?.toInt() ?: raw.toString().toInt()
                        "l" -> (raw as? Number)?.toLong() ?: raw.toString().toLong()
                        "f" -> (raw as? Number)?.toFloat() ?: raw.toString().toFloat()
                        "d" -> (raw as? Number)?.toDouble() ?: raw.toString().toDouble()
                        "ss" -> ((raw as? org.json.JSONArray)?.let { arr ->
                            (0 until arr.length()).map { arr.getString(it) }.toSet()
                        } ?: emptySet<String>())
                        else -> raw.toString()
                    }
                }
                MutablePreferences(map)
            } else MutablePreferences(LinkedHashMap())
        } catch (e: Exception) {
            android.util.Log.w("DataStore", "load failed for ${file.name}: ${e.message}")
            MutablePreferences(LinkedHashMap())
        }
    }

    private fun persist(prefs: Preferences) {
        try {
            val root = org.json.JSONObject()
            for ((key, value) in prefs.values) {
                val sk = "${(key as Preferences.Key<*>).typeTag}:${key.name}"
                when (value) {
                    is Set<*> -> {
                        val arr = org.json.JSONArray()
                        value.forEach { arr.put(it.toString()) }
                        root.put(sk, arr)
                    }
                    else -> root.put(sk, value)
                }
            }
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(root.toString(2), Charsets.UTF_8)
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        } catch (e: Exception) {
            android.util.Log.e("DataStore", "persist failed for ${file.name}", e)
        }
    }

    override val data: Flow<Preferences> = flow {
        emit(state.first())
        state.collect { emit(it) }
    }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        mutex.withLock {
            val newPrefs = transform(state.value)
            persist(newPrefs)
            state.value = newPrefs
            newPrefs
        }

    companion object {
        private val instances = ConcurrentHashMap<String, FilePreferencesDataStore>()

        fun get(file: File): FilePreferencesDataStore =
            instances.getOrPut(file.absolutePath) { FilePreferencesDataStore(file) }
    }
}
