package com.example.desktopbridge

import java.io.File

/**
 * Locates the epubcheck tool for the desktop distribution (v3.0 P5, plan §11.1).
 *
 * epubcheck is the EXTERNAL EPUB 3 conformance gate ("the export must pass
 * epubcheck with zero errors before it is offered for upload"). It is not
 * bundled with the app; this locator follows the same resolution-order
 * pattern as [EspeakLocator] / [FfmpegLocator]:
 *
 *   1. EPUBCHECK_JAR environment variable — a path to epubcheck.jar (its
 *      lib/ siblings must sit beside it; that is the official dist layout)
 *   2. EPUBCHECK_HOME environment variable — the dist directory
 *   3. <app dir>/epubcheck/epubcheck.jar  — a copy dropped next to the app
 *   4. ~/.authr/epubcheck/epubcheck.jar   — a user-installed copy
 *
 * The located invocation is `java -jar <jar>` (the tool itself is Java;
 * the app already requires a JRE). When nothing is found the packager
 * still produces artifacts, but the ship report discloses the epubcheck
 * verdict as UNVERIFIED — never silently green (ADR-0010 discipline).
 */
object EpubCheckLocator {

    @Volatile private var cached: String? = null

    /** Absolute path to a runnable epubcheck.jar, or null when absent. */
    fun epubCheckJar(): String? {
        cached?.let { p -> if (File(p).canRead()) return p; cached = null }
        return locate()?.also { cached = it }
    }

    /** True when the external gate is locally runnable. */
    fun isAvailable(): Boolean = epubCheckJar() != null

    /** The command that runs epubcheck on one file. */
    fun commandFor(epubFile: File): List<String>? {
        val jar = epubCheckJar() ?: return null
        return listOf(javaBin(), "-jar", jar, epubFile.absolutePath)
    }

    fun javaBin(): String {
        val javaHome = System.getProperty("java.home")
        val exe = if (System.getProperty("os.name").lowercase().contains("win")) "java.exe" else "java"
        return File(javaHome, "bin/$exe").absolutePath
    }

    private fun locate(): String? {
        val candidates = ArrayList<String>()

        // 1. explicit jar override
        System.getenv("EPUBCHECK_JAR")?.let { candidates += it }
        // 2. explicit home (dist directory containing epubcheck.jar + lib/)
        System.getenv("EPUBCHECK_HOME")?.let { home ->
            candidates += File(home, "epubcheck.jar").absolutePath
        }
        // 3. bundled next to the app / cwd
        val appDirs = ArrayList<File>()
        System.getProperty("compose.application.resources.dir")?.let { appDirs += File(it) }
        runCatching {
            EpubCheckLocator::class.java.protectionDomain?.codeSource?.location?.toURI()?.let { uri ->
                File(uri).parentFile?.let { appDirs += it }
            }
        }
        appDirs += File(System.getProperty("user.dir"))
        appDirs += File(System.getProperty("user.dir"), ".build-extras")
        for (dir in appDirs) {
            candidates += File(dir, "epubcheck/epubcheck.jar").absolutePath
            candidates += File(dir, "epubcheck-dist/epubcheck-5.2.1/epubcheck.jar").absolutePath
            candidates += File(dir, "epubcheck-dist/epubcheck.jar").absolutePath
        }
        // 4. user-installed copy
        candidates += File(File(System.getProperty("user.home"), ".authr/epubcheck"), "epubcheck.jar").absolutePath

        return candidates.firstOrNull { it.isNotBlank() && File(it).canRead() }
            ?.also { cached = it }
    }
}
