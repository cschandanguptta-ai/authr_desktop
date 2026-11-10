# ADR-0009 — Paste-ready serial exports; no platform API integrations

**Status:** Accepted (P3)
**Context:** Royal Road/Wattpad/GoodNovel/Dreame/Webnovel/Tapas have ToS that make unapproved automated posting a liability (plan risk register).
**Decision:** The app formats per-platform paste-ready chapter packages (recap header, spacing conventions, author note with links stripped where policy forbids, poll payloads only where supported) plus a plain-text clipboard bundle. No HTTP to any serial platform. Profiles are versioned data in code, adjustable without schema or pipeline changes.
**Alternatives considered:** platform API posting (rejected — ToS); generic markdown export (rejected — per-platform conventions matter).
**Consequences:** New platforms = a new `Profile` entry + tests; nothing else.
**Do not change unless:** the plan's §6.13 contract changes.
**Evidence:** `engine/serialization/SerialExportProfiles.kt`, `SerialExportProfilesTest`.
