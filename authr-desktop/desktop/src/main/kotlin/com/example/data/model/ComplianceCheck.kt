package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * COMPLIANCE CHECKS (ADR-0022, spec schema v2.2 `compliance_checks`): the
 * pre-publication compliance gate's persistent results. Retail compilation
 * is blocked while any check has status='blocking' and resolved=0 (spec
 * Invariant 18) — mirroring how the manuscript-QA gate blocks pipeline
 * stage 8 (ADR-0018).
 */
@Entity(
    tableName = "compliance_checks",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId"]), Index(value = ["bookId", "checkType"])]
)
data class ComplianceCheck(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bookId: Int,
    /** 'plagiarism' | 'trademark' | 'sensitivity' | 'ai_disclosure' | 'copyright' | 'defamation'. */
    val checkType: String,
    /** 'chapter:5' | 'full_manuscript' | 'character:Elena' | null = whole project. */
    val scope: String? = null,
    /** 'pending' | 'pass' | 'warning' | 'blocking'. */
    val status: String = "pending",
    /** JSON array of specific findings. */
    val findings: String? = null,
    /** JSON array of recommended fixes. */
    val recommendedActions: String? = null,
    /** 1 = issue addressed or waived (writer sovereignty: only the author waives). */
    val resolved: Boolean = false,
    /** How it was resolved (waiver, fix, etc.). */
    val resolvedNote: String? = null,
    val checkedAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null
) {
    companion object {
        const val TYPE_PLAGIARISM = "plagiarism"
        const val TYPE_TRADEMARK = "trademark"
        const val TYPE_SENSITIVITY = "sensitivity"
        const val TYPE_AI_DISCLOSURE = "ai_disclosure"
        const val TYPE_COPYRIGHT = "copyright"
        const val TYPE_DEFAMATION = "defamation"
        val TYPES = listOf(
            TYPE_PLAGIARISM, TYPE_TRADEMARK, TYPE_SENSITIVITY,
            TYPE_AI_DISCLOSURE, TYPE_COPYRIGHT, TYPE_DEFAMATION
        )

        const val STATUS_PENDING = "pending"
        const val STATUS_PASS = "pass"
        const val STATUS_WARNING = "warning"
        const val STATUS_BLOCKING = "blocking"
        val STATUSES = listOf(STATUS_PENDING, STATUS_PASS, STATUS_WARNING, STATUS_BLOCKING)

        /**
         * The Invariant-18 gate predicate: unresolved blocking findings stop
         * packaging. Warnings never block (spec: sensitivity starts
         * warning-only); resolved findings never block.
         */
        fun blocksPackaging(checks: List<ComplianceCheck>): Boolean =
            checks.any { it.status == STATUS_BLOCKING && !it.resolved }
    }
}
