#!/usr/bin/env bash
# End-to-end contribution session on this build machine (loopback only, nothing hosted):
#   - the pinned DeProof node agent (scripts/build-node-agent.sh) with its proof backend: the pinned DeProof source's
#     tools/proof_jobs.py, its gnark prover-worker built from that source, and a freshly created owner-pinned sample job;
#   - services/storage-gateway behind the pinned Caddy with a throwaway self-signed certificate (as gateway-proxy-it.sh);
#   - ContributionSessionE2ETest, which pairs with READ_NODE,RUN_PROOF_JOB and runs the app's ContributionSession with
#     the real NodeProofPart and GatewayRoundTripPart, then a second session stopped while ACTIVE.
# Usage: scripts/session-e2e.sh <evidence-dir>
# Exit code is non-zero unless the test ran (not skipped) and passed. Phones are not involved (phone gates: NOT_RUN).
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="${1:?evidence dir}"
mkdir -p "$OUT"
# shellcheck disable=SC1091
source scripts/node-agent.pin
bash scripts/build-node-agent.sh build/node-agent/deproof-node > "$OUT/node-agent-build.txt"
src=build/node-agent/src
( cd "$src/prover-worker" && mkdir -p build && for c in prove verify; do
    GOTOOLCHAIN="$GO_TOOLCHAIN" CGO_ENABLED=0 GOFLAGS=-mod=mod go build -trimpath -buildvcs=false -ldflags='-buildid=' -o "build/$c" "./cmd/$c"; done )
{ echo "deproof revision: $(git -C "$src" rev-parse HEAD)"; sha256sum build/node-agent/deproof-node "$src/prover-worker/build/prove" "$src/prover-worker/build/verify" "$src/tools/proof_jobs.py"; } > "$OUT/binaries.txt"

CADDY_VERSION=2.11.7
CADDY_SHA512=a7a433a1b133efc3c8d10eb0b99d52a24b5ef5c322dc77f5282182b1c0402139ab83f3a99f0c52409df77d20123fb0b523edad8a66d8f5e49136197bf61ef0e7
tools="${EDGEORE_TOOLS:-$HOME/.cache/edgeore-tools}/caddy-$CADDY_VERSION"; caddy="$tools/caddy"
if [ ! -x "$caddy" ]; then
  mkdir -p "$tools"; tgz="$tools/caddy.tar.gz"
  curl -fsSL -o "$tgz" "https://github.com/caddyserver/caddy/releases/download/v$CADDY_VERSION/caddy_${CADDY_VERSION}_linux_amd64.tar.gz"
  echo "$CADDY_SHA512  $tgz" | sha512sum -c - >/dev/null
  tar -xzf "$tgz" -C "$tools" caddy LICENSE
fi
"$caddy" version | grep -q "^v$CADDY_VERSION " || { echo "unexpected caddy version" >&2; exit 1; }

work="$(mktemp -d)"; chmod 700 "$work"
pids=()
cleanup() { for p in "${pids[@]}"; do kill "$p" 2>/dev/null || true; done; rm -rf "$work"; }
trap cleanup EXIT

# The proof tool's Python dependency, pinned by the DeProof source (tools/requirements.txt), in a build-local venv.
venv=build/node-agent/venv
[ -x "$venv/bin/python3" ] || python3 -m venv "$venv"
"$venv/bin/pip" install -q -r "$src/tools/requirements.txt"
export PATH="$PWD/$venv/bin:$PATH"   # the agent runs "python3" from PATH
"$venv/bin/pip" freeze | grep -i '^cryptography==' >> "$OUT/binaries.txt"

# Node agent with an owner-pinned proof job (created fresh; its private key is not kept by proof_jobs.py).
python3 "$src/tools/proof_jobs.py" create "$work/job" > "$work/job-created.json"
port=19845
build/node-agent/deproof-node -state "$work/node" -pair-scopes READ_NODE,RUN_PROOF_JOB -listen "127.0.0.1:$port" \
  -proof-tool "$PWD/$src/tools/proof_jobs.py" -proof-job "$work/job/job.json" -proof-owner "$work/job/owner-public.key" > "$work/node.txt" 2>&1 &
pids+=($!)
for _ in $(seq 1 50); do grep -q "TLS certificate SHA-256" "$work/node.txt" && break; sleep 0.2; done
grep -q "TLS certificate SHA-256" "$work/node.txt" || { cat "$work/node.txt" >&2; exit 4; }
challenge=$(sed -n 's/^Pairing challenge (2 minutes): //p' "$work/node.txt")
code=$(sed -n 's/^Single-use pairing code: //p' "$work/node.txt")
cert=$(sed -n 's/^TLS certificate SHA-256: //p' "$work/node.txt")
grep -v "Single-use pairing code" "$work/node.txt" > "$OUT/node-agent-startup.txt"

# Gateway behind Caddy (throwaway cert and token, mode-700 temp dir). Quota 4 MiB.
openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -keyout "$work/key.pem" -out "$work/cert.pem" \
  -days 2 -subj "/CN=edgeore-session-e2e" -addext "subjectAltName=IP:127.0.0.1" 2>/dev/null
pin_b64="$(openssl x509 -in "$work/cert.pem" -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64)"
token="$(openssl rand -hex 24)"
EDGEORE_STORAGE_TOKEN="$token" EDGEORE_STORAGE_DB="$work/gateway.sqlite3" EDGEORE_STORAGE_MAX_BYTES=4194304 \
  python3 services/storage-gateway/server.py > "$work/gateway.log" 2>&1 & pids+=($!)
proxy=127.0.0.1:18444
EDGEORE_PROXY_ADDR="$proxy" EDGEORE_TLS_CERT="$work/cert.pem" EDGEORE_TLS_KEY="$work/key.pem" \
  "$caddy" run --config services/storage-gateway/Caddyfile.example --adapter caddyfile > "$work/caddy.log" 2>&1 & pids+=($!)
for _ in $(seq 50); do curl -s -o /dev/null --cacert "$work/cert.pem" "https://$proxy/" && break; sleep 0.2; done
echo "sha256/$pin_b64" > "$OUT/throwaway-gateway-cert-spki-pin.txt"

python3 - "$work/it.json" <<PY
import json, sys
json.dump({"nodeEndpoint": "https://127.0.0.1:$port", "nodeCertSha256": "$cert", "challenge": '''$challenge''', "code": "$code",
           "gatewayEndpoint": "https://$proxy", "gatewaySpkiPin": "sha256/$pin_b64", "gatewayToken": "$token",
           "evidenceDir": "$(cd "$OUT" && pwd)"}, open(sys.argv[1], "w"))
PY
chmod 600 "$work/it.json"
results=app/build/test-results/testDebugUnitTest
rm -f "$results"/TEST-com.edgeore.app.ContributionSessionE2ETest.xml
set +e
EDGEORE_SESSION_IT="$work/it.json" ./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.edgeore.app.ContributionSessionE2ETest' > "$work/gradle.txt" 2>&1
status=$?
set -e
grep -E "BUILD|FAILED" "$work/gradle.txt" | tail -3 || true
cp "$results/TEST-com.edgeore.app.ContributionSessionE2ETest.xml" "$OUT/" 2>/dev/null || true
ls "$work/node" | sort > "$OUT/node-state-files.txt"
cp "$work"/node/usage-*.json "$work"/node/contribution-*.json "$OUT/" 2>/dev/null || true
echo "gradle exit: $status"
[ "$status" = 0 ] || exit "$status"
python3 - "$OUT/TEST-com.edgeore.app.ContributionSessionE2ETest.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
s = ET.parse(sys.argv[1]).getroot()
t, f, e, k = (int(s.get(a, 0)) for a in ("tests", "failures", "errors", "skipped"))
print(f"ContributionSessionE2ETest: tests={t} failures={f} errors={e} skipped={k}")
sys.exit(0 if t >= 1 and f == 0 and e == 0 and k == 0 else 5)
PY
