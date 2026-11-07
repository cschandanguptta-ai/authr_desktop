package android.os

/** Desktop replacement for android.os.Process. */
object Process {
    fun myPid(): Int = java.lang.management.ManagementFactory.getRuntimeMXBean().let {
        it.name.substringBefore("@").toIntOrNull() ?: 0
    }
    fun myUid(): Int = 10001
}
