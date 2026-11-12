package com.example

import com.example.desktopbridge.PocketTtsBridge
import com.example.engine.audio.AudiobookRenderer
import com.example.engine.audio.PocketSynthesizer
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The production synthesizer adapter (v3.0 D-13): Pocket TTS under the
 * renderer contract, POCKET_STRUCTURAL continuity — and LOUD failure when
 * the binary or voice pack is missing, because a silent Edge fallback
 * would mix engines inside one render while the report claims structural
 * continuity (plan §7.4).
 */
class PocketSynthesizerTest {

    @Test
    fun `a missing voice pack fails loud, never falls back to edge`() {
        // No .safetensors in the packs dir: the adapter must refuse with a
        // note naming what is missing — not route the fragment to Edge TTS.
        // (Which piece the note names depends on the machine: the binary
        // check runs first, so the sandbox names the binary and the
        // reference laptop names the pack. Either way: loud, and no output.)
        val packsDir = kotlin.io.path.createTempDirectory("pocket-pack-missing").toFile()
        val packDir = File(packsDir, "voice_packs").apply { mkdirs() }
        try {
            val synth = PocketSynthesizer(packsDir = packDir)
            val out = File(packDir, "seg.wav")
            val res = synth.synthesize("Vaeldrin crossed the marsh.", out)

            assertFalse(res.ok, "a missing engine piece must not synthesize")
            assertNotNull(res.note, "the failure carries a note")
            assertTrue(
                res.note!!.contains("voice pack") || res.note!!.contains("binary"),
                "the note names the missing engine piece: ${res.note}"
            )
            assertFalse(out.isFile, "no partial output")
            assertEquals(AudiobookRenderer.ContinuityMode.POCKET_STRUCTURAL, synth.continuityMode)
            assertEquals(0.6, synth.temperature, 1e-9)
            assertTrue(synth.engineLabel.contains("Pocket TTS"))
        } finally {
            packsDir.deleteRecursively()
        }
    }

    @Test
    fun `a present pack without the binary still fails loud`() {
        // The sandbox (and any machine without the Kyutai binary — OQ-14)
        // must get an honest binary-not-found failure, fast: an Edge
        // fallback would be a network round trip and a continuity lie.
        assumeTrue(
            "reference laptop: binary present — loud-failure path N/A",
            !PocketTtsBridge.isAvailable()
        )
        val root = kotlin.io.path.createTempDirectory("pocket-bin-missing").toFile()
        val packsDir = File(root, "voice_packs").apply { mkdirs() }
        try {
            File(packsDir, "${PocketSynthesizer.DEFAULT_PACK}.safetensors").writeBytes(ByteArray(16))
            val synth = PocketSynthesizer(packsDir = packsDir)
            assertFalse(synth.isReady(), "binary absent: the engine is honestly not ready")

            val res = synth.synthesize("The tide turned.", File(root, "seg.wav"))
            assertFalse(res.ok)
            assertTrue(
                res.note!!.contains("binary"),
                "the note names the missing binary: ${res.note}"
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
