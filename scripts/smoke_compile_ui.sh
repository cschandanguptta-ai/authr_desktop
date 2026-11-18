#!/bin/bash
# P1.5 smoke compile: Main.kt and L4LlmDraftGenerator.kt are edited in S3
# but not compiled by any direct test route (no Compose compiler plugin in
# the sandbox). This is a TYPE/SYNTAX verification only — output classes go
# to a throwaway dir and are NOT promoted to the tracked build tree (the
# production Main/L4 bytecode comes from a full Gradle build, per the
# convention of every previous sprint).
set -u
BASE="${AUTHR_BASE:-/home/z/my-project}"
AD=$BASE/authr-desktop/desktop
IMG=$AD/build/compose/binaries/main/app/AuthrOS/lib/app
EXTRAS="${AUTHR_EXTRAS:-/tmp/my-project/.build-extras}"
JAVA="${AUTHR_JAVA:-/tmp/my-project/tools/jdk/bin/java}"
SRC=$AD/src/main/kotlin

rm -rf /tmp/smoke; mkdir -p /tmp/smoke/l4 /tmp/smoke/main

CP="$(ls "$IMG"/*.jar | tr '\n' ':')"
# fresh S3 engine classes (new assemble signature) shadow the tracked ones
CP="/tmp/my-project/.build-extras/s3-engine-classes:$CP$AD/build/classes/kotlin/main"

COMPILER_CP="$EXTRAS/kotlin-compiler-embeddable-2.2.10.jar:$EXTRAS/trove4j-1.0.20200330.jar"
for j in "$IMG"/kotlin-stdlib-*.jar "$IMG"/kotlin-reflect-*.jar "$IMG"/kotlinx-coroutines-core-jvm-*.jar "$IMG"/annotations-*.jar; do
  COMPILER_CP="$COMPILER_CP:$j"
done

FAIL=0

echo "=== smoke: L4LlmDraftGenerator.kt (engine, no Compose) ==="
if "$JAVA" -Xmx2g -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -Xplugin="$EXTRAS/kotlin-maven-serialization-2.2.10.jar" \
  -classpath "$CP" -jvm-target 21 -no-stdlib -nowarn \
  -d /tmp/smoke/l4 "$SRC/com/example/engine/L4LlmDraftGenerator.kt" >/tmp/smoke/l4.log 2>&1; then
  echo "L4 SMOKE OK (ragEngine param + assemble call type-check)"
else
  echo "L4 SMOKE FAILED:"; grep -E "error:" /tmp/smoke/l4.log | head -15; FAIL=1
fi

# compose compiler plugin: only present if a Gradle cache survived
CC=$(find /home/z/.gradle /root/.gradle -name "compose-compiler-*.jar" 2>/dev/null | head -1)
if [ -n "${CC:-}" ]; then
  echo "=== smoke: Main.kt (Compose compiler found: $CC) ==="
  EXTRA="-Xplugin=$CC"
else
  echo "=== smoke: Main.kt (NO compose compiler — plain K2, @Composable transform skipped) ==="
  EXTRA=""
fi
# Main.kt must compile JOINTLY with the sources whose signatures it consumes:
# L4LlmDraftGenerator (S3 ragEngine param) + PipelineViewModel (S1 voiceEngine
# param) are ahead of their tracked binaries by design (no Gradle in the
# sandbox; bytecode refreshes at the next full build). Compiling against the
# stale classes would false-fail on both.
MAIN_DEPS="$SRC/com/example/engine/L4LlmDraftGenerator.kt \
$SRC/com/example/ui/viewmodel/PipelineViewModel.kt \
$SRC/com/example/ui/viewmodel/WriterStudioViewModel.kt"
if "$JAVA" -Xmx2g -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -Xplugin="$EXTRAS/kotlin-maven-serialization-2.2.10.jar" $EXTRA \
  -classpath "$CP" -jvm-target 21 -no-stdlib -nowarn \
  -d /tmp/smoke/main "$SRC/com/example/Main.kt" $MAIN_DEPS >/tmp/smoke/main.log 2>&1; then
  echo "MAIN SMOKE OK (RagEngine/CitationIntegrityChecker DI wiring type-checks)"
else
  # Front-end (type) errors = MY wiring is wrong -> FAIL. Back-end inline
  # errors (Compose plugin missing in sandbox) = expected without Gradle;
  # the plugin-less compile still fully type-checks the DI graph, which is
  # what this smoke is for. (The CompilationException carries "error:" in
  # its Caused-by text, so match type errors only on source-position lines.)
  if grep -qE "^[^ ].*\.kt:[0-9]+:[0-9]+: error:" /tmp/smoke/main.log; then
    echo "MAIN SMOKE FAILED (type errors):"; grep -E "error:" /tmp/smoke/main.log | head -15; FAIL=1
  elif grep -q "Couldn't inline method call" /tmp/smoke/main.log; then
    echo "MAIN SMOKE OK (DI graph type-checks; Compose inline lowering skipped — no plugin in sandbox)"
  else
    echo "MAIN SMOKE FAILED:"; head -15 /tmp/smoke/main.log; FAIL=1
  fi
fi

exit $FAIL
