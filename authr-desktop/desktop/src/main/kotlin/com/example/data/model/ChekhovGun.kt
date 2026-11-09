package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Chekhov continuity ledger entry (v3.0, plan §5.2 / Table 8, Appendix A).
 *
 * Every promise the confirmed text makes — a weapon described in detail, a
 * debt, a skill, a named grudge, an unanswered question — is a gun with a
 * strict lifecycle:
 *   INTRODUCED -> ESCALATED -> RESOLVED
 *                                 \-> ORPHANED -> WAIVED (writer note required)
 * The final-act recovery gate blocks compile while any confirmed gun is
 * ORPHANED, unless the writer explicitly waives it (waivers are never silent;
 * they are listed in the ship report).
 */
@Entity(
    tableName = "chekhov_guns",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId"]), Index(value = ["bookId", "name"])]
)
data class ChekhovGun(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bookId: Int,
    val name: String,
    val description: String = "",
    /** INTRODUCED / ESCALATED / RESOLVED / ORPHANED / WAIVED */
    val status: String = "INTRODUCED",
    val introducedChapter: Int,
    /** JSON array of chapter numbers where the gun was re-emphasized. */
    val escalationChapters: String = "[]",
    val resolvedChapter: Int? = null,
    /** Quote from the confirmed text archived with the entry (resolution proof). */
    val quote: String = "",
    val severity: String = "normal",
    /** Mandatory note when status = WAIVED; waivers are listed in the ship report. */
    val waiveNote: String = ""
)
