package android.os

import java.io.File

/**
 * Desktop replacement for android.os.Environment.
 *
 * "External storage" maps to the user's real home directories:
 *  - DIRECTORY_DOWNLOADS -> ~/Downloads
 *  - DIRECTORY_DOCUMENTS -> ~/Documents
 */
object Environment {

    const val DIRECTORY_DOWNLOADS = "Downloads"       // Environment.DIRECTORY_DOWNLOADS == "Download" on Android; keep app-visible name
    const val DIRECTORY_DOCUMENTS = "Documents"
    const val DIRECTORY_MUSIC = "Music"
    const val DIRECTORY_PICTURES = "Pictures"
    const val DIRECTORY_MOVIES = "Movies"

    fun isExternalStorageEmulated(): Boolean = true
    fun isExternalStorageRemovable(): Boolean = false
    fun getExternalStorageState(): String = "mounted"
    fun getExternalStorageDirectory(): File = File(System.getProperty("user.home") ?: ".")

    /**
     * Returns the public directory for the given type ("Downloads", "Documents", ...),
     * creating it if necessary. Mirrors Android's public-storage semantics.
     */
    fun getExternalStoragePublicDirectory(type: String?): File {
        val home = File(System.getProperty("user.home") ?: ".")
        val dir = if (type.isNullOrBlank()) home else File(home, type)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
