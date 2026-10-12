#!/usr/bin/env bash
# Static Kotlin analysis with the standalone detekt-cli and ktlint jars (hardening 6).
# The detekt Gradle plugin broke compilation here, so neither tool touches the Gradle build or the version catalog.
# Existing findings are recorded in baselines; any NEW finding fails (exit 1).
#   scripts/lint-static.sh                    check against the committed baselines
#   scripts/lint-static.sh --update-baselines rewrite the baselines (review the diff before committing)
# Needs: java (17+), curl, sha256sum. Jars are cached in ${STATIC_TOOLS_DIR:-$HOME/.cache/edgeore-static}.
set -euo pipefail
cd "$(dirname "$0")/.."
DETEKT_VERSION=1.23.7
DETEKT_SHA256=84beded283012cb2b38bcaef4996452fcd6069d2e9ca74b50eaa79e0ad21897e
KTLINT_VERSION=1.5.0
KTLINT_SHA256=a16be01dcc480aab2f55f444b620142152f66e31564b3b9376506d624c28a2ad
DIR="${STATIC_TOOLS_DIR:-$HOME/.cache/edgeore-static}"
mkdir -p "$DIR"
fetch() { # name url sha
  local f="$DIR/$1"
  [ -f "$f" ] || curl -sSfL -o "$f.part" "$2" && { [ -f "$f" ] || mv "$f.part" "$f"; }
  echo "$3  $f" | sha256sum -c --quiet - || { echo "FAIL: checksum mismatch for $1" >&2; rm -f "$f"; exit 2; }
}
fetch "detekt-cli-$DETEKT_VERSION-all.jar" "https://github.com/detekt/detekt/releases/download/v$DETEKT_VERSION/detekt-cli-$DETEKT_VERSION-all.jar" "$DETEKT_SHA256"
fetch "ktlint-$KTLINT_VERSION" "https://github.com/pinterest/ktlint/releases/download/$KTLINT_VERSION/ktlint" "$KTLINT_SHA256"

SRC="app/src/main/java,app/src/test/java,ondevice-llm/src,tools/receipt-checker/src"
DETEKT_BASELINE=config/detekt/baseline.xml
KTLINT_BASELINE=config/ktlint/baseline.xml
detekt() { java -jar "$DIR/detekt-cli-$DETEKT_VERSION-all.jar" --input "$SRC" --build-upon-default-config --config config/detekt/detekt.yml "$@"; }
ktlint() { java -jar "$DIR/ktlint-$KTLINT_VERSION" --relative "app/src/**/*.kt" "ondevice-llm/src/**/*.kt" "tools/receipt-checker/src/**/*.kt" "$@"; }

if [ "${1:-}" = "--update-baselines" ]; then
  detekt --create-baseline --baseline "$DETEKT_BASELINE" >/dev/null || true
  ktlint --baseline="$KTLINT_BASELINE" >/dev/null 2>&1 || true
  rm -f "$KTLINT_BASELINE"; ktlint --reporter=baseline,output="$KTLINT_BASELINE" >/dev/null 2>&1 || true
  echo "Baselines rewritten: $DETEKT_BASELINE $KTLINT_BASELINE"; exit 0
fi
rc=0
if detekt --baseline "$DETEKT_BASELINE"; then echo "PASS detekt (no findings beyond baseline)"; else echo "FAIL detekt: new findings"; rc=1; fi
if ktlint --baseline="$KTLINT_BASELINE"; then echo "PASS ktlint (no findings beyond baseline)"; else echo "FAIL ktlint: new findings"; rc=1; fi
exit $rc
