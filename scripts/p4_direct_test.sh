#!/bin/bash
# P4 direct compile + test run — no Gradle needed.
# Compiles engine/audio + EspeakLocator + the 5 P4 test classes against the
# surviving app-image classpath + Nov 9 desktop.jar, then runs them via JUnitCore.
set -u
BASE="${AUTHR_BASE:-/home/z/my-project}"
AD=$BASE/authr-desktop/desktop
IMG=$AD/build/compose/binaries/main/app/AuthrOS/lib/app
EXTRAS=$BASE/.build-extras
OUT=$BASE/.build-extras/p4-classes
SRC=$AD/src/main/kotlin
TSRC=$AD/src/test/kotlin

rm -rf "$OUT"; mkdir -p "$OUT"

# classpath: app image jars + Nov 9 main classes + fetched test jars
CP=$(ls "$IMG"/*.jar | tr '\n' ':')
CP+="$AD/build/classes/kotlin/main:"
CP+="$EXTRAS/junit-4.13.2.jar:$EXTRAS/hamcrest-core-1.3.jar:"
CP+="$EXTRAS/kotlin-test-2.2.10.jar:$EXTRAS/kotlin-test-junit-2.2.10.jar"
# strip the WRONG junit (3.8.2) from the app image end of the path: junit 4 wins by order? No —
# put junit 4 FIRST so it shadows junit 3.8.2.
CP="$EXTRAS/junit-4.13.2.jar:$EXTRAS/hamcrest-core-1.3.jar:$EXTRAS/kotlin-test-2.2.10.jar:$EXTRAS/kotlin-test-junit-2.2.10.jar:$CP"

JAVA="${AUTHR_JAVA:-/tmp/my-project/tools/jdk/bin/java}"
COMPILER_CP="$EXTRAS/kotlin-compiler-embeddable-2.2.10.jar:$EXTRAS/trove4j-1.0.20200330.jar:$IMG/kotlin-stdlib-2.2.10-ab998b5a8873e5635565578249af83.jar:$IMG/kotlin-reflect-1.8.21-6c7edbaeeb3b6b1b678269e482fa1c.jar:$IMG/kotlinx-coroutines-core-jvm-1.10.2-16bb58d0777fca3b2efde66862478c.jar:$IMG/annotations-23.0.0-8484cd17d040d837983323f760b2c660.jar"

MAIN_SRCS="$SRC/com/example/engine/audio/PronunciationPass.kt $SRC/com/example/engine/audio/MasteringChain.kt $SRC/com/example/engine/audio/AcxAudit.kt $SRC/com/example/engine/audio/SmilExporter.kt $SRC/com/example/engine/audio/AudiobookRenderer.kt $SRC/com/example/desktopbridge/EspeakLocator.kt"
TEST_SRCS="$TSRC/com/example/PronunciationPassTest.kt $TSRC/com/example/MasteringChainTest.kt $TSRC/com/example/AcxAuditTest.kt $TSRC/com/example/SmilExporterTest.kt $TSRC/com/example/AudiobookRendererTest.kt $TSRC/com/example/AudiobookGate.kt"

echo "=== compiling P4 main+test (direct kotlinc) ==="
"$JAVA" -Xmx2g -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -classpath "$CP" -jvm-target 21 -no-stdlib -nowarn \
  -d "$OUT" $MAIN_SRCS $TEST_SRCS 2>&1 | tail -30
[ ${PIPESTATUS[0]} -ne 0 ] && { echo "COMPILE FAILED"; exit 1; }
echo "compile OK"

echo "=== running P4 test suite via JUnitCore ==="
cat > /tmp/RunP4Tests.java <<'EOF'
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

public class RunP4Tests {
    public static void main(String[] args) throws Exception {
        Class<?>[] classes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            classes[i] = Class.forName(args[i]);
        }
        Result r = JUnitCore.runClasses(classes);
        System.out.println("RUNS=" + r.getRunCount() + " FAILURES=" + r.getFailureCount() +
            " TIME=" + r.getRunTime() + "ms");
        for (Failure f : r.getFailures()) {
            System.out.println("FAIL: " + f.getTestHeader());
            System.out.println("  " + String.valueOf(f.getMessage()).replace("\n", "\n  "));
        }
        System.out.println(r.wasSuccessful() ? "ALL GREEN" : "RED");
        System.exit(r.wasSuccessful() ? 0 : 1);
    }
}
EOF
JAVAC="$(dirname "$JAVA")/javac"
"$JAVAC" -cp "$OUT:$CP" -d "$OUT" /tmp/RunP4Tests.java || { echo "launcher compile failed"; exit 1; }
RUN_CP="$OUT:$CP"
"$JAVA" -cp "$RUN_CP" RunP4Tests \
  com.example.PronunciationPassTest \
  com.example.MasteringChainTest \
  com.example.AcxAuditTest \
  com.example.SmilExporterTest \
  com.example.AudiobookRendererTest
