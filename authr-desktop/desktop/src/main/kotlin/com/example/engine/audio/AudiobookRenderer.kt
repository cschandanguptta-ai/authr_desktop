package com.example.engine.audio

import com.example.data.model.Chapter
import com.example.desktopbridge.EspeakLocator
import com.example.engine.WriterGate
import java.io.File
import java.io.RandomAccessFile

/**
 * The audiobook renderer (v3.0 P4, plan §7): orchestrates the studio-grade
 * pipeline from confirmed manuscript chapters to a retail-ready audiobook.
 *
 * Per chapter (canon only — [WriterGate.canonChapters], AGENTS.md hard rule 1):
 *
 *  1. Pre-synthesis normalization — approved pronunciation rules replace
 *     their terms with respellings (plan §7.3), so every engine inherits the
 *     same corrections.
 *  2. Fragmentation — the chapter text is fragmented (paragraphs, long
 *     paragraphs split by sentence, `SmilExporter.fragmentText`).
 *  3. Synthesis — each fragment synthesizes to its own audio segment, so
 *     SMIL clip times are *measured*, not estimated. Continuity is the
 *     synthesizer's contract (plan §7.4): Pocket TTS is structural (a fixed
 *     safetensors voice pack + a project-pinned synthesis temperature), so
 *     narrator identity is identical across chapters by construction;
 *     ElevenLabs chains previous_request_ids across requests; the mode used
 *     is recorded in the render report.
 *  4. Join + mastering — fragment segments concatenate losslessly into the
 *     chapter working file, then the three-layer ffmpeg mastering chain
 *     (plan §7.5) runs per chapter, delivering MP3 CBR 192 kbps / 44.1 kHz.
 *  5. ACX audit — every chapter meters against the retail standard
 *     (plan §7.6) with a red/green report per criterion.
 *  6. SMIL export — fragments pair with their measured clip times in an
 *     EPUB 3 Media Overlays SMIL file plus the paired XHTML content doc.
 *
 * Across the whole master: the mastered chapter working files join once
 * more, the mastering chain runs again across the joined master, and the
 * full audit gates the deliverable.
 *
 * The synthesis-speed gate (plan §13): the renderer measures total
 * synthesized audio seconds against total synthesis wall seconds —
 * [RenderReport.synthesisSpeedFactor] must be ≥ 2× (real time or faster).
 */
class AudiobookRenderer(
    val synthesizer: Synthesizer,
    val mastering: MasteringChain,
    val audit: AcxAudit,
    val smil: SmilExporter = SmilExporter,
    val synthesisTimeoutMs: Long = 15 * 60_000L
) {

    /** A synthesis engine; continuity is part of the contract (plan §7.4). */
    interface Synthesizer {
        val engineLabel: String
        val continuityMode: ContinuityMode
        val temperature: Double

        /** Synthesizes [text] into [outWav]; returns the measured duration or null. */
        fun synthesize(text: String, outWav: File): SynthesisResult
    }

    data class SynthesisResult(
        val ok: Boolean,
        val durationSec: Double?,
        val wallMs: Long,
        val note: String? = null
    )

    /** Cross-segment acoustic continuity modes (plan §7.4). */
    enum class ContinuityMode(val label: String) {
        POCKET_STRUCTURAL("Pocket TTS structural — fixed safetensors voice pack, identity identical across chapters by construction; loudness pinned by the mastering chain; per-project synthesis temperature recorded"),
        ELEVENLABS_CHAINED("ElevenLabs previous_request_ids chaining — each request references the previous, carrying prosody and timbre across segment boundaries"),
        ESPEAK_LOCAL("espeak-ng local — the bundled desktop dependency; deterministic fixed-voice synthesis (render engine for CI-less environments)"),
        EDGE_FALLBACK("Edge TTS fallback — degraded route; continuity not negotiated")
    }

    /** One rendered chapter with its audit, SMIL and timing evidence. */
    data class ChapterRender(
        val chapterNumber: Int,
        val title: String,
        val fragments: List<SmilExporter.Fragment>,
        val chapterAudioSec: Double,
        val synthesisWallMs: Long,
        val mp3: File?,
        val smilFile: File?,
        val xhtmlFile: File?,
        val audit: AcxAudit.AuditReport?,
        val timing: SmilExporter.TimingCheck,
        val masteringStages: List<MasteringChain.StageResult>
    ) {
        val allGreen: Boolean get() = audit?.allGreen == true && timing.ok
    }

    /** The full render report — what the audiobook report records (plan §7.4). */
    data class RenderReport(
        val projectTitle: String,
        val engineLabel: String,
        val continuityMode: ContinuityMode,
        val temperature: Double,
        val chapters: List<ChapterRender>,
        val masterMp3: File?,
        val masterAudit: AcxAudit.AuditReport?,
        val totalAudioSec: Double,
        val totalSynthesisWallMs: Long,
        val skippedChapters: List<Int>,
        val failureNotes: List<String>
    ) {
        val synthesisSpeedFactor: Double
            get() = if (totalSynthesisWallMs > 0) totalAudioSec / (totalSynthesisWallMs / 1000.0) else 0.0
        val allChaptersGreen: Boolean get() = chapters.isNotEmpty() && chapters.all { it.allGreen }
        val masterGreen: Boolean get() = masterAudit?.allGreen == true
        val allGreen: Boolean get() = allChaptersGreen && masterGreen && failureNotes.isEmpty()

        fun summary(): String = buildString {
            appendLine("Audiobook render — $projectTitle")
            appendLine("  Engine: $engineLabel")
            appendLine("  Continuity: ${continuityMode.label}")
            appendLine("  Temperature: $temperature")
            chapters.forEach { ch ->
                appendLine(
                    "  Chapter ${ch.chapterNumber} '${ch.title}': %.1fs audio, ".format(ch.chapterAudioSec) +
                        "audit ${if (ch.audit?.allGreen == true) "GREEN" else "RED"}, " +
                        "SMIL ${if (ch.timing.ok) "OK" else "PROBLEMS"}"
                )
            }
            appendLine(
                "  Master: ${masterMp3?.name ?: "—"}, audit ${if (masterGreen) "GREEN" else "RED"}"
            )
            appendLine(
                "  Total audio: %.1fs, synthesis wall: %.1fs, speed %.2fx real time".format(
                    totalAudioSec, totalSynthesisWallMs / 1000.0, synthesisSpeedFactor
                )
            )
            if (skippedChapters.isNotEmpty()) appendLine("  Skipped non-canon chapters: $skippedChapters")
            if (failureNotes.isNotEmpty()) failureNotes.forEach { appendLine("  NOTE: $it") }
            append(if (allGreen) "  ALL GREEN" else "  FAILED")
        }
    }

    /** Renders the confirmed chapters of a project into [workDir]. */
    fun render(
        projectTitle: String,
        chapters: List<Chapter>,
        workDir: File,
        approvedRules: List<PronunciationPass.ApprovedRule> = emptyList(),
        ambientBed: File? = null
    ): RenderReport {
        val canon = WriterGate.canonChapters(chapters)
        val skipped = (chapters - canon.toSet()).map { it.chapterNumber }
        val sorted = canon.sortedBy { it.chapterNumber }

        val chapterRenders = ArrayList<ChapterRender>()
        val failures = ArrayList<String>()
        val masteredChapterWavs = ArrayList<File>()
        var totalAudio = 0.0
        var totalSynthMs = 0L

        workDir.mkdirs()
        val chaptersDir = File(workDir, "chapters").apply { mkdirs() }
        val smilDir = File(workDir, "smil").apply { mkdirs() }
        val xhtmlDir = File(workDir, "xhtml").apply { mkdirs() }
        val audioDir = File(workDir, "audio").apply { mkdirs() }

        sorted.forEach { ch ->
            // 1: fragment the CANON text — fragments display the manuscript
            // in the read-along surface; the synthesizer gets the normalized
            // text (approved respellings) so the two stay aligned 1:1
            val canonTexts = SmilExporter.fragmentText(ch.content)
            val texts = canonTexts.map { PronunciationPass.normalize(it, approvedRules) }
            if (canonTexts.isEmpty()) {
                failures += "chapter ${ch.chapterNumber}: no text fragments"
                return@forEach
            }

            // 1-3: synthesize each fragment (measured clip times)
            val segFiles = ArrayList<File>()
            val segDurations = ArrayList<Double>()
            var chapterSynthMs = 0L
            var chapterFailed = false
            texts.forEachIndexed { idx, text ->
                val seg = File(chaptersDir, "ch${pad2(ch.chapterNumber)}_seg${idx + 1}.wav")
                val res = synthesizer.synthesize(text, seg)
                chapterSynthMs += res.wallMs
                if (!res.ok || res.durationSec == null || !seg.isFile || seg.length() == 0L) {
                    failures += "chapter ${ch.chapterNumber} fragment ${idx + 1}: synthesis failed (${res.note ?: res.ok})"
                    chapterFailed = true
                    return@forEachIndexed
                }
                segFiles += seg
                segDurations += res.durationSec
            }
            if (chapterFailed) return@forEach

            // 4: join segments losslessly, then run the mastering chain once
            val chapterWork = File(chaptersDir, "ch${pad2(ch.chapterNumber)}_raw.wav")
            val joined = mastering.concatWavs(segFiles, chapterWork)
            if (!joined) {
                failures += "chapter ${ch.chapterNumber}: fragment join failed"
                return@forEach
            }
            val chapterWav = File(chaptersDir, "ch${pad2(ch.chapterNumber)}_mastered.wav")
            val stages = mastering.master(chapterWork, chapterWav, ambientBed = ambientBed, mp3 = false)
            if (stages.lastOrNull()?.applied != true || chapterWav.length() == 0L) {
                failures += "chapter ${ch.chapterNumber}: mastering failed — " +
                    stages.filter { !it.applied }.joinToString { "${it.layer}: ${it.detail}" }
                return@forEach
            }
            masteredChapterWavs += chapterWav

            // delivery encode (retail format; no chain layers — the chain ran once)
            val chapterMp3 = File(audioDir, "ch${pad2(ch.chapterNumber)}.mp3")
            val encoded = mastering.encodeMp3(chapterWav, chapterMp3)
            if (!encoded) {
                failures += "chapter ${ch.chapterNumber}: MP3 encode failed"
                return@forEach
            }

            // 5: ACX audit on the delivered MP3
            val chapterAudit = audit.audit(chapterMp3, "Chapter ${ch.chapterNumber}")

            // 6: SMIL with measured clip times. The synthesized segment sum is
            // the canonical chapter duration (the SMIL clips describe the
            // synthesized material); the delivered MP3 duration is an
            // independent cross-check — LAME encoder padding (~50 ms) may sit
            // between the two, a larger gap means audio was lost in mastering.
            var cursor = 0.0
            val fragments = segDurations.mapIndexed { idx, dur ->
                SmilExporter.Fragment(
                    id = "frag-${idx + 1}",
                    text = canonTexts[idx], // the read-along surface shows the manuscript
                    clipBeginSec = Math.round(cursor * 1000.0) / 1000.0,
                    clipEndSec = Math.round((cursor + dur) * 1000.0) / 1000.0
                ).also { cursor += dur }
            }
            val chapterDuration = segDurations.sum()
            mp3DurationSec(chapterMp3)?.let { probed ->
                if (kotlin.math.abs(probed - chapterDuration) > 0.25) {
                    failures += "chapter ${ch.chapterNumber}: delivered MP3 duration " +
                        "${"%.2f".format(probed)}s deviates from the synthesized segment sum " +
                        "${"%.2f".format(chapterDuration)}s"
                }
            }
            val timing = smil.timingSanity(fragments, chapterDuration)

            val xhtmlName = "ch${pad2(ch.chapterNumber)}.xhtml"
            val smilFile = File(smilDir, "ch${pad2(ch.chapterNumber)}.smil").apply {
                writeText(
                    smil.smilDocument(
                        ch.chapterNumber, xhtmlName,
                        "audio/ch${pad2(ch.chapterNumber)}.mp3", fragments
                    )
                )
            }
            val xhtmlFile = File(xhtmlDir, xhtmlName).apply {
                writeText(smil.xhtmlDocument(ch.chapterNumber, ch.title, fragments))
            }

            totalAudio += chapterDuration
            totalSynthMs += chapterSynthMs
            chapterRenders += ChapterRender(
                chapterNumber = ch.chapterNumber,
                title = ch.title,
                fragments = fragments,
                chapterAudioSec = chapterDuration,
                synthesisWallMs = chapterSynthMs,
                mp3 = chapterMp3,
                smilFile = smilFile,
                xhtmlFile = xhtmlFile,
                audit = chapterAudit,
                timing = timing,
                masteringStages = stages
            )
        }

        // whole master: join mastered chapters, run the chain once more, audit
        var masterMp3: File? = null
        var masterAudit: AcxAudit.AuditReport? = null
        if (masteredChapterWavs.size == sorted.size && sorted.isNotEmpty()) {
            val masterWork = File(workDir, "master_raw.wav")
            if (mastering.concatWavs(masteredChapterWavs, masterWork)) {
                val masterWav = File(workDir, "master_mastered.wav")
                // layers 2-3 run once more across the joined master; layer 1
                // (bed ducking) ran per chapter segment — the ambience is
                // already in the material, a second bed would double it
                val stages = mastering.master(masterWork, masterWav, ambientBed = null, mp3 = false)
                if (stages.lastOrNull()?.applied == true) {
                    val mp3 = File(audioDir, "master.mp3")
                    if (mastering.encodeMp3(masterWav, mp3)) {
                        masterMp3 = mp3
                        masterAudit = audit.audit(mp3, "Whole master")
                    } else {
                        failures += "whole master: MP3 encode failed"
                    }
                } else {
                    failures += "whole master: mastering failed — " +
                        stages.filter { !it.applied }.joinToString { "${it.layer}: ${it.detail}" }
                }
            } else {
                failures += "whole master: chapter join failed"
            }
        } else if (sorted.isNotEmpty()) {
            failures += "whole master not rendered: only ${masteredChapterWavs.size}/${sorted.size} chapters mastered"
        }

        return RenderReport(
            projectTitle = projectTitle,
            engineLabel = synthesizer.engineLabel,
            continuityMode = synthesizer.continuityMode,
            temperature = synthesizer.temperature,
            chapters = chapterRenders,
            masterMp3 = masterMp3,
            masterAudit = masterAudit,
            totalAudioSec = totalAudio,
            totalSynthesisWallMs = totalSynthMs,
            skippedChapters = skipped,
            failureNotes = failures
        )
    }

    /** MP3 duration via ffprobe (seconds); null when probing fails. */
    private fun mp3DurationSec(file: File): Double? {
        val ffprobe = audit.ffprobePath ?: return null
        return try {
            val (code, out, _) = MasteringChain.ProcessCommandRunner.run(
                listOf(
                    ffprobe, "-v", "error", "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1", file.absolutePath
                ),
                60_000
            )
            if (code == 0) out.trim().toDoubleOrNull() else null
        } catch (_: Exception) {
            null
        }
    }

    private fun pad2(n: Int): String = if (n < 10) "0$n" else n.toString()
}

/**
 * The local render synthesizer over espeak-ng — the bundled desktop
 * dependency (plan §7.1). Deterministic fixed-voice synthesis (continuity
 * mode ESPEAK_LOCAL): the render engine for CI-less environments and the
 * phonemizer's own voice; Pocket TTS is the primary production engine
 * (POCKET_STRUCTURAL, `tts/PocketTtsManager.kt`) with this same renderer
 * contract.
 */
class EspeakSynthesizer(
    private val binary: String? = EspeakLocator.espeakPath(),
    private val voice: String = "en-us",
    private val wordsPerMinute: Int = 175,
    override val temperature: Double = 0.0
) : AudiobookRenderer.Synthesizer {

    override val engineLabel: String = "espeak-ng ${binary?.let { File(it).name } ?: "?"} ($voice)"

    override val continuityMode: AudiobookRenderer.ContinuityMode =
        AudiobookRenderer.ContinuityMode.ESPEAK_LOCAL

    override fun synthesize(text: String, outWav: File): AudiobookRenderer.SynthesisResult {
        val bin = binary
            ?: return AudiobookRenderer.SynthesisResult(false, null, 0, "espeak-ng not found")
        val start = System.currentTimeMillis()
        return try {
            outWav.parentFile?.mkdirs()
            // text on stdin: no argv length limit, no shell-hostile characters
            val proc = ProcessBuilder(
                bin, "-v", voice, "-s", wordsPerMinute.toString(), "-w", outWav.absolutePath
            ).redirectErrorStream(false).start()
            proc.outputStream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            val errDrain = Thread { proc.errorStream.bufferedReader().readText() }
                .apply { isDaemon = true; start() }
            val finished = proc.waitFor(10 * 60_000L, java.util.concurrent.TimeUnit.MILLISECONDS)
            errDrain.join(1_000)
            if (!finished) {
                proc.destroyForcibly()
                AudiobookRenderer.SynthesisResult(false, null, System.currentTimeMillis() - start, "espeak-ng timeout")
            } else if (proc.exitValue() != 0 || !outWav.isFile || outWav.length() == 0L) {
                AudiobookRenderer.SynthesisResult(false, null, System.currentTimeMillis() - start, "espeak-ng exit ${proc.exitValue()}")
            } else {
                val dur = wavDurationSec(outWav)
                AudiobookRenderer.SynthesisResult(dur != null, dur, System.currentTimeMillis() - start)
            }
        } catch (e: Exception) {
            AudiobookRenderer.SynthesisResult(false, null, System.currentTimeMillis() - start, e.message)
        }
    }

    companion object {
        /** 16-bit PCM WAV duration from the header (no external tools). */
        fun wavDurationSec(file: File): Double? = try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(128)
                val read = raf.read(header)
                if (read < 44) return null
                // RIFF....WAVE + 'fmt ' chunk + 'data' chunk (canonical layout)
                if (String(header, 0, 4, Charsets.US_ASCII) != "RIFF" ||
                    String(header, 8, 4, Charsets.US_ASCII) != "WAVE"
                ) return null
                var pos = 12
                var sampleRate = 0; var channels = 0; var bits = 0
                var dataLen = -1L
                while (pos + 8 <= read) {
                    val id = String(header, pos, 4, Charsets.US_ASCII)
                    val size = le32(header, pos + 4)
                    when (id) {
                        "fmt " -> {
                            channels = le16(header, pos + 10)
                            sampleRate = le32(header, pos + 12).toInt()
                            bits = le16(header, pos + 22)
                        }
                        "data" -> { dataLen = size; break }
                    }
                    pos += 8 + size.toInt() + (size % 2).toInt()
                }
                if (dataLen < 0 || channels <= 0 || bits <= 0 || sampleRate <= 0) return null
                dataLen.toDouble() / (channels * (bits / 8) * sampleRate)
            }
        } catch (_: Exception) {
            null
        }

        private fun le32(b: ByteArray, off: Int): Long =
            (b[off].toLong() and 0xFF) or ((b[off + 1].toLong() and 0xFF) shl 8) or
                ((b[off + 2].toLong() and 0xFF) shl 16) or ((b[off + 3].toLong() and 0xFF) shl 24)

        private fun le16(b: ByteArray, off: Int): Int =
            (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)
    }
}
