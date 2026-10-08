#!/usr/bin/env bash
# Starts a real DeProof node agent on loopback and runs the Kotlin client integration test against it.
# Usage: DEPROOF_NODE=/path/to/deproof-node bash scripts/node-agent-it.sh
# Build the agent from https://github.com/CodesbyFebin/DeProof--EdgeORE: (cd node-agent && go build -o deproof-node ./cmd/deproof-node)
set -euo pipefail
cd "$(dirname "$0")/.."
: "${DEPROOF_NODE:?set DEPROOF_NODE to the deproof-node binary}"
work=$(mktemp -d)
port=${NODE_IT_PORT:-19843}
"$DEPROOF_NODE" -state "$work/state" -pair-scopes READ_NODE -listen "127.0.0.1:$port" > "$work/out.txt" 2>&1 &
pid=$!
trap 'kill $pid 2>/dev/null || true; rm -rf "$work"' EXIT
for _ in $(seq 1 50); do grep -q "TLS certificate SHA-256" "$work/out.txt" && break; sleep 0.2; done
challenge=$(sed -n 's/^Pairing challenge (2 minutes): //p' "$work/out.txt")
code=$(sed -n 's/^Single-use pairing code: //p' "$work/out.txt")
cert=$(sed -n 's/^TLS certificate SHA-256: //p' "$work/out.txt")
python3 - "$work/it.json" "$port" "$challenge" "$code" "$cert" <<'PY'
import json, sys
path, port, challenge, code, cert = sys.argv[1:]
json.dump({"endpoint": f"https://127.0.0.1:{port}", "challenge": challenge, "code": code, "certSha256": cert}, open(path, "w"))
PY
EDGEORE_NODE_IT="$work/it.json" ./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.edgeore.app.NodeAgentIntegrationTest' -i | grep -E "NODE_IT|PASSED|FAILED|SKIPPED|BUILD" || true
