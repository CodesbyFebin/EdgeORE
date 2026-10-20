# Strategy rules: what each preset relies on, and how it was checked

Upstream source: [`regolith-labs/ore`](https://github.com/regolith-labs/ore) at **`48c203bd75db3cc45105ec29d8f8db719e5a2263`** ("no cli", 2026-10-02). This was the newest commit on the default branch when checked on 2026-10-14, and it is the same commit the rest of EdgeORE is pinned to.
Live chain: Solana mainnet-beta, `api.mainnet-beta.solana.com`, read-only `getAccountInfo`, slot ~456,863,956 (2026-10-14). Program `oreV3EG1i9BEgiAJ8b177Z2S2rMarzak4NMv1kULvWv`. The read scripts are in [`tools/strategy-rules-check/`](../../tools/strategy-rules-check/).

Each preset only pre-fills the review. You still review the exact message and sign it in your wallet (MWA). The mainnet cap stays 0.01 SOL, Rounds stays 1, and the risk tick is still required. Nothing deploys automatically or in the background. **Every square has the same 1-in-25 chance. No preset raises your odds or promises profit.**

| Rule | Source (at `48c203bd`) | Live check | Status |
|---|---|---|---|
| **Solo vs split tiles.** `distribution_mask()` takes keccak(round id, little-endian) and runs a Fisher-Yates shuffle. The first 10 indices become solo tiles (bit = 1); the other 15 are split. `is_split_reward(w)` is true when bit w is 0. reset.rs sets `top_miner = SPLIT_ADDRESS` when the winning tile is split. | `api/src/state/round.rs` (`distribution_mask`, `is_split_reward`); `program/src/reset.rs` | Rounds 440338–440347 (10 consecutive settled rounds): winning square from `slot_hash`, then the mask. Split ⇔ `top_miner == SpLiT1111…12` held in **10/10**. | **Verified** |
| **Motherlode.** The pool is `Treasury.motherlode` (grams, 11 decimals). reset.rs adds up to 0.2 ORE per round. It is hit when `rng.reverse_bits() % 500 == 0`; then `Round.motherlode` gets the pool and the pool resets to 0. | `api/src/state/treasury.rs`; `api/src/state/round.rs` `did_hit_motherlode`; `program/src/reset.rs` | `Treasury.motherlode` = 1,640,000,000,000 grams (164 ORE) at the read. `Round.motherlode` = 0 in all 10 rounds (no hit). | **Verified** that the field exists and how it is exposed. The 1-in-500 rate comes from source only; no hit was observed. |
| **Round timing.** `Board.end_slot` is `u64::MAX` until the first deploy; then `end_slot = start_slot + Config.round_slots`. Deploy needs `start_slot ≤ slot < end_slot`. Reset can run from `end_slot + intermission_slots`. | `api/src/state/board.rs`; `api/src/state/config.rs`; `program/src/deploy.rs`; `program/src/reset.rs` | Board round 440348: `end_slot − start_slot = 240`. `Config.round_slots = 240`, `intermission_slots = 48`. | **Verified** |
| **Losing-square refund vs fees.** `calculate_fees`: on every square with SOL, the admin fee is `max(deployed/100, 1)`. On non-winning squares there is also a protocol fee `max((deployed − admin)/10, 1)`. Checkpoint returns the rest of a losing square to its miners, pro rata, and 99% of the winning square. | `api/src/state/round.rs` `calculate_fees`; `program/src/checkpoint.rs`; `program/src/reset.rs` | `Round.total_vaulted == protocol_fee` and `total_returned_sol == total − admin − protocol` in **10/10** rounds. | **Verified** |

So losing-square SOL is refunded minus a 10% fee, plus a 1% admin fee: about 10.9% of it is taken as fees. See [`tile-insight-ev.md`](tile-insight-ev.md).

## Presets

| # | Preset | Uses rule | Ships |
|---|---|---|---|
| 1 | **Solo Hunter**: pre-fills the 10 ★ solo tiles of the live round (`distribution_mask(round id)`) | solo/split | Enabled |
| 2 | **Quiet Squares**: the 3 squares with the least SOL on the live board read | fees (for the trade-off text) | Enabled |
| 3 | **Motherlode Watch**: the review opens only while the live `Treasury.motherlode` ≥ your threshold; otherwise "Sit out" | motherlode | Enabled |
| 4 | **Split Sweep**: all 15 split tiles, `floor(cap / 15)` lamports each, total ≤ 0.01 SOL | solo/split, fees | Enabled |
| 5 | **Crew Blanket**: uses the existing verified Crew plan. It needs full 25-square coverage with no overlap; each member reviews and signs their own share | crew plan | Enabled |
| 6 | **Late Look**: an in-app reminder while EdgeORE is open, when ≤ 40 slots are left (from `Board.end_slot` and the measured slot rate), to re-check crowding. No auto-sign and no background work | round timing | Enabled |

Code: [`StrategyPlaybook.kt`](../../app/src/main/java/com/edgeore/app/edge/StrategyPlaybook.kt). Tests: [`StrategyPlaybookTest.kt`](../../app/src/test/java/com/edgeore/app/edge/StrategyPlaybookTest.kt).
A preset whose rule is marked unverified in `StrategyRules.Rule` ships disabled, labelled "Unverified rule". None are unverified at `48c203bd`. If the protocol guard reports a changed program, re-check this page.
