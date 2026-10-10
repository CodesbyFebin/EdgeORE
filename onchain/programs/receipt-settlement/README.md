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
   exactly once.
4. The creator can **cancel** the job and reclaim the unspent budget once no
   accepted receipt is still unclaimed.

Written from scratch for EdgeORE; it does not reuse the earlier pasted draft.

## Accounts

| Account | PDA seeds | Purpose |
|---|---|---|
| `Job` | `["job", creator, job_id u64 LE]` | creator, verifier, budget, committed, paid, section count, receipt/pending counters, status |
| `Vault` | `["vault", job]` | program-owned lamport escrow (budget + its own rent) |
| `Receipt` | `["receipt", job, receipt_id]` | signed receipt fields, digest, submitted slot, settled flag + settled slot |
| `SectionMarker` | `["section", job, section u16 LE]` | makes `(job, section)` unique |
| `OutputMarker` | `["output", job, output_hash]` | rejects a repeated output hash within one job |

## Instructions and rules

**`create_job(job_id, budget, section_count, verifier)`** — `budget > 0`,
`section_count > 0`, `verifier` not the default key. Transfers `budget`
lamports into the vault on top of the vault's rent-exempt minimum.

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
sha256( "EdgeORE/receipt-settlement/v1" || program_id || job || job_id u64 LE ||
        receipt_id || section u16 LE || worker ||
        input_hash || output_hash || model_hash ||
        quoted_price u64 LE || actual_charge u64 LE )
```

Binding the program id, job account and worker means a signed receipt cannot be
replayed on another deployment, against another job, or by another worker.

**`claim()`** — signer must equal `receipt.worker` (`has_one`), receipt must be
unsettled. Pays exactly `actual_charge` from the vault without touching the
vault's rent reserve, sets `settled = true` and `settled_slot` to the current
slot, updates `paid`/`pending_claims` with checked arithmetic. **No transaction
signature is stored** (a program cannot observe its own transaction signature);
the settlement transaction's signature is whatever the client/RPC reports.

**`cancel_job()`** — creator only (job PDA seeds + `has_one`), job `Active`,
and **`pending_claims == 0`**: every accepted receipt must be claimed first, so
a cancellation never strands a worker holding an accepted receipt. The vault is
closed to the creator (unspent budget + vault rent). The `Job` account is kept
as a `Cancelled` tombstone so the same `(creator, job_id)` cannot be re-opened
over old receipt/section PDAs; further submissions fail with `JobNotActive`.

All arithmetic on balances and counters is checked (`checked_add`/`checked_sub`,
Anchor's checked `add_lamports`/`sub_lamports`); the release profile also keeps
`overflow-checks = true`.

## Known limitations

- Unaudited prototype; no fuzzing, no formal verification.
- A worker who never claims blocks the creator's cancellation indefinitely (by
  design of the rule above; no timeout/expiry is implemented).
- The verifier is a single trusted key per job; there is no key rotation,
  multi-verifier quorum or dispute process.
- Rent for receipt and marker accounts is paid by the worker and is not
  reclaimed (no close instructions for receipts/markers).
- The Job tombstone's rent is not reclaimed after cancellation.
- Output-hash dedupe is exact-match only (see above).

## Toolchain (as used for the recorded test run)

- Rust (host tests): 1.89.0 via `rust-toolchain.toml`
- Anchor CLI / `anchor-lang`: 1.2.1 (installed with `avm` 1.2.1)
- Solana/Agave CLI: 3.1.10 (`cargo-build-sbf` 3.1.10, platform-tools v1.52)
- LiteSVM 0.10.0 (with `precompiles` feature so the Ed25519 program runs)

## Build and test

From `onchain/`:

```sh
anchor build --arch v0
anchor test --skip-build     # runs `cargo test` (LiteSVM, in-process)
```

`--arch v0` is required: Anchor 1.2.1 defaults to SBPF v3, and LiteSVM 0.10.0
(Agave 3.1 runtime) refused to load the v3 artifact (`InvalidAccountData`).
`anchor test` has no `--arch` flag, hence `--skip-build` after an explicit
build. No local validator is started (`skip_local_validator = true`), no
cluster is contacted and no wallet or funds are needed.

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
