package android.graphics

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.imageio.ImageIO

/**
 * Desktop replacement for android.graphics.Bitmap, backed by a
 * java.awt.image.BufferedImage.
 */
class Bitmap(val image: BufferedImage) {

    val width: Int get() = image.width
    val height: Int get() = image.height

    fun copy(config: Config, isMutable: Boolean): Bitmap {
        val newImage = BufferedImage(image.colorModel, image.copyData(null), image.isAlphaPremultiplied, null)
        return Bitmap(newImage)
    }

    fun compress(format: CompressFormat, quality: Int, stream: OutputStream): Boolean {
        return try {
            when (format) {
                CompressFormat.JPEG -> {
                    // JPEG has no alpha; draw onto an opaque background first
                    val rgb = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
                    val g = rgb.createGraphics()
                    g.drawImage(image, 0, 0, null)
                    g.dispose()
                    val ios = ImageIO.createImageOutputStream(stream)
                    val writer = ImageIO.getImageWritersByFormatName("jpg").next()
                    writer.output = ios
                    val param = writer.defaultWriteParam
                    param.compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
                    param.compressionQuality = quality / 100f
                    writer.write(null, javax.imageio.IIOImage(rgb, null, null), param)
                    writer.dispose()
                    ios.flush()
                    true
                }
                CompressFormat.PNG -> ImageIO.write(image, "png", stream)
                CompressFormat.WEBP -> ImageIO.write(image, "png", stream) // desktop fallback: PNG container
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("Bitmap", "compress failed", e)
            false
        }
    }

    fun recycle() { /* GC handles it on desktop */ }
    val isRecycled: Boolean get() = false

    fun setPixel(x: Int, y: Int, color: Int) { image.setRGB(x, y, color) }
    fun getPixel(x: Int, y: Int): Int = image.getRGB(x, y)

    fun toPngBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }

    enum class Config { ARGB_8888, RGB_565, ALPHA_8 }

    enum class CompressFormat { JPEG, PNG, WEBP }

    companion object {
        @JvmStatic
        fun createBitmap(width: Int, height: Int, config: Config): Bitmap {
            val type = if (config == Config.RGB_565) BufferedImage.TYPE_USHORT_565_RGB
            else BufferedImage.TYPE_INT_ARGB
            return Bitmap(BufferedImage(width.coerceAtLeast(1), height.coerceAtLeast(1), type))
        }

        @JvmStatic
        fun createScaledBitmap(source: Bitmap, width: Int, height: Int, filter: Boolean): Bitmap {
            val scaled = BufferedImage(width.coerceAtLeast(1), height.coerceAtLeast(1),
                if (source.image.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
            val g = scaled.createGraphics()
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                if (filter) java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR
                else java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            g.drawImage(source.image, 0, 0, width, height, null)
            g.dispose()
            return Bitmap(scaled)
        }

        @JvmStatic
        fun createBitmap(source: Bitmap): Bitmap = source.copy(Config.ARGB_8888, true)
    }
}

/**
 * Desktop replacement for android.graphics.BitmapFactory.
 */
object BitmapFactory {

    class Options {
        var inJustDecodeBounds: Boolean = false
        var inSampleSize: Int = 1
        var inPreferredConfig: Bitmap.Config = Bitmap.Config.ARGB_8888
        var outWidth: Int = -1
        var outHeight: Int = -1
        var outMimeType: String? = null
    }

    fun decodeFile(pathName: String, opts: Options? = null): Bitmap? = decode(InputStream_ = null, file = File(pathName), bytes = null, opts = opts)

    fun decodeStream(`is`: InputStream, outPadding: Any? = null, opts: Options? = null): Bitmap? =
        decode(InputStream_ = `is`, file = null, bytes = null, opts = opts)

    fun decodeByteArray(data: ByteArray, offset: Int, length: Int, opts: Options? = null): Bitmap? =
        decode(InputStream_ = null, file = null, bytes = data.copyOfRange(offset, offset + length), opts = opts)

    private fun decode(InputStream_ : InputStream?, file: File?, bytes: ByteArray?, opts: Options?): Bitmap? {
        return try {
            val img: BufferedImage? = when {
                file != null -> if (file.exists()) ImageIO.read(file) else null
                bytes != null -> ImageIO.read(bytes.inputStream())
                else -> InputStream_?.use { ImageIO.read(it) }
            }
            if (opts?.inJustDecodeBounds == true) {
                if (img != null) {
                    opts.outWidth = img.width
                    opts.outHeight = img.height
                    opts.outMimeType = "image/png"
                }
                return null
            }
            if (img == null) return null
            var result = Bitmap(img)
            val sample = opts?.inSampleSize ?: 1
            if (sample > 1) {
                result = Bitmap.createScaledBitmap(result, (img.width / sample).coerceAtLeast(1),
                    (img.height / sample).coerceAtLeast(1), true)
            }
            result
        } catch (e: Exception) {
            android.util.Log.w("BitmapFactory", "decode failed: ${e.message}")
            null
        }
    }
}
