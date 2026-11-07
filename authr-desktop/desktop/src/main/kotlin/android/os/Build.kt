package android.os

/**
 * Desktop replacement for android.os.Build.
 *
 * SDK_INT is pinned to 30 (Android 11) deliberately:
 *  - below TIRAMISU(33): the POST_NOTIFICATIONS runtime-permission branch is skipped
 *  - below S(31): the Material You dynamic color branch is skipped (not available on desktop)
 *  - above Q(29): the legacy WRITE_EXTERNAL_STORAGE branch is skipped
 * which yields sensible desktop behavior without touching app logic.
 */
object Build {
    const val MODEL: String = "Desktop"
    const val MANUFACTURER: String = "AuthrOS"
    const val BRAND: String = "authr"
    const val DEVICE: String = "desktop"
    const val PRODUCT: String = "desktop"
    const val HARDWARE: String = "desktop"
    val SUPPORTED_ABIS: Array<String> = arrayOf(System.getProperty("os.arch") ?: "x86_64")
    val DISPLAY: String = "${System.getProperty("os.name")} (${System.getProperty("os.version")})"

    object VERSION {
        const val SDK_INT = 30
        const val RELEASE = "11 (Desktop)"
        const val CODENAME = "REL"
    }

    object VERSION_CODES {
        const val M = 23
        const val N = 24
        const val O = 26
        const val P = 28
        const val Q = 29
        const val R = 30
        const val S = 31
        const val TIRAMISU = 33
    }
}
