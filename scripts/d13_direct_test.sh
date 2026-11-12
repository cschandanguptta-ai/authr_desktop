#!/bin/bash
# D-13 + D-01/02/03/09 slice test route — compiles the new workbench/wiring/
# policy tests against the fresh main classes (built by d15_build_ui.sh) and
# runs them plus the publishing + audio regression sets through JUnitCore.
# Same no-Gradle pattern as p4/p5/p6/d15.
set -u
BASE="${AUTHR_BASE:-/home/z/my-project}"
AD=$BASE/authr-desktop/desktop
IMG=$AD/build/compose/binaries/main/app/AuthrOS/lib/app
EXTRAS=$BASE/.build-extras
OUT=$EXTRAS/d13-test-classes
SRC=$AD/src/main/kotlin
TSRC=$AD/src/test/kotlin

rm -rf "$OUT"; mkdir -p "$OUT"

CP="$EXTRAS/junit-4.13.2.jar:$EXTRAS/hamcrest-core-1.3.jar:$EXTRAS/kotlin-test-2.2.10.jar:$EXTRAS/kotlin-test-junit-2.2.10.jar:$EXTRAS/kotlinx-coroutines-test-jvm-1.10.2.jar"
CP+=":$(ls "$IMG"/*.jar | tr '\n' ':')"
CP+="$AD/build/classes/kotlin/main"
CP+=":$EXTRAS/pdfbox-2.0.33.jar:$EXTRAS/fontbox-2.0.33.jar:$EXTRAS/commons-logging-1.2.jar"

JAVA="${AUTHR_JAVA:-/tmp/my-project/tools/jdk/bin/java}"
COMPILER_CP="$EXTRAS/kotlin-compiler-embeddable-2.2.10.jar:$EXTRAS/trove4j-1.0.20200330.jar:$IMG/kotlin-stdlib-2.2.10-ab998b5a8873e5635565578249af83.jar:$IMG/kotlin-reflect-1.8.21-6c7edbaeeb3b6b1b678269e482fa1c.jar:$IMG/kotlinx-coroutines-core-jvm-1.10.2-16bb58d0777fca3b2efde66862478c.jar:$IMG/annotations-23.0.0-8484cd17d040d837983323f760b2c660.jar"

T0=$(date +%s)
echo "=== compiling D13 slice tests + audio engine mains (direct kotlinc, p4 same-module pattern) ==="
# The P4 audio mains compile IN the same kotlinc invocation as the tests
# (the p4 pattern): same-module smart casts on ChapterRender.audit /
# RenderReport.masterAudit; everything else resolves from the prebuilt
# main classes on the classpath.
TEST_SRCS="$SRC/com/example/engine/audio/PronunciationPass.kt \
$SRC/com/example/engine/audio/MasteringChain.kt \
$SRC/com/example/engine/audio/AcxAudit.kt \
$SRC/com/example/engine/audio/SmilExporter.kt \
$SRC/com/example/engine/audio/AudiobookRenderer.kt \
$SRC/com/example/desktopbridge/EspeakLocator.kt \
$TSRC/com/example/AudiobookWorkbenchTest.kt \
$TSRC/com/example/PocketSynthesizerTest.kt \
$TSRC/com/example/WorkerWiringTest.kt \
$TSRC/com/example/MigrationFailFastTest.kt \
$TSRC/com/example/SecurePrefsFallbackTest.kt \
$TSRC/com/example/AudiobookRendererTest.kt \
$TSRC/com/example/PronunciationPassTest.kt \
$TSRC/com/example/MasteringChainTest.kt \
$TSRC/com/example/AcxAuditTest.kt \
$TSRC/com/example/SmilExporterTest.kt \
$TSRC/com/example/SerialExportProfilesTest.kt \
$TSRC/com/example/SerialDashboardTest.kt \
$TSRC/com/example/PublishingWorkbenchTest.kt \
$TSRC/com/example/RetailPackagerTest.kt"
if ! "$JAVA" -Xmx2g -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -classpath "$CP" -jvm-target 21 -no-stdlib -nowarn \
  -d "$OUT" $TEST_SRCS >"$EXTRAS/d13-test-compile.log" 2>&1; then
  echo "D13 test compile failed:"; grep -E "error:" "$EXTRAS/d13-test-compile.log" | head -20
  exit 1
fi
T1=$(date +%s)
echo "=== test compile wall clock: $((T1-T0))s ==="

RUN_CP="$OUT:$CP"
cat > /tmp/RunD13Tests.java <<'EOF'
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

public class RunD13Tests {
    public static void main(String[] args) throws Exception {
        Class<?>[] classes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) classes[i] = Class.forName(args[i]);
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
"$JAVAC" -cp "$OUT:$EXTRAS/junit-4.13.2.jar" -d "$OUT" /tmp/RunD13Tests.java || { echo "launcher compile failed"; exit 1; }

"$JAVA" -Xmx3g -cp "$RUN_CP" RunD13Tests \
  com.example.AudiobookWorkbenchTest \
  com.example.PocketSynthesizerTest \
  com.example.WorkerWiringTest \
  com.example.MigrationFailFastTest \
  com.example.SecurePrefsFallbackTest \
  com.example.AudiobookRendererTest \
  com.example.PronunciationPassTest \
  com.example.MasteringChainTest \
  com.example.AcxAuditTest \
  com.example.SmilExporterTest \
  com.example.SerialExportProfilesTest \
  com.example.SerialDashboardTest \
  com.example.PublishingWorkbenchTest \
  com.example.RetailPackagerTest
