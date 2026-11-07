package android.util

/**
 * Desktop replacement for android.util.Log.
 *
 * Mirrors the Android logging API surface used by the app while adding an
 * in-memory ring buffer so the in-app LogBook / bug report dialog can show
 * the same content that logcat would show on Android.
 */
object Log {

    private const val MAX_BUFFER = 4000
    private val buffer = ArrayDeque<String>(MAX_BUFFER)
    private val lock = Any()
    var verboseToStdout: Boolean = true

    private fun timestamp(): String =
        java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())

    private fun record(level: String, tag: String?, msg: String, tr: Throwable?): Int {
        val line = buildString {
            append(timestamp())
            append(" ")
            append(level)
            append("/")
            append(tag ?: "Unknown")
            append(": ")
            append(msg)
            if (tr != null) {
                append("\n")
                append(getStackTraceString(tr))
            }
        }
        synchronized(lock) {
            buffer.addLast(line)
            while (buffer.size > MAX_BUFFER) buffer.removeFirst()
        }
        if (verboseToStdout) {
            println(line)
        }
        return 0
    }

    fun d(tag: String, msg: String) = record("D", tag, msg, null)
    fun d(tag: String, msg: String, tr: Throwable?) = record("D", tag, msg, tr)
    fun i(tag: String, msg: String) = record("I", tag, msg, null)
    fun i(tag: String, msg: String, tr: Throwable?) = record("I", tag, msg, tr)
    fun v(tag: String, msg: String) = record("V", tag, msg, null)
    fun v(tag: String, msg: String, tr: Throwable?) = record("V", tag, msg, tr)
    fun w(tag: String, msg: String) = record("W", tag, msg, null)
    fun w(tag: String, msg: String, tr: Throwable?) = record("W", tag, msg, tr)
    fun w(tag: String, tr: Throwable?) = record("W", tag, tr?.message ?: "warning", tr)
    fun e(tag: String, msg: String) = record("E", tag, msg, null)
    fun e(tag: String, msg: String, tr: Throwable?) = record("E", tag, msg, tr)

    fun wtf(tag: String, msg: String) = record("F", tag, msg, null)
    fun wtf(tag: String, msg: String, tr: Throwable?) = record("F", tag, msg, tr)

    /** Returns the full buffered log (equivalent of `logcat -d -v time`). */
    fun dump(): String = synchronized(lock) { buffer.joinToString("\n") }

    fun clear() = synchronized(lock) { buffer.clear() }

    fun getStackTraceString(tr: Throwable?): String {
        if (tr == null) return ""
        val sw = java.io.StringWriter()
        tr.printStackTrace(java.io.PrintWriter(sw))
        return sw.toString()
    }

    fun isLoggable(tag: String, level: Int): Boolean = true

    const val VERBOSE = 2
    const val DEBUG = 3
    const val INFO = 4
    const val WARN = 5
    const val ERROR = 6
    const val ASSERT = 7
}
