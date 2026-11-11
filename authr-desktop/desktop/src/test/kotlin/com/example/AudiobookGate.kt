package com.example

import com.example.data.model.Chapter
import com.example.engine.DraftState
import com.example.engine.audio.AcxAudit
import com.example.engine.audio.AudiobookRenderer
import com.example.engine.audio.EspeakSynthesizer
import com.example.engine.audio.MasteringChain
import com.example.engine.audio.PronunciationPass
import java.io.File

/**
 * P4 exit-gate render (plan §13, Table 33): a ten-chapter audiobook renders
 * and passes the ACX audit all-green; synthesis ≥ 2× real time; SMIL timing
 * sanity passes.
 *
 * The gate runs the full §7 pipeline on a ten-chapter confirmed manuscript:
 * the IPA pronunciation pass (real espeak-ng phonemizer, lorebook terms,
 * writer approval simulated as a single approve-all pass — the gate stands in
 * for the writer), a deterministic ambient bed (layer-1 ducking evidence),
 * per-fragment synthesis with measured clip times, the three-layer mastering
 * chain per chapter and once more across the master, the five-criterion ACX
 * audit per chapter and on the master, and the SMIL read-along export.
 */
fun main(args: Array<String>) {
    val outRoot = File(args.getOrElse(0) { "p4-gate" })
    val workDir = File(args.getOrElse(1) { outRoot.parentFile.resolve("${outDirName(outRoot)}.work").absolutePath })
    val ffmpeg = com.example.desktopbridge.FfmpegLocator.ffmpegPath()
    val ffprobe = com.example.desktopbridge.FfmpegLocator.ffprobePath()
    val espeak = com.example.desktopbridge.EspeakLocator.espeakPath()
    checkNotNull(ffmpeg) { "ffmpeg not found" }
    checkNotNull(ffprobe) { "ffprobe not found" }
    checkNotNull(espeak) { "espeak-ng not found" }

    println("=== P4 exit gate — ten-chapter audiobook render ===")
    println("ffmpeg: $ffmpeg")
    println("espeak: $espeak")

    // 1. the confirmed manuscript (canon: writerConfirmed + CONFIRMED)
    val chapters = GateManuscript.chapters()
    check(chapters.size == 10) { "gate needs ten chapters" }

    // 2. IPA pronunciation pass (plan §7.3): scan → approve → normalize
    val phonemizer = PronunciationPass.EspeakPhonemizer(espeak)
    val candidates = PronunciationPass.scan(chapters, GateManuscript.LOREBOOK_TERMS, phonemizer)
    println("\n--- Pronunciation pass: ${candidates.size} candidates ---")
    candidates.forEach { println("  ${it.term.padEnd(14)} [${it.source.label.padEnd(22)}] ${it.ipa.padEnd(16)} → ${it.respelling}") }
    // the gate stands in for the writer's single approval pass
    val approved = candidates.map { PronunciationPass.ApprovedRule(it.term, it.ipa, it.respelling) }
    val containerRules = PronunciationPass.toContainerRules(GateManuscript.TITLE, approved)

    // 3. deterministic ambient bed for layer-1 ducking (plan §7.5 Table 25)
    workDir.mkdirs()
    val bed = AcousticGateBed.generate(ffmpeg!!, workDir)
    println("\n--- Ambient bed: ${bed.name} (deterministic pink noise, −55 dBFS RMS) ---")

    // 4. render
    val synthesizer = EspeakSynthesizer(espeak)
    val mastering = MasteringChain(ffmpegPath = ffmpeg)
    val audit = AcxAudit(ffmpegPath = ffmpeg, ffprobePath = ffprobe)
    val renderer = AudiobookRenderer(synthesizer, mastering, audit)
    val report = renderer.render(
        projectTitle = GateManuscript.TITLE,
        chapters = chapters,
        workDir = workDir,
        approvedRules = approved,
        ambientBed = bed
    )

    // 5. exit-gate checks (plan §13 Table 33, P4 row)
    println("\n--- Render summary ---")
    println(report.summary())
    println()
    report.chapters.forEach { ch ->
        println(ch.audit?.asTable())
        println("  ${ch.timing.summary()}")
        println("  Mastering: " + ch.masteringStages.filter { it.layer != "encode" }.joinToString { it.layer })
        println()
    }
    println(report.masterAudit?.asTable())

    val gateChecks = listOf(
        "Ten chapters rendered" to (report.chapters.size == 10),
        "ACX audit all-green (every chapter + master)" to (report.allChaptersGreen && report.masterGreen),
        "Synthesis ≥ 2× real time" to (report.synthesisSpeedFactor >= 2.0),
        "SMIL timing sanity (every chapter)" to report.chapters.all { it.timing.ok },
        "No failure notes" to report.failureNotes.isEmpty()
    )
    println("\n--- Exit gate (plan §13 Table 33, P4) ---")
    gateChecks.forEach { (name, pass) -> println("  [${if (pass) "PASS" else "FAIL"}] $name") }
    val gateGreen = gateChecks.all { it.second }

    // 6. deliverable evidence tree
    val audioDir = File(outRoot, "audio").apply { mkdirs() }
    val smilDir = File(outRoot, "smil").apply { mkdirs() }
    val xhtmlDir = File(outRoot, "xhtml").apply { mkdirs() }
    report.chapters.forEach { ch ->
        ch.mp3?.copyTo(File(audioDir, ch.mp3.name), overwrite = true)
        ch.smilFile?.copyTo(File(smilDir, ch.smilFile.name), overwrite = true)
        ch.xhtmlFile?.copyTo(File(xhtmlDir, ch.xhtmlFile.name), overwrite = true)
    }
    report.masterMp3?.copyTo(File(audioDir, "master.mp3"), overwrite = true)
    File(outRoot, "pronunciation-rules.json").writeText(
        """{"projectTitle": "${containerRules.projectTitle}",""" +
            """"rules": [""" + containerRules.rules.joinToString(",\n  ") { r ->
                """{"term": "${r.term}", "ipa": "${r.ipa}", "respelling": "${r.respelling}", "approvedByWriter": ${r.approvedByWriter}}"""
            } + "\n]}" + "\n"
    )
    File(outRoot, "gate-report.md").writeText(gateReportMarkdown(report, candidates, gateChecks))

    println(
        "\nAudiobook: %d chapters, %.1f min audio (master %.1f min), synthesis %.1fx real time".format(
            report.chapters.size, report.totalAudioSec / 60.0, report.totalAudioSec / 60.0, report.synthesisSpeedFactor
        )
    )
    println("Evidence tree: ${outRoot.absolutePath}")
    println(if (gateGreen) "P4 EXIT GATE: GREEN" else "P4 EXIT GATE: RED")
    if (!gateGreen) kotlin.system.exitProcess(1)
}

private fun outDirName(f: File): String = f.name

private fun gateReportMarkdown(
    report: AudiobookRenderer.RenderReport,
    candidates: List<PronunciationPass.Candidate>,
    gateChecks: List<Pair<String, Boolean>>
): String = buildString {
    appendLine("# P4 Exit-Gate Render Report — ${report.projectTitle}")
    appendLine()
    appendLine("- Engine: ${report.engineLabel}")
    appendLine("- Continuity mode: ${report.continuityMode.label}")
    appendLine("- Synthesis temperature: ${report.temperature}")
    appendLine("- Chapters rendered: ${report.chapters.size} (skipped non-canon: ${report.skippedChapters})")
    appendLine("- Total audio: %.1f min; synthesis wall: %.1f s; speed: %.1f× real time".format(
        report.totalAudioSec / 60.0, report.totalSynthesisWallMs / 1000.0, report.synthesisSpeedFactor))
    appendLine()
    appendLine("## Exit-gate checks (plan §13 Table 33, P4 row)")
    appendLine()
    gateChecks.forEach { (name, pass) -> appendLine("- [${if (pass) "x" else " "}] $name") }
    appendLine()
    appendLine("## Pronunciation pass (approved rules, writer approval simulated by the gate)")
    appendLine()
    appendLine("| Term | Source | IPA | Respelling |")
    appendLine("| --- | --- | --- | --- |")
    candidates.forEach { appendLine("| ${it.term} | ${it.source.label} | ${it.ipa} | ${it.respelling} |") }
    appendLine()
    appendLine("## ACX audit — per chapter and whole master")
    appendLine()
    report.chapters.forEach { ch ->
        appendLine("### Chapter ${ch.chapterNumber} — ${ch.title} (%.1f s)".format(ch.chapterAudioSec))
        appendLine()
        appendLine("| Criterion | Requirement | Measured | Verdict |")
        appendLine("| --- | --- | --- | --- |")
        ch.audit?.criteria?.forEach {
            appendLine("| ${it.name} | ${it.requirement} | ${it.measured} | ${if (it.pass) "GREEN" else "RED"} |")
        }
        appendLine()
        appendLine("- SMIL: ${ch.timing.summary()}")
        appendLine("- Mastering stages: " + ch.masteringStages.joinToString("; ") { "${it.layer} — ${if (it.applied) "applied" else "SKIPPED"}" })
        appendLine()
    }
    appendLine("### Whole master")
    appendLine()
    appendLine("| Criterion | Requirement | Measured | Verdict |")
    appendLine("| --- | --- | --- | --- |")
    report.masterAudit?.criteria?.forEach {
        appendLine("| ${it.name} | ${it.requirement} | ${it.measured} | ${if (it.pass) "GREEN" else "RED"} |")
    }
    appendLine()
    if (report.failureNotes.isNotEmpty()) {
        appendLine("## Failure notes")
        appendLine()
        report.failureNotes.forEach { appendLine("- $it") }
    }
}

/** Deterministic ambient bed at −55 dBFS RMS (plan §7.5 Table 25, layer 1). */
object AcousticGateBed {
    fun generate(ffmpeg: String, workDir: File): File {
        val raw = File(workDir, "bed_raw.wav")
        val (code, _, _) = MasteringChain.ProcessCommandRunner.run(
            listOf(
                ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", "anoisesrc=color=pink:seed=42:duration=900",
                "-af", "lowpass=f=400", "-ar", "44100", "-ac", "1", "-c:a", "pcm_s16le",
                raw.absolutePath
            ),
            120_000
        )
        check(code == 0 && raw.isFile) { "bed generation failed" }
        // normalize the bed to −55 dBFS RMS so the ducked floor stays below
        // the ACX −60 dB noise ceiling with margin
        val audit = AcxAudit(ffmpegPath = ffmpeg)
        val rms = audit.overallRmsDb(raw) ?: error("bed RMS measurement failed")
        val gain = -55.0 - rms
        val bed = File(workDir, "ambient_bed.wav")
        val (code2, _, _) = MasteringChain.ProcessCommandRunner.run(
            listOf(
                ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
                "-i", raw.absolutePath, "-af", "volume=${"%.2f".format(gain)}dB",
                "-c:a", "pcm_s16le", bed.absolutePath
            ),
            120_000
        )
        check(code2 == 0 && bed.isFile) { "bed normalization failed" }
        raw.delete()
        return bed
    }
}

/** The gate's ten-chapter confirmed manuscript. */
object GateManuscript {
    const val TITLE = "The Iron Vale"
    val LOREBOOK_TERMS = listOf("Vaelmoor", "Aelyth", "Katharsis", "Shea'lyn", "iron-vein")

    private val bodies = listOf(
        """
        Aelyth woke before the bells of Vaelmoor. Frost had drawn its thin lines across the shutters, and the iron-vein under the floor hummed the way it always did before a change in the weather.

        She dressed in the dark, out of habit rather than necessity, and counted the coins in the clay jar by the stove. Seven. Enough for the ferry, not enough for questions.

        The road north was still closed, and the ferryman had stopped pretending it would open. Downriver, the foundries of Katharsis ran their night shifts, and the sky above the water carried the color of poured tin.

        Aelyth ate standing up, listening. Somewhere below the hill, a door slammed, and the iron-vein answered with a note so low she felt it in her teeth.
        """.trimIndent(),
        """
        The ferryman was a widow named Prue who talked to the river the way other people talked to horses. She took Aelyth's six coins without counting them and pushed off before the second bell.

        Mid-channel, the mist thickened until the far bank vanished. Prue kept the bow steady and kept talking, a low running commentary about currents and grates and the year the iron-vein had shifted the sandbar.

        Aelyth held the gunwale and watched the water. Where the vein ran beneath the river, the current wore a different texture, almost braided, as if the river were being combed by an unseen hand.

        They landed south of the customs house, where nobody asked names. Aelyth climbed the levee and looked back once. Vaelmoor sat under its haze of smoke and bells, small enough now to fit inside a cupped hand.
        """.trimIndent(),
        """
        Katharsis announced itself by smell first: hot metal, then quenching oil, then the sour breath of the tanneries. By the time Aelyth saw the foundry walls, she had already stopped noticing it.

        The gate wardens wanted a purpose. She gave them the true one, that she was looking for her brother's debt ledger, and it worked the way truth sometimes does in places built around furnaces: nobody believed it, so nobody argued.

        The ledger clerk kept a room above the weighing house. He was old, precise, and unimpressed. He asked for a surname, and when she said she had none worth keeping, he wrote iron-vein in the margin and said it would serve.

        The pages she needed were marked with red thread. She read them twice, paid the copying fee, and left before the light could move too far across the floor.
        """.trimIndent(),
        """
        Her brother, Corin, had signed three contracts in Katharsis and broken none of them, which was the confusing part. The ledger showed payments in, payments out, and then a final entry that read only: carried to Vaelmoor.

        Carried by whom, Aelyth wanted to ask the page. The clerk had already explained the copying rules; questions cost extra and answers cost more.

        Outside, the shift horn sounded, and the street filled with men and women carrying their tools like luggage. An old furnace-tender watched Aelyth study her copy and offered, unasked, that the iron-vein had been restless all winter.

        Restless how, she asked. He shrugged with one shoulder, the way people do when they have decided to say exactly one thing and no more. It sings in a new key, he said, and walked on.
        """.trimIndent(),
        """
        The north road from Katharsis climbed through stubble fields and then through nothing at all: heath, stone, wind. Aelyth walked it in a day and a half, sleeping once in a shepherd's hut that smelled of lanolin and old smoke.

        In the hut's wall, someone had driven a nail through a coin, a Katharsis halfpiece, and hung a small iron ring from it. She left both where they were. Some things are prayers, and some things are just nails.

        On the second afternoon she met Shea'lyn at the toll bridge, sitting on the rail the way other people sit on chairs, boots swinging over the water, a ledger of her own open on her knees.

        You're the sister, Shea'lyn said, before Aelyth had spoken. Corin talked about you the way the sky talks about rain. Sit down. This is going to take longer than the bridge.
        """.trimIndent(),
        """
        Shea'lyn's ledger was not a ledger. It was a hearing record: dates, depths, and the pitch of the iron-vein as measured by ear, going back eleven years. Half the entries were in a shorthand Aelyth couldn't parse.

        Corin had paid Shea'lyn's mother to listen, and later paid Shea'lyn, and the payments lined up with the entries in the Katharsis copy, almost. In the spring, the records said, the vein had changed key twice, and then a third time.

        The third change is the one that matters, Shea'lyn said. The first two, the vein does every dozen years or so. It's breathing. The third one is new, and new is never cheap.

        She closed the book. Your brother went to Vaelmoor to ask the bell founders what the vein was singing. That was six months ago. The bells haven't changed. Whatever he found out, he found it out somewhere else.
        """.trimIndent(),
        """
        They crossed the bridge together, Shea'lyn paying the toll for both with a smile that the toll-keeper clearly owed money to. The road beyond ran down toward the coast and the small gray towns that lived off the vein's outcroppings.

        In the first of those towns, a smith agreed to test a bar of Shea'lyn's iron against a bar of the town's. He heated both, and both sang. But the town's bar held its note a half-step higher, and when it cooled, it ticked like a cooling kettle for twice as long.

        Metal remembers, the smith said, and would say nothing else. He charged them for the coal. He charged them for the advice. He charged them, at the end, for a cup of water, and then gave the coin back, embarrassed, when Shea'lyn laughed.

        They slept in his loft anyway, because it was there, and because the alternative was the yard, where the iron racks clicked to themselves all night like insects.
        """.trimIndent(),
        """
        The coast road ended at a customs post that had burned and been rebuilt twice. Behind it, the cliff path to Vaelmoor's northern gate was closed with an official seal and an unofficial gap.

        They took the gap. Halfway up, the iron-vein broke the surface of the rock in a long black seam, wide as a table, humming. Shea'lyn laid her palm on it and held it there until her hand shook.

        Well, she said. It's not a new key at all. It's two keys at once. The old note and another underneath it, lower, patient. Your brother heard it too. Anyone with ears could.

        Aelyth touched the seam. The hum climbed through her arm the way cold water climbs cloth. It sounded, she thought, like a room where someone is waiting, and has been waiting, and does not mind waiting longer.
        """.trimIndent(),
        """
        The northern gate of Vaelmoor opened at dusk for the ore carts, and two dusty travelers walking in with the mules drew no eye at all. The bell foundry stood where it had always stood, dark now, its great wheels stilled for the night.

        The night watchman knew Shea'lyn by name and Aelyth by description. Corin's sister, he said. He said it gently, the way people say a thing they have practiced.

        He would not say more in the yard. Inside, by the cupola, with the doors shut, he told it plainly: Corin had come, and measured, and gone down the vein-shaft that only the founders use, and had not come up.

        The shaft was sealed in the spring, he said. Not by the founders. By Corin, from the inside, with the founders' own cement. He had left his tools, his lamp, and the debt ledger's missing page, weighted with a bell-clapper on the top step.
        """.trimIndent(),
        """
        The missing page was a confession and a receipt. Corin had heard the second note under the first and understood it for what it was: the vein answering something farther down, something older, that had begun, very slowly, to hum back.

        He had sealed the shaft not to keep others out but to keep the interval between the notes from widening, the page said. Cement holds a note, he had written, the way a hand holds a string. Not forever. Long enough.

        Aelyth read the page twice, standing in the foundry dark, with the sealed shaft somewhere beneath her feet and the hum arriving through the floor like weather. Shea'lyn read it once, then laid her palm flat on the page, as she had on the seam.

        In the morning, Aelyth paid the ferryman's widow with her last coin and asked her to carry a copy south, to Katharsis, to the clerk who wrote iron-vein for a surname. Then she climbed the levee and looked north, toward the vale, and started walking.
        """.trimIndent()
    )

    private val titles = listOf(
        "Frost and Bells", "The Ferryman's Widow", "The Weighing House", "Red Thread",
        "The Toll Bridge", "The Hearing Record", "Metal Remembers", "Two Keys",
        "The Sealed Shaft", "What the Vein Answered"
    )

    fun chapters(): List<Chapter> = bodies.mapIndexed { idx, body ->
        Chapter(
            bookId = 99,
            chapterNumber = idx + 1,
            title = titles[idx],
            content = body,
            summary = "gate chapter ${idx + 1}",
            status = "Completed",
            writerConfirmed = true,
            draftStatus = DraftState.CONFIRMED
        )
    }
}
