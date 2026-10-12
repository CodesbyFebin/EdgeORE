#!/usr/bin/env bash
# Hardening 1 — owner-run devnet gate: deploy receipt-settlement, then one sample devnet SOL transfer.
# PREPARED, NOT RUN. Needs explicit owner approval and a funded devnet wallet. Never targets mainnet.
# Runbook: docs/DEVNET-RUNBOOK.md. The deploy step delegates to scripts/deploy-devnet.sh (docs/DEPLOY-DEVNET.md).
#
#   EDGEORE_DEPLOY_CONFIRM=devnet DEPLOY_COMMIT=<sha> DEPLOYER_KEYPAIR=<path> PROGRAM_KEYPAIR=<path> \
#   EDGEORE_TRANSFER_CONFIRM=devnet TRANSFER_TO=<devnet pubkey> [TRANSFER_SOL=0.001] scripts/devnet-deploy.sh [deploy|transfer|all]
set -euo pipefail
cd "$(dirname "$0")/.."
STEP="${1:-all}"
RPC_URL="${RPC_URL:-https://api.devnet.solana.com}"
DEVNET_GENESIS="EtWTRABZaYq6iMfeYKouRu166VU2xqa1wcaWoxPkrZBG"
OUT_DIR="${OUT_DIR:-$PWD/deploy-evidence/devnet-$(date -u +%Y%m%dT%H%M%SZ)}"
case "$RPC_URL" in *mainnet*) echo "Refused: mainnet-looking RPC_URL" >&2; exit 2;; esac
mkdir -p "$OUT_DIR"

deploy() { OUT_DIR="$OUT_DIR/deploy" RPC_URL="$RPC_URL" bash scripts/deploy-devnet.sh; }

transfer() {
  [ "${EDGEORE_TRANSFER_CONFIRM:-}" = devnet ] || { echo "Refused: set EDGEORE_TRANSFER_CONFIRM=devnet after owner approval" >&2; exit 2; }
  : "${DEPLOYER_KEYPAIR:?funded devnet keypair (outside the repo)}"; : "${TRANSFER_TO:?destination devnet pubkey}"
  local sol="${TRANSFER_SOL:-0.001}"
  python3 -c "import sys; v=float(sys.argv[1]); sys.exit(0 if 0 < v <= 0.01 else 1)" "$sol" || { echo "Refused: sample transfer is capped at 0.01 SOL" >&2; exit 2; }
  case "$(realpath "$DEPLOYER_KEYPAIR")" in "$(pwd -P)"/*) echo "Refused: keypair inside the repo" >&2; exit 2;; esac
  [ "$(solana genesis-hash --url "$RPC_URL")" = "$DEVNET_GENESIS" ] || { echo "Refused: RPC is not devnet" >&2; exit 2; }
  local sig
  sig="$(solana transfer --url "$RPC_URL" --keypair "$DEPLOYER_KEYPAIR" --allow-unfunded-recipient --output json "$TRANSFER_TO" "$sol" | python3 -c 'import json,sys;print(json.load(sys.stdin)["signature"])')"
  solana confirm --url "$RPC_URL" -v "$sig" > "$OUT_DIR/transfer-confirm.txt" 2>&1 || true
  { echo "signature: $sig"; echo "explorer: https://explorer.solana.com/tx/$sig?cluster=devnet"; echo "amount_sol: $sol"; echo "to: $TRANSFER_TO"; echo "rpc: $RPC_URL"; } | tee "$OUT_DIR/transfer-record.txt"
  echo "This is a CLI sample transfer, not an in-app (MWA) transfer. The in-app gate is in docs/DEVNET-RUNBOOK.md step 4."
}

case "$STEP" in deploy) deploy;; transfer) transfer;; all) deploy; transfer;; *) echo "usage: $0 [deploy|transfer|all]" >&2; exit 2;; esac
echo "Evidence: $OUT_DIR"
