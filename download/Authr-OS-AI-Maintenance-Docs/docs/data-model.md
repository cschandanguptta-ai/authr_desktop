# Data Model

Room KMP database, version 15, 11 entities. Facts cite `desktop/src/main/kotlin/com/example/data/`. Mermaid ER for the core writing domain; `stories`/`audio_tracks`/`*_fts` are v2.3-legacy support tables.

## ER diagram

```mermaid
erDiagram
    BOOKS ||--o{ CHAPTERS : "chapters.bookId (FK CASCADE)"
    BOOKS ||--o{ SERIAL_ARCS : "serial_arcs.bookId (FK CASCADE, unique bookId+arcIndex)"
    BOOKS ||--o{ CHEKHOV_GUNS : "chekhov_guns.bookId (FK CASCADE)"
    BOOKS ||--o{ QUESTIONS : "questions.bookId (FK CASCADE)"
    BOOKS ||--o{ LOREBOOK : "lorebook.bookId"
    BOOKS ||--o{ AUDIO_TRACKS : "audio_tracks (book context)"
    SERIAL_ARCS |o--o| QUESTIONS : "questionRef / answeredQuestionRef (no FK — logical link)"
    CHAPTERS |o--o| SERIAL_ARCS : "chapters.arcId (no FK — logical link)"
    CHAPTERS ||--o{ CHAPTERS_FTS : "FTS mirror"
    STORIES ||--o{ STORIES_FTS : "FTS mirror"

    BOOKS {
        int id PK
        string title
        string genre
        string synopsis
        string outline
        string format
        bool isSerialized "v2.3 seed — switches the serial profile"
        int targetChapters "default 100; 0 = unset"
        string releaseSchedule "default Mon/Wed/Fri"
        int freeTierChapters "default 5"
        int wordMin "default 1000"
        int wordMax "default 2000"
        int bufferTarget "default 8 (4-8 range)"
        string exportProfile "default royal_road"
        bool writerConfirmed_note "engine state lives on chapters, not books"
        string compiledManuscript
        string currentPhase
        string storyBible
        string characterBible
        string worldBible
        string agentLogs
        int completionPercentage
    }
    CHAPTERS {
        int id PK
        int bookId FK
        int chapterNumber
        string title
        string content
        string summary
        string status "free text: Drafting | Proposed (N words) | Completed (N words) | Released (ISO date)"
        bool writerConfirmed "engine state — canon gate input"
        string draftStatus "PROPOSED | CONFIRMED | ARCHIVED"
        int arcId "logical link to serial_arcs.id"
        string endingType "DANGER | EMOTIONAL_REVELATION | MYSTERY_DISCOVERY | IRREVERSIBLE_CHOICE | REVERSAL | ANTICIPATION | QUIET"
        string beatCoverage "JSON {coverage: n/7}"
        string balanceShares "JSON {band: pct}"
        string worksheet "JSON sections A-E"
        string qcScores "JSON {c1..c7, r1..r7}"
        int qcTotal "0-14"
        string hookCheck "JSON {scannedWords, sources[]}"
        string ctaNote
        string pollJson
    }
    SERIAL_ARCS {
        int id PK
        int bookId FK
        int arcIndex "unique per book"
        string conflict
        int questionRef "questions.id (logical)"
        string phaseBounds "JSON {setup:[a,b],escalation:[..],reversal:[..],climax:[..]}"
        int answeredQuestionRef "questions.id (logical)"
        string contribution
        string seeds "JSON array"
    }
    CHEKHOV_GUNS {
        int id PK
        int bookId FK
        string name
        string description
        string status "INTRODUCED | ESCALATED | RESOLVED | ORPHANED | WAIVED"
        int introducedChapter
        string escalationChapters "JSON array"
        int resolvedChapter
        string quote "context window ±40 chars — duplicate-detection key"
        string severity
        string waiveNote "mandatory when WAIVED"
    }
    QUESTIONS {
        int id PK
        int bookId FK
        string level "IMMEDIATE | PROGRESSIVE | LONG_TERM"
        string text
        int openedChapter
        int dueChapter "progressive only"
        int answeredChapter
        int answeredByReveal
    }
    LOREBOOK {
        int id PK
        int bookId
        string key
        string value
    }
```

Full column lists: `data/model/Book.kt`, `Chapter.kt`, `SerialArc.kt`, `ChekhovGun.kt`, `QuestionEntry.kt`, `Lorebook.kt`, `Story.kt`, `AudioTrack.kt`, `PluginRecord.kt` (plugins: `id, kind, name, path, version, enabled`, unique `(kind, name)`).

## Relationships & constraints

- FKs with `ON DELETE CASCADE`: chapters / serial_arcs / chekhov_guns / questions → books (FACT: `@ForeignKey` in each entity).
- `serial_arcs` unique index `(bookId, arcIndex)` (FACT: `SerialArc.kt`).
- **No DB-level FK** for `chapters.arcId`, `serial_arcs.questionRef`, `serial_arcs.answeredQuestionRef` — logical links only (FACT: entity definitions; the completion requirement checks them in code, `MicroArcPlanner.checkArcs`).
- Legacy tables: `stories` + `stories_fts`, `chapters_fts`, `audio_tracks`, `lorebook` (v2.3 baseline; FACT: `AppDatabase` entity list).

## Migration history (FACT: `data/local/AppDatabase.kt`)

| Version | Change |
| --- | --- |
| 12→13 | `books.lastModifiedAt` (DB-side default 0; entity declares none — Room tolerates DB-side defaults) |
| 13→14 | v2.3 serial seeds: `isSerialized`, `targetChapters`, `releaseSchedule` |
| 14→15 | v3.0 schema v2 (plan Table 32 + Appendix A): serial profile columns on books; writer-confirmed engine state + serialization columns on chapters; new tables `serial_arcs`, `chekhov_guns`, `questions`, `plugins` |

## Migration rules (how to safely change schema)

1. **Additive only** — `ALTER TABLE … ADD COLUMN` / `CREATE TABLE` / `CREATE INDEX`. No column drops, no type changes, no NOT NULL without a DB-side DEFAULT.
2. Bump `AppDatabase.version` and append a new `Migration(from, to)`; register it in `getDatabase().addMigrations(...)`.
3. **Do not declare `@ColumnInfo defaultValue`** on migrated columns — Kotlin defaults govern writes; Room's `TableInfo.equalsCommon` tolerates database-side defaults (documented in `Book.kt`).
4. Unknown prefs/settings keys are ignored on migration (plan §12); legacy chapters migrate as canon (`writerConfirmed=1`, `draftStatus='CONFIRMED'` — FACT: `MIGRATION_14_15` comments).
5. `fallbackToDestructiveMigration(true)` is a safety net, not a tool — a missing migration that triggers it destroys user data (see troubleshooting).
6. Mirror every schema change into `AuthrContainer` payloads (`container/AuthrContainer.kt` toPayload/toEntity pairs) and bump the container's field set — the round-trip test pins this (`AuthrContainerRoundTripTest`).

## JSON field conventions (inside TEXT columns)

- `worksheet`: `{"A_objective","B_character","C_plot","D_reader","E_ending"}` (FACT: `PreDraftWorksheet.toJson`).
- `qcScores`: `{"c1".."c7","r1".."r7"}` (FACT: `QcScorecard.toJson`).
- `beatCoverage`: `{"coverage":"7/7"}` (FACT: WriterStudioViewModel confirm flow).
- `balanceShares`: `{"<Band name>": pct}` with band display names as keys (FACT: `BalanceMeter.balanceSharesJson`).
- `hookCheck`: `{"scannedWords":N,"sources":["…"]}` (FACT: confirm flow).
- `phaseBounds`: `{"setup":[1,3],"escalation":[4,7],"reversal":[8,11],"climax":[12,15]}` (FACT: `MicroArcPlanner.phaseBoundsJson`).
- `seeds`, `escalationChapters`: JSON string arrays.
- Serialization via `org.json` (on the JVM classpath; FACT: `desktop/build.gradle.kts`), not kotlinx-serialization, for these engine fields.
