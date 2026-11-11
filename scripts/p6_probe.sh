#!/bin/bash
# P6 latency probe runner (D-08, ADR-0010: measured, not estimated).
# Compiles scripts/latency_probe.kt against the CURRENT engine classes and
# runs it. Pass EXTRA_CP="<dir>" to shadow the engine classes with a freshly
# compiled fixed build (post-fix measurement uses the same probe unchanged).
set -u
BASE=/home/z/my-project
AD=$BASE/authr-desktop/desktop
IMG=$AD/build/compose/binaries/main/app/AuthrOS/lib/app
EXTRAS=$BASE/.build-extras
OUT=/tmp/p6-probe/classes
EXTRA_CP="${EXTRA_CP:-}"

rm -rf "$OUT"; mkdir -p "$OUT"

# Compile classpath: post-fix engine classes FIRST (when measuring the fix),
# then the stock build classes + app image jars.
CP="${EXTRA_CP:+$EXTRA_CP:}$AD/build/classes/kotlin/main:$(ls "$IMG"/*.jar | tr '\n' ':')"

JAVA=/tmp/my-project/tools/jdk/bin/java
COMPILER_CP="$EXTRAS/kotlin-compiler-embeddable-2.2.10.jar:$EXTRAS/trove4j-1.0.20200330.jar:$IMG/kotlin-stdlib-2.2.10-ab998b5a8873e5635565578249af83.jar:$IMG/kotlin-reflect-1.8.21-6c7edbaeeb3b6b1b678269e482fa1c.jar:$IMG/kotlinx-coroutines-core-jvm-1.10.2-16bb58d0777fca3b2efde66862478c.jar:$IMG/annotations-23.0.0-8484cd17d040d837983323f760b2c660.jar"

"$JAVA" -Xmx2g -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -classpath "$CP" -jvm-target 21 -no-stdlib -nowarn \
  -d "$OUT" "$BASE/scripts/latency_probe.kt" || { echo "probe compile failed"; exit 1; }

# Shadow order: probe classes first, then the EXTRA_CP fixed-engine build
# (post-fix runs), THEN the stock build/classes — first match on CP wins.
RUN_CP="$OUT${EXTRA_CP:+:$EXTRA_CP}:$CP"
"$JAVA" -Xmx3g -cp "$RUN_CP" com.example.Latency_probeKt
