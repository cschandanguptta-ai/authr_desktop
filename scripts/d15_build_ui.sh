#!/bin/bash
# D-15 UI slice build — full main-tree compile through the direct kotlinc
# route (no Gradle in this sandbox), then:
#   1. refresh the tracked stock classes (desktop/build/classes/kotlin/main)
#      so every p4/p5/p6 direct route compiles against the CURRENT tree,
#   2. rebuild the app jar and swap it into the compose app image in place
#      (same filename — the launcher cfg lists it by name), so the shipped
#      app actually carries the Publishing workbench + P4/P5 engine.
# Also prints the compile-wall-clock baseline the latency work (ADR-0010
# discipline: measured, not estimated) records in tech-debt.md.
set -eu
BASE="${AUTHR_BASE:-/home/z/my-project}"
AD=$BASE/authr-desktop/desktop
IMG=$AD/build/compose/binaries/main/app/AuthrOS/lib/app
EXTRAS=$BASE/.build-extras
OUT=$EXTRAS/d15-classes
SRC=$AD/src/main/kotlin

JAVA="${AUTHR_JAVA:-/tmp/my-project/tools/jdk/bin/java}"
COMPILER_CP="$EXTRAS/kotlin-compiler-embeddable-2.2.10.jar:$EXTRAS/trove4j-1.0.20200330.jar:$IMG/kotlin-stdlib-2.2.10-ab998b5a8873e5635565578249af83.jar:$IMG/kotlin-reflect-1.8.21-6c7edbaeeb3b6b1b678269e482fa1c.jar:$IMG/kotlinx-coroutines-core-jvm-1.10.2-16bb58d0777fca3b2efde66862478c.jar:$IMG/annotations-23.0.0-8484cd17d040d837983323f760b2c660.jar"
# dependency classpath: the app image IS the runtime truth (compose 1.9.3 etc.)
# + pdfbox/fontbox/commons-logging: declared in build.gradle.kts (P5 slice) but
#   ABSENT from the P3-era app image — the D-15 bundle puts them beside the app
#   jar and lists them in the launcher cfg, or the packaged app throws
#   NoClassDefFoundError the moment the print interior renders.
CP="$(ls "$IMG"/*.jar | tr '\n' ':')"
CP+="$EXTRAS/pdfbox-2.0.33.jar:$EXTRAS/fontbox-2.0.33.jar:$EXTRAS/commons-logging-1.2.jar"

# every main source — the full tree, so the swapped jar is complete
# + the KSP-generated Room implementations (AppDatabase_Impl, DAO impls):
# without them the packaged app dies at getDatabase() ("AppDatabase_Impl
# does not exist"). The generated tree predates no source change (verified
# 2026-11-12: generated mtime newer than every data/ source mtime).
SRCS=$(find "$SRC" -name '*.kt' | sort | tr '\n' ' ')
SRCS+="$(find "$AD/build/generated/ksp/main/kotlin" -name '*.kt' | sort | tr '\n' ' ')"
COUNT=$(find "$SRC" -name '*.kt' | wc -l)
KSP_COUNT=$(find "$AD/build/generated/ksp/main/kotlin" -name '*.kt' | wc -l)

rm -rf "$OUT"; mkdir -p "$OUT"

echo "=== compiling $COUNT main + $KSP_COUNT ksp-generated sources (direct kotlinc, compose + serialization plugins) ==="
T0=$(date +%s)
LOG=$EXTRAS/d15-compile.log
if ! "$JAVA" -Xmx3g -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -classpath "$CP" \
  -Xplugin="$EXTRAS/kotlin-compose-compiler-plugin-embeddable-2.2.10.jar" \
  -Xplugin="$EXTRAS/kotlin-maven-serialization-2.2.10.jar" \
  -jvm-target 21 -no-stdlib -nowarn \
  -d "$OUT" $SRCS >"$LOG" 2>&1; then
  echo "D15 compile failed — errors:"; grep -E "error:" "$LOG" | head -30
  exit 1
fi
T1=$(date +%s)
echo "=== main-tree compile wall clock: $((T1-T0))s ($COUNT+$KSP_COUNT sources) ==="

CLASSES=$(find "$OUT" -name '*.class' | wc -l)
echo "=== compiled class files: $CLASSES ==="

# 1. refresh the tracked stock classes (load-bearing classpath for the routes)
rm -rf "$AD/build/classes/kotlin/main"
cp -r "$OUT" "$AD/build/classes/kotlin/main"
# kotlinc names the module file main.kotlin_module; the compose app image jar
# carries desktop.kotlin_module — rename for parity (module-file readers).
if [ -f "$AD/build/classes/kotlin/main/META-INF/main.kotlin_module" ] && \
   [ ! -f "$AD/build/classes/kotlin/main/META-INF/desktop.kotlin_module" ]; then
  mv "$AD/build/classes/kotlin/main/META-INF/main.kotlin_module" \
     "$AD/build/classes/kotlin/main/META-INF/desktop.kotlin_module"
fi

# 2. rebuild the app jar (classes only — the P3 jar carried no resources,
#    just empty directory entries) and swap it into the app image in place.
# The app jar is the FIRST app.classpath entry in the launcher cfg — never
# glob desktop-*: the image also carries the compose desktop-jvm LIBRARY
# jar, which this script clobbered once (fixed 2026-11-12).
CFG=$IMG/AuthrOS.cfg
APPJAR=$(grep -m1 '^app.classpath' "$CFG" | sed 's/.*\$APPDIR\///')
JARFILE="$IMG/$APPJAR"
rm -f "$JARFILE"
echo "=== swapping app jar: $APPJAR ==="
# jar tool: use the JDK's jar command if present, else python zipfile fallback
if [ -x /tmp/my-project/tools/jdk/bin/jar ]; then
  (cd "$OUT" && mkdir -p composeResources/authrdesktop.desktop.generated.resources && /tmp/my-project/tools/jdk/bin/jar cf "$JARFILE" META-INF composeResources $(ls))
else
  python3 - "$OUT" "$JARFILE" <<'PYEOF'
import os, sys, zipfile
out, jar = sys.argv[1], sys.argv[2]
os.makedirs(os.path.join(out, "composeResources", "authrdesktop.desktop.generated.resources"), exist_ok=True)
os.makedirs(os.path.join(out, "META-INF"), exist_ok=True)
mf = os.path.join(out, "META-INF", "MANIFEST.MF")
if not os.path.exists(mf):
    with open(mf, "w") as f: f.write("Manifest-Version: 1.0\r\n\r\n")
with zipfile.ZipFile(jar, "w", zipfile.ZIP_DEFLATED) as z:
    for root, dirs, files in os.walk(out):
        dirs.sort()
        for d in dirs:
            rel = os.path.relpath(os.path.join(root, d), out)
            z.writestr(rel + "/", "")
        for f in sorted(files):
            p = os.path.join(root, f)
            z.write(p, os.path.relpath(p, out))
PYEOF
fi
echo "=== swapped app jar: $(basename "$JARFILE") ($(du -h "$JARFILE" | cut -f1)) ==="

# 3. bundle the print-interior runtime jars beside the app jar + register
#    them in the launcher cfg (the D-15 bundle half of the dependency)
for j in pdfbox-2.0.33.jar fontbox-2.0.33.jar commons-logging-1.2.jar; do
  cp "$EXTRAS/$j" "$IMG/$j"
done
CFG=$IMG/AuthrOS.cfg
for j in pdfbox-2.0.33.jar fontbox-2.0.33.jar commons-logging-1.2.jar; do
  grep -q "$j" "$CFG" || echo "app.classpath=\$APPDIR/$j" >> "$CFG"
done
echo "=== bundled print runtime jars + cfg entries ==="

# 4. OQ-16 / ADR-0013 bundle half: the epubcheck dist rides the app image so
#    the locator's <app dir>/epubcheck/epubcheck.jar candidate resolves and
#    the packaged app's packages are VERIFIED, not UNVERIFIED.
if [ -d "$EXTRAS/epubcheck-dist/epubcheck-5.2.1" ] && [ ! -f "$IMG/epubcheck/epubcheck.jar" ]; then
  rm -rf "$IMG/epubcheck"
  cp -r "$EXTRAS/epubcheck-dist/epubcheck-5.2.1" "$IMG/epubcheck"
  echo "=== bundled epubcheck dist ($(du -sh "$IMG/epubcheck" | cut -f1)) ==="
fi

# 5. D-16 bundle half: the serif faces the print interior embeds ride the
#    image too (the resolver's app-dir candidates search <app dir>/fonts/).
mkdir -p "$IMG/fonts"
for f in DejaVuSerif.ttf DejaVuSerif-Bold.ttf; do
  [ -f "/usr/share/fonts/truetype/dejavu/$f" ] && cp -n "/usr/share/fonts/truetype/dejavu/$f" "$IMG/fonts/"
done
echo "=== bundled print faces: $(ls "$IMG/fonts" 2>/dev/null | tr '\n' ' ') ==="
echo "=== D15 build done ==="
