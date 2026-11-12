#!/bin/bash
# Package the Authr OS desktop app image as the release zip (2026-11-12).
# Reproducible: rebuilds authr-os-desktop-linux-x64-<ver>.zip from the
# tracked app image + a generated INSTALL.md. Run from anywhere.
set -euo pipefail

AUTHR_BASE="${AUTHR_BASE:-/home/z/my-project}"
DIST="$AUTHR_BASE/authr-desktop/desktop/build/compose/binaries/main/app/AuthrOS"
OUT="$AUTHR_BASE/download"
VERSION="$(sed -n 's/.*jpackage.app-version=\(.*\)/\1/p' "$DIST/lib/app/AuthrOS.cfg" | head -1)"
[ -n "$VERSION" ] || { echo "cannot read app version from AuthrOS.cfg"; exit 1; }
NAME="authr-os-desktop-linux-x64-$VERSION"

[ -d "$DIST" ] || { echo "app image missing: $DIST"; exit 1; }

STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
cp -a "$DIST" "$STAGE/AuthrOS"

# INSTALL.md is generated here so the version never drifts from the image.
cat > "$STAGE/INSTALL.md" <<EOF
# Authr OS — Desktop Edition (Linux x64, v$VERSION)

A self-contained Compose Desktop application: no Java installation required,
nothing else to download. The bundle carries its own Java 21 runtime, the
bundled epubcheck 5.2.1 verifier (EPUB packages are verified with no extra
install), print fonts (DejaVu Serif), and the espeak-ng-free Pocket/eSpeak
synthesis route for audiobook rendering.

## Requirements

- Linux x86-64 with glibc >= 2.38 (Ubuntu 23.04+, Debian 12+, Fedora 38+)
- A graphical desktop session (X11 or XWayland)

## Install

1. Unzip anywhere you like:

   \`\`\`sh
   unzip $NAME.zip -d ~/apps
   \`\`\`

2. Make the launcher executable (the zip preserves it, but this makes sure):

   \`\`\`sh
   chmod +x ~/apps/AuthrOS/bin/AuthrOS
   \`\`\`

## Run

\`\`\`sh
~/apps/AuthrOS/bin/AuthrOS
\`\`\`

First launch creates your data root at \`~/.local/share/authr\` and the
\`~/.authr/\` container directory. Nothing is written outside those two
locations (plus standard config dirs).

## What's inside

\`\`\`
AuthrOS/
  bin/AuthrOS          native launcher (jpackage)
  lib/runtime/         bundled Java 21.0.13 runtime (jlink image)
  lib/app/             application jars + epubcheck/ + fonts/
  lib/AuthrOS.png      application icon
\`\`\`

## Notes

- **Version**: $VERSION (the desktop edition ships with the v3.0 engine:
  writer studio, serialization dashboard, audiobook engine, publishing
  workbench). There was never a 2.3.0 desktop build — v2.3 was the
  Android source project; the desktop edition is new in 3.0.
- **EPUB verification**: shipped packages run epubcheck from
  \`lib/app/epubcheck\` automatically — no environment install needed.
- **Optional API keys**: provider keys (Gemini / OpenAI-compatible) are
  configured inside the app Settings; without a key the app runs fully
  offline with local pipelines, and Gemini auto-discovery quietly fails
  (a \`HTTP 400\` line in the log is normal without a key).
- **Headless servers**: not supported — this is a desktop GUI app.

## Integrity

The release checksum is published next to the zip
(\`$NAME.zip.sha256\`). Verify with:

\`\`\`sh
sha256sum -c $NAME.zip.sha256
\`\`\`
EOF

cd "$STAGE"
rm -f "$OUT/$NAME.zip"
zip -q -r -X "$OUT/$NAME.zip" INSTALL.md AuthrOS
cd "$OUT" && sha256sum "$NAME.zip" > "$NAME.zip.sha256"

echo "built: $OUT/$NAME.zip ($(du -h "$OUT/$NAME.zip" | cut -f1))"
echo "  integrity verified by: sha256sum -c $NAME.zip.sha256"
