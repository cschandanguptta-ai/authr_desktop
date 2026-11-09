package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Serial micro-arc (v3.0, plan §6.5 / §6.12, Appendix A).
 *
 * A micro-arc is the macro planning unit of the web-serial format: a chained
 * 10-15 chapter cycle with four phases — Objective & Setup (1-3), Escalation
 * (4-7), Reversal & Pivot (8-11), Climax & Resolution (12-15). Every arc must
 * answer at least one important question introduced within it (completion
 * requirement) and declare its contribution to the larger novel.
 */
@Entity(
    tableName = "serial_arcs",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId"]), Index(value = ["bookId", "arcIndex"], unique = true)]
)
data class SerialArc(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bookId: Int,
    val arcIndex: Int,
    /** The localized conflict statement of this arc. */
    val conflict: String,
    /** Question ledger entry this arc introduces and must answer (questions.id). */
    val questionRef: Int? = null,
    /** JSON: {"setup":[1,3],"escalation":[4,7],"reversal":[8,11],"climax":[12,15]} */
    val phaseBounds: String,
    /** Question ledger entry answered by this arc at close (questions.id). */
    val answeredQuestionRef: Int? = null,
    /** The arc's declared contribution to the larger novel (ship report lists it). */
    val contribution: String = "",
    /** JSON array of next-arc seeds (objective, characters, complications). */
    val seeds: String = "[]"
)
