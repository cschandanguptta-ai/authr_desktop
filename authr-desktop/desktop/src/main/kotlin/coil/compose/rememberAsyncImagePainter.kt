package coil.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Desktop replacement for coil.compose.rememberAsyncImagePainter.
 *
 * Loads images from file:// URIs, plain paths and http(s) URLs in the
 * background (OkHttp) and decodes them with Skia — the same observable
 * behavior Coil provides on Android (empty painter, then the image).
 */
private val client: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}

@Composable
fun rememberAsyncImagePainter(model: Any?): Painter {
    var painter by remember(model) { mutableStateOf<Painter>(EmptyPainter) }

    LaunchedEffect(model) {
        if (model == null) {
            painter = EmptyPainter
            return@LaunchedEffect
        }
        val loaded = withContext(Dispatchers.IO) {
            try {
                val bytes: ByteArray? = when (model) {
                    is String -> when {
                        model.startsWith("http://") || model.startsWith("https://") ->
                            client.newCall(Request.Builder().url(model).build()).execute()
                                .use { response -> if (response.isSuccessful) response.body?.bytes() else null }
                        model.startsWith("file://") -> File(model.removePrefix("file://")).readBytes()
                        else -> File(model).takeIf { it.exists() }?.readBytes()
                    }
                    is File -> model.takeIf { it.exists() }?.readBytes()
                    is android.net.Uri -> {
                        val path = model.path
                        if (path != null && File(path).exists()) File(path).readBytes() else null
                    }
                    else -> null
                }
                bytes?.let { org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap() }
            } catch (e: Exception) {
                android.util.Log.w("coil", "image load failed for $model: ${e.message}")
                null
            }
        }
        if (loaded != null) {
            val bitmap: ImageBitmap = loaded
            painter = BitmapPainter(bitmap)
        }
    }

    return painter
}

private object EmptyPainter : Painter() {
    override val intrinsicSize: Size get() = Size.Unspecified
    override fun DrawScope.onDraw() {}
}
