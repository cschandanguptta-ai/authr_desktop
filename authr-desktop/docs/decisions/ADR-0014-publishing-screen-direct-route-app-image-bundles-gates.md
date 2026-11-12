# ADR-0014: D-15 slice — the Publishing screen ships through the no-Gradle direct route; the app image bundles its gates

Date: 2026-11-12 · Status: accepted · Deciders: program agent (maintenance slice), no human in the loop — this ADR records the decisions for audit

## Context

The P5 report handed off D-15 as "a Publishing workbench screen + the packaging call site wired from the UI," implicitly assuming a Gradle build could produce it. The gate sandbox has **no Gradle wrapper** (FACT: repo root has none) and Maven Central 429-bursts block dependency resolution; meanwhile the shipped compose app image (`AuthrOS/`) was a **P3-era build** — its app jar carried none of the P4 audio engine or the P5 publishing engine, so no UI slice could "just call `RetailPackager`" at runtime.

## Decisions

1. **The screen is compiled through the direct kotlinc route, not Gradle.** `scripts/d15_build_ui.sh` compiles the FULL main tree (167 sources + 10 KSP-generated Room impls) with the Compose compiler plugin (`kotlin-compose-compiler-plugin-embeddable-2.2.10`) and the serialization plugin against the app image's own jars, then swaps the fresh classes into the app jar **in place** (the launcher cfg references it by name). Rationale: the app image jars ARE the runtime truth; compiling against them keeps binary compatibility exact. The KSP tree is included because Room's generated `AppDatabase_Impl`/DAO impls are not source — without them the packaged app dies at `getDatabase()` (observed, then fixed).
   - Guardrail learned the hard way: the app jar is the cfg's FIRST `app.classpath` entry — never glob `desktop-*` (the image also carries the compose `desktop-jvm` library jar; a first draft of the script clobbered it, restored from git).
2. **The tracked stock classes are refreshed by the same run.** `desktop/build/classes/kotlin/main` is the load-bearing compile classpath of every p4/p5/p6 direct route; after this slice it is current (P4+P5 engine + the new UI). Consequence (measured, see D-17): a single-file test iteration compiles only the changed files — the recurring 84 s full-tree rebuild is a build-step, not a test-step cost.
3. **The image bundles its gates** (closes the bundle halves of OQ-16/D-15 and D-16):
   - `lib/app/epubcheck/` — the epubcheck 5.2.1 dist, where `EpubCheckLocator`'s app-dir candidate finds it: packages built by the shipped app are VERIFIED, not UNVERIFIED;
   - `lib/app/fonts/` — DejaVuSerif(.ttf/-Bold.ttf), where the extended `PrintInterior.resolveFontPath` app-dir candidates find them (same locator pattern, tried before system dirs, after `~/.authr/fonts`);
   - `lib/app/pdfbox|fontbox|commons-logging` + cfg entries — the print runtime, so the packaged app does not throw `NoClassDefFoundError` on the first interior render.
4. **Retained: epubcheck stays a blocking EXTERNAL subprocess** (per-run JVM), per ADR-0011; bundling the dist does not move the check in-process.

## Evidence (ADR-0010: measured, not estimated)

- Full-tree compile: **84 s wall** (167+10 sources, 995 class files, -Xmx3g); test-set compile: 10-13 s; JUnit route: **49/49 green** (11 PrintInterior incl. the bundled-face guard, 4 PublishingWorkbench, 34 publishing regression); suite total 226.
- App smoke under Xvfb (software GL): engine-init log line **0.23 s** after exec; zero exceptions; alive-until-kill. A rendered-pixel walkthrough needs a real display (no screenshot tooling in the sandbox) — the runtime proof is: launch clean, Room opens, UI surfaces constructed.
- One-action packaging (3 runs, stable): novel (4 ch) **7.3-8.0 s**; two-volume serial **12.5-13.1 s** (was 14.2 s sequential). Breakdown: Epub3Exporter 109 ms, PrintInterior 503 ms, container export+import ~250 ms, **epubcheck ~6.6-7.2 s per EPUB** (JVM spawn 0.10 s + tool init 0.16 s are noise — the check itself is the cost). Steps 4+5 therefore run concurrently (verdict order preserved); on the gate hardware's 2 vCPUs the serial saves ~1.4 s wall; the pool caps at 4 so larger machines reclaim more.

## Consequences

- The shipped app image now equals the source tree; a fresh clone runs the current UI with no Gradle. The old P3-era jar is replaced, not archived (git history holds it).
- `docs/config-and-env.md` documents the bundled locations; `scripts/p5_fetch_toolchain.sh` gained the three previously-unfetched jars (serialization plugin, coroutines-test, compose compiler plugin) so a wiped `.build-extras/` restores completely.
- The Gradle `:desktop:test` re-run on a Gradle-capable machine remains the standing verification for the declared dependency set (D-15 note) — the direct route compiles against the resolved jars, which is stronger than nothing but not a Gradle resolution proof.
