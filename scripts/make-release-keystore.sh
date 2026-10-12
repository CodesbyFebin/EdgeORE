#!/usr/bin/env bash
# Generates the owner's release signing keystore OUTSIDE the repository (hardening 7). Never run by CI or agents.
# Passwords come only from the environment and are never written to disk, argv history or the repo.
#   EDGEORE_STORE_PASS=... [EDGEORE_KEY_PASS=...] scripts/make-release-keystore.sh [~/edgeore-keys/edgeore-release.jks]
# Then build with: EDGEORE_KEYSTORE=<path> EDGEORE_STORE_PASS=... ./gradlew :app:assembleRelease
# Back the keystore up offline. Losing it means the dApp Store listing can never ship an update under this key.
set -euo pipefail
REPO="$(cd "$(dirname "$0")/.." && pwd -P)"
OUT="${1:-$HOME/edgeore-keys/edgeore-release.jks}"
ALIAS="${EDGEORE_KEY_ALIAS:-edgeore}"
DNAME="${EDGEORE_DNAME:-CN=EdgeORE, O=CodesbyFebin}"
: "${EDGEORE_STORE_PASS:?Set EDGEORE_STORE_PASS in the environment (min 12 characters)}"
KEY_PASS="${EDGEORE_KEY_PASS:-$EDGEORE_STORE_PASS}"
[ "${#EDGEORE_STORE_PASS}" -ge 12 ] || { echo "Refused: EDGEORE_STORE_PASS must be at least 12 characters" >&2; exit 2; }
command -v keytool >/dev/null || { echo "Refused: keytool (JDK 17) not on PATH" >&2; exit 2; }
mkdir -p "$(dirname "$OUT")"
ABS="$(cd "$(dirname "$OUT")" && pwd -P)/$(basename "$OUT")"
case "$ABS" in "$REPO"/*) echo "Refused: the keystore must live outside the repository ($REPO)" >&2; exit 2;; esac
[ -e "$ABS" ] && { echo "Refused: $ABS already exists; not overwriting a signing key" >&2; exit 2; }
umask 077
STORE_PASS_ENV="$EDGEORE_STORE_PASS" KEY_PASS_ENV="$KEY_PASS" keytool -genkeypair -v -keystore "$ABS" -storetype PKCS12 \
  -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10000 -dname "$DNAME" \
  -storepass:env STORE_PASS_ENV -keypass:env KEY_PASS_ENV
chmod 600 "$ABS"
echo "Created $ABS (alias $ALIAS). Certificate SHA-256:"
STORE_PASS_ENV="$EDGEORE_STORE_PASS" keytool -list -v -keystore "$ABS" -storepass:env STORE_PASS_ENV -alias "$ALIAS" | grep -E "SHA256:" || true
echo "Next: EDGEORE_KEYSTORE=$ABS EDGEORE_STORE_PASS=... ./gradlew :app:assembleRelease"
