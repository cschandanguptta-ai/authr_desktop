#!/bin/bash
# ADR-0018 pipeline slice — compiles the new pipeline engine mains + the
# touched data layer against the prebuilt app image, then compiles the UI
# slice (Main.kt + screen + VM) the same way. Same no-Gradle pattern as the
# other direct routes; exit 1 on the first error with the error lines.
set -u
BASE="${AUTHR_BASE:-/home/z/my-project}"
AD=$BASE/authr-desktop/desktop
IMG=$AD/build/compose/binaries/main/app/AuthrOS/lib/app
EXTRAS=$BASE/.build-extras
OUT=$EXTRAS/pipeline-main-classes
SRC=$AD/src/main/kotlin

rm -rf "$OUT"; mkdir -p "$OUT"

CP="$(ls "$IMG"/*.jar | tr '\n' ':')"
CP+="$AD/build/classes/kotlin/main"

JAVA="${AUTHR_JAVA:-/tmp/my-project/tools/jdk/bin/java}"
COMPILER_CP="$EXTRAS/kotlin-compiler-embeddable-2.2.10.jar:$EXTRAS/trove4j-1.0.20200330.jar:$IMG/kotlin-stdlib-2.2.10-ab998b5a8873e5635565578249af83.jar:$IMG/kotlin-reflect-1.8.21-6c7edbaeeb3b6b1b678269e482fa1c.jar:$IMG/kotlinx-coroutines-core-jvm-1.10.2-16bb58d0777fca3b2efde66862478c.jar:$IMG/annotations-23.0.0-8484cd17d040d837983323f760b2c660.jar"

echo "=== compiling pipeline engine slice (direct kotlinc, serialization plugin on) ==="
MAIN_SRCS="$SRC/com/example/engine/pipeline/PipelineStages.kt \
$SRC/com/example/engine/pipeline/EditorialMemoryStore.kt \
$SRC/com/example/engine/pipeline/StagePrompts.kt \
$SRC/com/example/engine/pipeline/RetentionAnalyzer.kt \
$SRC/com/example/engine/pipeline/ManuscriptQaSuite.kt \
$SRC/com/example/engine/pipeline/StageGates.kt \
$SRC/com/example/engine/pipeline/PipelineOrchestrator.kt \
$SRC/com/example/data/model/EditorialMemory.kt \
$SRC/com/example/data/local/EditorialMemoryDao.kt \
$SRC/com/example/data/local/AppDatabase.kt \
$SRC/com/example/data/repository/AppRepository.kt"

if ! "$JAVA" -Xmx2g -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -Xplugin="$EXTRAS/kotlin-maven-serialization-2.2.10.jar" \
  -classpath "$CP" -jvm-target 21 -no-stdlib -nowarn \
  -d "$OUT" $MAIN_SRCS >"$EXTRAS/pipeline-compile.log" 2>&1; then
  echo "PIPELINE MAIN COMPILE FAILED:"
  grep -E "error:" "$EXTRAS/pipeline-compile.log" | head -30
  exit 1
fi
echo "PIPELINE MAIN COMPILE OK"

echo "=== compiling pipeline UI slice (VM + screen + Main) ==="
UI_OUT=$EXTRAS/pipeline-ui-classes
rm -rf "$UI_OUT"; mkdir -p "$UI_OUT"
UI_CP="$OUT:$CP"
UI_SRCS="$SRC/com/example/ui/viewmodel/PipelineViewModel.kt \
$SRC/com/example/ui/screens/PipelineWorkbenchScreen.kt \
$SRC/com/example/Main.kt"

if ! "$JAVA" -Xmx2g -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -Xplugin="$EXTRAS/kotlin-maven-serialization-2.2.10.jar" \
  -classpath "$UI_CP" -jvm-target 21 -no-stdlib -nowarn \
  -d "$UI_OUT" $UI_SRCS >"$EXTRAS/pipeline-ui-compile.log" 2>&1; then
  echo "PIPELINE UI COMPILE FAILED:"
  grep -E "error:" "$EXTRAS/pipeline-ui-compile.log" | head -30
  exit 1
fi
echo "PIPELINE UI COMPILE OK"
