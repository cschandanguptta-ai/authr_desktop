package android.graphics

import java.awt.BasicStroke
import java.awt.Color as AwtColor
import java.awt.Graphics2D
import java.awt.RenderingHints

/**
 * Desktop replacement for android.graphics.Paint / Canvas / Color.
 * AWT-backed; supports the exact drawing operations the app performs
 * (solid fills, bitmaps and wrapped text via android.text.StaticLayout).
 */
class Paint {
    var color: Int = 0xFF000000.toInt()
    var textSize: Float = 14f
    var isAntiAlias: Boolean = true
    var isFakeBoldText: Boolean = false
    var isUnderlineText: Boolean = false
    var strokeWidth: Float = 1f
    var style: Style = Style.FILL
    var textAlign: Align = Align.LEFT
    var typeface: Typeface? = null

    enum class Style { FILL, STROKE, FILL_AND_STROKE }

    enum class Align { LEFT, CENTER, RIGHT }

    fun setColor(color: Int) { this.color = color }
    fun getTextWidths(text: String): FloatArray = floatArrayOf(measureText(text))
    fun measureText(text: String): Float {
        val fm = fontMetrics()
        return fm.stringWidth(text).toFloat()
    }
    fun ascent(): Float = -fontMetrics().ascent
    fun descent(): Float = fontMetrics().descent.toFloat()

    fun fontMetrics(): java.awt.FontMetrics {
        val f = awtFont()
        return java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            .createGraphics().getFontMetrics(f)
    }

    fun awtFont(): java.awt.Font {
        val base = typeface?.awtFont() ?: java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 12)
        val styleBits = (if (isFakeBoldText || base.isBold) java.awt.Font.BOLD else 0) or
                (if (isUnderlineText) java.awt.Font.ITALIC else 0)
        return base.deriveFont(styleBits, textSize)
    }

    fun configure(g: Graphics2D) {
        g.color = AwtColor(color, true)
        g.font = awtFont()
        if (isAntiAlias) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        }
        if (style == Style.STROKE) {
            g.stroke = BasicStroke(strokeWidth)
        }
    }
}

class Typeface private constructor(internal val family: String, internal val styleBits: Int, internal val bold: Boolean) {
    internal fun awtFont(): java.awt.Font = java.awt.Font(family, styleBits, 12)

    companion object {
        @JvmStatic val DEFAULT = Typeface(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, false)
        @JvmStatic val DEFAULT_BOLD = Typeface(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, true)
        @JvmStatic val SANS_SERIF = DEFAULT
        @JvmStatic val SERIF = Typeface(java.awt.Font.SERIF, java.awt.Font.PLAIN, false)
        @JvmStatic val MONOSPACE = Typeface(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, false)

        @JvmStatic
        fun create(family: String?, style: Int): Typeface {
            val awtFamily = when (family) {
                "serif" -> java.awt.Font.SERIF
                "monospace" -> java.awt.Font.MONOSPACED
                "sans-serif", null -> java.awt.Font.SANS_SERIF
                else -> family
            }
            return Typeface(awtFamily, style, style and java.awt.Font.BOLD != 0)
        }

        @JvmStatic
        fun defaultFromStyle(style: Int): Typeface = create(null, style)
    }
}

class Canvas(val bitmap: Bitmap) {

    private val g: Graphics2D = bitmap.image.createGraphics()

    init {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    }

    fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        paint.configure(g)
        val x = minOf(left, right); val y = minOf(top, bottom)
        val w = kotlin.math.abs(right - left); val h = kotlin.math.abs(bottom - top)
        if (paint.style == Paint.Style.FILL) {
            g.fillRect(x.toInt(), y.toInt(), w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))
        } else {
            g.drawRect(x.toInt(), y.toInt(), w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))
        }
    }

    fun drawBitmap(bitmap: Bitmap, left: Float, top: Float, paint: Paint?) {
        g.drawImage(bitmap.image, left.toInt(), top.toInt(), null)
    }

    fun drawColor(color: Int) {
        g.color = AwtColor(color, true)
        g.fillRect(0, 0, bitmap.width, bitmap.height)
    }

    fun drawText(text: String, x: Float, y: Float, paint: Paint) {
        paint.configure(g)
        g.drawString(text, x, y)
    }

    fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
        paint.configure(g)
        g.drawLine(startX.toInt(), startY.toInt(), stopX.toInt(), stopY.toInt())
    }

    fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {
        paint.configure(g)
        g.fillOval((cx - radius).toInt(), (cy - radius).toInt(), (radius * 2).toInt(), (radius * 2).toInt())
    }

    fun save(): Int { g.create(); return 0 }
    fun restore() {}
    fun setBitmap(bitmap: Bitmap) { /* canvas is bound at construction on desktop */ }

    /** Desktop extension: direct access for the text layout shim. */
    fun graphics2D(): Graphics2D = g
}

object Color {
    const val BLACK: Int = -0x1000000
    const val DKGRAY: Int = -0xbbbbbc
    const val GRAY: Int = -0x777778
    const val LTGRAY: Int = -0x333334
    const val WHITE: Int = -0x1
    const val RED: Int = -0x10000
    const val GREEN: Int = -0xff0100
    const val BLUE: Int = -0xffff01
    const val YELLOW: Int = -0x100
    const val CYAN: Int = -0xff0001
    const val MAGENTA: Int = -0xff01
    const val TRANSPARENT: Int = 0

    @JvmStatic
    fun parseColor(colorString: String): Int {
        return parseColorOrNull(colorString)
            ?: throw IllegalArgumentException("Unknown color: $colorString")
    }

    fun parseColorOrNull(colorString: String): Int? {
        val s = colorString.trim()
        if (!s.startsWith("#")) return null
        val hex = s.substring(1)
        return try {
            when (hex.length) {
                3 -> {
                    val r = hex[0].digitToIntOrNull(16) ?: return null
                    val g = hex[1].digitToIntOrNull(16) ?: return null
                    val b = hex[2].digitToIntOrNull(16) ?: return null
                    (0xFF shl 24) or (r * 17 shl 16) or (g * 17 shl 8) or (b * 17)
                }
                6 -> {
                    val v = hex.toIntOrNull(16) ?: return null
                    (0xFF shl 24) or v
                }
                8 -> hex.toLongOrNull(16)?.toInt()
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    @JvmStatic fun alpha(color: Int): Int = color ushr 24
    @JvmStatic fun red(color: Int): Int = (color shr 16) and 0xFF
    @JvmStatic fun green(color: Int): Int = (color shr 8) and 0xFF
    @JvmStatic fun blue(color: Int): Int = color and 0xFF
    @JvmStatic fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    @JvmStatic fun argb(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) or (r shl 16) or (g shl 8) or b
}
