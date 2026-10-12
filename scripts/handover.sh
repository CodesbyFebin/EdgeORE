#!/usr/bin/env bash
# Reproduces the EdgeORE build and every machine-runnable gate from a fresh clone.
# Prints PASS / FAIL / NOT_RUN per gate. Counts are per suite and are never summed.
# Needs: JDK 17, Android SDK (ANDROID_HOME), python3 >= 3.11. Device/wallet/devnet gates are always NOT_RUN here.
set -uo pipefail
export PYTHONDONTWRITEBYTECODE=1
cd "$(dirname "$0")/.."
declare -a R
gate() { local name=$1; shift; local log="/tmp/handover-${name// /_}.log"
  if "$@" >"$log" 2>&1; then R+=("PASS    $name"); else R+=("FAIL    $name (log: $log)"); fi; }
junit() { python3 - "$1" <<'P'
import glob,sys,xml.etree.ElementTree as E
t=f=e=s=0
for p in glob.glob(sys.argv[1]+'/TEST-*.xml'):
    r=E.parse(p).getroot();t+=int(r.get('tests',0));f+=int(r.get('failures',0));e+=int(r.get('errors',0));s+=int(r.get('skipped',0))
print(f"tests={t} passed={t-f-e-s} failed={f} errors={e} skipped={s}")
P
}
gate "build-policy"            python3 scripts/build-policy.py
gate "build-policy tests"      python3 scripts/test_build_policy.py -v
gate "manifest-checker tests"  python3 scripts/test_check_manifest.py -v
gate "storage gateway tests"   python3 -m unittest discover -s services/storage-gateway -v
gate "offline kit tests"       python3 -m unittest discover -s tools/edgeore-kit -v
gate "android gate (qualify.sh: unit tests, lint, debug APK, release manifest)" bash scripts/qualify.sh
gate "receipt checker tests"   bash gradlew --no-daemon -p tools/receipt-checker test installDist
gate "release R8 build (unsigned)" bash gradlew --no-daemon :app:assembleRelease
if [ "${SESSION_E2E:-0}" = 1 ]; then gate "contribution session e2e" bash scripts/session-e2e.sh "evidence/contribution-session/e2e-$(git rev-parse --short=12 HEAD)"
else R+=("NOT_RUN contribution session e2e (set SESSION_E2E=1; needs node agent + gateway toolchain)"); fi
for g in "device runbook gates 0-10" "wallet (MWA) session on Seeker" "devnet transfer (owner-approved)" \
         "release signing (owner key)" "MobSF scan" "dApp Store publish (publisher KYC)" "demo video"; do
  R+=("NOT_RUN $g (owner step)"); done
echo; echo "== EdgeORE handover gates @ $(git rev-parse --short=12 HEAD) =="
printf '%s\n' "${R[@]}"
echo; echo "-- per-suite counts (separate, not summed) --"
echo "android unit:     $(junit "evidence/build-$(git rev-parse --short=12 HEAD)/junit") (from qualify.sh evidence; the e2e step reruns one test separately)"
echo "receipt checker:  $(junit tools/receipt-checker/build/test-results/test)"
for n in "storage gateway tests" "offline kit tests" "build-policy tests" "manifest-checker tests"; do
  echo "$n: $(grep -E '^(Ran|OK|FAILED)' "/tmp/handover-${n// /_}.log" | tr '\n' ' ')"; done
for a in app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/release/app-release-unsigned.apk; do
  [ -f "$a" ] && echo "sha256 $(sha256sum "$a" | cut -d' ' -f1)  $a ($(stat -c %s "$a") bytes)"; done
if printf "%s\n" "${R[@]}" | grep -q "^FAIL"; then exit 1; fi
