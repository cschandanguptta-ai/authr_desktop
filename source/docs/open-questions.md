# Open Questions & Human Confirmation Checklist

This document tracks unresolved ambiguities and architectural questions identified during codebase analysis. Every item requires human/maintainer confirmation.

---

## Architecture & Product Scope

- [ ] **1. Status of Orphaned Screens (`AudioScreen`, `TranslateScreen`, `MetadataScreen`, `SceneAutomationScreen`)**
  - **Context**: These four major screens (totaling >4,000 lines) exist in `app/src/main/java/com/example/ui/screens/` but were removed from `MainActivity.kt` tab navigation in recent updates.
  - **Question**: Are these screens permanently deprecated and candidates for deletion, or will they be re-introduced as sub-flows (e.g. from a project context menu or bottom sheet)?
  - **Status**: UNKNOWN: needs human confirmation.

- [ ] **2. Long-Term Plan for Dual Data Models (`Story` vs `Book`)**
  - **Context**: The app supports both `Story` (legacy 5-part Interactive Wizard) and `Book` (normalized multi-chapter Autonomous Studio).
  - **Question**: Should the Interactive Wizard be migrated to generate 5-chapter `Book` entities so `Story` and `StoryDao` can be safely deprecated?
  - **Status**: UNKNOWN: needs human confirmation.

- [ ] **3. Release Keystore & CI/CD Pipeline**
  - **Context**: `app/build.gradle.kts:28-34` references environment variables `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` with fallback to `${rootDir}/my-upload-key.jks`.
  - **Question**: What CI/CD system builds release APKs/AABs, and where are the production release signing keys stored?
  - **Status**: UNKNOWN: needs human confirmation.

- [ ] **4. WorkManager Battery Constraint Policy**
  - **Context**: WorkManager requests mandate `.setRequiresBatteryNotLow(true)`. On low-battery devices, generation silently stays in `ENQUEUED` state.
  - **Question**: Should this constraint be relaxed to allow generation on low battery with a warning banner, or kept strictly enforced to avoid mid-chapter OS kills?
  - **Status**: UNKNOWN: needs human confirmation.

- [ ] **5. In-Memory vs Persistent Rate Limiting**
  - **Context**: `RequestQueueManager` tracks requests per minute in volatile JVM memory.
  - **Question**: If an app process is killed and resurrected by WorkManager, should the rate limiter state be backed by SQLite/DataStore to guarantee global RPM adherence across process deaths?
  - **Status**: UNKNOWN: needs human confirmation.

- [ ] **6. Cleanup of Root Patch Scripts**
  - **Context**: Over 80 utility scripts (`patch_*.py`, `fix_*.py`) reside in the root repository folder.
  - **Question**: Can these historical build/patch scripts be safely deleted or moved to a `/tools/scripts/` archive folder?
  - **Status**: UNKNOWN: needs human confirmation.
