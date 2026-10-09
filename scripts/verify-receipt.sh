#!/usr/bin/env bash
# Offline verification after dependency bootstrap; never queries Solana or modifies an export.
set -euo pipefail
root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
if [[ $# -ne 1 && $# -ne 3 ]]; then
  echo "Usage: $0 EXPORT.json [--trusted-key DEVICE-SPKI.der]" >&2
  exit 2
fi
if [[ $# -eq 3 && $2 != --trusted-key ]]; then
  echo "Expected --trusted-key DEVICE-SPKI.der" >&2
  exit 2
fi
# No Android SDK required. Versions match the repository's existing dependencies.
# Build diagnostics stay on stderr; a bootstrap failure is not a verification rejection.
if ! bash "$root/gradlew" -p "$root/tools/receipt-checker" --no-daemon --console=plain installDist >&2; then
  echo "CHECKER ERROR: dependency bootstrap/build failed; receipt not checked" >&2
  exit 2
fi
exec "$root/tools/receipt-checker/build/install/edgeore-receipt-checker/bin/edgeore-receipt-checker" "$@"
