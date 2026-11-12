package com.example.engine.audio

import android.content.Context
import com.example.desktopbridge.PocketTtsBridge
import com.example.tts.PocketTtsManager
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * The production synthesizer over Pocket TTS (v3.0 D-13, plan §7.4) — the
 * [AudiobookRenderer.Synthesizer] adapter for the Kyutai `pocket-tts`
 * binary, completing the production path the P4 gate proved through
 * [EspeakSynthesizer] (the bundled-local engine).
 *
 * Continuity contract (POCKET_STRUCTURAL): a fixed safetensors voice pack
 * renders identity-identical across chapters by construction. To keep that
 * label HONEST this adapter calls [PocketTtsBridge] DIRECTLY — never
 * [PocketTtsManager.synthesizeToFile], which silently falls back to Edge
 * TTS when the pack or binary is missing. Mixing Edge segments into a
 * POCKET_STRUCTURAL render would break cross-chapter identity while the
 * report claims it holds; here a missing binary or pack fails LOUD with a
 * note, and the writer's offline choice is the espeak engine
 * (ESPEAK_LOCAL) picked explicitly in the workbench.
 *
 * The bridge call is suspend; the renderer's [AudiobookRenderer.Synthesizer]
 * contract is blocking (see [EspeakSynthesizer]), so the adapter bridges
 * with [runBlocking] — the renderer synthesizes one fragment at a time on a
 * single worker, so the block is the same shape as the espeak process wait.
 */
class PocketSynthesizer(
    private val packsDir: File,
    private val packName: String = DEFAULT_PACK,
    override val temperature: Double = PocketTtsManager.synthesisTemperature
) : AudiobookRenderer.Synthesizer {

    override val engineLabel: String = "Pocket TTS ($packName)"

    override val continuityMode: AudiobookRenderer.ContinuityMode =
        AudiobookRenderer.ContinuityMode.POCKET_STRUCTURAL

    /** True when the binary AND the voice pack resolve — the engine the picker may offer. */
    fun isReady(): Boolean =
        PocketTtsBridge.isAvailable() &&
            PocketTtsBridge.resolveVoicePack(packName, packsDir) != null

    override fun synthesize(text: String, outWav: File): AudiobookRenderer.SynthesisResult {
        val start = System.currentTimeMillis()
        if (!PocketTtsBridge.isAvailable()) {
            return AudiobookRenderer.SynthesisResult(
                false, null, 0,
                "Pocket TTS binary not found (install to <app>/pocket-tts, PATH, or ~/.authr/pocket-tts)"
            )
        }
        val pack = PocketTtsBridge.resolveVoicePack(packName, packsDir)
        if (pack == null) {
            return AudiobookRenderer.SynthesisResult(
                false, null, 0,
                "voice pack '$packName' not installed in ${packsDir.absolutePath}"
            )
        }
        val ok = runBlocking {
            PocketTtsBridge.synthesizeToFile(
                text = text,
                outputFile = outWav,
                packName = packName,
                packsDir = packsDir,
                temperature = temperature
            )
        }
        if (!ok || !outWav.isFile || outWav.length() == 0L) {
            return AudiobookRenderer.SynthesisResult(
                false, null, System.currentTimeMillis() - start,
                "pocket-tts failed: ${PocketTtsBridge.lastError.value ?: "no output"}"
            )
        }
        val duration = EspeakSynthesizer.wavDurationSec(outWav)
        return AudiobookRenderer.SynthesisResult(
            duration != null, duration, System.currentTimeMillis() - start
        )
    }

    companion object {
        /** The default narrator pack (mirrors PocketTtsManager's default voice). */
        const val DEFAULT_PACK = "pocket-narrator-soft"

        /** The engine picker's availability probe (Main.kt side, where Context lives). */
        fun isReady(context: Context, packName: String = DEFAULT_PACK): Boolean =
            PocketTtsBridge.isAvailable() &&
                PocketTtsBridge.resolveVoicePack(packName, PocketTtsManager.voicePacksDir(context)) != null
    }
}
