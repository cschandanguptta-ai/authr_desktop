# Authr OS — The Autonomous Author Operating System

Compose Desktop edition (v3.0) of the audio-novel writing studio, plus the
original v2.3 Android project it was ported from.

## What's in this repo

| Path | What it is |
| --- | --- |
| `authr-desktop/` | The v3.0 desktop app — Kotlin 2.2.10 / Compose Multiplatform 1.9.3, single `:desktop` Gradle module (`authr-desktop/desktop`). Writer Studio, Serial Dashboard, audiobook engine, Publishing workbench (EPUB 3 + epubcheck gate + print-interior PDF + retail packaging). |
| `source/` | The v2.3 Android Studio project (AGP 9.1.1, minSdk 24, targetSdk 36) the desktop edition was ported from. Builds `assembleDebug` / `assembleRelease` APKs. |
| `scripts/` | The no-Gradle direct-route test/build scripts (kotlinc + JUnitCore) used as CI gates, plus release packaging. |
| `.github/workflows/ci.yml` | The direct test suite (280 tests across 6 routes) — no Gradle needed, runs on every push. |
| `.github/workflows/installers.yml` | Windows MSI + Android APK (debug & release) installer builders; on `v*` tags also publishes the GitHub Release. |

## Installers & Releases

The `installers` workflow builds the installers on every push (run artifacts
under the Actions tab). Pushing a `v*` tag additionally **publishes a GitHub
Release** — Releases page, repo right sidebar — with:

- `AuthrOS-3.1.0.msi` — jpackage/WiX installer (upgrade UUID
  `f5d50191-48ab-5410-b4d0-042ebed917cb`, stable across releases).
- `AuthrOS-android-debug.apk` + `AuthrOS-android-release.apk` — the Android
  project (minSdk 24 / targetSdk 36); the release APK is signed with a
  CI-ephemeral keystore, so Android shows the standard "unknown developer"
  prompt on install.
- `CHECKSUMS.txt` — SHA-256 for every asset.

The Android release keystore is intentionally NOT in git — CI generates an
ephemeral one. For Play uploads, build locally with your own keystore:
`KEYSTORE_PATH=/path/to/my-upload-key.jks STORE_PASSWORD=... KEY_PASSWORD=... gradle assembleRelease`.

## Building locally

Desktop (Linux/macOS/Windows; JDK 21, Gradle 8.14):

```bash
cd authr-desktop
gradle :desktop:packageReleaseMsi      # Windows installer (on Windows)
gradle :desktop:run                    # run the app
```

Android (JDK 21, Gradle 9.1.0, Android SDK with compileSdk 36.1):

```bash
cd source
gradle assembleDebug
```

## Tests

The authoritative suite is the no-Gradle direct route (the tracked
`desktop/build` tree is the load-bearing classpath — do not gitignore it):

```bash
bash scripts/ci_direct_suite.sh   # 280 tests, 6 routes, green or the job fails
```
`scripts/p5_fetch_toolchain.sh` fetches the toolchain jars reproducibly from
Maven Central into `.build-extras/` (gitignored).

## Docs

- `authr-desktop/docs/` — the v3.0 plan, ADRs, tech-debt ledger, testing rules.
- `authr-desktop/AGENTS.md` — canonical build/test commands.
