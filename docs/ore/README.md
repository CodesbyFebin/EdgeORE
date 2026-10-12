# ORE v3 board (read-only) and advisor

Opened from the central **ORE board** icon on Mine (a route like Review, not a sixth nav destination: the least
disruptive place). Label on screen: **Read-only · mainnet observation**.

## Verified sources (checked 2026-10-12)

| Item | Value | Source |
|---|---|---|
| Program id | `oreV3EG1i9BEgiAJ8b177Z2S2rMarzak4NMv1kULvWv` | `api/src/lib.rs` `declare_id!` at regolith-labs/ore [`48c203bd75db3cc45105ec29d8f8db719e5a2263`](https://github.com/regolith-labs/ore/tree/48c203bd75db3cc45105ec29d8f8db719e5a2263) (workspace version 3.8.25) |
| Deployed build | verify.osec.io: `is_verified: true`, commit `48c203bd75db…`, on-chain hash `9dbd2e0d2325…`, last verified 2026-10-02 17:52 UTC | `https://verify.osec.io/status/oreV3EG1i9BEgiAJ8b177Z2S2rMarzak4NMv1kULvWv` |
| Board | PDA `["board"]` = `BrcSxdp1nXFzou1YyDnQJcPNBNHgoypZmTsyKBSLLXzi` (bump 255); 8-byte discriminator 105 + `round_id, start_slot, end_slot, production_cost_ema` (u64) = 40 bytes | `api/src/state/board.rs`, `mod.rs`, `consts.rs` `BOARD_ADDRESS`; last changed in `6763094395330d0eba8464a3279ff0da20f7784d` |
| Round | PDA `["round", round_id LE]`; discriminator 109; `id, deployed[25], mass[25], count[25], slot_hash[32], expires_at, motherlode, rent_payer, rewards[25], total_vaulted, total_returned_sol, total_miners, top_miner` = 952 bytes | `api/src/state/round.rs` |
| Treasury | PDA `["treasury"]` = `45db2FSR4mcXdSVVZbKbwojU6uYDpMyhpEi7cC8nHaWG` (bump 253); discriminator 104; `motherlode` (u64) first = the live motherlode pool | `api/src/state/treasury.rs`, `program/src/reset.rs` (+0.2 ORE per round, paid out with a 1 in 500 chance) |
| Round timing | `end_slot = u64::MAX` until the first deploy; then `start_slot + config.round_slots` | `program/src/deploy.rs`, `reset.rs` |
| ORE decimals | `TOKEN_DECIMALS = 11` | `api/src/consts.rs` |
| Devnet | **Not found.** The source names no devnet deployment and its `localnet.sh` clones mainnet accounts. A direct devnet query was **not possible** from the build host (api.devnet.solana.com closed the TLS handshake; other public devnet RPCs need keys). | — |

Cross-checks on real bytes: the PDAs derived by EdgeORE equal the source constants; the Round account at the derived
PDA holds the same id as `Board.round_id`; account sizes are exactly 40 / 952 / 48 bytes; `Round.expires_at =
end_slot + ONE_DAY_SLOTS` (288,000) in both fixtures.

## What the screen shows

- DEPLOYED (sum of `Round.deployed`, SOL), MOTHERLODE (`Treasury.motherlode`, ORE), TIME (from `Board.end_slot` and the
  RPC's slot; seconds only with a slot rate measured by the RPC's `getRecentPerformanceSamples`, otherwise slots).
- Lite: the total. Pro: the 5×5 grid of `Round.deployed` per square (SOL, rounded down to 3 decimals), with miner counts in
  the accessibility label.
- The RPC, slot and local time of the read. Refreshes every 15 s while open. The public mainnet RPC is configurable (https only).
- **Not shown:** prices (no price source configured), the web board's tile sparkle (its meaning was not verified), any
  amount chips, steppers or deploy control. A visible informational label: "Deploy unavailable in this release (read-only)".

## Read-only by construction

`ore/` uses `AccountReader` (`solana/AccountReader.kt`), whose only methods are `accounts` (getMultipleAccounts) and
`slotRate`. `ReadOnlyAccountRpc` implements it and has no transaction, blockhash or fee method; it shares only the JSON
transport (`JsonRpcHttp`) with the wallet client. There is no ORE instruction builder. `OreReadOnlyArchitectureTest` fails if
the ORE path references signing, transaction, wallet or instruction APIs, or if the reader gains a non-read method.

## Advisor

A chat card on the ORE screen. Backends: the owned-host Ollama path (when a host and model are chosen in AI) or the
on-device LiteRT-LM model (when one is downloaded and selected). No cloud key. Otherwise: "Not configured".

- Input: a system prompt, the snapshot as text, the source-cited facts in `OreAdvisor.FACTS`, and the question. No tools or
  functions are sent; nothing it returns is executed.
- Output: every number not present in that input is replaced with "[number not in snapshot]", and every reply ends with
  "Not financial advice. ORE rounds can lose SOL. EdgeORE cannot deploy."
- Not run end to end on this build host (no Ollama and no on-device model here): its prompt, number guard, notice and
  not-configured states are unit-tested only.

## Fixtures

`app/src/test/resources/ore/mainnet-round-437127.json` and `mainnet-round-437134.json`: raw account bytes from
`https://api.mainnet-beta.solana.com`, with context slot, block time, performance sample and capture time (UTC), written by
`capture.py` in the same folder.
