#!/bin/bash
# P4 exit-gate render (plan §13 Table 33): compiles + runs the P4 unit suite,
# then renders the ten-chapter gate audiobook end to end through the real
# espeak-ng + ffmpeg toolchain. Evidence tree lands in download/.
set -eu
BASE=/home/z/my-project
AD=$BASE/authr-desktop/desktop
EXTRAS=$BASE/.build-extras
OUT=$EXTRAS/p4-classes
IMG=$AD/build/compose/binaries/main/app/AuthrOS/lib/app
JAVA=/tmp/my-project/tools/jdk/bin/java

GATE_DIR="$BASE/download/Authr-OS-3.0-P4-Audiobook-Gate"
WORK_DIR="$EXTRAS/p4-gate-work"

# 1. compile + unit tests (fresh)
bash "$BASE/scripts/p4_direct_test.sh"

# 2. exit-gate render
echo
echo "=== P4 exit-gate render ==="
CP="$EXTRAS/junit-4.13.2.jar:$EXTRAS/hamcrest-core-1.3.jar:$EXTRAS/kotlin-test-2.2.10.jar:$EXTRAS/kotlin-test-junit-2.2.10.jar:$(ls "$IMG"/*.jar | tr '\n' ':')$AD/build/classes/kotlin/main:$OUT"
rm -rf "$WORK_DIR" "$GATE_DIR"
mkdir -p "$WORK_DIR" "$GATE_DIR"
"$JAVA" -Xmx1g -cp "$CP" com.example.AudiobookGateKt "$GATE_DIR" "$WORK_DIR" 2>&1 | tee "$GATE_DIR.gate-run.log" | tail -60
