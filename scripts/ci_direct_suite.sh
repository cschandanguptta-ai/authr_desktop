#!/bin/bash
# CI entry point (v3.0 D-07): the whole no-Gradle direct-route test suite in
# one command, portable to any machine with JDK 21 + curl (GitHub Actions
# ubuntu-latest included). The app image + compiled main classes come from
# the checkout (desktop/build is deliberately tracked — the load-bearing
# classpath of the direct routes, see AGENTS.md); the toolchain jars land in
# .build-extras via the reproducible fetch script.
#
# Env overrides (all optional; sandbox defaults shown):
#   AUTHR_BASE   /home/z/my-project        repo root (checkout dir on CI)
#   AUTHR_JAVA   /tmp/my-project/tools/jdk/bin/java   a JDK 21 java binary
#   AUTHR_EXTRAS $AUTHR_BASE/.build-extras toolchain jar cache
set -euo pipefail

BASE="${AUTHR_BASE:-/home/z/my-project}"
export AUTHR_BASE="$BASE"
if [ -z "${AUTHR_JAVA:-}" ]; then
  if [ -x /tmp/my-project/tools/jdk/bin/java ]; then
    export AUTHR_JAVA=/tmp/my-project/tools/jdk/bin/java
  else
    export AUTHR_JAVA="$(command -v java)"
  fi
fi
echo "CI direct suite — BASE=$BASE JAVA=$AUTHR_JAVA"

# 1. toolchain (idempotent; cached by CI on the fetch script's hash)
bash "$BASE/scripts/p5_fetch_toolchain.sh"

# 2. the direct test routes, in program order. Each script compiles its
#    slice against the tracked main classes and runs it via JUnitCore;
#    set -e fails the job on the first red route.
echo "=== P4 audio route ==="
bash "$BASE/scripts/p4_direct_test.sh" | tail -3
echo "=== P5 publishing route ==="
bash "$BASE/scripts/p5_direct_test.sh" | tail -3
echo "=== P6 annotator route ==="
bash "$BASE/scripts/p6_direct_test.sh" | tail -3
echo "=== D-13 workbench/wiring/policy route ==="
bash "$BASE/scripts/d13_direct_test.sh" | tail -3
echo "=== D-15 publishing workbench route ==="
bash "$BASE/scripts/d15_direct_test.sh" | tail -3
echo "=== 2026-11-12 audit-fix route (persona registry, honest telemetry) ==="
bash "$BASE/scripts/audit_direct_test.sh" | tail -3

echo "=== ADR-0018 pipeline route (authoring pipeline, editorial memory, gates) ==="
bash "$BASE/scripts/adr0018_direct_test.sh" | tail -3

echo "=== v3.0 port S0 route (Room v17 engine tables + voice feature spike) ==="
bash "$BASE/scripts/s0_direct_test.sh" | tail -3

echo "=== v3.0 port S1 route (voice engine, drift tracking, stage-7 wiring) ==="
bash "$BASE/scripts/s1_direct_test.sh" | tail -3

echo "=== v3.0 port S2 route (context orchestrator, summary cache, spend ledger) ==="
bash "$BASE/scripts/s2_direct_test.sh" | tail -3

echo "CI DIRECT SUITE GREEN"
