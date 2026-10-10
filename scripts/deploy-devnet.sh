#!/usr/bin/env bash
# EdgeORE receipt-settlement — DEVNET deployment gate (J-4).
#
# PREPARED, NOT RUN. Do not run this without completing the gate in
# docs/DEPLOY-DEVNET.md. It spends devnet SOL from DEPLOYER_KEYPAIR and makes
# a publicly visible, UPGRADEABLE program deployment on Solana devnet.
#
# Steps: clean clone -> anchor build -> record .so sha256 -> deploy ->
# record program id, deploy slot, upgrade authority -> solana program dump ->
# sha256 comparison of the built .so and the on-chain bytes.
#
# Required environment:
#   EDGEORE_DEPLOY_CONFIRM=devnet   explicit opt-in; anything else aborts
#   DEPLOY_COMMIT=<40-hex sha>      commit to build (must exist on REPO_URL)
#   DEPLOYER_KEYPAIR=<path>         funded devnet keypair; fee payer and
#                                   upgrade authority. Must be outside the repo.
#   PROGRAM_KEYPAIR=<path>          program-id keypair; its pubkey must equal
#                                   declare_id! in the commit. Outside the repo.
# Optional:
#   REPO_URL   (default https://github.com/CodesbyFebin/EdgeORE.git)
#   RPC_URL    (default https://api.devnet.solana.com)
#   OUT_DIR    (default <cwd>/deploy-evidence/<commit12>-<utc>)
set -euo pipefail

DEVNET_GENESIS="EtWTRABZaYq6iMfeYKouRu166VU2xqa1wcaWoxPkrZBG"
SBPF_V3_FEATURE="5cC3foj77CWun58pC51ebHFUWavHWKarWyR5UUik7dnC"
UPGRADEABLE_LOADER="BPFLoaderUpgradeab1e11111111111111111111111"
EXPECT_ANCHOR="1.2.1"
EXPECT_SOLANA="4.3.0"
REPO_URL="${REPO_URL:-https://github.com/CodesbyFebin/EdgeORE.git}"
RPC_URL="${RPC_URL:-https://api.devnet.solana.com}"

die() { echo "ABORT: $*" >&2; exit 1; }
log() { echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] $*"; }

[ "${EDGEORE_DEPLOY_CONFIRM:-}" = "devnet" ] || die "set EDGEORE_DEPLOY_CONFIRM=devnet to confirm (see docs/DEPLOY-DEVNET.md)"
[[ "${DEPLOY_COMMIT:-}" =~ ^[0-9a-f]{40}$ ]] || die "DEPLOY_COMMIT must be a full 40-hex commit sha"
[ -f "${DEPLOYER_KEYPAIR:-}" ] || die "DEPLOYER_KEYPAIR not found"
[ -f "${PROGRAM_KEYPAIR:-}" ] || die "PROGRAM_KEYPAIR not found"
case "$RPC_URL" in *mainnet*) die "refusing a mainnet-looking RPC_URL";; esac

for t in git anchor solana solana-keygen sha256sum python3 readelf; do
  command -v "$t" >/dev/null || die "missing tool: $t"
done
anchor --version | grep -q "$EXPECT_ANCHOR" || die "anchor $EXPECT_ANCHOR required, got: $(anchor --version)"
solana --version | grep -q "$EXPECT_SOLANA" || die "solana $EXPECT_SOLANA required, got: $(solana --version)"

OUT_DIR="${OUT_DIR:-$PWD/deploy-evidence/${DEPLOY_COMMIT:0:12}-$(date -u +%Y%m%dT%H%M%SZ)}"
mkdir -p "$OUT_DIR"
EVID="$OUT_DIR/deploy-record.txt"
exec > >(tee -a "$OUT_DIR/deploy-devnet.log") 2>&1
rec() { echo "$1: $2" >> "$EVID"; echo "  $1: $2"; }

log "0. preflight against $RPC_URL"
GENESIS="$(solana genesis-hash --url "$RPC_URL")"
[ "$GENESIS" = "$DEVNET_GENESIS" ] || die "RPC genesis $GENESIS is not devnet"
solana feature status --url "$RPC_URL" "$SBPF_V3_FEATURE" | tee "$OUT_DIR/sbpf-v3-feature.txt"
grep -q "active since" "$OUT_DIR/sbpf-v3-feature.txt" || die "SBPF v3 feature not active on this cluster"
DEPLOYER="$(solana-keygen pubkey "$DEPLOYER_KEYPAIR")"
PROGRAM_ID="$(solana-keygen pubkey "$PROGRAM_KEYPAIR")"
rec started_utc "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
rec rpc_url "$RPC_URL"
rec genesis_hash "$GENESIS"
rec commit "$DEPLOY_COMMIT"
rec deployer "$DEPLOYER"
rec deployer_balance_before "$(solana balance --url "$RPC_URL" "$DEPLOYER")"
rec program_id_keypair_pubkey "$PROGRAM_ID"

log "1. clean clone of $REPO_URL @ $DEPLOY_COMMIT"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
git clone --quiet --no-local "$REPO_URL" "$WORK/repo"
git -C "$WORK/repo" checkout --quiet --detach "$DEPLOY_COMMIT"
[ "$(git -C "$WORK/repo" rev-parse HEAD)" = "$DEPLOY_COMMIT" ] || die "checkout mismatch"
[ -z "$(git -C "$WORK/repo" status --porcelain)" ] || die "clone not clean"
CALLER_REPO="$(git rev-parse --show-toplevel 2>/dev/null || echo /nonexistent)"
for k in "$DEPLOYER_KEYPAIR" "$PROGRAM_KEYPAIR"; do
  case "$(readlink -f "$k")" in
    "$WORK"/*|"$CALLER_REPO"/*) die "keypair $k is inside a repository";;
  esac
done
ONCHAIN="$WORK/repo/onchain"
DECLARED="$(sed -n 's/^declare_id!("\([1-9A-HJ-NP-Za-km-z]*\)");/\1/p' "$ONCHAIN/programs/receipt-settlement/src/lib.rs")"
rec declare_id "$DECLARED"
[ "$DECLARED" = "$PROGRAM_ID" ] || die "PROGRAM_KEYPAIR ($PROGRAM_ID) != declare_id! ($DECLARED); sync keys in a reviewed commit first"

log "2. anchor build"
( cd "$ONCHAIN" && anchor build ) > "$OUT_DIR/anchor-build.log" 2>&1 || die "anchor build failed (see anchor-build.log)"
SO="$ONCHAIN/target/deploy/receipt_settlement.so"
[ -f "$SO" ] || die "no artifact at $SO"
cp "$SO" "$OUT_DIR/built.so"
SO_SHA="$(sha256sum "$SO" | cut -d' ' -f1)"
SO_LEN="$(stat -c %s "$SO")"
rec built_so_sha256 "$SO_SHA"
rec built_so_bytes "$SO_LEN"
rec built_so_elf_flags "$(readelf -h "$SO" | grep Flags | tr -s ' ')"

log "3. deploy (upgradeable loader; upgrade authority = deployer)"
solana program deploy "$SO" \
  --url "$RPC_URL" \
  --keypair "$DEPLOYER_KEYPAIR" \
  --program-id "$PROGRAM_KEYPAIR" \
  --upgrade-authority "$DEPLOYER_KEYPAIR" \
  --output json > "$OUT_DIR/deploy.json"
cat "$OUT_DIR/deploy.json"
rec deploy_signature "$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1])).get("signature",""))' "$OUT_DIR/deploy.json")"

log "4. record program id, slot, upgrade authority"
solana program show "$PROGRAM_ID" --url "$RPC_URL" --output json > "$OUT_DIR/program-show.json"
for k in programId owner programdataAddress authority lastDeploySlot dataLen lamports; do
  rec "onchain_$k" "$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1])).get(sys.argv[2]))' "$OUT_DIR/program-show.json" "$k")"
done
grep -qx "onchain_programId: $PROGRAM_ID" "$EVID" || die "program id mismatch"
grep -qx "onchain_authority: $DEPLOYER" "$EVID" || die "upgrade authority is not the deployer"
grep -qx "onchain_owner: $UPGRADEABLE_LOADER" "$EVID" || die "not owned by the upgradeable loader"

log "5. solana program dump"
solana program dump "$PROGRAM_ID" "$OUT_DIR/dumped.so" --url "$RPC_URL"
rec dumped_bytes "$(stat -c %s "$OUT_DIR/dumped.so")"
rec dumped_sha256 "$(sha256sum "$OUT_DIR/dumped.so" | cut -d' ' -f1)"

log "6. sha256 comparison"
# The program-data account can be larger than the ELF (e.g. with --max-len);
# the dump then has trailing zero bytes. Compare the first SO_LEN bytes and
# require any remainder to be all zero.
head -c "$SO_LEN" "$OUT_DIR/dumped.so" > "$OUT_DIR/dumped-prefix.so"
PREFIX_SHA="$(sha256sum "$OUT_DIR/dumped-prefix.so" | cut -d' ' -f1)"
TAIL_NONZERO="$(tail -c +"$((SO_LEN + 1))" "$OUT_DIR/dumped.so" | tr -d '\0' | wc -c)"
rec dumped_prefix_sha256 "$PREFIX_SHA"
rec dumped_tail_nonzero_bytes "$TAIL_NONZERO"
if [ "$PREFIX_SHA" = "$SO_SHA" ] && [ "$TAIL_NONZERO" = "0" ]; then
  rec sha256_match PASS
else
  rec sha256_match FAIL
  die "on-chain bytes differ from the built artifact"
fi
rec deployer_balance_after "$(solana balance --url "$RPC_URL" "$DEPLOYER")"
rec finished_utc "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
log "done; evidence in $OUT_DIR. The program is UPGRADEABLE by $DEPLOYER."
