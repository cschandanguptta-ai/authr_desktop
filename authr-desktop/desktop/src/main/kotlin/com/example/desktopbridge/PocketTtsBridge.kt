package com.example.desktopbridge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Pocket TTS desktop process bridge (v3.0 P0 spike, plan §7.1 / Table 23).
 *
 * Pocket TTS (Kyutai Labs, MIT) ships standalone, dependency-free binaries for
 * Linux / macOS / Windows. The bridge invokes the binary as a child process —
 * the same strategy the port already uses for ffmpeg — so no Python runtime is
 * pulled into the distribution.
 *
 * Resolution order for the binary:
 *   1. <app dir>/pocket-tts/<os>/pocket-tts        — bundled with the distribution
 *   2. POCKET_TTS_BIN environment variable          — explicit user override
 *   3. PATH                                         — a normally installed binary
 *   4. ~/.authr/pocket-tts/pocket-tts               — user-installed model pack dir
 *
 * Voice packs (cloned narrator voices compiled to safetensors, plan §7.4) live
 * in <data>/audio/voice_packs and are referenced by name; a fixed voice state
 * reproduces the same narrator identity across chapters by construction.
 *
 * If no binary is present the bridge reports [Status.MISSING] and the manager
 * degrades gracefully to the Edge TTS route (plan: "cloud engines remain
 * configurable per agent for scale and quality, never as a hard dependency").
 */
object PocketTtsBridge {

    enum class Status { MISSING, READY, SYNTHESIZING }

    data class VoicePack(val name: String, val file: File)

    @Volatile var synthesizeTimeoutMs: Long = 15 * 60 * 1000L

    private val _status = MutableStateFlow(Status.MISSING)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    @Volatile private var cachedBinary: String? = null

    /** Returns the voice pack for [packName], or null when it does not exist yet. */
    fun resolveVoicePack(packName: String, packsDir: File): VoicePack? {
        val f = File(packsDir, "$packName.safetensors")
        return if (f.isFile) VoicePack(packName, f) else null
    }

    /** Lists every installed voice pack (name only, sorted). */
    fun listVoicePacks(packsDir: File): List<String> =
        packsDir.listFiles { f -> f.isFile && f.name.endsWith(".safetensors") }
            ?.map { it.name.removeSuffix(".safetensors") }
            ?.sorted()
            ?: emptyList()

    /**
     * Synthesizes [text] with the narrator voice [packName] into [outputFile]
     * (16-bit 44.1 kHz WAV). Suspend + IO dispatcher; returns true on success.
     */
    suspend fun synthesizeToFile(
        text: String,
        outputFile: File,
        packName: String,
        packsDir: File,
        temperature: Double = 0.6
    ): Boolean = withContext(Dispatchers.IO) {
        val binary = locateBinary()
        if (binary == null) {
            _lastError.value = "Pocket TTS binary not found (install to <app>/pocket-tts or set POCKET_TTS_BIN)"
            _status.value = Status.MISSING
            return@withContext false
        }
        _status.value = Status.SYNTHESIZING
        try {
            outputFile.parentFile?.mkdirs()
            // Invocation mirrors the verified Kyutai `pocket-tts` CLI (v4.0.1):
            //   pocket-tts generate --text T --voice V --output-path O --temperature T
            // Voice packs (.safetensors produced by `export-voice`) feed the
            // --voice argument directly; built-in narrator names (e.g. alba)
            // resolve inside the binary. Measured on the reference machine:
            // 10,010 words -> 47.5 min audio in 6.5 min wall (7.3x real time).
            val args = mutableListOf(
                binary,
                "generate",
                "--voice", packName,
                "--temperature", temperature.toString(),
                "--output-path", outputFile.absolutePath,
                "--text", text
            )
            val proc = ProcessBuilder(args).apply {
                redirectErrorStream(false)
            }.start()

            // Drain stderr so a chatty model can never deadlock the pipe.
            val stderrDrain = Thread {
                proc.errorStream.use { it.readBytes() }
            }.apply { isDaemon = true; start() }

            val finished = proc.waitFor(synthesizeTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!finished) {
                proc.destroyForcibly()
                _lastError.value = "Pocket TTS synthesis timed out after ${synthesizeTimeoutMs}ms"
                return@withContext false
            }
            stderrDrain.join(1_000)
            val ok = proc.exitValue() == 0 && outputFile.exists() && outputFile.length() > 0
            if (!ok) _lastError.value = "Pocket TTS exited with code ${proc.exitValue()}"
            _status.value = if (ok) Status.READY else Status.MISSING
            ok
        } catch (e: Throwable) {
            _lastError.value = e.message ?: e.javaClass.simpleName
            _status.value = Status.MISSING
            false
        }
    }

    /**
     * Compiles a narrator WAV reference into a safetensors voice pack
     * (plan §7.1: "get_state_for_audio_prompt; export-voice compiles a
     * reference into a safetensors voice pack for instant reload").
     */
    suspend fun cloneVoice(
        referenceWav: File,
        packName: String,
        packsDir: File
    ): Boolean = withContext(Dispatchers.IO) {
        val binary = locateBinary() ?: run {
            _lastError.value = "Pocket TTS binary not found"
            return@withContext false
        }
        packsDir.mkdirs()
        try {
            // Verified CLI: pocket-tts export-voice AUDIO_PATH EXPORT_PATH
            // (converts a WAV reference into a .safetensors voice pack).
            val proc = ProcessBuilder(
                binary, "export-voice",
                referenceWav.absolutePath,
                File(packsDir, "$packName.safetensors").absolutePath
            ).start()
            proc.errorStream.use { it.readBytes() }
            val finished = proc.waitFor(synthesizeTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!finished) proc.destroyForcibly()
            proc.exitValue() == 0 && resolveVoicePack(packName, packsDir) != null
        } catch (e: Throwable) {
            _lastError.value = e.message ?: e.javaClass.simpleName
            false
        }
    }

    /** True when a pocket-tts binary is discoverable (used by the settings UI). */
    fun isAvailable(): Boolean = locateBinary() != null

    private fun locateBinary(): String? {
        cachedBinary?.let { b -> if (File(b).canExecute()) return b; cachedBinary = null }
        val exeName = if (isWindows()) "pocket-tts.exe" else "pocket-tts"
        val candidates = ArrayList<String>()

        // 1. bundled next to the app (same layout as the ffmpeg bundle)
        appDirCandidates().forEach { dir ->
            candidates += File(File(dir, "pocket-tts"), osDir() + "/" + exeName).absolutePath
            candidates += File(File(dir, "pocket-tts"), exeName).absolutePath
        }
        // 2. explicit override
        System.getenv("POCKET_TTS_BIN")?.let { candidates += it }
        // 3. PATH
        try {
            val path = System.getenv("PATH") ?: ""
            for (dir in path.split(File.pathSeparator)) {
                if (dir.isBlank()) continue
                val exe = File(dir, exeName)
                if (exe.canExecute()) { candidates += exe.absolutePath; break }
            }
        } catch (_: Exception) {}
        // 4. user model-pack dir
        candidates += File(File(System.getProperty("user.home"), ".authr/pocket-tts"), exeName).absolutePath

        return candidates.firstOrNull { it.isNotBlank() && File(it).canExecute() }
            ?.also { cachedBinary = it }
    }

    private fun osDir(): String {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("win") -> "windows"
            os.contains("mac") || os.contains("darwin") -> "macos"
            else -> "linux"
        }
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")

    private fun appDirCandidates(): List<File> {
        val out = ArrayList<File>()
        System.getProperty("compose.application.resources.dir")?.let { out += File(it) }
        System.getProperty("compose.application.app.dir")?.let { out += File(it) }
        runCatching {
            PocketTtsBridge::class.java.protectionDomain?.codeSource?.location?.toURI()?.let { uri ->
                File(uri).parentFile?.let { out += it }
            }
        }
        out += File(System.getProperty("user.dir"))
        return out
    }
}
