# receipt-settlement (devnet-only prototype)

> **Status: devnet-only, unaudited, NOT deployed anywhere, NOT integrated into
> the EdgeORE Android app.** There is no token, no reward, no yield and no
> mining in this program. It has not been reviewed by any security auditor and
> must not hold real funds. It is not an ORE integration and makes no claim
> about ORE.

A small Anchor program that settles verifier-approved work receipts in SOL
lamports:

1. A **creator** opens a job, escrowing a lamport budget in a program-owned
   vault PDA and naming an **authorized verifier** public key.
2. A **worker** submits a receipt for one section of the job. The same
   transaction must carry an Ed25519 signature by the job's verifier over the
   canonical receipt digest.
3. The worker named in the receipt **claims** the signed charge from the vault,
   exactly once, within the job's claim window.
4. The creator can **cancel** the job and reclaim the unspent budget once no
   accepted receipt is still unclaimed, or once every claim window has passed.
5. Afterwards, receipt and marker rent goes back to the worker who paid it, and
   job rent goes back to the creator (`close_receipt`, `close_markers`,
   `close_job`).

Written from scratch for EdgeORE; it does not reuse the earlier pasted draft.

## Accounts

| Account | PDA seeds | Purpose |
|---|---|---|
| `Job` | `["job", creator, job_id u64 LE]` | creator, verifier, budget, committed, paid, section count, claim window + latest claim deadline, receipt/pending counters, created slot, open receipt/marker account count, status (`Active` / `Cancelled` / `Completed`) |
| `Vault` | `["vault", job]` | program-owned lamport escrow (budget + its own rent) |
| `Receipt` | `["receipt", job, receipt_id]` | signed receipt fields, digest, submitted slot, claim deadline slot, settled flag + settled slot |
| `SectionMarker` | `["section", job, section u16 LE]` | makes `(job, section)` unique; records job, receipt, worker (rent payer) |
| `OutputMarker` | `["output", job, output_hash]` | rejects a repeated output hash within one job; records job, receipt, worker |

## Instructions and rules

**`create_job(job_id, budget, section_count, verifier, claim_window_slots)`** —
`budget > 0`, `section_count > 0`, `verifier` not the default key,
`claim_window_slots > 0`. Transfers `budget` lamports into the vault on top of
the vault's rent-exempt minimum.

**Claim window.** Each accepted receipt gets
`claim_deadline_slot = submitted_slot + claim_window_slots` (checked add). It
can be claimed while `current slot <= claim_deadline_slot`. The job keeps
`claim_deadline`, the latest deadline over all its receipts.

**`submit_receipt(args)`** — `args = { receipt_id, section, input_hash,
output_hash, model_hash, quoted_price, actual_charge }`; the signer is the
worker. Enforced:

- job status is `Active`;
- `section < section_count`;
- `0 < actual_charge <= quoted_price` (**no tolerance**: the signed charge may
  never exceed the quote);
- `committed + actual_charge <= budget` (checked add);
- receipt id unique per job (receipt PDA `init`);
- one receipt per `(job, section)` (section marker `init`);
- the same `output_hash` cannot be accepted twice in one job (output marker
  `init`). **This is only a simple exact-duplicate check, not collusion or
  plagiarism detection** — a trivially different output passes it;
- **verifier signature** (below).

**Signature check.** The instruction immediately before `submit_receipt` must
be an Ed25519 native-program instruction. The program reads it through the
instructions sysvar (address-constrained, loaded with the `_checked` loaders)
and requires: program id is the Ed25519 program, no accounts, exactly one
signature, zero padding byte, signature/pubkey/message instruction indexes all
`u16::MAX` (i.e. pointing into that same instruction's data — explicit indexes
are rejected), every region bounds-checked, message size exactly 32, public key
equal to `job.verifier`, and message equal to the canonical digest recomputed
on-chain from the submitted arguments. The Ed25519 program fails the whole
transaction if the signature itself is invalid.

Canonical digest (`src/digest.rs`):

```
sha256( "EdgeORE/receipt-settlement/v2" || program_id || job || job_id u64 LE ||
        job_created_slot u64 LE ||
        receipt_id || section u16 LE || worker ||
        input_hash || output_hash || model_hash ||
        quoted_price u64 LE || actual_charge u64 LE )
```

Binding the program id, job account and worker means a signed receipt cannot be
replayed on another deployment, against another job, or by another worker.
`job_created_slot` separates a job from a later job re-created at the same
address after `close_job`. `close_job` requires `current slot > created_slot`,
so the re-created job always has a larger created slot, and a signature for
the old job no longer matches.

**`claim()`** — signer must equal `receipt.worker` (`has_one`), job `Active`,
receipt unsettled, and `current slot <= receipt.claim_deadline_slot`
(otherwise `ClaimWindowExpired`). Pays exactly `actual_charge` from the vault without touching the
vault's rent reserve, sets `settled = true` and `settled_slot` to the current
slot, updates `paid`/`pending_claims` with checked arithmetic. **No transaction
signature is stored** (a program cannot observe its own transaction signature);
the settlement transaction's signature is whatever the client/RPC reports.

**`rotate_verifier(new_verifier)`** — creator only (job PDA seeds +
`has_one`), job `Active`, `new_verifier` not the default key and different
from the current one. `submit_receipt` checks the Ed25519 signer against the
*current* `job.verifier`, so receipts signed by the old key are rejected after
rotation, even if they were signed before it. Receipts already accepted are
unaffected and stay claimable. Emits `VerifierRotated`.

**`cancel_job()`** — creator only (job PDA seeds + `has_one`), job `Active`,
and either **`pending_claims == 0`** or **`current slot > job.claim_deadline`**.
A worker whose receipt is still inside its claim window can therefore never be
cut off by a cancellation, and a worker who never claims cannot lock the
creator's funds forever. The vault is closed to the creator, returning the
unspent budget, the charges of receipts that expired unclaimed, and the vault
rent. Expired receipts stay `settled = false`. The cancel rule waits for the
*latest* deadline in the job, so it is conservative: one recent receipt keeps
the job open even after it is claimed, if older receipts expired unclaimed. The job becomes `Completed` if every section was submitted and claimed,
otherwise `Cancelled`. Either way, further submissions, claims and rotations
fail with `JobNotActive`. `cancel_job` is also how a fully claimed job is
finalized.

**`close_receipt()`** — the receipt must be settled, or unsettled with its claim
window passed, so closing it can never strand a claimable payment (otherwise
`ReceiptStillClaimable`). The signer must be the worker, or the creator once
the job is no longer `Active` (otherwise `Unauthorized`). Rent always goes to
`receipt.worker`. While the job is active, the section and output markers stay
in place, so re-submitting the same signed receipt fails on the section marker.

**`close_markers(section, output_hash)`** — only once the job is no longer
`Active` (otherwise `MarkersStillNeeded`), because the markers are what enforce
section and output uniqueness. Both markers must belong to the same receipt.
The signer must be the worker or the creator, and rent goes to the worker.

**`close_job()`** — creator only. The job must be `Cancelled` or `Completed`
(its vault was already closed by `cancel_job`), with `open_accounts == 0` (all
receipt and marker accounts closed) and `current slot > created_slot`. Job rent
goes to the creator. The creator can close a departed worker's
expired or settled receipts and markers themselves (rent still goes to the
worker) to get there.

All arithmetic on balances and counters is checked (`checked_add`/`checked_sub`,
Anchor's checked `add_lamports`/`sub_lamports`); the release profile also keeps
`overflow-checks = true`.

## Known limitations

- Unaudited prototype; no fuzzing, no formal verification.
- Claim windows are measured in slots, so their length in wall-clock time
  depends on slot times.
- The verifier is a single trusted key per job. The creator can rotate it, but
  there is no multi-verifier quorum or dispute process, and rotation takes
  effect at the next submission, with no grace period for receipts the old
  key already signed.
- Markers (and their rent) stay locked until the job is finalized, because
  they enforce uniqueness while the job is active.
- To close a job, its creator may have to send one cleanup transaction per
  receipt left open by workers (transaction fees only; rent goes to workers).
- Output-hash dedupe is exact-match only (see above).

## Toolchain

- Host Rust for tests and IDL build: 1.99.0 via `rust-toolchain.toml`
  (LiteSVM 0.18 / Agave 4.3 crates need rustc >= 1.97.1). The program crate
  itself still declares `rust-version = 1.89.0`, because the SBF build uses the
  platform-tools compiler (rustc 1.95 in platform-tools v1.57).
- Anchor CLI / `anchor-lang`: 1.2.1 (installed with `avm` 1.2.1)
- Solana/Agave CLI: 4.3.0 (`cargo-build-sbf` 4.3.0, platform-tools v1.57),
  pinned in `Anchor.toml` `[toolchain]`
- LiteSVM 0.18.0 (with `precompiles` feature so the Ed25519 program runs)
- Program artifact: **SBPF v3** (Anchor 1.2.1's default `--arch`)

### Why SBPF v3 now (and why it was v0 before)

The first version built with `--arch v0` because LiteSVM 0.10.0 refused the v3
artifact (`add_program` -> `InvalidAccountData`). LiteSVM 0.10 embeds the
Agave 3.1 runtime (`solana-sbpf` 0.13.1). Its loader config *allows* v3 when
the feature is enabled, so the problem was not a disabled feature: the v3 ELF
that platform-tools v1.57 emits did not load in that older runtime.
LiteSVM 0.18.0 (Agave 4.3 runtime) loads the same v3 artifact. All tests pass
against it, so the program now builds as v3.

On mainnet-beta, the v3 feature (`5cC3foj77CWun58pC51ebHFUWavHWKarWyR5UUik7dnC`) shows
"active since epoch 993" (read-only `solana feature status`). **Devnet was not
checked:** the box could not reach `api.devnet.solana.com` (TLS connection
reset). A future devnet deployment must confirm the feature is active there or
rebuild with `--arch v0`.

## Build and test

From `onchain/`, with the Agave 4.3.0 binaries first on `PATH`:

```sh
anchor build                 # SBPF v3
anchor test --skip-build     # runs `cargo test` (LiteSVM, in-process)
```

No cluster is contacted and no wallet or funds are needed for the LiteSVM run.

The program id in `declare_id!` / `Anchor.toml` comes from a keypair generated
locally by `anchor init` under `target/deploy/` (gitignored, never committed).
There is deliberately no devnet/mainnet program entry or deploy script; a devnet
deployment would need its own explicit decision and review.

## Tests (`tests/settlement.rs`)

Happy path (submit → claim → cancel with exact balance checks); bad signature;
missing Ed25519 instruction; Ed25519 offsets using an explicit instruction
index; wrong verifier; each receipt field tampered after signing (plus a
different worker replaying the signed receipt); over budget; charge above quote
and zero charge; section out of range; duplicate receipt id; duplicate section;
duplicate output hash; double claim; claim by the wrong worker; cancel with a
pending claim; cancel by a non-creator; invalid `create_job` parameters.

Signatures are real Ed25519 signatures from test keypairs; the digest is
recomputed in the test with `sha2`, independently of the program's code. These
are in-process LiteSVM tests, not tests against a live cluster.
