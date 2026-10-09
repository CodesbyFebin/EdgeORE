#!/usr/bin/env bash
# Build gate: clean unit tests + lint + debug APK + instrumented-test APK, with evidence under evidence/build-<commit>/.
# Optional: SCREENS=1 also verifies the screenshot suite; NODE_IT=1 also runs the live node-agent integration test.
# Exit code is Gradle's (or the first failing optional step). Nothing here is a device result.
set -uo pipefail
cd "$(dirname "$0")/.."
commit=$(git rev-parse --short=12 HEAD)
[ -z "$(git status --porcelain --untracked-files=no)" ] || commit="$commit-dirty"
out="evidence/build-$commit"
mkdir -p "$out"
log="$out/build.log"
started=$(date -u +%Y-%m-%dT%H:%M:%SZ)
./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest >"$log" 2>&1
status=$?
rm -rf "$out/junit" && mkdir -p "$out/junit"
cp app/build/test-results/testDebugUnitTest/TEST-*.xml "$out/junit/" 2>/dev/null || true
apk=app/build/outputs/apk/debug/app-debug.apk
bt=$(ls -d "${ANDROID_HOME:-$ANDROID_SDK_ROOT}"/build-tools/* 2>/dev/null | sort -V | tail -1)
{
  echo "# Build gate for $commit"
  echo
  echo "JVM/build results only. No phone, wallet, owned AI host or on-device node session is part of this gate."
  echo
  echo "- Command: \`./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest\`"
  echo "- Gradle exit: EXIT=$status"
  echo "- Started: $started · Finished: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "- Toolchain: $(java -version 2>&1 | head -1); $(grep -o 'gradle-[0-9.]*' gradle/wrapper/gradle-wrapper.properties | head -1)"
  echo
  echo "## Unit tests (JUnit XML in \`junit/\`, parsed by attribute name)"
  echo '```'
  python3 scripts/junit-summary.py "$out/junit"
  echo '```'
  echo
  echo "## Lint (debug)"
  echo '```'
  grep -E "No issues found|errors?, [0-9]+ warnings?|Lint found" "$log" | head -3 || echo "lint result line not found in log"
  echo '```'
  if [ -f "$apk" ]; then
    echo
    echo "## Debug APK"
    echo '```'
    [ -n "$bt" ] && "$bt/aapt" dump badging "$apk" | head -1
    echo "sha256 $(sha256sum "$apk" | cut -d' ' -f1)"
    echo "size   $(stat -c %s "$apk") bytes"
    echo "BuildConfig.GIT_COMMIT = $(unzip -p "$apk" 'classes*.dex' | strings | grep -m1 -E '^[0-9a-f]{12}(-dirty)?$' || echo 'not found')"
    [ -n "$bt" ] && echo "signer $("$bt/apksigner" verify --print-certs "$apk" | sed -n 's/.*certificate SHA-256 digest: //p' | head -1) (Android debug key unless you configured release signing)"
    echo '```'
  fi
} > "$out/summary.md"
if [ "$status" = 0 ] && [ "${SCREENS:-0}" = 1 ]; then
  ./gradlew --no-daemon :app:verifyRoborazziDebug -Pscreens >"$out/screens-verify.log" 2>&1; status=$?
  echo -e "\n## Screenshot suite\nverifyRoborazziDebug exit: EXIT=$status" >> "$out/summary.md"
fi
if [ "$status" = 0 ] && [ "${NODE_IT:-0}" = 1 ]; then
  bash scripts/node-agent-it.sh >"$out/node-agent-it.log" 2>&1; status=$?
  echo -e "\n## Live node agent\nscripts/node-agent-it.sh exit: EXIT=$status" >> "$out/summary.md"
fi
cat "$out/summary.md"
exit "$status"
