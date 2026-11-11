# AGENTS.md — Authr OS Desktop (v3.0, phase P5 complete — all plan phases P0–P5 implemented)

Authr OS is a multi-agent AI authoring workstation ported from Android to desktop (Kotlin JVM + Compose Multiplatform). Six Weaver sub-agents (Executive, Plot Architect, Character Psychologist, World Builder, Scene Composer, Continuity Manager) run over a multi-provider LLM orchestrator (Gemini, Mistral, OpenRouter, Groq, NVIDIA, Custom OpenAI, Ollama) to plan, draft, quality-gate, serialize, narrate, and export books and web-novel serials. Every generated artifact is a PROPOSED proposal that becomes canon only when the writer confirms it (writer-sovereignty, `WriterGate`). Projects live in a Room SQLite database and travel as `.authr` container zip files.

## Doc index — read this when…

| Doc | Read this when… |
| --- | --- |
| `docs/system-map.md` | You need to find where a feature lives, how components connect, or what the entry points/background jobs are. |
| `docs/change-impact.md` | **Before changing any code.** Tells you what else breaks when you touch a module. |
| `docs/invariants-and-gotchas.md` | Before touching confirm flows, ledgers, the canon gate, DB schema, or the work pipeline. Lists rules that must never break and desktop-port traps. |
| `docs/troubleshooting.md` | Something broke. Symptom → cause → where to look → fix. |
| `docs/data-model.md` | Changing entities, DAOs, or migrations, or understanding tables/relations. |
| `docs/api-contracts.md` | Changing LLM provider calls, request/response shapes, or provider failover. |
| `docs/config-and-env.md` | Adding/reading env vars, API keys, SharedPreferences keys, or feature fields. |
| `docs/testing.md` | Before running or writing tests; explains the exit-gate suites and fakes. |
| `docs/tech-debt.md` | Planning refactor work; known shortcuts and fragile areas with file refs. |
| `docs/open-questions.md` | You need a decision from a human; also where UNKNOWNs are collected. |
| `docs/decisions/` | Understanding WHY something is the way it is (ADRs, one per major decision). |

## Hard rules (never do these)

1. **Never feed unconfirmed text into any ledger, export, or downstream layer.** All consumers must run `WriterGate.canonChapters(...)` first (`engine/DraftState.kt`). The P1/P3 tests assert this; breaking it fails `WriterGateTest`, `SixLayerPipelineTest`, `WriterStudioFlowTest`, `SerializationGoldenSerialTest`.
2. **Never delete or "simplify" the PROPOSED/CONFIRMED/ARCHIVED state machine** (`engine/DraftState.kt`) or the confirm-time persistence in `ui/viewmodel/WriterStudioViewModel.confirmChapter`.
3. **Never make a DB migration destructive or non-additive.** Migrations in `data/local/AppDatabase.kt` are ALTER-add/CREATE only; `fallbackToDestructiveMigration(true)` exists but no shipped migration path relies on it — do not add one.
4. **Never auto-assign an ending type or a QC score.** The advisor classifies (`engine/serialization/EndingAdvisor.kt`); L6 proposes scores; the writer always sets finals (`engine/serialization/QcScorecard.kt`).
5. **Never gate a writer action behind an LLM call.** Deterministic guardrails (cliché linter, validators, ledgers) are local code and must stay local (`engine/`, `engine/serialization/`).
6. **Never add a platform API integration for serial publishing.** Exports are paste-ready packages only (`engine/serialization/SerialExportProfiles.kt`).
7. **Never invent facts in docs.** Every claim cites a file; label FACT / INFERRED / UNKNOWN per `docs/open-questions.md`.

## Commands

Run from `authr-desktop/` (Gradle 8.13, JDK 21; sandbox note: `org.gradle.java.home` in `gradle.properties` points to `/tmp/my-project/tools/jdk`):

```bash
./gradlew :desktop:test                 # full test suite (209 tests as of P5)
./gradlew :desktop:compileKotlin        # fast compile check
./gradlew :desktop:createDistributable  # 188 MB app image → desktop/build/compose/binaries/main/app/AuthrOS
./gradlew :desktop:run                  # run the desktop app
```

P4/P5 sandbox fallback (Gradle deps blocked): `../scripts/p4_direct_test.sh` compiles the `engine/audio` suite with direct kotlinc + JUnitCore (jars via `../scripts/p4_fetch_toolchain.sh` into `../.build-extras/`); `../scripts/p4_gate.sh` runs the ten-chapter audiobook exit gate. `../scripts/p5_direct_test.sh` / `../scripts/p5_gate.sh` do the same for the publishing engine (extra jars: pdfbox/fontbox, the kotlinx-serialization compiler plugin, and the epubcheck 5.2.1 dist under `.build-extras/epubcheck-dist/`); the P5 gate produces full retail packages for a novel + a serial with the real epubcheck verdicts and all four gutter-ladder bands.

There is no separate lint task (`kotlin.code.style=official` is set but no linter task configured — FACT: `desktop/build.gradle.kts`). There is no CI pipeline (FACT: no CI config files in the repo). Schema migration is manual: edit `AppDatabase.kt` (`version` + a new `Migration`); Room `exportSchema = false` (FACT: `data/local/AppDatabase.kt`).

## Workflow

1. **Before changing code**: read `docs/change-impact.md` (blast radius) and the relevant module sections of `docs/system-map.md` + `docs/invariants-and-gotchas.md`.
2. **Make the change** with tests: engine changes get unit tests in `desktop/src/test/kotlin/com/example/` (JVM-pure, Room-free fakes — see `docs/testing.md`).
3. **Run** `./gradlew :desktop:test` — all 209 must stay green. Exit-gate suites are load-bearing; if you break one, fix the code, never weaken the assertion (see ADR-0007).
4. **After changing code**: update the affected docs in this folder (system-map table rows, change-impact rows, data-model if schema moved, config-and-env if config moved). An outdated doc is a bug.
5. **After fixing a real incident**: add the symptom + fix to `docs/troubleshooting.md` and any new rule to `docs/invariants-and-gotchas.md`.
