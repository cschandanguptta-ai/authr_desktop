#!/bin/bash
# P5 exit gate (plan §13, Table 33, P5 row) — retail package in one action.
#
# 1. compiles the P5 engine + tests + gate harness (direct kotlinc route,
#    same classpath contract as p5_direct_test.sh)
# 2. runs PublishingGate: TWO complete projects (novel "The Iron Vale" +
#    the 15-chapter golden serial) round-trip through real .authr containers
#    and produce full retail packages in ONE action each; every EPUB is then
#    run through the REAL epubcheck 5 tool; print interiors are checked
#    against the 6×9 in trim and the plan's gutter ladder — plus four band
#    probes covering ALL FOUR ladder bands.
# 3. evidence tree → download/Authr-OS-3.0-P5-Retail-Package/
#    gate log        → download/Authr-OS-3.0-P5-Retail-Package.gate-run.log
set -u
BASE=/home/z/my-project
EVIDENCE="$BASE/download/Authr-OS-3.0-P5-Retail-Package"
LOG="$BASE/download/Authr-OS-3.0-P5-Retail-Package.gate-run.log"

echo "=== P5 exit gate — retail package in one action ===" | tee "$LOG"

# 1. compile + test suite first (the gate reuses the compiled classes)
bash "$BASE/scripts/p5_direct_test.sh" 2>&1 | tee -a "$LOG"
[ ${PIPESTATUS[0]} -ne 0 ] && { echo "P5 SUITE RED — gate aborted" | tee -a "$LOG"; exit 1; }

# 2. run the gate harness
echo "" | tee -a "$LOG"
echo "--- PublishingGate (two projects, one action each) ---" | tee -a "$LOG"
EXTRAS=$BASE/.build-extras
IMG=$BASE/authr-desktop/desktop/build/compose/binaries/main/app/AuthrOS/lib/app
AD=$BASE/authr-desktop/desktop
CP="$EXTRAS/p5-classes:$EXTRAS/junit-4.13.2.jar:$EXTRAS/hamcrest-core-1.3.jar:$EXTRAS/kotlin-test-2.2.10.jar:$EXTRAS/kotlin-test-junit-2.2.10.jar"
CP+=":$(ls "$IMG"/*.jar | tr '\n' ':')"
CP+="$AD/build/classes/kotlin/main"
CP+=":$EXTRAS/pdfbox-2.0.33.jar:$EXTRAS/fontbox-2.0.33.jar:$EXTRAS/commons-logging-1.2.jar"

mkdir -p "$EVIDENCE"
/tmp/my-project/tools/jdk/bin/java -Xmx3g -cp "$CP" com.example.PublishingGateKt "$EVIDENCE" 2>&1 | tee -a "$LOG"
GATE_CODE=${PIPESTATUS[0]}
if [ $GATE_CODE -ne 0 ]; then
  echo "P5 EXIT GATE: RED (harness exit $GATE_CODE)" | tee -a "$LOG"
  exit 1
fi

# 3. summarize the evidence tree
echo "" | tee -a "$LOG"
echo "--- Evidence tree ---" | tee -a "$LOG"
( cd "$EVIDENCE" && ls -laR | head -60 ) | tee -a "$LOG"

# final verdict line comes from the harness output (P5 EXIT GATE: GREEN)
if grep -q "P5 EXIT GATE: GREEN" "$LOG"; then
  echo "Gate log: $LOG" | tee -a "$LOG"
  echo "Evidence: $EVIDENCE" | tee -a "$LOG"
  exit 0
else
  echo "P5 EXIT GATE: RED (no GREEN line in log)" | tee -a "$LOG"
  exit 1
fi
