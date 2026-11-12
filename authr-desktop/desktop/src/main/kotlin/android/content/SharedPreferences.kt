package android.content

import java.io.File

/**
 * Desktop replacement for android.content.SharedPreferences.
 *
 * A file-backed, change-notifying key/value store. Values are persisted as
 * JSON so the on-disk format stays human-inspectable, and the same file is
 * shared by the plain and the EncryptedSharedPreferences implementation
 * (the encrypted variant wraps the file with AES/GCM).
 */
interface SharedPreferences {

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putStringSet(key: String, values: Set<String>?): Editor
        fun putInt(key: String, value: Int): Editor
        fun putLong(key: String, value: Long): Editor
        fun putFloat(key: String, value: Float): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun apply()
        fun commit(): Boolean
    }

    fun getAll(): Map<String, *>
    fun getString(key: String, defValue: String?): String?
    fun getStringSet(key: String, defValue: Set<String>?): Set<String>?
    fun getInt(key: String, defValue: Int): Int
    fun getLong(key: String, defValue: Long): Long
    fun getFloat(key: String, defValue: Float): Float
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun contains(key: String): Boolean
    fun edit(): Editor

    fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener)
    fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener)

    fun interface OnSharedPreferenceChangeListener {
        fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String)
    }

    companion object {
        /**
         * A JSON-file-backed SharedPreferences implementation.
         *
         * @param file    the backing file (created on first apply())
         * @param encode  optional transform applied before writing (e.g. AES/GCM encrypt)
         * @param decode  optional transform applied after reading (e.g. AES/GCM decrypt)
         */
        fun fileBacked(
            file: File,
            encode: ((String) -> String)? = null,
            decode: ((String) -> String)? = null
        ): SharedPreferences = FileBackedSharedPreferences(file, encode, decode)

        /**
         * A purely in-memory [SharedPreferences] — nothing ever touches disk.
         * The D-03 secure-storage fallback: when the encrypted store cannot
         * be opened, secrets ride in memory for the session instead of
         * persisting unencrypted.
         */
        fun inMemory(): SharedPreferences = MemorySharedPreferences()
    }
}

internal class FileBackedSharedPreferences(
    private val file: File,
    private val encode: ((String) -> String)? = null,
    private val decode: ((String) -> String)? = null
) : SharedPreferences {

    private val lock = Any()
    private val listeners = java.util.concurrent.CopyOnWriteArraySet<SharedPreferences.OnSharedPreferenceChangeListener>()
    private var values: MutableMap<String, Any> = load()

    private fun load(): MutableMap<String, Any> {
        return try {
            if (file.exists()) {
                var text = file.readText(Charsets.UTF_8)
                if (decode != null) text = decode.invoke(text)
                val root = org.json.JSONObject(text)
                val map = HashMap<String, Any>()
                for (key in root.keys()) {
                    when (val v = root.get(key)) {
                        is Boolean -> map[key] = v
                        is Int -> map[key] = v
                        is Long -> map[key] = v
                        is Double -> map[key] = if (v == v.toLong().toDouble()) v.toLong() else v
                        is org.json.JSONArray -> {
                            // string set
                            val set = LinkedHashSet<String>()
                            for (i in 0 until v.length()) set.add(v.getString(i))
                            map[key] = set
                        }
                        else -> map[key] = v.toString()
                    }
                }
                map
            } else {
                HashMap()
            }
        } catch (e: Exception) {
            android.util.Log.w("SharedPreferences", "Failed to load ${file.name}: ${e.message}; starting fresh")
            HashMap()
        }
    }

    private fun persist() {
        try {
            val root = org.json.JSONObject()
            for ((k, v) in values) {
                when (v) {
                    is Set<*> -> {
                        val arr = org.json.JSONArray()
                        v.forEach { arr.put(it.toString()) }
                        root.put(k, arr)
                    }
                    is Float -> root.put(k, v.toDouble())
                    is Int, is Long, is Boolean, is String -> root.put(k, v)
                    else -> root.put(k, v.toString())
                }
            }
            var text = root.toString(2)
            if (encode != null) text = encode.invoke(text)
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text, Charsets.UTF_8)
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        } catch (e: Exception) {
            android.util.Log.e("SharedPreferences", "Failed to persist ${file.name}", e)
        }
    }

    override fun getAll(): Map<String, *> = synchronized(lock) { HashMap(values) }

    override fun getString(key: String, defValue: String?): String? =
        synchronized(lock) { values[key] as? String ?: defValue }

    override fun getStringSet(key: String, defValue: Set<String>?): Set<String>? =
        synchronized(lock) { (values[key] as? Set<*>)?.map { it.toString() }?.toSet() ?: defValue }

    override fun getInt(key: String, defValue: Int): Int =
        synchronized(lock) { (values[key] as? Number)?.toInt() ?: defValue }

    override fun getLong(key: String, defValue: Long): Long =
        synchronized(lock) { (values[key] as? Number)?.toLong() ?: defValue }

    override fun getFloat(key: String, defValue: Float): Float =
        synchronized(lock) { (values[key] as? Number)?.toFloat() ?: defValue }

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        synchronized(lock) { values[key] as? Boolean ?: defValue }

    override fun contains(key: String): Boolean = synchronized(lock) { values.containsKey(key) }

    override fun edit(): SharedPreferences.Editor = EditorImpl()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.add(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.remove(listener)
    }

    private inner class EditorImpl : SharedPreferences.Editor {
        private val pending = HashMap<String, Any?>()
        private var clearAll = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            pending[key] = value; return this
        }

        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
            pending[key] = values; return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            pending[key] = value; return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            pending[key] = value; return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            pending[key] = value; return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            pending[key] = value; return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            pending[key] = REMOVE; return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearAll = true; return this
        }

        override fun apply() {
            val changed = ArrayList<String>()
            synchronized(lock) {
                if (clearAll) values.clear()
                for ((k, v) in pending) {
                    if (v === REMOVE) values.remove(k)
                    else if (v == null) values.remove(k)
                    else values[k] = v
                    changed.add(k)
                }
                persist()
            }
            for (key in changed) {
                listeners.forEach { it.onSharedPreferenceChanged(this@FileBackedSharedPreferences, key) }
            }
        }

        override fun commit(): Boolean {
            apply()
            return true
        }
    }

    companion object {
        private object REMOVE
    }
}

/**
 * A purely in-memory [SharedPreferences] (v3.0 D-03): a session-scoped map
 * with NO disk backing. The secure-storage fallback — secrets stay volatile
 * when the encrypted store cannot be opened, instead of persisting
 * unencrypted. Listeners are accepted and invoked in-process.
 */
internal class MemorySharedPreferences : SharedPreferences {

    private val values = java.util.concurrent.ConcurrentHashMap<String, Any>()
    private val listeners =
        java.util.concurrent.CopyOnWriteArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()

    private inner class EditorImpl : SharedPreferences.Editor {
        private val pending = java.util.concurrent.ConcurrentLinkedQueue<() -> Unit>()
        override fun putString(key: String, value: String?) = apply { pending += { put(key, value) } }
        override fun putStringSet(key: String, values: Set<String>?) = apply { pending += { put(key, values?.toSet()) } }
        override fun putInt(key: String, value: Int) = apply { pending += { put(key, value) } }
        override fun putLong(key: String, value: Long) = apply { pending += { put(key, value) } }
        override fun putFloat(key: String, value: Float) = apply { pending += { put(key, value) } }
        override fun putBoolean(key: String, value: Boolean) = apply { pending += { put(key, value) } }
        override fun remove(key: String) = apply { pending += { values.remove(key); changed(key) } }
        override fun clear() = apply { pending += { val keys = values.keys.toList(); values.clear(); keys.forEach(::changed) } }
        override fun apply() { pending.forEach { it() } }
        override fun commit(): Boolean { apply(); return true }
        private fun put(key: String, value: Any?) {
            if (value == null) values.remove(key) else values[key] = value
            changed(key)
        }
        private fun changed(key: String) = listeners.forEach { it.onSharedPreferenceChanged(this@MemorySharedPreferences, key) }
    }

    private fun <T : Any> read(key: String, def: T?): T? {
        @Suppress("UNCHECKED_CAST")
        return values[key] as? T ?: def
    }

    override fun getAll(): Map<String, *> = values.toMap()
    override fun getString(key: String, defValue: String?) = read(key, defValue)
    override fun getStringSet(key: String, defValue: Set<String>?) = read(key, defValue)
    override fun getInt(key: String, defValue: Int) = read(key, defValue) ?: defValue
    override fun getLong(key: String, defValue: Long) = read(key, defValue) ?: defValue
    override fun getFloat(key: String, defValue: Float) = read(key, defValue) ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = read(key, defValue) ?: defValue
    override fun contains(key: String) = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = EditorImpl()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners += listener
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners -= listener
    }
}
