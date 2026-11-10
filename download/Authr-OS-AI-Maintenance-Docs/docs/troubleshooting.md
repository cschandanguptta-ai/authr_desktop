# Troubleshooting

Symptom → likely cause → where to look → fix. Labeling: FACT / INFERRED. Log guidance first, because everything else depends on it.

## Reading logs

- **Desktop app log**: `android.util.Log` writes to stdout/stderr in the desktop port (FACT: `android/util/Log.kt` shim prints). Launch the packaged binary with stdout captured (`bin/AuthrOS > app.log 2>&1`) or run `gradle :desktop:run`. Tag conventions: `DesktopApplication` (startup + data root), `DesktopWorkManager` (worker chains), `LlmOrchestrator` (provider failover, keys masked), `CompilationWorker`, `OutlineWorker`, `DraftingWorker`, `WriterStudio`/ViewModel statuses surface in-app instead.
- **Agent chat log** (the in-app narration of every pipeline step): stored per book in `books.agentLogs` (FACT: `worker/WorkerUtils.logAgentChat` writes via `bookDao`). This is the primary forensic surface for what the autonomous pipeline decided.
- **Startup line**: `Authr OS starting; data root: <path>` (FACT: `Main.kt` `onCreate`). Data root on Linux: `~/.local/share/authr` (observed in smoke runs) — `filesDir` = `<dataRoot>` and everything (DB, prefs, container, plugins) lives under it.

## Symptom tables

### Auth failures (LLM)

| Symptom | Likely cause | Where to look | Fix |
| --- | --- | --- | --- |
| `Skipping <provider>: no API keys configured.` | No key in secure prefs and no `.env` fallback for that provider | `LlmOrchestrator.getApiKeys`, `docs/config-and-env.md` | Enter key in Settings (stored in secure prefs), or add to `.env` (`GEMINI_API_KEY` etc., see `BuildConfig.loadDotEnv` — cwd `.env`, `~/.authr/.env`, `~/.env`). |
| `HTTP 401 Unauthorized: The API key for <provider> is invalid or expired.` | Bad/expired key | `LlmOrchestrator` HTTP-exception mapping | Replace the key; the failover chain moves to the next provider automatically. |
| `Gemini API key is invalid or empty.` | Key still the placeholder `MY_GEMINI_API_KEY` | `LlmOrchestrator` Gemini block checks for the placeholder | Set a real key. |
| `HTTP 429 Too Many Requests` | Provider rate limit | `LlmOrchestrator` failover logs | Automatic failover; reduce `RequestQueueManager` concurrency or wait. |
| `HTTP 413 Payload Too Large` | Prompt/context exceeds the provider limit | `LlmOrchestrator` mapping; prompt assembly in `DraftingWorker` / `StudioViewModel` | Shorten RAG context / chapter summaries; try a provider with a bigger window. |
| All providers fail: `All configured AI providers and keys failed to reply.` | Network down or every configured key bad | `LlmOrchestrator.callAgent` final throw | Check connectivity; verify keys per provider. |
| Queue length stuck / "waiting requests" never replay after restart | Persistence is accounting-only — failed requests are saved and counted, never re-executed | `RequestQueueManager.loadQueueFromPersistence` fills `serializedRequests`; no replay consumer exists | Re-issue the failed generation from the UI; do not wait for automatic replay. |

### Database errors

| Symptom | Likely cause | Where to look | Fix |
| --- | --- | --- | --- |
| `IllegalStateException: Pre-packaged database has an invalid version` / schema validation failure on migration | Entity default mismatch (rule: no `@ColumnInfo` defaults on migrated columns) or a non-additive migration | `data/local/AppDatabase.kt`, `data/model/Book.kt` comment | Revert the schema change; follow `docs/data-model.md` migration rules. |
| Data "disappeared" after version bump | `fallbackToDestructiveMigration(true)` wiped rows because NO matching migration existed | `AppDatabase.getDatabase` | Add the missing migration; the DB file lives at `<dataRoot>/files/databases/audio_novel_studio_db` (FACT: `Context.getDatabasePath`). |
| FTS search returns nothing | Sanitizer stripped the query (short/stopword input) | `AppRepository.sanitizeFtsQuery` | Use longer queries; FTS tables are `stories_fts`/`chapters_fts`. |
| Ledger/arc queries return empty in tests | Nullable DAOs default to null → empty lists | `AppRepository` constructor (nullable `chekhovGunDao/questionDao/serialArcDao`) | Pass the fake DAOs (see `docs/testing.md`). |

### Build / deploy failures

| Symptom | Likely cause | Where to look | Fix |
| --- | --- | --- | --- |
| Gradle: `Could not resolve …` / HTTP 429 storms | Maven Central rate-limits parallel metadata bursts (recurred in P2 AND P3) | `settings.gradle.kts` comment (mavenLocal first) | Retry the build — Gradle caches each success; or curl-seed missing artifacts into `~/.m2/repository` (P2 workaround, documented in the P3 report §7). |
| `org.gradle.java.home` path missing | Sandbox recycle removed `/tmp/my-project/tools/jdk` | `gradle.properties` | Re-install JDK 21 there or point the property at a local JDK 21. |
| Kotlin: `Return type mismatch: expected Pair, actual Serializable` | Kotlin parses `if (c) a else b to c` as `(if (c) a else b) to c` | the offending `return` | Use explicit locals/braces (P3 case: `LiveAstAnnotator.classifyEnding`). |
| Tests fail after touching a validator/cue regex | Golden corpora are cue-locked | `engine/serialization/GoldenSerial.kt`, `ChekhovLedgerTest.chapterTexts` | Update BOTH sides consistently; see `docs/change-impact.md`. |
| `createDistributable` fails on jlink | Needs a full JDK (JRE lacks jlink) | `gradle.properties` (`org.gradle.java.home`) | Point at the full JDK 21. |
| Headless sandbox: `HeadlessException` / `Cannot create Linux GL context` | No X display / no GPU | smoke harness `scripts/p3_smoke.sh` uses Xvfb + `-Dskiko.renderApi=SOFTWARE` | Run under Xvfb with software rendering; rendering stays CPU-bound. |

### Third-party API failures (LLM + TTS)

| Symptom | Likely cause | Where to look | Fix |
| --- | --- | --- | --- |
| Provider "returned empty text" | Model returned refusal/empty candidate | `LlmOrchestrator` per-provider `text ?: throw` | Failover handles it; try another model via Settings model pickers. |
| `HTTP 404 … model does not exist` | Stale model name in prefs | `gemini_model` / `groq_model` / `openrouter_model` etc. in secure prefs | Pick a current model in Settings (ModelDiscoveryService lists live models). |
| Pocket TTS: `Pocket TTS binary not found` | Binary not in any resolution path | `desktopbridge/PocketTtsBridge.locateBinary` (bundled → `POCKET_TTS_BIN` → PATH → `~/.authr/pocket-tts`) | Install the binary per that order or set `POCKET_TTS_BIN`. |
| Exported serial package looks wrong for the platform (spacing/recap/poll missing) | Wrong profile selected, or the profile's contract misunderstood | `engine/serialization/SerialExportProfiles.PROFILES` + `buildPackage`; `Book.exportProfile`; the dashboard picker row shows only the first four profiles (tech-debt D-09) | Pick the right profile (batch export accepts every id); recap exports only when the profile defaults it AND a recap block is supplied; polls export only where `supportsPolls`. |
| TTS output garbled/silent | MP3 decode path | `desktopbridge/Mp3ToWav`, `utils/WavUtils`, jlayer | Check the input format; Edge/Cloud managers have their own key requirements (`BuildConfig` keys). |
| Edge TTS: `WebSocket Failure` / handshake rejected, narration silently skips | Microsoft rotated the reverse-engineered handshake: pinned `TRUSTED_CLIENT_TOKEN`, `Sec-MS-GEC` token scheme and Edge UA/Origin headers in `EdgeTtsManager` can be invalidated server-side at any time | `tts/EdgeTtsManager.kt` (`TRUSTED_CLIENT_TOKEN`, `generateGecToken`, pinned `Sec-MS-GEC-Version`) | Update the token/version from a current edge-tts community source. NOTE the blast radius: `PocketTtsManager` (fallback, engine marker `edge-fallback`) and the desktop `TextToSpeech` shim route through Edge too — three engines go quiet together. `speakParagraphSuspend` swallows exceptions and returns `false` (no dialog, only `EdgeTtsManager` tags in stdout logs). |
| Edge TTS keeps replaying old audio after a synthesis/format fix | Chunk cache keys on MD5(text, voice, speed, pitch) — cached MP3s never expire | `EdgeTtsManager.speakParagraphSuspend` (`edge_tts_chunk_<md5>.mp3` in cacheDir) | Clear the app cache dir; if the cache key format ever changes, bump it in the same commit. |
| ffmpeg: binary not found | Not bundled and no override | `desktopbridge/FfmpegLocator` (bundled → `FFMPEG_HOME` → PATH) | Set `FFMPEG_HOME` or install ffmpeg on PATH. |

### Performance issues

| Symptom | Likely cause | Where to look | Fix |
| --- | --- | --- | --- |
| LLM responses slow | Queue concurrency limit (`Semaphore`) serializes requests | `data/api/RequestQueueManager.kt` | Raise the limit carefully; priority lanes (HIGH for EXECUTIVE/PLOT) already jump the queue. |
| Annotator/linter sluggish on huge chapters | Regex scans over full text (echo detector is O(n·window)) | `LiveAstAnnotator.continuityFlags` | Cap chapter size (envelope advisory) or optimize the echo map. |
| UI jank on the library screen | `StoryScreen` is 4,446 lines with heavy recomposition | `ui/screens/StoryScreen.kt` | Treat as tech-debt D-04 before optimizing locally. |

### Config/env mistakes

| Symptom | Likely cause | Where to look | Fix |
| --- | --- | --- | --- |
| Keys "not saved" | Secure prefs fell back to plain prefs (keystore failure) | `LlmOrchestrator.getSecurePrefs` catch → `secure_studio_settings_fallback` | Still functional; see tech-debt D-03. |
| Provider per-agent override ignored | `agent_provider_<agentKey>` key mismatch | `LlmOrchestrator.getProviderForAgent` | Agent keys: `executive|plot|character|world|scene|continuity`. |
| Ollama "Bearer none" rejected by custom endpoint | Custom Ollama-compatible API needs a real key | `LlmOrchestrator` Ollama block | Set `ollama_key` in Settings. |
| Serial dashboard shows "No serialized projects yet" | Book not flagged serial | `SerialDashboardViewModel.refreshBooks` filters `isSerialized` | Toggle the serial format on the project (Book.isSerialized). |
| Scorecard gate shows `-1/14` or "No scorecard recorded" | `chapters.qcScores` blank — `QcScorecard.of` returns null and `gate()` short-circuits (total −1, releasable=false, placeholder band STRONG) | `engine/serialization/QcScorecard.gate` (null branch); dashboard scorecard editor | Score the chapter in the Serial Dashboard (L6 proposes, writer confirms). The STRONG band with total −1 is a placeholder shape, not a classification — not a bug. |

### Where to look first (generic decision tree)

1. If it's a **gate/blocking** message → find its emitter: Chekhov Ledger (`🧨` prefix), Serialization (`📖`/`⛔` prefix) — all logged into `books.agentLogs` by `worker/CompilationWorker.kt` and surfaced in the dashboard suite report.
2. If it's a **test failure in engine/** → the golden corpora in `engine/serialization/GoldenSerial.kt` / test classes are the contract; read `docs/change-impact.md` first.
3. If it's a **UI** problem → ViewModels hold the state (`docs/system-map.md` VM table); screens are thin-ish except the two god-classes.
4. If it's a **worker chain** that "does nothing" → tech-debt D-01 (no registered factories on desktop).

## How to reproduce locally

- Full suite: `./gradlew :desktop:test` (JDK 21).
- Headless UI smoke: `bash scripts/p3_smoke.sh` (Xvfb :99, software skiko, screenshots to `/tmp/render-p3`).
- Single-class compile: `./gradlew :desktop:compileKotlin` / `:desktop:compileTestKotlin`.
- Golden-corpus probe (reflection/jshell over the built jar): `scripts/CorpusProbe.java` + `jshell` (see P3 report §4).

## How to roll back

- **Code**: git — the repo has a linear history with per-phase commits (`git log --oneline` shows v3.0 P1/P2/P3 commits); revert the phase commit.
- **Database**: no down-migrations exist (additive-only policy). Restore the SQLite file from a `.authr` container export (container import recreates the project; FACT: `container/AuthrContainer.export/import`) or the raw DB file copy at `<dataRoot>/files/databases/audio_novel_studio_db`.
- **A blocked serial export**: it is intentionally blocked — resolve/waive guns, satisfy scorecard bands, or complete the arc question (see `docs/troubleshooting.md` gates above). Never bypass a HARD gate in code.
