#!/bin/bash
# P5 direct compile + test run — no Gradle needed (P4 pattern, extended).
# Compiles engine/publishing + EpubCheckLocator + the MODIFIED AuthrContainer
# (structured KDP workbench) + the P4 audio mains (AudiobookGate dependency),
# then runs the P5 test classes + the container round-trip guard via JUnitCore.
# Requires: scripts/p5_fetch_toolchain.sh (jars in /home/z/my-project/.build-extras),
# the app image classpath + Nov 9/P4 build classes.
set -u
BASE=/home/z/my-project
AD=$BASE/authr-desktop/desktop
IMG=$AD/build/compose/binaries/main/app/AuthrOS/lib/app
EXTRAS=$BASE/.build-extras
OUT=$EXTRAS/p5-classes
SRC=$AD/src/main/kotlin
TSRC=$AD/src/test/kotlin

rm -rf "$OUT"; mkdir -p "$OUT"

# classpath: fetched test jars FIRST (shadow the app image's junit 3.8.2),
# then the app image jars + main classes
CP="$EXTRAS/junit-4.13.2.jar:$EXTRAS/hamcrest-core-1.3.jar:$EXTRAS/kotlin-test-2.2.10.jar:$EXTRAS/kotlin-test-junit-2.2.10.jar"
CP+=":$(ls "$IMG"/*.jar | tr '\n' ':')"
CP+="$AD/build/classes/kotlin/main"
# publishing-gate external tools
CP+=":$EXTRAS/pdfbox-2.0.33.jar:$EXTRAS/fontbox-2.0.33.jar:$EXTRAS/commons-logging-1.2.jar"

JAVA=/tmp/my-project/tools/jdk/bin/java
COMPILER_CP="$EXTRAS/kotlin-compiler-embeddable-2.2.10.jar:$EXTRAS/trove4j-1.0.20200330.jar:$IMG/kotlin-stdlib-2.2.10-ab998b5a8873e5635565578249af83.jar:$IMG/kotlin-reflect-1.8.21-6c7edbaeeb3b6b1b678269e482fa1c.jar:$IMG/kotlinx-coroutines-core-jvm-1.10.2-16bb58d0777fca3b2efde66862478c.jar:$IMG/annotations-23.0.0-8484cd17d040d837983323f760b2c660.jar"

# P5 mains + modified container + P4 audio mains (the gate needs GateManuscript
# from AudiobookGate.kt, which imports the P4 engine)
MAIN_SRCS="$SRC/com/example/engine/publishing/PublishingMetadata.kt \
$SRC/com/example/engine/publishing/Epub3Exporter.kt \
$SRC/com/example/engine/publishing/PrintInterior.kt \
$SRC/com/example/engine/publishing/VolumeBundler.kt \
$SRC/com/example/engine/publishing/ShipReport.kt \
$SRC/com/example/engine/publishing/RetailPackager.kt \
$SRC/com/example/desktopbridge/EpubCheckLocator.kt \
$SRC/com/example/container/AuthrContainer.kt \
$SRC/com/example/engine/audio/PronunciationPass.kt \
$SRC/com/example/engine/audio/MasteringChain.kt \
$SRC/com/example/engine/audio/AcxAudit.kt \
$SRC/com/example/engine/audio/SmilExporter.kt \
$SRC/com/example/engine/audio/AudiobookRenderer.kt \
$SRC/com/example/desktopbridge/EspeakLocator.kt"

TEST_SRCS="$TSRC/com/example/PublishingMetadataTest.kt \
$TSRC/com/example/Epub3ExporterTest.kt \
$TSRC/com/example/PrintInteriorTest.kt \
$TSRC/com/example/VolumeBundlerTest.kt \
$TSRC/com/example/ShipReportTest.kt \
$TSRC/com/example/RetailPackagerTest.kt \
$TSRC/com/example/AuthrContainerRoundTripTest.kt \
$TSRC/com/example/AudiobookGate.kt \
$TSRC/com/example/PublishingGate.kt"

echo "=== compiling P5 main+test (direct kotlinc, serialization plugin on) ==="
"$JAVA" -Xmx2g -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -Xplugin="$EXTRAS/kotlin-maven-serialization-2.2.10.jar" \
  -classpath "$CP" -jvm-target 21 -no-stdlib -nowarn \
  -d "$OUT" $MAIN_SRCS $TEST_SRCS 2>&1 | tail -30
[ ${PIPESTATUS[0]} -ne 0 ] && { echo "COMPILE FAILED"; exit 1; }
echo "compile OK"

echo "=== running P5 test suite via JUnitCore ==="
cat > /tmp/RunP5Tests.java <<'EOF'
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

public class RunP5Tests {
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
"$JAVAC" -cp "$OUT:$CP" -d "$OUT" /tmp/RunP5Tests.java || { echo "launcher compile failed"; exit 1; }
RUN_CP="$OUT:$CP"
"$JAVA" -Xmx3g -cp "$RUN_CP" RunP5Tests \
  com.example.PublishingMetadataTest \
  com.example.Epub3ExporterTest \
  com.example.PrintInteriorTest \
  com.example.VolumeBundlerTest \
  com.example.ShipReportTest \
  com.example.RetailPackagerTest \
  com.example.AuthrContainerRoundTripTest
