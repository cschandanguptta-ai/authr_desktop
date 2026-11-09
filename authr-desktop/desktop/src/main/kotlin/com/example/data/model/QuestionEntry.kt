package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Question ledger entry (v3.0, plan §6.7 / Table 15, Appendix A).
 *
 * Tracks the three information levels of the serialization format:
 *   IMMEDIATE    - what the reader needs to understand the current scene
 *   PROGRESSIVE  - what the reader gradually discovers over the next few
 *                  chapters (tracked with expected-answer windows; overdue
 *                  questions surface on the writer's dashboard)
 *   LONG_TERM    - what stays unresolved until a major turning point or climax
 *
 * Questions share one ledger UI with the Chekhov guns: guns are promises about
 * objects, skills, and grudges; questions are promises about information.
 */
@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId"]), Index(value = ["bookId", "level"])]
)
data class QuestionEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bookId: Int,
    /** IMMEDIATE / PROGRESSIVE / LONG_TERM */
    val level: String,
    val text: String,
    val openedChapter: Int,
    /** Expected-answer window for PROGRESSIVE questions (chapter number). */
    val dueChapter: Int? = null,
    val answeredChapter: Int? = null,
    /** Revelation entry (questions.id) that answered this question. */
    val answeredByReveal: Int? = null
)
