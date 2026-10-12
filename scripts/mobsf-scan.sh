#!/usr/bin/env bash
# Local MobSF static scan of an APK (hardening 12). No secrets: the API key is generated for this one throwaway
# container and never leaves the machine. Nothing is uploaded to any hosted service.
#   scripts/mobsf-scan.sh [APK] [OUT_DIR]
# Needs: docker, curl, python3. Exit 0: scan finished with 0 unreviewed high-severity findings (raw reports retained); 1: high findings; 2: could not run.
set -euo pipefail
cd "$(dirname "$0")/.."
APK="${1:-app/build/outputs/apk/release/app-release-unsigned.apk}"
OUT="${2:-evidence/mobsf/$(git rev-parse --short=12 HEAD)}"
IMAGE="${MOBSF_IMAGE:-opensecurity/mobile-security-framework-mobsf:v4.3.0}"
PORT="${MOBSF_PORT:-18000}"
command -v docker >/dev/null && docker info >/dev/null 2>&1 || { echo "NOT_RUN: docker is not available"; exit 2; }
[ -f "$APK" ] || { echo "NOT_RUN: APK not found: $APK"; exit 2; }
mkdir -p "$OUT"
KEY="$(python3 -c 'import secrets;print(secrets.token_hex(32))')"
NAME="edgeore-mobsf-$$"
docker run -d --rm --name "$NAME" -p "127.0.0.1:$PORT:8000" -e MOBSF_API_KEY="$KEY" "$IMAGE" >/dev/null
trap 'docker stop "$NAME" >/dev/null 2>&1 || true' EXIT
for _ in $(seq 1 90); do curl -sf "http://127.0.0.1:$PORT/" >/dev/null && break; sleep 2; done
H="Authorization: $KEY"
UP="$(curl -sf -H "$H" -F "file=@$APK" "http://127.0.0.1:$PORT/api/v1/upload")"
HASH="$(python3 -c 'import json,sys;print(json.loads(sys.argv[1])["hash"])' "$UP")"
curl -sf -H "$H" -d "hash=$HASH" "http://127.0.0.1:$PORT/api/v1/scan" >/dev/null
curl -sf -H "$H" -d "hash=$HASH" "http://127.0.0.1:$PORT/api/v1/report_json" -o "$OUT/report.json"
curl -sf -H "$H" -d "hash=$HASH" "http://127.0.0.1:$PORT/api/v1/scorecard" -o "$OUT/scorecard.json" || true
sha256sum "$APK" > "$OUT/apk.sha256"
python3 scripts/mobsf-policy.py "$OUT"
