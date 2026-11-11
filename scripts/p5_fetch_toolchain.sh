#!/bin/bash
# P5 direct-compile toolchain fetcher — extends the P4 pattern with the
# publishing gates' external tools:
#   - kotlin-compiler + junit + kotlin-test (direct kotlinc route, same as P4)
#   - pdfbox 2.0.33 + fontbox + commons-logging (print interior PDF writer)
#   - epubcheck 5.2.1 standalone dist (the external EPUB 3 conformance gate)
# Jars land in /home/z/my-project/.build-extras/ (survives recycles).

DEST=/home/z/my-project/.build-extras
mkdir -p "$DEST"
CENTRAL="https://repo.maven.apache.org/maven2"

fetch() { # path-under-central
  local rel="$1"
  local out="$DEST/$(basename "$rel")"
  [ -s "$out" ] && { echo "have  $(basename "$rel")"; return 0; }
  local tries=0
  while [ $tries -lt 30 ]; do
    local code
    code=$(curl -sS -L --max-time 300 -o "$out.part" -w "%{http_code}" "$CENTRAL/$rel" 2>/dev/null)
    case "$code" in
      200) mv "$out.part" "$out"; echo "OK    $(basename "$rel") ($(du -h "$out" | cut -f1))"; return 0 ;;
      404) rm -f "$out.part"; echo "404   $rel"; return 2 ;;
      *)   rm -f "$out.part"; tries=$((tries+1)); echo "  $code  retry $tries for $(basename "$rel")"; sleep $((5 + tries*3)) ;;
    esac
  done
  echo "FAIL  $rel"; return 1
}

# --- direct-compile toolchain (same as P4) ---
fetch "org/jetbrains/kotlin/kotlin-compiler-embeddable/2.2.10/kotlin-compiler-embeddable-2.2.10.jar"
fetch "org/jetbrains/intellij/deps/trove4j/1.0.20200330/trove4j-1.0.20200330.jar"
fetch "junit/junit/4.13.2/junit-4.13.2.jar"
fetch "org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"
fetch "org/jetbrains/kotlin/kotlin-test/2.2.10/kotlin-test-2.2.10.jar"
fetch "org/jetbrains/kotlin/kotlin-test-junit/2.2.10/kotlin-test-junit-2.2.10.jar"

# --- print interior: pdfbox 2.0.33 (API: PDType0Font TTF embedding) ---
fetch "org/apache/pdfbox/pdfbox/2.0.33/pdfbox-2.0.33.jar"
fetch "org/apache/pdfbox/fontbox/2.0.33/fontbox-2.0.33.jar"
fetch "commons-logging/commons-logging/1.2/commons-logging-1.2.jar"

# --- compose compiler plugin (D-15 UI slice: compiles @Composable sources
#     through the direct kotlinc route; version rides the Kotlin plugin
#     "org.jetbrains.kotlin.plugin.compose" 2.2.10 in desktop/build.gradle.kts) ---
fetch "org/jetbrains/kotlin/kotlin-compose-compiler-plugin-embeddable/2.2.10/kotlin-compose-compiler-plugin-embeddable-2.2.10.jar"

# --- kotlinx.serialization compiler plugin (the container/workbench code
#     needs -Xplugin:serialization; p5_direct_test.sh line 59 references it —
#     restore-path bug fixed 2026-11-12: no fetcher carried this jar before) ---
fetch "org/jetbrains/kotlin/kotlin-maven-serialization/2.2.10/kotlin-maven-serialization-2.2.10.jar"

# --- kotlinx-coroutines-test (runTest for the ViewModel state-machine tests;
#     restore-path gap fixed 2026-11-12 — version matches the app image's
#     kotlinx-coroutines-core-jvm-1.10.2) ---
fetch "org/jetbrains/kotlinx/kotlinx-coroutines-test-jvm/1.10.2/kotlinx-coroutines-test-jvm-1.10.2.jar"

# --- epubcheck 5.2.1 standalone (fat jar inside the dist zip) ---
if [ ! -s "$DEST/epubcheck.jar" ]; then
  Z="$DEST/epubcheck-5.2.1.zip"
  tries=0
  while [ $tries -lt 20 ]; do
    code=$(curl -sS -L --max-time 600 -o "$Z.part" -w "%{http_code}" \
      "https://github.com/w3c/epubcheck/releases/download/v5.2.1/epubcheck-5.2.1.zip" 2>/dev/null)
    if [ "$code" = "200" ]; then mv "$Z.part" "$Z"; break; fi
    rm -f "$Z.part"; tries=$((tries+1)); echo "  $code retry $tries for epubcheck zip"; sleep $((5 + tries*3))
  done
  if [ -s "$Z" ]; then
    rm -rf "$DEST/epubcheck-dist"; mkdir -p "$DEST/epubcheck-dist"
    unzip -o -q "$Z" -d "$DEST/epubcheck-dist"
    # epubcheck.jar is NOT fat: its manifest Class-Path needs ./lib/*.jar,
    # so it must run from the dist directory (EpubCheckLocator resolves it).
    JAR=$(find "$DEST/epubcheck-dist" -name "epubcheck.jar" | head -1)
    [ -n "$JAR" ] && echo "OK    epubcheck dist ($(du -sh "$DEST/epubcheck-dist" | cut -f1))"
  else
    echo "FAIL  epubcheck dist zip"
  fi
elif [ -s "$DEST/epubcheck-dist/epubcheck-5.2.1/epubcheck.jar" ]; then
  echo "have  epubcheck dist"
fi
rm -f "$DEST/epubcheck.jar"

echo "fetch done"
ls -la "$DEST" | head -20
