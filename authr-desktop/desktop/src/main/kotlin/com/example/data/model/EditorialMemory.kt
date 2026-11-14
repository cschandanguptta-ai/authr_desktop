package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Editorial intelligence layer (ADR-0018): the persistent memory that keeps
 * every pipeline stage consistent — project direction, market findings,
 * positioning, decisions, issues, versions and per-stage state.
 *
 * One row per (bookId, slot); [slot] discriminates the payload kind so the
 * eight-stage pre-manuscript memory stays schema-stable when new slots are
 * added (additive-only migration doctrine, ADR-0002/0016).
 */
@Entity(tableName = "editorial_memory")
data class EditorialMemory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bookId: Int,
    /** Payload slot: market_findings | positioning | reader_personas | decisions | issues | versions | stage_state | engagement */
    val slot: String,
    /** JSON payload (never raw prose; readers parse defensively and fail loud on drift). */
    val payload: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val SLOT_MARKET = "market_findings"
        const val SLOT_POSITIONING = "positioning"
        const val SLOT_PERSONAS = "reader_personas"
        const val SLOT_DECISIONS = "decisions"
        const val SLOT_ISSUES = "issues"
        const val SLOT_VERSIONS = "versions"
        const val SLOT_STAGE_STATE = "stage_state"
        const val SLOT_ENGAGEMENT = "engagement"
        /** ADR-0018 stage-9 output: the raw publication package JSON. */
        const val SLOT_PUBLICATION_PACKAGE = "publication_package"

        /** All defined slots; new slots must be appended, never reordered. */
        val ALL_SLOTS = listOf(
            SLOT_MARKET, SLOT_POSITIONING, SLOT_PERSONAS, SLOT_DECISIONS,
            SLOT_ISSUES, SLOT_VERSIONS, SLOT_STAGE_STATE, SLOT_ENGAGEMENT, SLOT_PUBLICATION_PACKAGE
        )
    }
}
