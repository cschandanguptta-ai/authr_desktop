#!/bin/bash
# P4 direct-compile toolchain fetcher — the sandbox recycle wipes ~/.gradle and
# ~/.m2, so instead of re-seeding hundreds of Gradle artifacts we fetch FIVE
# small jars (kotlinc + junit + kotlin-test) and compile the P4 engine + tests
# directly against the surviving app-image classpath + Nov 9 desktop.jar.
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

fetch "org/jetbrains/kotlin/kotlin-compiler-embeddable/2.2.10/kotlin-compiler-embeddable-2.2.10.jar"
fetch "org/jetbrains/intellij/deps/trove4j/1.0.20200330/trove4j-1.0.20200330.jar"
fetch "junit/junit/4.13.2/junit-4.13.2.jar"
fetch "org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"
fetch "org/jetbrains/kotlin/kotlin-test/2.2.10/kotlin-test-2.2.10.jar"
fetch "org/jetbrains/kotlin/kotlin-test-junit/2.2.10/kotlin-test-junit-2.2.10.jar"
echo "fetch done"
