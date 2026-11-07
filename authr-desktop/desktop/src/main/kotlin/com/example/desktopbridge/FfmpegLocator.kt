package com.example.desktopbridge

import java.io.File

/**
 * Locates the ffmpeg/ffprobe binaries for the desktop distribution.
 *
 * Resolution order (user-selected "auto-detect + bundle" strategy):
 *   1. <app dir>/ffmpeg/bin/ffmpeg   — bundled with the distribution
 *      (app dir = the directory containing the running jar, or the
 *      compose.application.resources.dir for packaged apps)
 *   2. FFMPEG_HOME/bin/ffmpeg         — explicit user override
 *   3. system PATH                    — a normally installed ffmpeg
 */
object FfmpegLocator {

    @Volatile private var cachedFfmpeg: String? = null
    @Volatile private var cachedProbe: String? = null

    fun ffmpegPath(): String? {
        cachedFfmpeg?.let { return it }
        return locate("ffmpeg")?.also { cachedFfmpeg = it }
    }

    fun ffprobePath(): String? {
        cachedProbe?.let { return it }
        return locate("ffprobe")?.also { cachedProbe = it }
    }

    private fun locate(binary: String): String? {
        val candidates = ArrayList<String>()

        // 1. bundled next to the app
        appDirCandidates().forEach { dir ->
            val exe = File(dir, if (isWindows()) "$binary.exe" else binary)
            if (exe.canExecute()) candidates += exe.absolutePath
            val inBin = File(dir, "bin/$binary")
            if (inBin.canExecute()) candidates += inBin.absolutePath
        }

        // 2. FFMPEG_HOME
        System.getenv("FFMPEG_HOME")?.let { home ->
            val exe = File(home, if (isWindows()) "$binary.exe" else binary)
            if (exe.canExecute()) candidates += exe.absolutePath
        }

        // 3. PATH
        try {
            val path = System.getenv("PATH") ?: ""
            for (dir in path.split(File.pathSeparator)) {
                if (dir.isBlank()) continue
                val exe = File(dir, if (isWindows()) "$binary.exe" else binary)
                if (exe.canExecute()) { candidates += exe.absolutePath; break }
            }
        } catch (_: Exception) {}

        return candidates.firstOrNull { it != null }
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").lowercase().contains("win")

    private fun appDirCandidates(): List<File> {
        val out = ArrayList<File>()
        // packaged runtime resources dir
        System.getProperty("compose.application.resources.dir")?.let { out += File(it) }
        System.getProperty("compose.application.app.dir")?.let { out += File(it) }
        // directory of the running jar
        runCatching {
            com.example.desktopbridge.FfmpegLocator::class.java
                .protectionDomain?.codeSource?.location?.toURI()?.let { uri ->
                    val jar = File(uri)
                    out += jar.parentFile
                }
        }
        // working directory / common install locations
        out += File(System.getProperty("user.dir"))
        out += File(System.getProperty("user.home"), ".authr/ffmpeg")
        return out
    }
}
