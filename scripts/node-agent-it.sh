#!/usr/bin/env bash
# Starts a real DeProof node agent on loopback and runs the Kotlin client integration test against it.
# Usage: bash scripts/node-agent-it.sh                       (builds the pinned agent first)
#        DEPROOF_NODE=/path/to/deproof-node bash scripts/node-agent-it.sh
# Exit code is non-zero unless the agent started AND NodeAgentIntegrationTest ran (not skipped) and passed.
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -z "${DEPROOF_NODE:-}" ]; then
  bash scripts/build-node-agent.sh build/node-agent/deproof-node
  DEPROOF_NODE=build/node-agent/deproof-node
fi
echo "agent binary sha256: $(sha256sum "$DEPROOF_NODE" | cut -d' ' -f1)"
work=$(mktemp -d)
port=${NODE_IT_PORT:-19843}
"$DEPROOF_NODE" -state "$work/state" -pair-scopes READ_NODE -listen "127.0.0.1:$port" > "$work/out.txt" 2>&1 &
pid=$!
trap 'kill $pid 2>/dev/null || true; rm -rf "$work"' EXIT
ready=0
for _ in $(seq 1 50); do
  if grep -q "TLS certificate SHA-256" "$work/out.txt"; then ready=1; break; fi
  kill -0 "$pid" 2>/dev/null || break
  sleep 0.2
done
if [ "$ready" != 1 ]; then echo "node agent did not start:" >&2; cat "$work/out.txt" >&2; exit 4; fi
challenge=$(sed -n 's/^Pairing challenge (2 minutes): //p' "$work/out.txt")
code=$(sed -n 's/^Single-use pairing code: //p' "$work/out.txt")
cert=$(sed -n 's/^TLS certificate SHA-256: //p' "$work/out.txt")
python3 - "$work/it.json" "$port" "$challenge" "$code" "$cert" <<'PY'
import json, sys
path, port, challenge, code, cert = sys.argv[1:]
json.dump({"endpoint": f"https://127.0.0.1:{port}", "challenge": challenge, "code": code, "certSha256": cert}, open(path, "w"))
PY
results=app/build/test-results/testDebugUnitTest
rm -f "$results"/TEST-com.edgeore.app.NodeAgentIntegrationTest.xml
status=0
EDGEORE_NODE_IT="$work/it.json" ./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.edgeore.app.NodeAgentIntegrationTest' -i \
  > "$work/gradle.txt" 2>&1 || status=$?
grep -E "NODE_IT|PASSED|FAILED|SKIPPED|BUILD" "$work/gradle.txt" || true
echo "gradle exit: $status"
[ "$status" = 0 ] || exit "$status"
# Passing is not enough: a skipped test also exits zero.
python3 - "$results/TEST-com.edgeore.app.NodeAgentIntegrationTest.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
s = ET.parse(sys.argv[1]).getroot()
t, f, e, k = (int(s.get(a, 0)) for a in ("tests", "failures", "errors", "skipped"))
print(f"NodeAgentIntegrationTest: tests={t} failures={f} errors={e} skipped={k}")
sys.exit(0 if t >= 1 and f == 0 and e == 0 and k == 0 else 5)
PY
