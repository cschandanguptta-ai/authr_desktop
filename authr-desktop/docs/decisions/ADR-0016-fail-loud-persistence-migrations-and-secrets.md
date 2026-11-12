# ADR-0016: fail-loud persistence — no destructive migrations, secrets never persist unencrypted

Date: 2026-11-12 · Status: accepted · Deciders: program agent (delegated decision — OQ-3/OQ-4 were policy questions awaiting a human; the human delegated the calls), no human in the loop — this ADR records both decisions for audit

## Context

Two persistence policies shipped as Android-port leftovers that traded user safety for convenience, both logged as tech debt with the blast radius documented:

- **D-02 (OQ-3)**: `AppDatabase.getDatabase` set `fallbackToDestructiveMigration(true)` — any schema drift without a contiguous migration path SILENTLY WIPED the user's database (manuscripts, lorebooks, ledgers). The plan's writer-sovereignty pillar makes this the worst possible failure mode: data loss with no signal.
- **D-03 (OQ-4)**: `LlmOrchestrator.getSecurePrefs` and `SettingsViewModel` each caught keystore failures and fell back to PLAIN persistent `SharedPreferences` files (`secure_studio_settings_fallback`) — API keys persisted unencrypted with no user signal, in two independent construction sites.

## Decisions

1. **Migrations fail loud (D-02 / OQ-3 decided)**: `fallbackToDestructiveMigration(true)` is REMOVED. Opening a database whose schema version has no contiguous migration path throws `IllegalStateException` ("A migration from X to Y was required but not found"); a fresh install creates the current schema (15) unchanged. The desktop edition is pre-release — only developer databases predate this, so no real data depends on the wipe path. `MigrationFailFastTest` guards both halves against the REAL Room 2.8.5 KMP runtime the app ships: a v3 file throws and keeps its rows; a registered 3→15 path runs to the current version.
2. **Secrets never persist unencrypted (D-03 / OQ-4 decided)**: the fallback is IN-MEMORY ONLY — a new `SharedPreferences.Companion.inMemory()` implementation (session-scoped map, nothing ever touches disk). Both construction sites now route through one shared `LlmOrchestrator.openSecurePrefs(context)` (single site, test hook `forceFallback`); the old per-site plain-file fallback and the aggressive corrupt-store DELETE (a transient keystore failure must not nuke the store; a broken store stays for a fixed keystore to reopen) are gone. The degraded state is disclosed, not silent: `LlmOrchestrator.secureStorageDegraded` + a loud log + a red banner in the Settings screen where keys are entered.
3. **Rationale (both)**: the desktop edition's threat model favors loud, recoverable failure over silent downgrade. A wiped manuscript is unrecoverable; a re-entered API key is an annoyance; an unencrypted key file is a liability the user never agreed to.

## Evidence

- `MigrationFailFastTest` 2/2, `SecurePrefsFallbackTest` 2/2 (the fallback is volatile: a second open never sees the first session's key; zero secure-settings files hit disk — asserted by walking the temp data root).
- Full suite: d13 route 68/68, p5 59/59, p6 47/47 — 238 total.
- Parity note (OQ-5 verification, same day): the Android source still carries BOTH the plain-file fallback and `fallbackToDestructiveMigration`-era behavior — this ADR is a deliberate, documented desktop-vs-Android divergence; the Android repo should adopt the same policy when it next changes settings code.

## Consequences

- Pre-beta developer databases older than the migration chain (v<12) will fail to open LOUDLY; the fix is explicit migrations or a manual export — never a silent wipe.
- On machines with broken keystores, keys must be re-entered each session (the banner says so). The keystore itself is untouched by failures.
- `secure_studio_settings_fallback` is a legacy name that no desktop code writes anymore.
