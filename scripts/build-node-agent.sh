#!/usr/bin/env bash
# Builds the pinned DeProof node agent reproducibly and checks its revision and binary hash.
# Usage: bash scripts/build-node-agent.sh [OUT]      (default OUT=build/node-agent/deproof-node)
# Exits non-zero if the source revision or (when pinned) the binary SHA-256 does not match scripts/node-agent.pin.
set -euo pipefail
cd "$(dirname "$0")/.."
# shellcheck disable=SC1091
source scripts/node-agent.pin
out=${1:-build/node-agent/deproof-node}
src=${DEPROOF_SRC:-build/node-agent/src}
mkdir -p "$(dirname "$out")"
if [ ! -d "$src/.git" ]; then
  git clone --quiet "$DEPROOF_REPO" "$src"
fi
git -C "$src" fetch --quiet origin "$DEPROOF_REV" 2>/dev/null || git -C "$src" fetch --quiet origin
git -C "$src" -c advice.detachedHead=false checkout --quiet "$DEPROOF_REV"
rev=$(git -C "$src" rev-parse HEAD)
[ "$rev" = "$DEPROOF_REV" ] || { echo "revision mismatch: $rev != $DEPROOF_REV" >&2; exit 2; }
[ -z "$(git -C "$src" status --porcelain)" ] || { echo "agent source tree is dirty: $src" >&2; exit 2; }
(
  cd "$src/node-agent"
  GOTOOLCHAIN="$GO_TOOLCHAIN" CGO_ENABLED=0 GOOS=linux GOARCH=amd64 GOFLAGS=-mod=mod \
    go build -trimpath -buildvcs=false -ldflags='-buildid=' -o "$OLDPWD/$out" ./cmd/deproof-node
)
sha=$(sha256sum "$out" | cut -d' ' -f1)
echo "agent revision: $rev"
echo "go toolchain:   $(GOTOOLCHAIN="$GO_TOOLCHAIN" go version)"
echo "binary:         $out"
echo "sha256:         $sha"
if [ -n "${DEPROOF_NODE_SHA256_LINUX_AMD64:-}" ] && [ "$sha" != "$DEPROOF_NODE_SHA256_LINUX_AMD64" ]; then
  echo "binary hash mismatch: expected $DEPROOF_NODE_SHA256_LINUX_AMD64" >&2
  exit 3
fi
