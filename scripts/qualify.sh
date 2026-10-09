#!/usr/bin/env bash
# Build gate: clean unit tests + lint + debug APK + instrumented-test APK, plus the receipt verifier CLI (tests and an
# end-to-end PASS/FAIL check on a generated export), with evidence under evidence/build-<commit>/.
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
./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest \
  :verifier-cli:test :verifier-cli:installDist :verifier-cli:demoReceipt >"$log" 2>&1
status=$?
rm -rf "$out/junit" && mkdir -p "$out/junit"
cp app/build/test-results/testDebugUnitTest/TEST-*.xml "$out/junit/" 2>/dev/null || true
rm -rf "$out/junit-verifier-cli" && mkdir -p "$out/junit-verifier-cli"
cp verifier-cli/build/test-results/test/TEST-*.xml "$out/junit-verifier-cli/" 2>/dev/null || true
apk=app/build/outputs/apk/debug/app-debug.apk
bt=$(ls -d "${ANDROID_HOME:-$ANDROID_SDK_ROOT}"/build-tools/* 2>/dev/null | sort -V | tail -1)
{
  echo "# Build gate for $commit"
  echo
  echo "JVM/build results only. No phone, wallet, owned AI host or on-device node session is part of this gate."
  echo
  echo "- Command: \`./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :verifier-cli:test :verifier-cli:installDist :verifier-cli:demoReceipt\`"
  echo "- Gradle exit: EXIT=$status"
  echo "- Started: $started · Finished: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "- Toolchain: $(java -version 2>&1 | head -1); $(grep -o 'gradle-[0-9.]*' gradle/wrapper/gradle-wrapper.properties | head -1)"
  echo
  echo "## Unit tests (JUnit XML in \`junit/\`, parsed by attribute name)"
  echo '```'
  python3 scripts/junit-summary.py "$out/junit"
  echo '```'
  echo
  echo "## Receipt verifier CLI tests (JUnit XML in \`junit-verifier-cli/\`)"
  echo '```'
  python3 scripts/junit-summary.py "$out/junit-verifier-cli"
  echo '```'
  echo
  echo "## Lint (debug)"
  echo '```'
  if [ -f app/build/reports/lint-results-debug.txt ]; then cp app/build/reports/lint-results-debug.txt "$out/lint-debug.txt"; head -5 "$out/lint-debug.txt"; else echo "no lint report (lint did not run)"; fi
  echo '```'
  if [ -f "$apk" ]; then
    echo
    echo "## Debug APK"
    echo '```'
    [ -n "$bt" ] && "$bt/aapt" dump badging "$apk" | head -1
    echo "sha256 $(sha256sum "$apk" | cut -d' ' -f1)"
    echo "size   $(stat -c %s "$apk") bytes"
    stamp=$(unzip -p "$apk" 'classes*.dex' | strings | grep -E '^[0-9a-f]{12}(-dirty)?$' | sort -u | tr '\n' ' ')
    echo "BuildConfig.GIT_COMMIT = ${stamp:-not found}"
    [ -n "$bt" ] && echo "signer $("$bt/apksigner" verify --print-certs "$apk" | sed -n 's/.*certificate SHA-256 digest: //p' | head -1) (Android debug key unless you configured release signing)"
    echo '```'
  fi
} > "$out/summary.md"
if [ "$status" = 0 ]; then
  # End-to-end: the shell entry point must PASS the generated export and FAIL a copy with one body byte changed.
  demo=verifier-cli/build/demo/receipt-export.json
  {
    off=$(grep -bo 'Signed transfer' "$demo" | head -1 | cut -d: -f1)
    cp "$demo" "$out/verifier-e2e-original.json"
    cp "$demo" "$out/verifier-e2e-tampered.json"
    printf 'X' | dd of="$out/verifier-e2e-tampered.json" bs=1 seek=$((off + 2)) conv=notrunc status=none
    echo "\$ bash scripts/verify-receipt.sh $out/verifier-e2e-original.json"
    bash scripts/verify-receipt.sh "$out/verifier-e2e-original.json"; e1=$?
    echo "exit $e1"
    echo "\$ bash scripts/verify-receipt.sh $out/verifier-e2e-tampered.json   # one byte at offset $((off + 2)) changed"
    bash scripts/verify-receipt.sh "$out/verifier-e2e-tampered.json"; e2=$?
    echo "exit $e2"
    cmp -s "$demo" "$out/verifier-e2e-original.json" && echo "original unchanged: yes" || echo "original unchanged: NO"
  } >"$out/verifier-e2e.log" 2>&1
  if [ "${e1:-9}" = 0 ] && [ "${e2:-9}" = 1 ]; then v=0; else v=1; status=1; fi
  echo -e "\n## Receipt verifier end-to-end (generated export, not a real transfer)\noriginal exit ${e1:-?} (want 0), tampered copy exit ${e2:-?} (want 1): EXIT=$v (log: verifier-e2e.log)" >> "$out/summary.md"
fi
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
