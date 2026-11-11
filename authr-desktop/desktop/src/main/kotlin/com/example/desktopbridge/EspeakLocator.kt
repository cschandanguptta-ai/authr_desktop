package com.example.desktopbridge

import java.io.File

/**
 * Locates the espeak-ng binary for the desktop distribution (v3.0 P4, plan §7.3).
 *
 * espeak-ng already ships as a desktop dependency (the IPA phonemizer, plan
 * §7.1 "the desktop build reuses the bundled espeak-ng phonemizer"); this
 * locator follows the same resolution-order pattern as [FfmpegLocator] and
 * [PocketTtsBridge]:
 *
 *   1. <app dir>/espeak-ng/bin/espeak-ng   — bundled with the distribution
 *   2. ESPEAK_NG_BIN environment variable  — explicit user override
 *   3. system PATH                          — a normally installed espeak-ng
 *   4. ~/.authr/espeak-ng/espeak-ng        — user-installed copy
 *
 * Both `espeak-ng` and the legacy `espeak` name are accepted (the binary was
 * renamed between generations; either provides `--ipa`).
 */
object EspeakLocator {

    @Volatile private var cached: String? = null

    /** Path to a usable espeak-ng binary, or null when none is installed. */
    fun espeakPath(): String? {
        cached?.let { p -> if (File(p).canExecute()) return p; cached = null }
        return locate()?.also { cached = it }
    }

    /** True when a phonemizer binary is discoverable (used by the settings UI). */
    fun isAvailable(): Boolean = espeakPath() != null

    private fun locate(): String? {
        val names = if (isWindows()) listOf("espeak-ng.exe", "espeak.exe") else listOf("espeak-ng", "espeak")
        val candidates = ArrayList<String>()

        // 1. bundled next to the app
        appDirCandidates().forEach { dir ->
            names.forEach { n ->
                File(dir, n).let { if (it.canExecute()) candidates += it.absolutePath }
                File(dir, "bin/$n").let { if (it.canExecute()) candidates += it.absolutePath }
            }
        }
        // 2. explicit override
        System.getenv("ESPEAK_NG_BIN")?.let { candidates += it }
        // 3. PATH
        runCatching {
            val path = System.getenv("PATH") ?: ""
            for (dir in path.split(File.pathSeparator)) {
                if (dir.isBlank()) continue
                for (n in names) {
                    val exe = File(dir, n)
                    if (exe.canExecute()) { candidates += exe.absolutePath; break }
                }
            }
        }
        // 4. user-installed copy
        for (n in names) {
            candidates += File(File(System.getProperty("user.home"), ".authr/espeak-ng"), n).absolutePath
        }

        return candidates.firstOrNull { it.isNotBlank() && File(it).canExecute() }
            ?.also { cached = it }
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").lowercase().contains("win")

    private fun appDirCandidates(): List<File> {
        val out = ArrayList<File>()
        System.getProperty("compose.application.resources.dir")?.let { out += File(it) }
        System.getProperty("compose.application.app.dir")?.let { out += File(it) }
        runCatching {
            EspeakLocator::class.java.protectionDomain?.codeSource?.location?.toURI()?.let { uri ->
                File(uri).parentFile?.let { out += it }
            }
        }
        out += File(System.getProperty("user.dir"))
        return out
    }
}
