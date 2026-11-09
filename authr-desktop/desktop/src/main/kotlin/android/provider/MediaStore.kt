package android.provider

import android.net.Uri

/**
 * Desktop replacement for android.provider.MediaStore / OpenableColumns.
 * The Downloads collection is backed by the ContentResolver shim, which
 * writes real files into ~/Downloads/Weaver_Studio (the app's export dir).
 */
object MediaStore {
    object Downloads {
        const val DISPLAY_NAME = "_display_name"
        const val MIME_TYPE = "mime_type"
        const val RELATIVE_PATH = "relative_path"
        const val IS_PENDING = "is_pending"
        val EXTERNAL_CONTENT_URI: Uri = Uri.parse("content://media/external/downloads")
    }

    object Images {
        const val DISPLAY_NAME = "_display_name"
        const val MIME_TYPE = "mime_type"
        const val RELATIVE_PATH = "relative_path"
    }
}

object OpenableColumns {
    const val DISPLAY_NAME = "_display_name"
    const val SIZE = "_size"
}
