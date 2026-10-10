# ORE integration: future design

> **DESIGN ONLY - NOT BUILT. No ORE integration exists in EdgeORE.**
>
> EdgeORE contains no ORE client, no ORE program ID, no ORE instruction encoder and no ORE account reader.
> The Mine screen's "ORE rewards: Not observed / No qualified ORE adapter" card is accurate and stays that way
> until every prerequisite below is met and separately reviewed. EdgeORE has no partnership, endorsement or
> affiliation with ORE or Regolith Labs. Nothing in this document is a statement about returns of any kind.

Status: proposal for discussion. Date: 2026-10-10. Author: EdgeORE maintainers.

## 1. Sources actually read

| Source | What was used | Identity |
|---|---|---|
| `regolith-labs/ore` GitHub repository | Program, API crate, docs, changelog | `master` at commit [`48c203bd75db3cc45105ec29d8f8db719e5a2263`](https://github.com/regolith-labs/ore/commit/48c203bd75db3cc45105ec29d8f8db719e5a2263) ("no cli", committed 2026-10-02 17:31 UTC), workspace version `3.8.25`, license Apache-2.0 ([`Cargo.toml`](https://github.com/regolith-labs/ore/blob/48c203bd75db3cc45105ec29d8f8db719e5a2263/Cargo.toml)) |
| [`api/src/lib.rs`](https://github.com/regolith-labs/ore/blob/48c203bd75db3cc45105ec29d8f8db719e5a2263/api/src/lib.rs) | Program ID in `declare_id!` | `oreV3EG1i9BEgiAJ8b177Z2S2rMarzak4NMv1kULvWv` |
| [`api/src/instruction.rs`](https://github.com/regolith-labs/ore/blob/48c203bd75db3cc45105ec29d8f8db719e5a2263/api/src/instruction.rs) | Instruction enum and argument layouts | see §2.2 |
| [`api/src/consts.rs`](https://github.com/regolith-labs/ore/blob/48c203bd75db3cc45105ec29d8f8db719e5a2263/api/src/consts.rs) | Mint, PDA seeds, singleton account addresses, token decimals | see §2.3 |
| [`api/src/sdk.rs`](https://github.com/regolith-labs/ore/blob/48c203bd75db3cc45105ec29d8f8db719e5a2263/api/src/sdk.rs) | Upstream instruction builders (`deploy`, `checkpoint`, `claim_sol`, `claim_ore`, `automate`, ...) | |
| [`program/src/lib.rs`](https://github.com/regolith-labs/ore/blob/48c203bd75db3cc45105ec29d8f8db719e5a2263/program/src/lib.rs), [`program/src/deploy.rs`](https://github.com/regolith-labs/ore/blob/48c203bd75db3cc45105ec29d8f8db719e5a2263/program/src/deploy.rs), [`program/src/claim_ore.rs`](https://github.com/regolith-labs/ore/blob/48c203bd75db3cc45105ec29d8f8db719e5a2263/program/src/claim_ore.rs) | Dispatch, account lists, checks | |
| [`api/src/state/`](https://github.com/regolith-labs/ore/blob/48c203bd75db3cc45105ec29d8f8db719e5a2263/api/src/state) | `Board`, `Round`, `Miner`, `Treasury`, `Automation`, `Config` layouts | |
| [`docs/DEPLOY.md`](https://github.com/regolith-labs/ore/blob/48c203bd75db3cc45105ec29d8f8db719e5a2263/docs/DEPLOY.md) | Upgrade authority and verified-build process | |
| [`localnet.sh`](https://github.com/regolith-labs/ore/blob/48c203bd75db3cc45105ec29d8f8db719e5a2263/localnet.sh) | How upstream runs a local validator with cloned mainnet accounts | |
| [`docs/DISCRETIONARY_BPS.md`](https://github.com/regolith-labs/ore/blob/48c203bd75db3cc45105ec29d8f8db719e5a2263/docs/DISCRETIONARY_BPS.md), [`changelog/`](https://github.com/regolith-labs/ore/blob/48c203bd75db3cc45105ec29d8f8db719e5a2263/changelog) | Automation executor fees, reset verification notes | |

Not usable as a source: `https://ore.supply` and `https://ore.com` (the crate's `homepage`) are client-rendered web apps.
Fetched on 2026-10-10 without a browser they return only a page shell ("ORE", "Mine / Stake / Trade", "Start mining"),
and `docs.ore.supply` did not resolve. No statement in this document relies on those sites. Where the upstream
README and the source disagree (for example the README still lists `SetAdmin` / `SetFeeRate`, which are no longer
in the instruction enum), **the source at the pinned commit wins**.

Re-check all of this against the then-current upstream commit before any implementation; ORE changes often (§2.4).

## 2. What ORE is today (at `48c203bd75db`)

### 2.1 Mechanism, from the source
- ORE is a Solana program written with the Steel framework (`steel = 4.0.9`), not Anchor. **There is no Anchor IDL.**
  The published interface is the `ore-api` Rust crate (instruction structs, state layouts, PDA helpers and the
  builders in `sdk.rs`).
- "Mining" is not device computation. A participant sends a `Deploy` instruction that transfers **SOL** onto one or
  more of **25 squares** of a shared board for the current round (`Deploy { amount: u64, squares: u32 mask }`,
  [`deploy.rs`](https://github.com/regolith-labs/ore/blob/48c203bd75db3cc45105ec29d8f8db719e5a2263/program/src/deploy.rs)). Rounds are bounded by slots (`Board.start_slot`/`end_slot`,
  `Config` round length). A `Reset` closes the round using on-chain entropy (the `entropy-api` program and a
  `Var` account); `Checkpoint`, `ClaimSOL` and `ClaimORE` settle a miner's account afterwards.
- `Automate`/`AutomateV2` let a third-party executor deploy on a miner's behalf under a stored strategy, with an
  executor fee (`DISCRETIONARY_BPS.md`).
- Staking is no longer in this program: the workspace depends on a separate crate `ore-stake-api = 0.3.0`
  (a separate program). It was **not reviewed** for this document.
- Admin-only instructions: `Buyback`, `Bury`, `Wrap`, `UpdateProtocolConfig`, `NewVar`; `Liq` is declared but
  returns `InvalidInstructionData`.

Consequence for EdgeORE: **CPU, storage or bandwidth contributed by a phone has no representation in the ORE program.**
Taking part in ORE means putting SOL at risk in a round-based game. A device-resource app cannot "mine ORE"
in any sense the program recognises, and EdgeORE must never present it that way.

### 2.2 Instruction discriminators (1 byte, Steel `instruction!` macro)

| Byte | Instruction at `48c203bd75db` | Signer check in `program/src/*.rs` |
|---|---|---|
| 0 | `Automate` (and `AutomateV2` = 0 in the separate `OreInstructionV2` enum) | miner |
| 2 | `Checkpoint` | anyone (fee) |
| 3 | `ClaimSOL` | miner |
| 4 | `ClaimORE` | miner |
| 5 | `Close` | anyone (rent payer) |
| 6 | `Deploy` | miner or executor |
| 8 | `Log` | board PDA only (program-internal CPI) |
| 9 | `Reset` | anyone |
| 13, 14, 15, 19, 24 | `Buyback`, `Wrap`, `UpdateProtocolConfig`, `NewVar`, `Bury` | admin / bury authority |
| 25 | `Liq` (rejected by the program) | - |
| 1, 7, 10, 11, 12, 16-18, 20-23 | unassigned (rejected by `TryFromPrimitive`) | - |

This table is evidence for review, **not** an encoding table to copy into EdgeORE (§4.1).

### 2.3 Addresses declared upstream (mainnet)
From [`consts.rs`](https://github.com/regolith-labs/ore/blob/48c203bd75db3cc45105ec29d8f8db719e5a2263/api/src/consts.rs): mint `oreoU2P8bN6jkk3jbaiVxYnG1dCXcYxwhwyK9jSybcp` (`TOKEN_DECIMALS = 11`),
board `BrcSxdp1nXFzou1YyDnQJcPNBNHgoypZmTsyKBSLLXzi`, treasury `45db2FSR4mcXdSVVZbKbwojU6uYDpMyhpEi7cC8nHaWG`,
config `9c9X7aDRAF41faiDs94ELjT19UrGnn72wBW9hPsS4Awy`; PDA seeds `b"miner"`, `b"round"`, `b"automation"`, ...
No devnet deployment is declared in the repository. Upstream's own local testing (`localnet.sh`) loads a locally
built `ore.so` into `solana-test-validator` and **clones mainnet accounts**; it does not use devnet.

### 2.4 The interface changes, and the same byte has meant different things
Byte values taken from `git log -p -- api/src/instruction.rs` in the same clone (106 commits touch that file):

| Upstream commit (date) | Byte 3 | Byte 5 | Bytes 10 / 11 / 12 |
|---|---|---|---|
| `8c5fcb0` (2025-07-10) | `Deposit` | `Mine` | `SetBlockLimit` / `SetFeeCollector` / `SetFeeRate` |
| `0b9b2f3` (2025-09-23) | `Deploy` | `Log` | `SetAdmin` / `SetFeeCollector` / unassigned |
| `f2c552c` (2025-12-04) | `ClaimSOL` | `Close` | `Deposit` / `Withdraw` / `ClaimYield` |
| `48c203b` (2026-10-02) | `ClaimSOL` | `Close` | unassigned |

The program is upgradeable; `DEPLOY.md` names a Squads multisig as upgrade authority and checks deployed builds
against source with `solana-verify` / `verify.osec.io`. A client that hard-codes bytes keeps "working" after an
upgrade, but it then encodes a different instruction or none at all.

## 3. What a future integration could look like (if ever approved)

Scope it as **read-only observation first**. Writing to ORE is a separate decision with its own review.

**Phase A: read-only observer (no signing).**
- Pin `ore-api` at an exact crate version and upstream commit. Record both, plus the on-chain program's
  verified-build hash, in `docs/model-and-protocol-provenance.md`.
- Generate EdgeORE's Kotlin account decoders from those pinned layouts. Test them against fixture accounts captured
  from a local validator (`localnet.sh` approach), with golden bytes checked into the repo.
- Show `Board` round id and slot window, and the user's own `Miner` account if one exists, as observations with
  slot and time ("observed at slot N"). A failed RPC call reads "Unavailable", never zero. Use the same
  `RpcObservation` pattern as the devnet balance.
- No numbers that imply returns. No projections, rates or "today" totals.

**Phase B: user-initiated transactions (only if separately approved).**
- Build instructions only through the pinned upstream builders (`sdk.rs`), or a Kotlin port tested
  byte-for-byte against them.
- Reuse EdgeORE's existing exact-message review: decode the full message on the device and show program ID, every
  account and the decoded arguments, plus the SOL amount at risk. Then sign through MWA, verify the wallet's
  signature, submit separately and keep a durable operation record. One user action, one reviewed message.
  No background or automatic deploys, and no `Automate` delegation to an EdgeORE executor.
- Receipts record the real signature and the RPC confirmation response. A missing confirmation is OUTCOME_UNKNOWN,
  never success.

**Not part of any phase:** linking ORE to the contribution scheduler, device CPU/storage/bandwidth, AI work or
"proof of contribution". ORE has no such input (§2.1).

## 4. Prerequisites (all must hold before any code)

1. **Upstream identity only.** Program ID, mint, account addresses, seeds, discriminators and layouts come from the
   pinned upstream `ore-api` source or crate, never from a blog post, a pasted blueprint, an LLM answer or a guess.
   There is no Anchor IDL to fetch. The crate is the interface.
2. **Verified deployment.** The on-chain program's verified build must match the pinned commit (`solana-verify`).
   Re-check before each release; an upgrade invalidates the pin.
3. **Test environment.** Local validator with upstream's `ore.so` and cloned accounts. Devnet is not a stand-in
   unless upstream publishes a devnet deployment.
4. **Licence and attribution.** Upstream is Apache-2.0. Keep its notices for any ported code and name the commit.
5. **Wallet path.** The current MWA authorization, review, signature-verification and separate-submission gates
   must be passing on the candidate build, on a device.
6. **Legal and product review** of offering a SOL-at-risk game inside EdgeORE (see §5), done before any UI work.
7. **Copy review.** No earnings, rate, boost or "mining with your device" wording, in line with EdgeORE's existing
   claim rules.

## 5. Risks

- **Interface drift:** frequent upgrades re-use bytes (§2.4); a stale client can sign a different instruction.
- **Capital risk:** `Deploy` moves the user's SOL into a round. EdgeORE would be helping users risk funds.
  The UI must say so plainly and must not suggest an expected result.
- **Regulatory and store-policy risk:** a game of chance with SOL stakes may be treated as gambling in some places
  and by app-store policy. That needs review.
- **Custody and automation risk:** `Automate` lets an executor deploy for the user. EdgeORE must not become an
  executor or hold delegated authority.
- **Misrepresentation risk:** calling device resource sharing "ORE mining" would be false (§2.1).
- **Dependency risk:** `entropy-api` and `ore-stake-api` are further upstream programs with their own upgrade cycles.

## 6. Explicitly rejected from the pasted blueprint

Received in chat on 2026-10-10 and rejected before any code was written. None of it is, or will be, in EdgeORE:

| Rejected item | Why |
|---|---|
| Guessed instruction bytes `0x03` and `0x05` ... `0x0C` for "refining", "staking", "stORE", "DCA" and "reserve" classes | Not taken from upstream. At `48c203bd75db`, 0x03 is `ClaimSOL`, 0x05 `Close`, 0x06 `Deploy`, 0x08 `Log`, 0x09 `Reset`, and 0x07, 0x0A, 0x0B, 0x0C are unassigned. A year earlier the same bytes meant other things (§2.4). Signing such a message does something other than intended, or fails. |
| Placeholder program addresses (and an Anchor `declare_id!` with an invalid placeholder) | Unknown recipients for signed transactions. Upstream's program ID is `declare_id!` in `ore-api`, and it is not an Anchor program. |
| Hard-coded fake transaction IDs (`"reserve_buyback_tx_id"`) and a `[0u8; 64]` "signature" in settlement | Fabricated success. EdgeORE records only real signatures and RPC responses. `Buyback` is an admin-only instruction anyway. |
| Reward / APR / yield / boost-multiplier / earnings UI ("You earned 0.045 ORE today") | Unsupported claims, ruled out by EdgeORE's claim rules. No such number is observed, and device resources do not produce ORE (§2.1). |
| "Zero-Knowledge" badge | No zero-knowledge proof exists anywhere in the design. |
| Mapping device CPU/storage/bandwidth "proof of contribution" to ORE | ORE has no such input (§2.1). |

## 7. Decision record

- 2026-10-10: Design only. No branch implements any part of this. Revisit only if the user explicitly asks for Phase A,
  with the prerequisites in §4 checked against the then-current upstream commit.
