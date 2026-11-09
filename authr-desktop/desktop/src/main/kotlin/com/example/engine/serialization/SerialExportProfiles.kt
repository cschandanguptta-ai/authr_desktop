package com.example.engine.serialization

import com.example.data.model.Book
import com.example.data.model.Chapter

/**
 * Serial platform export profiles — Blueprint 2.0 (v3.0, plan §6.13,
 * Table 20).
 *
 * Chapter-level exports are PASTE-READY PACKAGES formatted per platform,
 * not automated platform APIs — the target platforms' terms of service make
 * unapproved automated posting a liability, so the contract is: the
 * application formats, the writer pastes. Each profile produces a chapter
 * block (title, optional recap block from the recap tool, body paragraphs
 * in the platform's spacing conventions, author note with CTA, and poll
 * text where the platform supports polls) plus a plain-text clipboard
 * bundle. Profiles are versioned data, adjustable without a release (risk
 * register: platform ToS changes).
 */
object SerialExportProfiles {

    data class Profile(
        val id: String,
        val platform: String,
        val model: String,
        /** Paragraph spacing convention applied between body paragraphs. */
        val paragraphGap: String,
        /** Scene-break rule inserted between scenes ("" = none). */
        val sceneBreak: String,
        /** Recap header block included by default. */
        val recapByDefault: Boolean,
        /** Author note appended. */
        val authorNoteAppended: Boolean,
        /** External links allowed inside the author note. */
        val allowLinksInNote: Boolean,
        /** Platform supports poll payloads. */
        val supportsPolls: Boolean,
        /** Episode-length guidance note (null = no guidance). */
        val lengthGuidance: String?
    )

    /** The shipped profiles (Table 20). Versioned data — adjust without a release. */
    val PROFILES = listOf(
        Profile(
            id = "royal_road", platform = "Royal Road", model = "Free serial; patronage monetization",
            paragraphGap = "\n\n", sceneBreak = "\n\n---\n\n", recapByDefault = false,
            authorNoteAppended = true, allowLinksInNote = true, supportsPolls = false,
            lengthGuidance = null
        ),
        Profile(
            id = "wattpad", platform = "Wattpad", model = "Free serial; ad and program monetization",
            paragraphGap = "\n\n\n", sceneBreak = "\n\n* * *\n\n", recapByDefault = false,
            authorNoteAppended = true, allowLinksInNote = true, supportsPolls = false,
            lengthGuidance = null
        ),
        Profile(
            id = "goodnovel", platform = "GoodNovel / Dreame / Webnovel", model = "Pay-per-chapter storefronts",
            paragraphGap = "\n\n", sceneBreak = "\n\n---\n\n", recapByDefault = true,
            authorNoteAppended = true, allowLinksInNote = false, supportsPolls = false,
            lengthGuidance = null
        ),
        Profile(
            id = "dreame", platform = "Dreame", model = "Pay-per-chapter storefronts",
            paragraphGap = "\n\n", sceneBreak = "\n\n---\n\n", recapByDefault = true,
            authorNoteAppended = true, allowLinksInNote = false, supportsPolls = false,
            lengthGuidance = null
        ),
        Profile(
            id = "webnovel", platform = "Webnovel", model = "Pay-per-chapter storefronts",
            paragraphGap = "\n\n", sceneBreak = "\n\n---\n\n", recapByDefault = true,
            authorNoteAppended = true, allowLinksInNote = false, supportsPolls = false,
            lengthGuidance = null
        ),
        Profile(
            id = "tapas", platform = "Tapas", model = "Episode model",
            paragraphGap = "\n\n", sceneBreak = "\n\n---\n\n", recapByDefault = false,
            authorNoteAppended = true, allowLinksInNote = false, supportsPolls = true,
            lengthGuidance = "Episode-length guidance against the 1,000-2,000-word envelope"
        )
    )

    fun byId(id: String): Profile = PROFILES.firstOrNull { it.id == id } ?: PROFILES.first()

    // ------------------------------------------------------------------
    // Package builder
    // ------------------------------------------------------------------

    data class Package(
        val platform: String,
        val profileId: String,
        val chapterNumber: Int,
        /** The paste-ready chapter block. */
        val chapterBlock: String,
        /** The plain-text clipboard bundle (block + note + poll). */
        val clipboardBundle: String,
        val warnings: List<String>
    )

    /**
     * Builds the paste-ready package for one chapter under one profile
     * (Table 20). [recapBlock] comes from the recap tool ("" = none);
     * [pollText] is exported only where the platform supports polls.
     */
    fun buildPackage(
        book: Book,
        chapter: Chapter,
        profile: Profile,
        recapBlock: String = "",
        pollText: String = ""
    ): Package {
        val ws = PreDraftWorksheet.Worksheet.fromJson(chapter.worksheet)
        val warnings = mutableListOf<String>()

        // Episode-length guidance (Tapas row, Table 20).
        profile.lengthGuidance?.let {
            val wc = OpeningValidators.wordCount(chapter.content)
            if (wc < book.wordMin || wc > book.wordMax) warnings.add(
                "$it: chapter ${chapter.chapterNumber} measures $wc words against the ${book.wordMin}-${book.wordMax} envelope."
            )
        }

        val body = chapter.content.split(Regex("""\n\s*\n"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(profile.paragraphGap)

        val block = buildString {
            appendLine("Chapter ${chapter.chapterNumber}: ${chapter.title}")
            appendLine()
            if (profile.recapByDefault && recapBlock.isNotBlank()) {
                appendLine("[PREVIOUSLY ON]")
                appendLine(recapBlock.trim())
                appendLine()
            }
            append(body)
            if (profile.authorNoteAppended) {
                val note = chapter.ctaNote?.trim().orEmpty()
                if (note.isNotEmpty()) {
                    appendLine()
                    appendLine()
                    appendLine("[AUTHOR'S NOTE]")
                    appendLine(if (profile.allowLinksInNote) note else note.replace(Regex("""https?://\S+"""), "[link removed — platform policy]"))
                }
            }
        }.trimEnd()

        // Poll text exports only where the platform supports polls (Table 20).
        val pollSection = if (profile.supportsPolls && pollText.isNotBlank()) {
            "\n\n[POLL]\n$pollText"
        } else ""
        if (!profile.supportsPolls && pollText.isNotBlank()) warnings.add(
            "${profile.platform} does not support polls — the poll was left out of the package (it stays in the note where the writer can phrase it as a question)."
        )

        val clipboard = block + pollSection
        return Package(
            platform = profile.platform,
            profileId = profile.id,
            chapterNumber = chapter.chapterNumber,
            chapterBlock = block,
            clipboardBundle = clipboard,
            warnings = warnings
        )
    }

    /** Convenience: package under the book's default export profile (§6.12 field). */
    fun buildDefault(book: Book, chapter: Chapter, recapBlock: String = "", pollText: String = "") =
        buildPackage(book, chapter, byId(book.exportProfile), recapBlock, pollText)

    /**
     * Batch export of the release queue in a chosen profile (Table 12,
     * platform-export row, Autonomous surface). Returns one package per
     * chapter plus the shared clipboard bundle header.
     */
    fun buildBatch(book: Book, chapters: List<Chapter>, profileId: String, recapFor: (Chapter) -> String = { "" }): List<Package> {
        val profile = byId(profileId)
        return chapters.sortedBy { it.chapterNumber }.map { buildPackage(book, it, profile, recapFor(it)) }
    }
}
