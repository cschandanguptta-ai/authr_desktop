package android.util

/**
 * Desktop replacement for android.util.Base64 backed by java.util.Base64.
 * Only the API surface used by the app is implemented.
 */
object Base64 {
    const val DEFAULT = 0
    const val NO_WRAP = 2
    const val URL_SAFE = 8
    const val CRLF = 4

    fun encodeToString(input: ByteArray, flags: Int): String {
        val encoder = if (flags and URL_SAFE != 0) {
            java.util.Base64.getUrlEncoder()
        } else {
            java.util.Base64.getEncoder()
        }
        val encoded = encoder.encodeToString(input)
        return if (flags == DEFAULT) "$encoded\n" else encoded
    }

    fun decode(source: String, flags: Int): ByteArray {
        val decoder = if (flags and URL_SAFE != 0) {
            java.util.Base64.getUrlDecoder()
        } else {
            java.util.Base64.getDecoder()
        }
        val cleaned = source.replace("\n", "").replace("\r", "")
        return decoder.decode(cleaned)
    }

    fun decode(source: ByteArray, flags: Int): ByteArray = decode(String(source, Charsets.UTF_8), flags)
}
