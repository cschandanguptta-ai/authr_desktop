package androidx.activity.compose

import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultContract
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import android.content.Context
import android.net.Uri
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Desktop replacement for androidx.activity.compose.rememberLauncherForActivityResult.
 *
 * Contracts are honored as follows:
 *  - GetContent / GetMultipleContents -> a native swing file picker; the
 *    selected file(s) are returned as file:// URIs which the ContentResolver
 *    shim can stream back into the app.
 *  - RequestMultiplePermissions -> always granted (desktop has no runtime
 *    permission system).
 */
object DesktopBackDispatcher {
    private val handlers = ArrayDeque<Pair<Boolean /*enabled*/, () -> Unit>>()

    fun push(enabled: Boolean, handler: () -> Unit) {
        handlers.addLast(enabled to handler)
    }

    fun pop(handler: () -> Unit) {
        val idx = handlers.indexOfLast { it.second === handler }
        if (idx >= 0) handlers.removeAt(idx)
    }

    /** Invoked by the top-level window when ESC is pressed. */
    fun dispatchBack(): Boolean {
        while (handlers.isNotEmpty()) {
            val (enabled, handler) = handlers.removeLast()
            if (enabled) {
                handler()
                return true
            }
        }
        return false
    }
}

@Composable
fun <I, O> rememberLauncherForActivityResult(
    contract: ActivityResultContract<I, O>,
    onResult: ActivityResultCallback<O>
): ActivityResultLauncher<I> = remember(contract) {
    ActivityResultLauncher(contract) { input ->
        val output: Any? = contract.launchDesktop(input)
        // Android semantics: cancelling the picker does NOT fire the callback.
        if (output != null) {
            @Suppress("UNCHECKED_CAST")
            (onResult as (Any?) -> Unit)(output)
        }
    }
}

/**
 * Desktop BackHandler: the window-level ESC handler pops the most recent
 * enabled handler (mirrors Android's system back gesture).
 */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    androidx.compose.runtime.DisposableEffect(enabled) {
        DesktopBackDispatcher.push(enabled, onBack)
        onDispose { DesktopBackDispatcher.pop(onBack) }
    }
}

private fun <I, O> ActivityResultContract<I, O>.launchDesktop(input: I): O? {
    return when (this) {
        is ActivityResultContracts.GetContent -> {
            @Suppress("UNCHECKED_CAST")
            pickFiles(false, input as? String, allowDirectories = false)
                ?.firstOrNull() as O?
        }
        is ActivityResultContracts.GetMultipleContents -> {
            @Suppress("UNCHECKED_CAST")
            pickFiles(true, input as? String, allowDirectories = false) as O?
        }
        is ActivityResultContracts.RequestMultiplePermissions -> {
            @Suppress("UNCHECKED_CAST")
            (input as? Array<String>)?.associate { it to true } as O?
        }
        else -> null
    }
}

private fun pickFiles(multi: Boolean, mimeType: String?, allowDirectories: Boolean): List<Uri>? {
    val chooser = JFileChooser()
    chooser.isMultiSelectionEnabled = multi
    chooser.fileSelectionMode =
        if (allowDirectories) JFileChooser.FILES_AND_DIRECTORIES else JFileChooser.FILES_ONLY
    when (mimeType) {
        "image/*", "image/png", "image/jpeg" -> chooser.fileFilter = FileNameExtensionFilter("Images (jpg, png, gif, webp, bmp)", "jpg", "jpeg", "png", "gif", "webp", "bmp")
        "audio/*", "audio/mpeg", "audio/wav" -> chooser.fileFilter = FileNameExtensionFilter("Audio (mp3, wav, m4a, ogg)", "mp3", "wav", "m4a", "ogg", "flac")
        "text/plain", "text/*" -> chooser.fileFilter = FileNameExtensionFilter("Text (txt, md, json)", "txt", "md", "json")
        "application/zip" -> chooser.fileFilter = FileNameExtensionFilter("Archives (zip, epub)", "zip", "epub")
        "application/epub+zip" -> chooser.fileFilter = FileNameExtensionFilter("EPUB books", "epub")
    }
    val result = if (SwingUtilities.isEventDispatchThread()) {
        chooser.showOpenDialog(null)
    } else {
        var r = JFileChooser.CANCEL_OPTION
        SwingUtilities.invokeAndWait { r = chooser.showOpenDialog(null) }
        r
    }
    return if (result == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFiles.ifEmpty { arrayOf(chooser.selectedFile) }
            .filterNotNull()
            .filter { it.isFile }
            .map { Uri.fromFile(it) }
    } else null
}
