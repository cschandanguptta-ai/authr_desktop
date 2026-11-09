package android.content

import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Desktop replacement for android.content.ContentResolver.
 *
 * The app only resolves its own exported files (MediaStore Downloads entries
 * and picked local files), so the resolver is backed by a registry that maps
 * content:// URIs to real files on disk.
 */
class ContentResolver(private val context: Context) {

    /** scheme://authority/path -> real backing file */
    private val registry = LinkedHashMap<String, File>()

    fun register(uri: Uri, file: File) {
        registry[uri.toString()] = file
    }

    fun resolve(uri: Uri): File? {
        registry[uri.toString()]?.let { return it }
        // Any file:// URI resolves directly
        if (uri.scheme == "file") return File(uri.path ?: return null)
        return null
    }

    fun insert(uri: Uri, values: ContentValues): Uri? {
        return try {
            if (uri.authority == "mediastore" || uri.authority?.contains("media") == true) {
                val displayName = values.getAsString(android.provider.MediaStore.Downloads.DISPLAY_NAME)
                    ?: "export_${System.currentTimeMillis()}"
                val relPath = values.getAsString(android.provider.MediaStore.Downloads.RELATIVE_PATH)
                    ?: android.os.Environment.DIRECTORY_DOWNLOADS
                val mime = values.getAsString(android.provider.MediaStore.Downloads.MIME_TYPE) ?: ""
                var targetDir = File(android.os.Environment.getExternalStoragePublicDirectory(relPath), "Weaver_Studio")
                if (!targetDir.exists()) targetDir.mkdirs()
                var target = File(targetDir, displayName)
                var counter = 1
                val base = target.nameWithoutExtension
                val ext = target.extension
                while (target.exists()) {
                    target = File(targetDir, if (ext.isBlank()) "${base}_$counter" else "${base}_${counter}.$ext")
                    counter++
                }
                val newUri = Uri.fromFile(target)
                register(newUri, target)
                newUri
            } else null
        } catch (e: Exception) {
            android.util.Log.e("ContentResolver", "insert failed", e)
            null
        }
    }

    fun openInputStream(uri: Uri): InputStream? {
        return try {
            val f = resolve(uri) ?: return null
            if (f.exists()) FileInputStream(f) else null
        } catch (e: Exception) {
            android.util.Log.e("ContentResolver", "openInputStream failed for $uri", e)
            null
        }
    }

    fun openOutputStream(uri: Uri, mode: String = "w"): OutputStream? {
        return try {
            val f = resolve(uri) ?: return null
            f.parentFile?.mkdirs()
            FileOutputStream(f, mode.contains("a"))
        } catch (e: Exception) {
            android.util.Log.e("ContentResolver", "openOutputStream failed for $uri", e)
            null
        }
    }

    fun openFileDescriptor(uri: Uri, mode: String) = resolve(uri)?.let { java.io.RandomAccessFile(it, mode) }

    /**
     * Cursor query used to obtain the display name of picked files.
     * Returns null when the URI is unknown; callers fall back to the URI path.
     */
    fun query(
        uri: Uri,
        projection: Array<out String?>?,
        selection: String?,
        selectionArgs: Array<out String?>?,
        sortOrder: String?
    ): Cursor? {
        val file = resolve(uri) ?: return null
        return DesktopCursor(file.name)
    }

    interface Cursor {
        fun moveToFirst(): Boolean
        fun moveToNext(): Boolean
        fun close()
        fun getString(columnIndex: Int): String
        fun getColumnIndex(columnName: String): Int
        fun getColumnIndexOrThrow(columnName: String): Int
        fun getCount(): Int
    }

    private class DesktopCursor(private val displayName: String) : Cursor {
        private var row = -1
        override fun moveToFirst(): Boolean { row = 0; return true }
        override fun moveToNext(): Boolean { row += 1; return false }
        override fun close() {}
        override fun getString(columnIndex: Int): String = displayName
        override fun getColumnIndex(columnName: String): Int =
            if (columnName == "_display_name") 0 else -1
        override fun getColumnIndexOrThrow(columnName: String): Int =
            if (columnName == "_display_name") 0 else throw IllegalArgumentException(columnName)
        override fun getCount(): Int = 1
    }
}
