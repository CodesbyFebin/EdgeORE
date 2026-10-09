#!/usr/bin/env bash
# Second-machine receipt check: runs the app's own ReceiptVerifier (app/src/main/.../receipts/Receipts.kt) on the JVM.
# No Android device, wallet or network is needed once the CLI is built.
#
# Usage: bash scripts/verify-receipt.sh [--trusted-key <base64 SPKI>]... <receipt-export.json> [more.json ...]
# Prints PASS/FAIL per file with the verifier's reasons. Exit 0 only if every file passes; 1 if any fails;
# 2 usage error; 3 the one-time build failed.
#
# Checks integrity and signatures of the exported records only (digests, device-key signatures, chain links,
# bundle digest, signed checkpoint, wallet Ed25519 signatures). It does NOT query Solana RPC: whether a
# transaction landed on chain is not checked. Use getSignatureStatuses with searchTransactionHistory for that.
set -uo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
bin="$root/verifier-cli/build/install/verifier-cli/bin/verifier-cli"
jar="$root/verifier-cli/build/install/verifier-cli/lib/verifier-cli.jar"

# Build once (first run needs the Gradle/Maven caches or network); rebuild only if the verifier sources changed.
stale=0
if [ ! -x "$bin" ] || [ ! -f "$jar" ]; then
  stale=1
elif [ -n "$(find "$root/app/src/main/java/com/edgeore/app/receipts" "$root/app/src/main/java/com/edgeore/app/crypto" \
          "$root/app/src/main/java/com/edgeore/app/io" "$root/verifier-cli/src/main" "$root/verifier-cli/build.gradle.kts" \
          -type f -newer "$jar" 2>/dev/null | head -1)" ]; then
  stale=1
fi
if [ "$stale" = 1 ]; then
  echo "verify-receipt: building verifier-cli (one time)..." >&2
  (cd "$root" && ./gradlew -q --no-daemon :verifier-cli:installDist) >&2 || { echo "verify-receipt: build failed" >&2; exit 3; }
fi
exec "$bin" "$@"
