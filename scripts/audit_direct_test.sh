#!/bin/bash
# 2026-11-12 audit-fix test route — the persona registry / honest telemetry /
# plugin-persona consumption guards. Same no-Gradle pattern as p4/p5/p6/d13/d15:
# compiles the affected test classes against the refreshed main classes
# (d15_build_ui.sh refreshes them) and runs them via JUnitCore.
set -u
BASE="${AUTHR_BASE:-/home/z/my-project}"
AD=$BASE/authr-desktop/desktop
IMG=$AD/build/compose/binaries/main/app/AuthrOS/lib/app
EXTRAS=$BASE/.build-extras
OUT=$EXTRAS/audit-test-classes
TSRC=$AD/src/test/kotlin

rm -rf "$OUT"; mkdir -p "$OUT"

CP="$EXTRAS/junit-4.13.2.jar:$EXTRAS/hamcrest-core-1.3.jar:$EXTRAS/kotlin-test-2.2.10.jar:$EXTRAS/kotlin-test-junit-2.2.10.jar:$EXTRAS/kotlinx-coroutines-test-jvm-1.10.2.jar"
CP+=":$(ls "$IMG"/*.jar | tr '\n' ':')"
CP+="$AD/build/classes/kotlin/main"
CP+=":$EXTRAS/pdfbox-2.0.33.jar:$EXTRAS/fontbox-2.0.33.jar:$EXTRAS/commons-logging-1.2.jar"

JAVA="${AUTHR_JAVA:-/tmp/my-project/tools/jdk/bin/java}"
COMPILER_CP="$EXTRAS/kotlin-compiler-embeddable-2.2.10.jar:$EXTRAS/trove4j-1.0.20200330.jar:$(ls "$IMG"/kotlin-stdlib-*.jar | head -1):$IMG/kotlin-reflect-1.8.21-6c7edbaeeb3b6b1b678269e482fa1c.jar:$IMG/kotlinx-coroutines-core-jvm-1.10.2-16bb58d0777fca3b2efde66862478c.jar:$IMG/annotations-23.0.0-8484cd17d040d837983323f760b2c660.jar"

T0=$(date +%s)
echo "=== compiling audit-fix test set (direct kotlinc) ==="
TEST_SRCS="$TSRC/com/example/PersonaRegistryTest.kt \
$TSRC/com/example/TextMetricsTest.kt \
$TSRC/com/example/PluginRegistryTest.kt \
$TSRC/com/example/AuthrContainerRoundTripTest.kt"
if ! "$JAVA" -Xmx2g -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -classpath "$CP" -jvm-target 21 -no-stdlib -nowarn \
  -d "$OUT" $TEST_SRCS >"$EXTRAS/audit-test-compile.log" 2>&1; then
  echo "Audit test compile failed:"; grep -E "error:" "$EXTRAS/audit-test-compile.log" | head -20
  exit 1
fi
T1=$(date +%s)
echo "=== test compile wall clock: $((T1-T0))s ==="

RUN_CP="$OUT:$CP"
cat > /tmp/RunAuditTests.java <<'EOF'
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

public class RunAuditTests {
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
rm -f /tmp/RunAuditTests.class
JAVAC_BIN="${JAVA%java}javac"
"$JAVAC_BIN" -cp "$EXTRAS/junit-4.13.2.jar" -d /tmp /tmp/RunAuditTests.java

echo "=== running audit-fix test set ==="
"$JAVA" -cp "$RUN_CP:/tmp" RunAuditTests \
  com.example.PersonaRegistryTest \
  com.example.TextMetricsTest \
  com.example.PluginRegistryTest \
  com.example.AuthrContainerRoundTripTest
