package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * RESEARCH VAULT (ADR-0021, spec schema v2.2 `research_sources` +
 * `research_facts`): the source-grounded research store. Sources are
 * ingested reference material; facts are extracted claims linked to the
 * chapters that cite them, enabling fact-checking and citation
 * verification before publication (spec Invariant 17).
 */

@Entity(
    tableName = "research_sources",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId"])]
)
data class ResearchSource(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bookId: Int,
    /** 'url' | 'pdf' | 'book_reference' | 'note' | 'interview' | 'dataset'. */
    val sourceType: String,
    val title: String,
    /** Source author or publisher. */
    val author: String? = null,
    /** URL for web sources. */
    val url: String? = null,
    /** Local file path for PDFs/documents. */
    val filePath: String? = null,
    /** Extracted or pasted key content. */
    val contentSnippet: String? = null,
    /** JSON array of topic tags. */
    val tags: String? = null,
    /** JSON: pre-formatted citations keyed by style, e.g. {"chicago": "...", "mla": "..."}. */
    val citationFormat: String? = null,
    /** 'verified' | 'unverified' | 'disputed' | 'primary' | 'secondary'. */
    val reliability: String = "unverified",
    val ingestedAt: Long = System.currentTimeMillis(),
    val lastVerifiedAt: Long? = null
) {
    companion object {
        const val TYPE_URL = "url"
        const val TYPE_PDF = "pdf"
        const val TYPE_BOOK_REFERENCE = "book_reference"
        const val TYPE_NOTE = "note"
        const val TYPE_INTERVIEW = "interview"
        const val TYPE_DATASET = "dataset"
        val TYPES = listOf(
            TYPE_URL, TYPE_PDF, TYPE_BOOK_REFERENCE, TYPE_NOTE, TYPE_INTERVIEW, TYPE_DATASET
        )

        const val RELIABILITY_VERIFIED = "verified"
        const val RELIABILITY_UNVERIFIED = "unverified"
        const val RELIABILITY_DISPUTED = "disputed"
        const val RELIABILITY_PRIMARY = "primary"
        const val RELIABILITY_SECONDARY = "secondary"
        val RELIABILITIES = listOf(
            RELIABILITY_VERIFIED, RELIABILITY_UNVERIFIED, RELIABILITY_DISPUTED,
            RELIABILITY_PRIMARY, RELIABILITY_SECONDARY
        )
    }
}

@Entity(
    tableName = "research_facts",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ResearchSource::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId"]), Index(value = ["sourceId"])]
)
data class ResearchFact(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bookId: Int,
    val sourceId: Int,
    /** The factual claim extracted from the source. */
    val factText: String,
    /** e.g. 'historical_date', 'scientific_claim', 'statistic'. */
    val category: String? = null,
    /** JSON array of chapter numbers that cite this fact. */
    val chapterRefs: String? = null,
    /** 1 = fact-checked against the source. */
    val verified: Boolean = false,
    /** 0.0..1.0 confidence in accuracy (spec CHECK range). */
    val confidence: Double = 0.5,
    /** Human note on the verification outcome. */
    val verificationNote: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
