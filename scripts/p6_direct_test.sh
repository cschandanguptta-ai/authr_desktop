#!/bin/bash
# P6 direct compile + test run — annotator latency slice (D-08 / ADR-0012),
# same no-Gradle pattern as p4/p5_direct_test.sh.
#
# Compiles the MODIFIED engine files (LiveAstAnnotator with the annotation
# cache, ClicheCleaner with the O(1) span-dedupe) against the stock build
# classes, then runs the annotator test files + the new perf guards via
# JUnitCore. Jars: scripts/p5_fetch_toolchain.sh into ../.build-extras/.
set -u
BASE=/home/z/my-project
AD=$BASE/authr-desktop/desktop
IMG=$AD/build/compose/binaries/main/app/AuthrOS/lib/app
EXTRAS=$BASE/.build-extras
OUT=$EXTRAS/p6-classes
SRC=$AD/src/main/kotlin
TSRC=$AD/src/test/kotlin

rm -rf "$OUT"; mkdir -p "$OUT"

# fetched test jars FIRST (shadow the app image's junit 3.8.2), then the
# app image jars + stock main classes
CP="$EXTRAS/junit-4.13.2.jar:$EXTRAS/hamcrest-core-1.3.jar:$EXTRAS/kotlin-test-2.2.10.jar:$EXTRAS/kotlin-test-junit-2.2.10.jar"
CP+=":$(ls "$IMG"/*.jar | tr '\n' ':')"
CP+="$AD/build/classes/kotlin/main"
# print interior (D-16 resolver tests need pdfbox)
CP+=":$EXTRAS/pdfbox-2.0.33.jar:$EXTRAS/fontbox-2.0.33.jar:$EXTRAS/commons-logging-1.2.jar"

JAVA=/tmp/my-project/tools/jdk/bin/java
COMPILER_CP="$EXTRAS/kotlin-compiler-embeddable-2.2.10.jar:$EXTRAS/trove4j-1.0.20200330.jar:$IMG/kotlin-stdlib-2.2.10-ab998b5a8873e5635565578249af83.jar:$IMG/kotlin-reflect-1.8.21-6c7edbaeeb3b6b1b678269e482fa1c.jar:$IMG/kotlinx-coroutines-core-jvm-1.10.2-16bb58d0777fca3b2efde66862478c.jar:$IMG/annotations-23.0.0-8484cd17d040d837983323f760b2c660.jar"

MAIN_SRCS="$SRC/com/example/engine/LiveAstAnnotator.kt \
$SRC/com/example/engine/ClicheCleaner.kt \
$SRC/com/example/engine/publishing/PrintInterior.kt"

TEST_SRCS="$TSRC/com/example/AnnotatorPerfTest.kt \
$TSRC/com/example/LiveAstAnnotatorTest.kt \
$TSRC/com/example/ClicheCleanerGoldenCorpusTest.kt \
$TSRC/com/example/TokenBiasPolicyTest.kt \
$TSRC/com/example/PrintInteriorTest.kt"

echo "=== compiling modified engine + annotator tests (direct kotlinc) ==="
"$JAVA" -Xmx2g -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -classpath "$CP" -jvm-target 21 -no-stdlib -nowarn \
  -d "$OUT" $MAIN_SRCS $TEST_SRCS || { echo "P6 compile failed"; exit 1; }

RUN_CP="$OUT:$CP"

cat > /tmp/RunP6Tests.java <<'EOF'
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

public class RunP6Tests {
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
JAVAC=/tmp/my-project/tools/jdk/bin/javac
"$JAVAC" -cp "$RUN_CP" -d "$OUT" /tmp/RunP6Tests.java || { echo "launcher compile failed"; exit 1; }

"$JAVA" -Xmx3g -cp "$RUN_CP" RunP6Tests \
  com.example.AnnotatorPerfTest \
  com.example.LiveAstAnnotatorTest \
  com.example.ClicheCleanerGoldenCorpusTest \
  com.example.TokenBiasPolicyTest \
  com.example.PrintInteriorTest
