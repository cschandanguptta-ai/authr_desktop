package androidx.core.view

/**
 * Desktop replacement for androidx.core.view.WindowCompat (no-op window
 * insets on desktop; keeps the theme code compiling 1:1).
 */
object WindowCompat {
    @JvmStatic
    fun getInsetsController(window: Any?, view: Any?): WindowInsetsControllerCompat =
        WindowInsetsControllerCompat()
}

class WindowInsetsControllerCompat {
    var isAppearanceLightStatusBars: Boolean = true
    var isAppearanceLightNavigationBars: Boolean = true
    fun setSystemBarsAppearance(appearance: Boolean, mask: Int) {}
    companion object {
        const val APPEARANCE_LIGHT_STATUS_BARS = 0x00000008
        const val APPEARANCE_LIGHT_NAVIGATION_BARS = 0x00000010
    }
}
