package android.content

/**
 * Desktop replacement for android.content.ContentValues — an ordered
 * key/value bag used by the MediaStore save flow.
 */
class ContentValues {

    private val map = LinkedHashMap<String, Any?>()

    fun put(key: String, value: String?) { map[key] = value }
    fun put(key: String, value: Int?) { map[key] = value }
    fun put(key: String, value: Long?) { map[key] = value }
    fun put(key: String, value: Float?) { map[key] = value }
    fun put(key: String, value: Double?) { map[key] = value }
    fun put(key: String, value: Boolean?) { map[key] = value }
    fun putAll(other: ContentValues) { map.putAll(other.map) }
    fun putNull(key: String) { map[key] = null }

    fun getAsString(key: String): String? = map[key]?.toString()
    fun getAsInteger(key: String): Int? = map[key] as? Int
    fun getAsLong(key: String): Long? = map[key] as? Long
    fun getAsFloat(key: String): Float? = map[key] as? Float
    fun getAsDouble(key: String): Double? = map[key] as? Double
    fun getAsBoolean(key: String): Boolean? = map[key] as? Boolean

    fun containsKey(key: String): Boolean = map.containsKey(key)
    fun remove(key: String) { map.remove(key) }
    fun clear() { map.clear() }
    fun keySet(): Set<String> = map.keys
    fun valueSet(): Collection<Any?> = map.values
    fun isEmpty(): Boolean = map.isEmpty()
    val size: Int get() = map.size
}
