# ADR-0002 — .authr container schema v2 with additive-only migrations

**Status:** Accepted (P0)
**Context:** Projects must round-trip losslessly between the desktop port and the Android edition, and legacy (v1 prototype, v2.3) containers must open without data loss.
**Decision:** One interchange format: a zip with `authr/manifest.json` (explicit `schemaVersion`/`appVersion`) and `authr/metadata/book.json` plus payload directories (`container/AuthrContainer.kt`). Migrations are forward-only, additive, with automatic backup and a migration note (`migrateForward`). Unknown keys ignored (plan §12).
**Alternatives considered:** destructive re-import (rejected — user data loss); versioned parallel code paths per schema (rejected — maintenance cost).
**Consequences:** `AuthrContainerRoundTripTest` (5 tests) pins the guarantee; payload mappings must be updated for every entity change (`toPayload`/`toEntity` pairs).
**Do not change unless:** a new schema version is being introduced by the plan, with a round-trip test updated in the same change.
**Evidence:** `container/AuthrContainer.kt` (SCHEMA_VERSION=2, export/import/migrateForward), `AuthrContainerRoundTripTest`.
