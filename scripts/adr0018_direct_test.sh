#!/bin/bash
# ADR-0018 pipeline slice test route — compiles the pipeline engine + tests
# against the tracked main classes (no Gradle, same pattern as p4/p5/d13)
# and runs them via JUnitCore. The engine mains compile in the same kotlinc
# invocation (same-module smart casts); the serialization plugin must be ON
# (the memory store DTOs are @Serializable).
set -u
BASE="${AUTHR_BASE:-/home/z/my-project}"
AD=$BASE/authr-desktop/desktop
IMG=$AD/build/compose/binaries/main/app/AuthrOS/lib/app
EXTRAS=$BASE/.build-extras
OUT=$EXTRAS/adr0018-test-classes
SRC=$AD/src/main/kotlin
TSRC=$AD/src/test/kotlin

rm -rf "$OUT"; mkdir -p "$OUT"

CP="$EXTRAS/junit-4.13.2.jar:$EXTRAS/hamcrest-core-1.3.jar:$EXTRAS/kotlin-test-2.2.10.jar:$EXTRAS/kotlin-test-junit-2.2.10.jar:$EXTRAS/kotlinx-coroutines-test-jvm-1.10.2.jar"
CP+=":$(ls "$IMG"/*.jar | tr '\n' ':')"
CP+="$AD/build/classes/kotlin/main"

JAVA="${AUTHR_JAVA:-/tmp/my-project/tools/jdk/bin/java}"
COMPILER_CP="$EXTRAS/kotlin-compiler-embeddable-2.2.10.jar:$EXTRAS/trove4j-1.0.20200330.jar:$IMG/kotlin-stdlib-2.2.10-ab998b5a8873e5635565578249af83.jar:$IMG/kotlin-reflect-1.8.21-6c7edbaeeb3b6b1b678269e482fa1c.jar:$IMG/kotlinx-coroutines-core-jvm-1.10.2-16bb58d0777fca3b2efde66862478c.jar:$IMG/annotations-23.0.0-8484cd17d040d837983323f760b2c660.jar"

T0=$(date +%s)
echo "=== compiling ADR-0018 pipeline slice (direct kotlinc, serialization plugin) ==="
# Engine mains compile WITH the tests (same-module): the store's DTOs and
# the analyzers' cross-references resolve in one invocation.
TEST_SRCS="$SRC/com/example/engine/pipeline/PipelineStages.kt \
$SRC/com/example/engine/pipeline/EditorialMemoryStore.kt \
$SRC/com/example/engine/pipeline/StagePrompts.kt \
$SRC/com/example/engine/pipeline/RetentionAnalyzer.kt \
$SRC/com/example/engine/pipeline/ManuscriptQaSuite.kt \
$SRC/com/example/engine/pipeline/StageGates.kt \
$SRC/com/example/data/model/EditorialMemory.kt \
$SRC/com/example/data/local/EditorialMemoryDao.kt \
$TSRC/com/example/PipelineEngineTest.kt"

if ! "$JAVA" -Xmx2g -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -Xplugin="$EXTRAS/kotlin-maven-serialization-2.2.10.jar" \
  -classpath "$CP" -jvm-target 21 -no-stdlib -nowarn \
  -d "$OUT" $TEST_SRCS >"$EXTRAS/adr0018-test-compile.log" 2>&1; then
  echo "ADR-0018 test compile failed:"
  grep -E "error:" "$EXTRAS/adr0018-test-compile.log" | head -20
  exit 1
fi
T1=$(date +%s)
echo "=== test compile wall clock: $((T1-T0))s ==="

RUN_CP="$OUT:$CP"
cat > /tmp/RunA18Tests.java <<'EOF'
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

public class RunA18Tests {
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
"$JDK_BIN/javac" -cp "$RUN_CP" -d "$OUT" /tmp/RunA18Tests.java
"$JAVA" -cp "$OUT:$RUN_CP" RunA18Tests com.example.PipelineEngineTest
STATUS=$?
if [ $STATUS -eq 0 ]; then echo "ALL GREEN"; fi
exit $STATUS
