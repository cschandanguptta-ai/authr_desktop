#!/bin/bash
# P3 headless UI smoke: Xvfb + rebuilt Authr OS app image + Robot screenshots
# of the Writer Studio and the new Serial Dashboard tab.
export JAVA_HOME=/tmp/my-project/tools/jdk
export LD_LIBRARY_PATH=/tmp/my-project/tools/xlibs/usr/lib/x86_64-linux-gnu:$LD_LIBRARY_PATH

DIST=/tmp/my-project/authr-desktop/desktop/build/compose/binaries/main/app/AuthrOS
CP=$(ls "$DIST"/lib/app/*.jar | tr '\n' ':')

rm -rf /tmp/render-p3 && mkdir -p /tmp/render-p3
rm -f /tmp/.X9-lock /tmp/.X11-unix/X9

Xvfb :99 -screen 0 1440x900x24 > /tmp/xvfb_p3.log 2>&1 &
XVFB_PID=$!
sleep 3

cd /home/z/my-project/scripts
rm -rf p3-smoke-classes && mkdir p3-smoke-classes
"$JAVA_HOME/bin/javac" -cp "$CP" -d p3-smoke-classes SmokeShotP3.java

DISPLAY=:99 LIBGL_ALWAYS_SOFTWARE=1 timeout 130 "$JAVA_HOME/bin/java" \
  -Djava.awt.headless=false \
  -Dskiko.library.path="$DIST/lib/app" -Dskiko.renderApi=SOFTWARE \
  -Dshot.out=/tmp/render-p3 \
  -cp "p3-smoke-classes:$CP" \
  SmokeShotP3 > /tmp/p3_smoke.log 2>&1
EXIT=$?

kill $XVFB_PID 2>/dev/null
grep -E "Saved screenshot|SMOKE OK|APP CRASHED|Exception" /tmp/p3_smoke.log | head -12
echo "exit=$EXIT"
ls -la /tmp/render-p3/ 2>/dev/null
