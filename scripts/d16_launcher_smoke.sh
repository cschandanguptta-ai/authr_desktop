#!/bin/bash
# Launcher smoke for the packaged app image (pre-zip gate, 2026-11-12):
# runs bin/AuthrOS (the real jpackage launcher) under Xvfb and verifies the
# process stays alive with zero exceptions for the full window.
DIST="${AUTHR_BASE:-/home/z/my-project}/authr-desktop/desktop/build/compose/binaries/main/app/AuthrOS"
export JAVA_HOME=/tmp/my-project/tools/jdk
export LD_LIBRARY_PATH=/tmp/my-project/tools/xlibs/usr/lib/x86_64-linux-gnu:$LD_LIBRARY_PATH
export LIBGL_ALWAYS_SOFTWARE=1

rm -f /tmp/.X9-lock /tmp/.X11-unix/X9
Xvfb :99 -screen 0 1440x900x24 > /tmp/xvfb_d16.log 2>&1 &
XVFB_PID=$!
sleep 3

START=$(date +%s)
DISPLAY=:99 timeout 40 "$DIST/bin/AuthrOS" > /tmp/d16_launcher.log 2>&1
EXIT=$?
ALIVE_SEC=$(( $(date +%s) - START ))

kill $XVFB_PID 2>/dev/null
echo "exit=$EXIT (124 = survived full 40s window, killed by timeout)"
echo "alive_seconds=$ALIVE_SEC"
echo "=== exceptions in log:"
grep -cE "Exception|FATAL|No factory" /tmp/d16_launcher.log || echo "0"
echo "=== first lines:"
head -6 /tmp/d16_launcher.log
echo "=== last lines:"
tail -4 /tmp/d16_launcher.log
