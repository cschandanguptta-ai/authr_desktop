package android.content.res

/**
 * Desktop replacement for android.content.res.Resources / DisplayMetrics.
 * Density 1.0 keeps the dp math used by the pinch-zoom code consistent.
 */
class Resources {
    val displayMetrics: DisplayMetrics = DisplayMetrics()
    val configuration: Configuration = Configuration()
}

class DisplayMetrics {
    var density: Float = 1.0f
    var scaledDensity: Float = 1.0f
    var widthPixels: Int = 1280
    var heightPixels: Int = 800
    var xdpi: Float = 96f
    var ydpi: Float = 96f
    var densityDpi: Int = 160
}

class Configuration {
    var orientation: Int = ORIENTATION_LANDSCAPE

    companion object {
        const val ORIENTATION_PORTRAIT = 1
        const val ORIENTATION_LANDSCAPE = 2
    }
}
