# ADR-0008 — Serial release state rides `Chapter.status` text, not a new column

**Status:** Accepted (P3)
**Context:** The buffer meter and cadence tracker need a released/unreleased distinction, but the plan's serialization field set (Table 21) defines no released column and the DB is frozen additive.
**Decision:** Released chapters carry status `"Released (ISO date)"` (prefix `Released`, FACT: `BufferCadence.RELEASED_PREFIX`); the buffer counts canon chapters not matching the prefix; release dates are parsed from the parenthesized ISO date.
**Alternatives considered:** new `releasedAt` column (rejected — schema churn outside the plan's field set); a separate table (rejected — overkill for a marker).
**Consequences:** Status is a semi-structured free-text field — consumers must use `BufferCadence.releaseMarker/isReleased/releaseDate`, never loose parsing; legacy "Completed (N words)" statuses read as unreleased, which is correct.
**Do not change unless:** the plan adds a released field (then migrate + keep the marker parser for old rows).
**Evidence:** `engine/serialization/BufferCadence.kt`, dashboard `releaseChapter`, `BufferCadenceTest`.
