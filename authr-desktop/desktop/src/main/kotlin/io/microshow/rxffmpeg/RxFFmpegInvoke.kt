package io.microshow.rxffmpeg

import com.example.desktopbridge.FfmpegLocator

/**
 * Desktop replacement for io.microshow.rxffmpeg.RxFFmpegInvoke.
 *
 * The Android original runs a bundled ffmpeg through JNI. On desktop the
 * identical command arrays are executed against an ffmpeg binary resolved
 * from (in order):
 *   1. the app's distribution "ffmpeg" directory (bundled build)
 *   2. the PATH
 * Return codes match: 0 = success.
 */
object RxFFmpegInvoke {

    fun getInstance(): RxFFmpegInvoke = this

    /**
     * Runs the given ffmpeg arguments (without the binary name, matching the
     * RxFFmpeg convention: the app passes "-y -i in.mp3 -af ... out.wav").
     */
    fun runCommand(cmd: Array<String>, @Suppress("UNUSED_PARAMETER") callback: Any?): Int {
        val ffmpeg = FfmpegLocator.ffmpegPath()
        if (ffmpeg == null) {
            android.util.Log.e("RxFFmpegInvoke", "ffmpeg not found. Install ffmpeg or bundle it next to the app.")
            return -1
        }
        return try {
            val full = arrayOf(ffmpeg) + cmd
            android.util.Log.d("RxFFmpegInvoke", "exec: ${full.joinToString(" ")}")
            val process = ProcessBuilder(*full)
                .redirectErrorStream(false)
                .start()
            // Drain output so a full pipe never deadlocks the child.
            Thread { try { process.errorStream.bufferedReader().forEachLine { android.util.Log.d("ffmpeg", it) } } catch (_: Exception) {} }.start()
            Thread { try { process.inputStream.bufferedReader().forEachLine { android.util.Log.d("ffmpeg", it) } } catch (_: Exception) {} }.start()
            process.waitFor()
        } catch (e: Exception) {
            android.util.Log.e("RxFFmpegInvoke", "ffmpeg failed: ${e.message}", e)
            -1
        }
    }

    fun exit() {
        // desktop: nothing to dispose (no JNI state)
    }
}
