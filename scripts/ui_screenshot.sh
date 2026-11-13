#!/bin/bash
# UI-UX desktop-style fix — screenshot capture (2026-11-13).
# Boots the packaged app (or a fresh app jar built by d15_build_ui.sh) under
# Xvfb, waits for first frame, captures the window via ffmpeg x11grab.
# Usage: ui_screenshot.sh <seconds-to-wait> <out.png> [java-args...]
set -u
DIST="${AUTHR_BASE:-/home/z/my-project}/authr-desktop/desktop/build/compose/binaries/main/app/AuthrOS"
export JAVA_HOME=/tmp/my-project/tools/jdk
export LD_LIBRARY_PATH="/tmp/my-project/tools/xlibs/usr/lib/x86_64-linux-gnu:${LD_LIBRARY_PATH:-}"
export LIBGL_ALWAYS_SOFTWARE=1

WAIT="${1:?wait-seconds}"; OUT="${2:?out.png}"; shift 2

rm -f /tmp/.X9-lock /tmp/X11-unix/X9
Xvfb :99 -screen 0 1440x900x24 > /tmp/xvfb_ui.log 2>&1 &
XVFB_PID=$!
sleep 3

# fresh app data root per run so onboarding/first-run state is deterministic
rm -rf /tmp/authr-uitest-data && mkdir -p /tmp/authr-uitest-data

DISPLAY=:99 "$DIST/bin/AuthrOS" "$@" > /tmp/ui_app.log 2>&1 &
APP_PID=$!

# poll the log for the workspace-ready line, or fall back to fixed wait
for i in $(seq 1 "$((WAIT*2))"); do
  sleep 0.5
  grep -q "Authr OS starting" /tmp/ui_app.log && break
done
sleep "$((WAIT<8 ? WAIT : 8))"

ffmpeg -y -loglevel error -f x11grab -video_size 1440x900 -i :99 \
  -frames:v 1 "$OUT" 2>>/tmp/ui_app.log

kill $APP_PID $XVFB_PID 2>/dev/null
echo "captured $OUT ($(du -h "$OUT" | cut -f1))"
grep -m1 "Authr OS starting" /tmp/ui_app.log || echo "WARN: no startup line in log"
grep -cE "Exception" /tmp/ui_app.log | sed 's/^/exceptions: /'
