package android.text

import android.graphics.Canvas
import android.graphics.Paint
import java.awt.font.FontRenderContext
import java.awt.font.LineBreakMeasurer
import java.text.AttributedString

/**
 * Desktop replacement for android.text.StaticLayout / TextPaint / Layout.
 *
 * Wraps text with AWT's LineBreakMeasurer so wrapped title rendering on the
 * generated cover art matches Android's StaticLayout behavior
 * (line spacing multiplier supported).
 */
open class Layout {
    object Alignment {
        val ALIGN_NORMAL = 0
        val ALIGN_OPPOSITE = 1
        val ALIGN_CENTER = 2
    }
}

class TextPaint : Paint() {
    var letterSpacing: Float = 0f
}

class StaticLayout private constructor(
    val text: String,
    val paint: TextPaint,
    val width: Int,
    val alignment: Int,
    val spacingMult: Float,
    val spacingAdd: Float,
    @Suppress("UNUSED_PARAMETER") includePad: Boolean
) : Layout() {

    val lines = ArrayList<String>()
    private val lineHeight: Float
    val height: Int

    init {
        val frc = FontRenderContext(null, true, false)
        val font = paint.awtFont()
        val metrics = java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            .createGraphics().getFontMetrics(font)
        val fmLineHeight = metrics.height.toFloat()
        val measurer = LineBreakMeasurer(AttributedString(text, mapOf(
            java.awt.font.TextAttribute.FONT to font
        )).iterator, frc)
        while (measurer.position < text.length) {
            val next = measurer.nextOffset(width.toFloat())
            val start = measurer.position
            val line = text.substring(start, next.coerceAtMost(text.length))
            lines.add(line)
            measurer.position = next.coerceAtMost(text.length)
            if (next <= start) break
        }
        lineHeight = fmLineHeight * spacingMult + spacingAdd
        height = (lines.size * lineHeight).toInt().coerceAtLeast(0)
    }

    fun draw(canvas: Canvas) {
        paint.configure(canvas.graphics2D())
        val fm = paint.fontMetrics()
        var y = fm.ascent.toFloat()
        for (line in lines) {
            val x = when (alignment) {
                Layout.Alignment.ALIGN_CENTER -> (width - fm.stringWidth(line)) / 2f
                Layout.Alignment.ALIGN_OPPOSITE -> (width - fm.stringWidth(line)).toFloat()
                else -> 0f
            }
            canvas.graphics2D().drawString(line, x, y)
            y += lineHeight
        }
    }

    val lineCount: Int get() = lines.size

    class Builder(private val source: CharSequence, private val start: Int, private val end: Int,
                  private val paint: TextPaint, private val width: Int) {
        private var alignment = Layout.Alignment.ALIGN_NORMAL
        private var spacingMult = 1f
        private var spacingAdd = 0f
        private var includePad = true

        fun setAlignment(alignment: Int): Builder { this.alignment = alignment; return this }
        fun setLineSpacing(add: Float, mult: Float): Builder { spacingAdd = add; spacingMult = mult; return this }
        fun setIncludePad(include: Boolean): Builder { includePad = include; return this }

        fun build(): StaticLayout = StaticLayout(
            source.substring(start, end), paint, width, alignment, spacingMult, spacingAdd, includePad
        )
    }

    companion object {
        @JvmStatic
        fun obtain(source: CharSequence, start: Int, end: Int, paint: TextPaint, width: Int): Builder =
            Builder(source, start, end, paint, width)
    }
}
