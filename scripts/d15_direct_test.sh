#!/bin/bash
# D-15 test route — compiles PublishingWorkbenchTest against the fresh main
# classes (refreshed by d15_build_ui.sh) and runs it plus the publishing
# regression set through JUnitCore. Same no-Gradle pattern as p4/p5/p6.
set -u
BASE="${AUTHR_BASE:-/home/z/my-project}"
AD=$BASE/authr-desktop/desktop
IMG=$AD/build/compose/binaries/main/app/AuthrOS/lib/app
EXTRAS=$BASE/.build-extras
OUT=$EXTRAS/d15-test-classes
TSRC=$AD/src/test/kotlin

rm -rf "$OUT"; mkdir -p "$OUT"

CP="$EXTRAS/junit-4.13.2.jar:$EXTRAS/hamcrest-core-1.3.jar:$EXTRAS/kotlin-test-2.2.10.jar:$EXTRAS/kotlin-test-junit-2.2.10.jar:$EXTRAS/kotlinx-coroutines-test-jvm-1.10.2.jar"
CP+=":$(ls "$IMG"/*.jar | tr '\n' ':')"
CP+="$AD/build/classes/kotlin/main"
CP+=":$EXTRAS/pdfbox-2.0.33.jar:$EXTRAS/fontbox-2.0.33.jar:$EXTRAS/commons-logging-1.2.jar"

JAVA="${AUTHR_JAVA:-/tmp/my-project/tools/jdk/bin/java}"
COMPILER_CP="$EXTRAS/kotlin-compiler-embeddable-2.2.10.jar:$EXTRAS/trove4j-1.0.20200330.jar:$IMG/kotlin-stdlib-2.2.10-ab998b5a8873e5635565578249af83.jar:$IMG/kotlin-reflect-1.8.21-6c7edbaeeb3b6b1b678269e482fa1c.jar:$IMG/kotlinx-coroutines-core-jvm-1.10.2-16bb58d0777fca3b2efde66862478c.jar:$IMG/annotations-23.0.0-8484cd17d040d837983323f760b2c660.jar"

T0=$(date +%s)
echo "=== compiling D15 test + publishing regression set (direct kotlinc) ==="
TEST_SRCS="$TSRC/com/example/PrintInteriorTest.kt \
$TSRC/com/example/PublishingWorkbenchTest.kt \
$TSRC/com/example/RetailPackagerTest.kt \
$TSRC/com/example/PublishingMetadataTest.kt \
$TSRC/com/example/VolumeBundlerTest.kt \
$TSRC/com/example/ShipReportTest.kt"
if ! "$JAVA" -Xmx2g -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -classpath "$CP" -jvm-target 21 -no-stdlib -nowarn \
  -d "$OUT" $TEST_SRCS >"$EXTRAS/d15-test-compile.log" 2>&1; then
  echo "D15 test compile failed:"; grep -E "error:" "$EXTRAS/d15-test-compile.log" | head -20
  exit 1
fi
T1=$(date +%s)
echo "=== test compile wall clock: $((T1-T0))s ==="

RUN_CP="$OUT:$CP"
cat > /tmp/RunD15Tests.java <<'EOF'
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

public class RunD15Tests {
    public static void main(String[] args) throws Exception {
        Class<?>[] classes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) classes[i] = Class.forName(args[i]);
        Result r = JUnitCore.runClasses(classes);
        System.out.println("RUNS=" + r.getRunCount() + " FAILURES=" + r.getFailureCount());
        r.getFailures().forEach(f -> {
            System.out.println("FAILED: " + f.getTestHeader());
            System.out.println("  " + f.getMessage());
        });
        System.exit(r.getFailureCount() == 0 ? 0 : 1);
    }
}
EOF
"$JAVA" -cp /tmp RunD15Tests 2>/dev/null >/dev/null || /tmp/my-project/tools/jdk/bin/javac -cp "$EXTRAS/junit-4.13.2.jar" -d /tmp /tmp/RunD15Tests.java

cd "$BASE"  # user.dir = repo root so EpubCheckLocator finds .build-extras dist
"$JAVA" -Xmx2g -cp "/tmp:$RUN_CP" RunD15Tests \
  com.example.PrintInteriorTest \
  com.example.PublishingWorkbenchTest \
  com.example.RetailPackagerTest \
  com.example.PublishingMetadataTest \
  com.example.VolumeBundlerTest \
  com.example.ShipReportTest
