#!/bin/bash
# v3.0 port S0 (Room v17 + engine tables) — compiles the touched data-layer
# slice against the prebuilt app image, then compiles and runs the updated
# MigrationFailFastTest (now covering 16->17 data preservation) plus a
# VoiceFeatureExtractor validation test. Same no-Gradle direct route as
# adr0018_direct_test.sh.
set -u
BASE="${AUTHR_BASE:-/home/z/my-project}"
AD=$BASE/authr-desktop/desktop
IMG=$AD/build/compose/binaries/main/app/AuthrOS/lib/app
EXTRAS=$BASE/.build-extras
SRC=$AD/src/main/kotlin
TSRC=$AD/src/test/kotlin
OUT=$EXTRAS/s0-engine-classes
TEST_OUT=$EXTRAS/s0-test-classes

rm -rf "$OUT" "$TEST_OUT"; mkdir -p "$OUT" "$TEST_OUT"

CP="$(ls "$IMG"/*.jar | tr '\n' ':')"
CP+="$AD/build/classes/kotlin/main"

JAVA="${AUTHR_JAVA:-/tmp/my-project/tools/jdk/bin/java}"
COMPILER_CP="$EXTRAS/kotlin-compiler-embeddable-2.2.10.jar:$EXTRAS/trove4j-1.0.20200330.jar:$IMG/kotlin-stdlib-2.2.10-ab998b5a8873e5635565578249af83.jar:$IMG/kotlin-reflect-1.8.21-6c7edbaeeb3b6b1b678269e482fa1c.jar:$IMG/kotlinx-coroutines-core-jvm-1.10.2-16bb58d0777fca3b2efde66862478c.jar:$IMG/annotations-23.0.0-8484cd17d040d837983323f760b2c660.jar"

T0=$(date +%s)
echo "=== compiling S0 main slice (new entities + DAOs + AppDatabase v17) ==="
MAIN_SRCS="$SRC/com/example/data/model/VoiceEvolutionEntry.kt \
$SRC/com/example/data/model/VoiceDriftLog.kt \
$SRC/com/example/data/model/SummaryCache.kt \
$SRC/com/example/data/model/ResearchVault.kt \
$SRC/com/example/data/model/ComplianceCheck.kt \
$SRC/com/example/data/model/EditorialMemory.kt \
$SRC/com/example/data/local/VoiceEngineDaos.kt \
$SRC/com/example/data/local/ResearchComplianceDaos.kt \
$SRC/com/example/data/local/AppDatabase.kt \
$SRC/com/example/engine/voice/VoiceFeatureExtractor.kt \
$SRC/com/example/engine/voice/VoiceDriftTracker.kt \
$SRC/com/example/engine/serialization/GoldenSerial.kt"

if ! "$JAVA" -Xmx2g -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -Xplugin="$EXTRAS/kotlin-maven-serialization-2.2.10.jar" \
  -classpath "$CP" -jvm-target 21 -no-stdlib -nowarn \
  -d "$OUT" $MAIN_SRCS >"$EXTRAS/s0-compile.log" 2>&1; then
  echo "S0 MAIN COMPILE FAILED:"
  grep -E "error:" "$EXTRAS/s0-compile.log" | head -30
  exit 1
fi
echo "S0 MAIN COMPILE OK"

echo "=== compiling S0 test slice ==="
TEST_CP="$OUT:$EXTRAS/junit-4.13.2.jar:$EXTRAS/hamcrest-core-1.3.jar:$EXTRAS/kotlin-test-2.2.10.jar:$EXTRAS/kotlin-test-junit-2.2.10.jar:$EXTRAS/kotlinx-coroutines-test-jvm-1.10.2.jar:$CP"
TEST_SRCS="$TSRC/com/example/MigrationFailFastTest.kt \
$TSRC/com/example/VoiceFeatureExtractorTest.kt"

if ! "$JAVA" -Xmx2g -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -Xplugin="$EXTRAS/kotlin-maven-serialization-2.2.10.jar" \
  -classpath "$TEST_CP" -jvm-target 21 -no-stdlib -nowarn \
  -d "$TEST_OUT" $TEST_SRCS >"$EXTRAS/s0-test-compile.log" 2>&1; then
  echo "S0 TEST COMPILE FAILED:"
  grep -E "error:" "$EXTRAS/s0-test-compile.log" | head -30
  exit 1
fi
T1=$(date +%s)
echo "S0 TEST COMPILE OK (wall clock: $((T1-T0))s)"

RUN_CP="$TEST_OUT:$OUT:$TEST_CP"
cat > /tmp/RunS0Tests.java <<'EOF'
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

public class RunS0Tests {
    public static void main(String[] args) throws Exception {
        Class<?>[] classes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) classes[i] = Class.forName(args[i]);
        Result r = JUnitCore.runClasses(classes);
        System.out.println("RUNS=" + r.getRunCount() + " FAILURES=" + r.getFailureCount() + " TIME=" + r.getRunTime() + "ms");
        for (Failure f : r.getFailures()) {
            System.out.println("FAILED: " + f.getDescription().getDisplayName());
            System.out.println(f.getTrace().split("\n")[0]);
        }
        System.exit(r.wasSuccessful() ? 0 : 1);
    }
}
EOF
JDK_BIN="$(dirname "$JAVA")"
if [ ! -f "$EXTRAS/RunS0Tests.class" ] || [ "$EXTRAS/RunS0Tests.java" -nt "$EXTRAS/RunS0Tests.class" ]; then
  "$JDK_BIN/javac" -cp "$EXTRAS/junit-4.13.2.jar" -d "$EXTRAS" /tmp/RunS0Tests.java || exit 1
  cp /tmp/RunS0Tests.java "$EXTRAS/"
fi

echo "=== running S0 tests (migration v17 + feature extractor) ==="
"$JAVA" -Xmx2g -cp "$RUN_CP:$EXTRAS" RunS0Tests \
  com.example.MigrationFailFastTest \
  com.example.VoiceFeatureExtractorTest
RC=$?
if [ $RC -ne 0 ]; then
  echo "S0 TESTS FAILED (rc=$RC)"
  exit 1
fi
echo "S0 ROUTE GREEN"
