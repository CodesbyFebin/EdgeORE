#!/usr/bin/env bash
# Integration run: services/storage-gateway behind a real Caddy HTTPS proxy with a throwaway self-signed certificate,
# exercised with curl and with the app's OwnedGatewayBackupProvider (SPKI-pinned) from the JVM test suite.
# Everything runs on loopback on this machine. This is a local test harness, not a hosted deployment.
#
# Usage: scripts/gateway-proxy-it.sh [evidence-dir]
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="${1:-}"
CADDY_VERSION=2.11.7
# SHA-512 of caddy_2.11.7_linux_amd64.tar.gz as listed in the release's caddy_2.11.7_checksums.txt.
CADDY_SHA512=a7a433a1b133efc3c8d10eb0b99d52a24b5ef5c322dc77f5282182b1c0402139ab83f3a99f0c52409df77d20123fb0b523edad8a66d8f5e49136197bf61ef0e7
GATEWAY_PORT=19844
PROXY_ADDR=127.0.0.1:18443
QUOTA=4096

tools="${EDGEORE_TOOLS:-$HOME/.cache/edgeore-tools}/caddy-$CADDY_VERSION"
caddy="$tools/caddy"
if [ ! -x "$caddy" ]; then
  mkdir -p "$tools"
  tgz="$tools/caddy.tar.gz"
  curl -fsSL -o "$tgz" "https://github.com/caddyserver/caddy/releases/download/v$CADDY_VERSION/caddy_${CADDY_VERSION}_linux_amd64.tar.gz"
  echo "$CADDY_SHA512  $tgz" | sha512sum -c - >/dev/null
  tar -xzf "$tgz" -C "$tools" caddy LICENSE
fi
"$caddy" version | grep -q "^v$CADDY_VERSION " || { echo "unexpected caddy version" >&2; exit 1; }

work="$(mktemp -d)"; chmod 700 "$work"
gw_pid=""; caddy_pid=""
cleanup() { [ -n "$caddy_pid" ] && kill "$caddy_pid" 2>/dev/null; [ -n "$gw_pid" ] && kill "$gw_pid" 2>/dev/null; rm -rf "$work"; }
trap cleanup EXIT

openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -keyout "$work/key.pem" -out "$work/cert.pem" \
  -days 2 -subj "/CN=edgeore-gateway-it" -addext "subjectAltName=IP:127.0.0.1" 2>/dev/null
pin_b64="$(openssl x509 -in "$work/cert.pem" -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64)"
token="$(openssl rand -hex 24)"   # 48 characters; lives only in $work and in the child processes' environment

EDGEORE_STORAGE_TOKEN="$token" EDGEORE_STORAGE_DB="$work/gateway.sqlite3" EDGEORE_STORAGE_MAX_BYTES="$QUOTA" \
  python3 services/storage-gateway/server.py & gw_pid=$!
EDGEORE_PROXY_ADDR="$PROXY_ADDR" EDGEORE_TLS_CERT="$work/cert.pem" EDGEORE_TLS_KEY="$work/key.pem" \
  "$caddy" run --config services/storage-gateway/Caddyfile.example --adapter caddyfile >"$work/caddy.log" 2>&1 & caddy_pid=$!
for _ in $(seq 50); do curl -s -o /dev/null --cacert "$work/cert.pem" "https://$PROXY_ADDR/" && break; sleep 0.2; done

pass=0; fail=0
check() { # name expected actual
  if [ "$2" = "$3" ]; then echo "PASS $1 (expected $2, got $3)"; pass=$((pass+1)); else echo "FAIL $1 (expected $2, got $3)"; fail=$((fail+1)); fi
}
url="https://$PROXY_ADDR"
c() { curl -s -o "$work/out" -w '%{http_code}' --cacert "$work/cert.pem" --pinnedpubkey "sha256//$pin_b64" "$@"; }
head -c 1024 /dev/urandom > "$work/obj"; d="$(sha256sum "$work/obj" | cut -d' ' -f1)"

echo "caddy $("$caddy" version | cut -d' ' -f1), gateway quota $QUOTA bytes, proxy $url (loopback only)"
check "TLS refused without trusting the self-signed cert" 000 "$(curl -s -o /dev/null -w '%{http_code}' "$url/v1/capabilities" || true)"
check "TLS refused with a wrong SPKI pin" 000 "$(curl -s -o /dev/null -w '%{http_code}' --cacert "$work/cert.pem" --pinnedpubkey "sha256//$(head -c32 /dev/zero | base64)" "$url/v1/capabilities" || true)"
check "auth: no token -> 401" 401 "$(c "$url/v1/capabilities")"
check "auth: wrong token -> 401" 401 "$(c -H "Authorization: Bearer $(printf 'w%.0s' $(seq 40))" "$url/v1/capabilities")"
H=(-H "Authorization: Bearer $token")
check "capabilities -> 200" 200 "$(c "${H[@]}" "$url/v1/capabilities")"
check "upload -> 200" 200 "$(c "${H[@]}" -X PUT -H "X-Ciphertext-SHA256: $d" -H 'Content-Type: application/octet-stream' --data-binary @"$work/obj" "$url/v1/jobs/curl-1")"
check "status -> 200 acknowledged" acknowledged "$(c "${H[@]}" "$url/v1/jobs/curl-1" >/dev/null; python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["state"])' "$work/out")"
check "download -> 200" 200 "$(c "${H[@]}" "$url/v1/objects/$d")"
check "download bytes match" "$d" "$(sha256sum "$work/out" | cut -d' ' -f1)"
head -c 4000 /dev/urandom > "$work/big"; bd="$(sha256sum "$work/big" | cut -d' ' -f1)"
check "quota exceeded -> 409" 409 "$(c "${H[@]}" -X PUT -H "X-Ciphertext-SHA256: $bd" --data-binary @"$work/big" "$url/v1/jobs/curl-2")"
head -c 8001025 /dev/zero > "$work/huge"
check "body over 8,001,024 bytes -> 413 from the proxy" 413 "$(c "${H[@]}" -X PUT -H "X-Ciphertext-SHA256: $(sha256sum "$work/huge" | cut -d' ' -f1)" --data-binary @"$work/huge" "$url/v1/jobs/curl-3")"
check "undocumented path -> 404 at the proxy" 404 "$(c "${H[@]}" "$url/admin")"
check "cancel -> 200" 200 "$(c "${H[@]}" -X POST -d '' "$url/v1/jobs/curl-1/cancel")"
check "download after cancel -> 404" 404 "$(c "${H[@]}" "$url/v1/objects/$d")"
echo "curl checks: $pass passed, $fail failed"

# JVM client: the app's provider with the SPKI pin (fresh database for a clean quota).
kill "$gw_pid"; wait "$gw_pid" 2>/dev/null || true
rm -f "$work"/gateway.sqlite3*
EDGEORE_STORAGE_TOKEN="$token" EDGEORE_STORAGE_DB="$work/gateway.sqlite3" EDGEORE_STORAGE_MAX_BYTES="$QUOTA" \
  python3 services/storage-gateway/server.py & gw_pid=$!
sleep 0.5
printf '{"endpoint":"%s","spkiPin":"sha256/%s","token":"%s","quotaBytes":%s}\n' "$url" "$pin_b64" "$token" "$QUOTA" > "$work/it.json"
chmod 600 "$work/it.json"
set +e
EDGEORE_GATEWAY_IT="$work/it.json" ./gradlew --no-daemon -q :app:testDebugUnitTest --tests 'com.edgeore.app.GatewayProxyIntegrationTest' >"$work/gradle.log" 2>&1
jvm=$?
set -e
python3 scripts/junit-summary.py app/build/test-results/testDebugUnitTest | head -2
echo "JVM provider test exit: $jvm"
if [ -n "$OUT" ]; then
  mkdir -p "$OUT"
  cp app/build/test-results/testDebugUnitTest/TEST-com.edgeore.app.GatewayProxyIntegrationTest.xml "$OUT/" 2>/dev/null || true
  # The certificate is throwaway and its key is deleted with $work; the SPKI pin is recorded for traceability only.
  echo "sha256/$pin_b64" > "$OUT/throwaway-cert-spki-pin.txt"
fi
[ "$fail" -eq 0 ] && [ "$jvm" -eq 0 ]
