package androidx.compose.ui.graphics

/**
 * Desktop bridge: converts the android.graphics.Bitmap shim into a Compose
 * ImageBitmap (used by the cover-art preview on the metadata screen).
 */
fun android.graphics.Bitmap.asImageBitmap(): ImageBitmap {
    val png = this.toPngBytes()
    val skiaImage = org.jetbrains.skia.Image.makeFromEncoded(png)
    return skiaImage.toComposeImageBitmap()
}
