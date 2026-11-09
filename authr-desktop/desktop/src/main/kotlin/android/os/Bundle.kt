package android.os

/**
 * Desktop replacement for android.os.Bundle — a simple typed key/value map.
 */
class Bundle {

    private val map = HashMap<String, Any?>()

    fun putString(key: String, value: String?) { map[key] = value }
    fun putInt(key: String, value: Int) { map[key] = value }
    fun putLong(key: String, value: Long) { map[key] = value }
    fun putFloat(key: String, value: Float) { map[key] = value }
    fun putBoolean(key: String, value: Boolean) { map[key] = value }
    fun putDouble(key: String, value: Double) { map[key] = value }

    fun getString(key: String, defaultValue: String? = null): String? =
        (map[key] as? String) ?: defaultValue

    fun getInt(key: String, defaultValue: Int = 0): Int = (map[key] as? Int) ?: defaultValue
    fun getLong(key: String, defaultValue: Long = 0L): Long = (map[key] as? Long) ?: defaultValue
    fun getFloat(key: String, defaultValue: Float = 0f): Float = (map[key] as? Float) ?: defaultValue
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean =
        (map[key] as? Boolean) ?: defaultValue
    fun getDouble(key: String, defaultValue: Double = 0.0): Double =
        (map[key] as? Double) ?: defaultValue

    fun containsKey(key: String): Boolean = map.containsKey(key)
    fun keySet(): Set<String> = map.keys
    fun remove(key: String) { map.remove(key) }
    fun clear() { map.clear() }
    fun isEmpty(): Boolean = map.isEmpty()

    fun putAll(bundle: Bundle) { map.putAll(bundle.map) }

    override fun toString(): String = "Bundle{${map.entries.joinToString(", ") { (k, v) -> "$k=$v" }}}"
}
