#!/usr/bin/env bash
# Runs unit tests, lint and debug assembly; records logs and the APK digest under evidence/.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p evidence
log="evidence/build-$(date -u +%Y%m%dT%H%M%SZ).log"
if ./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug >"$log" 2>&1; then
  sha256sum app/build/outputs/apk/debug/app-debug.apk | tee "$log.sha256"
  echo "Build commands passed. Device and wallet qualification remain NOT_RUN."
else
  echo "Build failed; inspect $log. No qualification granted." >&2
  exit 1
fi
