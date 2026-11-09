package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chapters",
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
data class Chapter(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bookId: Int,
    val chapterNumber: Int,
    val title: String,
    val content: String,
    val summary: String,
    val status: String = "Drafting", // e.g. Drafting, Completed, Failed
    // --- Engine state (v3.0 schema v2, plan Table 32): writer sovereignty ---
    // Migration note: no @ColumnInfo defaultValue — MIGRATION_14_15 supplies
    // SQL DEFAULTs (writerConfirmed 1, draftStatus 'CONFIRMED') which Room's
    // schema validation tolerates on the database side.
    /** No text enters the manuscript canon without an explicit writer confirmation. */
    val writerConfirmed: Boolean = true,
    /** PROPOSED / CONFIRMED / ARCHIVED draft state of the L4 output. */
    val draftStatus: String = "CONFIRMED",
    // --- Serialization fields (v3.0 schema v2, plan §6.12 / Table 21; Appendix A) ---
    /** Owning micro-arc (serial_arcs.id). */
    val arcId: Int? = null,
    /** Advised ending classification: DANGER, EMOTIONAL_REVELATION, MYSTERY_DISCOVERY,
     *  IRREVERSIBLE_CHOICE, REVERSAL, ANTICIPATION, QUIET (plan §6.4). */
    val endingType: String? = null,
    /** JSON: seven-beat functions present in confirmed text, with spans (plan §6.3). */
    val beatCoverage: String? = null,
    /** JSON: measured segment shares against the advisory bands (plan §6.3). */
    val balanceShares: String? = null,
    /** JSON: pre-draft worksheet sections A-E (plan §6.8). */
    val worksheet: String? = null,
    /** JSON: scorecard criteria 1-7 (0-2 each, plan §6.9). */
    val qcScores: String? = null,
    /** Scorecard total 0-14; zero-triggers enforced at export. */
    val qcTotal: Int? = null,
    /** JSON: opening validator result for chapters 1-5 (plan §6.2). */
    val hookCheck: String? = null,
    /** Author note exported with the chapter (plan §6.10). */
    val ctaNote: String? = null,
    /** JSON poll payload exported with the chapter where platforms support polls. */
    val pollJson: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
