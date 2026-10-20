# M1: ORE v3 ground truth (checked 13 Oct 2026)

Sources: regolith-labs/ore (master = `48c203bd75db3cc45105ec29d8f8db719e5a2263`, workspace version 3.8.25, homepage
ore.com), plus read-only mainnet reads (`getAccountInfo` / `getMultipleAccounts`) from `api.mainnet-beta.solana.com`.
The captures are in `app/src/test/resources/ore/mainnet-protocol-capture.json` and `mainnet-settled-rounds.json`.

Attribution: the source is published by the GitHub organisation **regolith-labs** (Regolith Labs). Neither the repository
nor its Cargo metadata uses the name "ORE Supply", so EdgeORE does not use it.

| Item | Value | How it was established |
|---|---|---|
| Program id | `oreV3EG1i9BEgiAJ8b177Z2S2rMarzak4NMv1kULvWv` | `api/src/lib.rs`; the account is owned by BPFLoaderUpgradeable |
| ProgramData | `GXa6JV9AwsccP3hxvKFcZGp4w3MMtf7PJ6HYuTSyokfJ`, last deployed at slot **452,682,055** | ProgramData header |
| Upgrade authority | `J5K5tWj3nKfxuSkAJ25WTMf4u5EsxJRfUoRKKxgrfFGV`, so the program **is upgradeable** | ProgramData header |
| Source ↔ deployed binary | 48c203bd is the verified build | From the earlier verify.osec.io report (12 Oct). Not re-checked in this pass |
| Board | 40 bytes, disc 105: round_id, start_slot, end_slot, production_cost_ema | `board.rs`; live size matches |
| Round | 8 + 1,096 bytes, disc 109 | `round.rs`; live size matches |
| Config `9c9X7aDR…S4Awy` | disc 101; admin.fee_rate = **100**, protocol.fee_rate = **1000** (bps) | Live read |
| Fees actually applied | 1% admin on every square, plus 10% of the remainder on losing squares, **hardcoded** in `calculate_fees` / `checkpoint.rs`. Config fee_rate is not read by deploy/reset/checkpoint at this commit | Source. On five live rounds `total_vaulted` equals the formula exactly |
| Losing-square SOL | **Refunded minus a 10% fee, plus a 1% admin fee**: about 89.1% comes back, 10.9% goes in fees | Source and live totals |
| Split vs solo | `distribution_mask(round_id)` gives 10 solo and 15 split tiles, knowable before the round ends | Source; split marker matched on all five live rounds |
| Emission per round | `rewards[0] = min(1 ORE, MAX_SUPPLY − supply)` and +0.2 ORE to the motherlode pool | `reset.rs`; `rewards[0]` = 1 ORE on all five live rounds |
| ORE decimals | 11 (`TOKEN_DECIMALS`), so 10^11 base units per ORE | `consts.rs` |
| Winner square recomputable? | **Yes, from `slot_hash`**: rng = XOR of its 4 u64 words, winner = rng % 25. Round has no separate winner field. How `slot_hash` is produced (entropy accounts in `reset.rs`) is **not** reproduced | Source; Kotlin matched a verbatim Rust port on five live rounds |
| Deploy instruction | Index 6, 12 accounts, 13-byte data: still matches master | Source unchanged since the pin |

Claims checked and rejected or unused: "losing-tile SOL returned" (false), "5M cap / DrillX" (not in this program),
"90/10 burn/stakers" and "v4 after round 364885" (not verified; not used).

EdgeORE's startup guardrail (`edge/ProtocolGuard.kt`) compares the ProgramData slot, the upgrade authority and the
Config fee rates against the values in this table. If any differ, it shows "ORE protocol changed — some screens may be
out of date".
