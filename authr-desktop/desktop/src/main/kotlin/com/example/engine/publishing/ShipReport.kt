package com.example.engine.publishing

import com.example.data.model.ChekhovGun
import com.example.engine.ChekhovLedger
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The end-to-end ship report (v3.0 P5, plan §15):
 *
 * "The ship report — the same one that lists Chekhov waivers, scorecard
 * notes, and cadence streaks — is the final artifact: if the report is
 * clean, the export behind it is defensible. That is the standard Rev 2
 * commits to: publish-ready means provable."
 *
 * The report composes, in one document:
 *  - the retail artifacts and every internal gate verdict that stands
 *    behind them (epubcheck per EPUB, print gutter-ladder geometry);
 *  - the external gates still owned by the destination (KDP Print
 *    Previewer) with their standing — VERIFIED when the tool ran,
 *    disclosed as outstanding when it did not (never silent, ADR-0010
 *    discipline: measured, not estimated);
 *  - the continuity disclosures: every Chekhov waiver with its mandatory
 *    note, scorecard notes, and the cadence/buffer summary for serials;
 *  - the pricing record: the royalty-simulator quotes the writer priced
 *    against (plan §11.2).
 */
object ShipReport {

    /** Verdict of one gate over one artifact. */
    data class GateVerdict(
        val artifact: String,
        val gate: String,
        val passed: Boolean,
        /** VERIFIED (tool ran) / UNVERIFIED (tool absent — disclosed) / FAILED. */
        val status: String,
        val detail: String
    )

    data class WaiverLine(val gunName: String, val introducedChapter: Int, val note: String)

    data class Input(
        val bookTitle: String,
        val author: String,
        val generatedAtIso: String,
        val generatorVersion: String,
        val artifacts: List<Artifact>,
        val gateVerdicts: List<GateVerdict>,
        val chekhovWaivers: List<WaiverLine>,
        val scorecardNotes: List<String>,
        val cadenceLines: List<String>,
        val pricingLines: List<String>,
        /** Process disclosures (writer-gate exclusions, workbench derivation…). */
        val disclosures: List<String> = emptyList(),
        /** One-action provenance: what the single invocation produced. */
        val oneActionSummary: String
    )

    data class Artifact(val path: String, val kind: String, val sizeBytes: Long)

    /** A report is clean only when every gate verdict passed. */
    fun isClean(verdicts: List<GateVerdict>): Boolean = verdicts.all { it.passed }

    fun render(input: Input): String = buildString {
        append("# Ship Report — ${input.bookTitle}\n\n")
        append("- Author: ${input.author.ifBlank { "(unset — workbench)" }}\n")
        append("- Generated: ${input.generatedAtIso}\n")
        append("- Generator: Authr OS ${input.generatorVersion} (P5 retail packager)\n")
        append("- Action: ${input.oneActionSummary}\n\n")

        append("## Retail artifacts\n\n")
        append("| Artifact | Kind | Bytes |\n| --- | --- | --- |\n")
        for (a in input.artifacts) {
            append("| ${a.path} | ${a.kind} | ${a.sizeBytes} |\n")
        }
        append("\n## Gate verdicts\n\n")
        append("| Artifact | Gate | Verdict | Status | Detail |\n| --- | --- | --- | --- | --- |\n")
        for (v in input.gateVerdicts) {
            append("| ${v.artifact} | ${v.gate} | ${if (v.passed) "GREEN" else "RED"} | ${v.status} | ${v.detail.replace("|", "\\|")} |\n")
        }
        val clean = isClean(input.gateVerdicts)
        append("\n**Package verdict: ${if (clean) "CLEAN — the export behind this report is defensible" else "NOT CLEAN — fix the red gates before upload"}**\n")

        append("\n## Process disclosures\n\n")
        if (input.disclosures.isEmpty()) {
            append("None.\n")
        } else {
            for (d in input.disclosures) append("- $d\n")
        }

        append("\n## Continuity disclosures (never silent)\n\n")
        append("### Chekhov waivers\n\n")
        if (input.chekhovWaivers.isEmpty()) {
            append("None — no guns were waived.\n")
        } else {
            append("| Gun | Introduced | Writer's note |\n| --- | --- | --- |\n")
            for (w in input.chekhovWaivers) {
                append("| ${w.gunName.replace("|", "\\|")} | ch. ${w.introducedChapter} | ${w.note.replace("|", "\\|")} |\n")
            }
        }
        append("\n### Scorecard notes\n\n")
        if (input.scorecardNotes.isEmpty()) {
            append("None.\n")
        } else {
            for (n in input.scorecardNotes) append("- $n\n")
        }
        append("\n### Cadence & buffer (serials)\n\n")
        if (input.cadenceLines.isEmpty()) {
            append("Not a serial — n/a.\n")
        } else {
            for (c in input.cadenceLines) append("- $c\n")
        }

        append("\n## Pricing record (simulator, plan §11.2)\n\n")
        for (p in input.pricingLines) append("- $p\n")

        append("\n## External gates owned by the destination\n\n")
        append("- epubcheck: run locally against every EPUB (see verdicts above); zero errors is the offer-for-upload precondition (plan §11.1).\n")
        append("- KDP Print Previewer: an Amazon-side check of the uploaded interior PDF; the gutter ladder in this package follows the plan's bands and is disclosed per band in the verdicts. The Previewer run itself happens at upload time on KDP's site.\n")
        append("- ACX audio: rendered and audited by the P4 audiobook engine (separate evidence tree; referenced here for the one-project-many-formats picture).\n")
    }

    /** Convenience: derive waiver lines from the ledger entities. */
    fun waiverLines(guns: List<ChekhovGun>): List<WaiverLine> =
        guns.filter { it.status == ChekhovLedger.WAIVED }
            .map { WaiverLine(it.name, it.introducedChapter, it.waiveNote.ifBlank { "(no note recorded)" }) }
}
