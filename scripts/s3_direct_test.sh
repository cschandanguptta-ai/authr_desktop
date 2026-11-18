#!/bin/bash
# v3.0 port S3 (RAG + epistemic isolation, ADR-0022) — compiles the RAG
# engine + citation checker + layer-7 wiring against the prebuilt app
# image, then compiles and runs the S3 tests. Same no-Gradle direct
# route as s0/s1/s2.
set -u
BASE="${AUTHR_BASE:-/home/z/my-project}"
AD=$BASE/authr-desktop/desktop
IMG=$AD/build/compose/binaries/main/app/AuthrOS/lib/app
EXTRAS=${AUTHR_EXTRAS:-/tmp/my-project/.build-extras}
SRC=$AD/src/main/kotlin
TSRC=$AD/src/test/kotlin
OUT=$EXTRAS/s3-engine-classes
TEST_OUT=$EXTRAS/s3-test-classes

rm -rf "$OUT" "$TEST_OUT"; mkdir -p "$OUT" "$TEST_OUT"

CP="$(ls "$IMG"/*.jar | tr '\n' ':')"
CP+="$AD/build/classes/kotlin/main"

JAVA="${AUTHR_JAVA:-/tmp/my-project/tools/jdk/bin/java}"
COMPILER_CP="$EXTRAS/kotlin-compiler-embeddable-2.2.10.jar:$EXTRAS/trove4j-1.0.20200330.jar:$IMG/kotlin-stdlib-2.2.10-ab998b5a8873e5635565578249af83.jar:$IMG/kotlin-reflect-1.8.21-6c7edbaeeb3b6b1b678269e482fa1c.jar:$IMG/kotlinx-coroutines-core-jvm-1.10.2-16bb58d0777fca3b2efde66862478c.jar:$IMG/annotations-23.0.0-8484cd17d040d837983323f760b2c660.jar"

T0=$(date +%s)
echo "=== compiling S3 main slice (RAG engine + citation checker + layer-7 wiring) ==="
MAIN_SRCS="$SRC/com/example/engine/rag/RagEngine.kt \
$SRC/com/example/engine/rag/CitationIntegrityChecker.kt \
$SRC/com/example/engine/context/ContextAssembler.kt \
$SRC/com/example/engine/context/SummaryCacheService.kt \
$SRC/com/example/engine/context/TokenSpendLedger.kt \
$SRC/com/example/data/model/SummaryCache.kt \
$SRC/com/example/data/model/EditorialMemory.kt \
$SRC/com/example/data/model/ResearchVault.kt \
$SRC/com/example/data/model/Lorebook.kt \
$SRC/com/example/engine/pipeline/EditorialMemoryStore.kt \
$SRC/com/example/engine/pipeline/PipelineStages.kt \
$SRC/com/example/engine/pipeline/RetentionAnalyzer.kt \
$SRC/com/example/engine/pipeline/ManuscriptQaSuite.kt \
$SRC/com/example/engine/serialization/GoldenSerial.kt"

if ! "$JAVA" -Xmx2g -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -Xplugin="$EXTRAS/kotlin-maven-serialization-2.2.10.jar" \
  -classpath "$CP" -jvm-target 21 -no-stdlib -nowarn \
  -d "$OUT" $MAIN_SRCS >"$EXTRAS/s3-compile.log" 2>&1; then
  echo "S3 MAIN COMPILE FAILED:"
  grep -E "error:" "$EXTRAS/s3-compile.log" | head -30
  exit 1
fi
echo "S3 MAIN COMPILE OK"

echo "=== compiling S3 test slice ==="
TEST_CP="$OUT:$EXTRAS/junit-4.13.2.jar:$EXTRAS/hamcrest-core-1.3.jar:$EXTRAS/kotlin-test-2.2.10.jar:$EXTRAS/kotlin-test-junit-2.2.10.jar:$EXTRAS/kotlinx-coroutines-test-jvm-1.10.2.jar:$CP"
TEST_SRCS="$TSRC/com/example/RagEngineTest.kt"

if ! "$JAVA" -Xmx2g -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -Xplugin="$EXTRAS/kotlin-maven-serialization-2.2.10.jar" \
  -classpath "$TEST_CP" -jvm-target 21 -no-stdlib -nowarn \
  -d "$TEST_OUT" $TEST_SRCS >"$EXTRAS/s3-test-compile.log" 2>&1; then
  echo "S3 TEST COMPILE FAILED:"
  grep -E "error:" "$EXTRAS/s3-test-compile.log" | head -30
  exit 1
fi
T1=$(date +%s)
echo "S3 TEST COMPILE OK (wall clock: $((T1-T0))s)"

RUN_CP="$TEST_OUT:$OUT:$TEST_CP"

echo "=== running S3 tests (epistemic isolation, hybrid retrieval, citation gate, layer-7 wiring) ==="
"$JAVA" -Xmx2g -cp "$RUN_CP:$EXTRAS" RunTests \
  com.example.RagEngineTest
RC=$?
if [ $RC -ne 0 ]; then
  echo "S3 TESTS FAILED (rc=$RC)"
  exit 1
fi
echo "S3 ROUTE GREEN"
