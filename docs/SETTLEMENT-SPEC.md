# EdgeORE receipt-settlement — SETTLEMENT-SPEC v2

**This file is the single canonical reference** for the on-chain settlement
program in `onchain/programs/receipt-settlement/`: instructions, accounts and
their byte layouts, PDA seeds, the signed digest, errors and events. If code
and this file disagree, that is a bug in one of them.

> Status: **devnet-only prototype, unaudited, NOT deployed** (no devnet, no
> mainnet). Not integrated into the EdgeORE Android app. No token, reward,
> yield or mining. Program id (local keypair, never committed):
> `8JMkxGfiEfXJ3xJgt9Dh2bzdp6ozL6WQ5E534WfLwHfT`. Toolchain: Anchor 1.2.1,
> Agave 4.3.0 platform tools, SBPF v3 artifact. Deployment is gated by
> [DEPLOY-DEVNET.md](DEPLOY-DEVNET.md); once deployed on devnet the program is
> **upgradeable**.

## 1. Vocabulary and the v2 decision (J-2)

The spec vocabulary is `create_job / accept_job / submit_proof /
verify_and_settle / refund_after_deadline`. The v1 program
(`create_job / submit_receipt / claim / cancel_job` + housekeeping) did **not**
map 1:1: v1 had no accept step, `submit_receipt` merged "proof" and
"verification", `claim` was worker-only, and `cancel_job` needed the creator's
signature. Because nothing is deployed, renaming is free, so v2 **renames and
restructures** so the mapping is exactly 1:1:

| Spec step | v2 instruction | Signer(s) | v1 predecessor |
|---|---|---|---|
| create_job | `create_job` | creator (customer) | `create_job` (+ terms_hash, absolute deadline from Clock; claim window removed) |
| accept_job | `accept_job` | node | *(none — new)* |
| submit_proof | `submit_proof` | the accepted node | proof half of `submit_receipt` |
| verify_and_settle | `verify_and_settle` | anyone (settler) + verifier's Ed25519 sig in the tx | verification half of `submit_receipt` + `claim` |
| refund_after_deadline | `refund_after_deadline` | anyone (cranker) | `cancel_job` (creator-only) |

Housekeeping outside the spec vocabulary (unchanged in purpose):
`rotate_verifier` (now locked after the first accept), `close_assignment`
(replaces `close_receipt` + `close_markers`), `close_job`.

## 2. Roles, signers and fee payers

| Role | Who | Pays |
|---|---|---|
| creator | the customer | job + vault rent and the escrowed budget (`create_job`); fee of `rotate_verifier`, `close_job` |
| node | the worker that accepts a section | assignment rent (`accept_job`), output-marker rent (`submit_proof`); gets both back via `close_assignment` |
| verifier | key bound in `job.verifier` | nothing; it only produces an Ed25519 signature off-chain |
| settler | **any** signer of `verify_and_settle` (node, verifier, relayer) | transaction fee only (incl. the Ed25519 signature's fee); receives nothing |
| cranker | **any** signer of `refund_after_deadline` | transaction fee only (the crank allocates nothing); receives nothing |

The refund crank needs **no creator signature**; the destination is fixed to
`job.creator` by constraint.

## 3. Lifecycle

```
create_job ──► Open ──(slot > deadline_slot) refund_after_deadline──► Refunded ──► close_assignment* ──► close_job
                │
                ├─ accept_job(section)          slot <= deadline_slot
                ├─ submit_proof(section)        slot <= deadline_slot, accepted node only
                └─ verify_and_settle(section)   slot <= deadline_slot, once per section
```

`deadline_slot` is inclusive: accept/prove/settle are allowed while
`Clock::slot <= deadline_slot`; refund is allowed when `Clock::slot >
deadline_slot`. There is no early cancel: the budget is locked until the
deadline (by design: nodes that accepted are protected until then).

## 4. Accounts, seeds and layouts

All accounts are Anchor accounts: 8-byte discriminator, then Borsh fields.
Integers little-endian; `bool` is 1 byte (0/1); enum is a 1-byte variant index.

| Account | Seeds (program-derived) | Size | Discriminator |
|---|---|---|---|
| `Job` | `["job", creator, job_id u64 LE]` | 167 | `4b7c50cba1b4ca50` |
| `Vault` | `["vault", job]` | 41 | `d308e82b02987577` |
| `Assignment` | `["assignment", job, section u16 LE]` | 317 | `6ac96e3359aa491f` |
| `OutputMarker` | `["output", job, output_hash]` | 136 | `56f5cb61c6e2b971` |

### Job (167 bytes)

| Off | Field | Type | Meaning |
|---|---|---|---|
| 0 | discriminator | [u8;8] | |
| 8 | creator | Pubkey | customer; refund destination |
| 40 | job_id | u64 | creator-chosen id (seed) |
| 48 | terms_hash | [u8;32] | **bound at create_job**, never changes, non-zero |
| 80 | verifier | Pubkey | **bound at create_job**; changeable only while `accepted_count == 0` |
| 112 | budget | u64 | escrowed lamports (excl. vault rent) |
| 120 | committed | u64 | Σ actual_charge of submitted proofs (reserved) |
| 128 | paid | u64 | Σ actual_charge of settled proofs |
| 136 | section_count | u16 | |
| 138 | accepted_count | u16 | |
| 140 | proof_count | u16 | |
| 142 | settled_count | u16 | |
| 144 | created_slot | u64 | `Clock::slot` of the create_job tx |
| 152 | deadline_slot | u64 | **bound at create_job** = created_slot + deadline_slots |
| 160 | open_accounts | u32 | assignment + output-marker accounts not yet closed |
| 164 | status | enum u8 | 0 = Open, 1 = Refunded |
| 165 | bump | u8 | |
| 166 | vault_bump | u8 | |

### Vault (41 bytes)

| Off | Field | Type |
|---|---|---|
| 8 | job | Pubkey |
| 40 | bump | u8 |

Lamports = rent-exempt minimum + (budget − paid). Program-owned; payouts debit
it directly and never below the rent minimum.

### Assignment (317 bytes) — one per (job, section)

| Off | Field | Type | Written by |
|---|---|---|---|
| 8 | job | Pubkey | accept_job |
| 40 | section | u16 | accept_job |
| 42 | node | Pubkey | accept_job — **the only payee** and the only proof submitter |
| 74 | consent_hash | [u8;32] | accept_job (non-zero) |
| 106 | limits_hash | [u8;32] | accept_job (non-zero) |
| 138 | accepted_slot | u64 | accept_job |
| 146 | proof_submitted | bool | submit_proof |
| 147 | receipt_id | [u8;32] | submit_proof |
| 179 | input_hash | [u8;32] | submit_proof |
| 211 | output_hash | [u8;32] | submit_proof |
| 243 | model_hash | [u8;32] | submit_proof |
| 275 | quoted_price | u64 | submit_proof |
| 283 | actual_charge | u64 | submit_proof |
| 291 | proof_slot | u64 | submit_proof |
| **299** | **settled** | **bool** | verify_and_settle — **settle-once flag** |
| 300 | settled_slot | u64 | verify_and_settle |
| 308 | settled_amount | u64 | verify_and_settle |
| 316 | bump | u8 | accept_job |

Settle-once is structural: `settled` is a field of the per-section account,
set by the single successful `verify_and_settle` and never cleared; the
assignment cannot be closed while the job is Open, and the `(job, section)`
PDA can only be `init`-ed once, so there is no second account a second
settlement could use.

### OutputMarker (136 bytes)

| Off | Field | Type |
|---|---|---|
| 8 | job | Pubkey |
| 40 | assignment | Pubkey |
| 72 | node | Pubkey (rent payer) |
| 104 | output_hash | [u8;32] |

Rejects a repeated `output_hash` within one job (`init` fails). This is an
exact-duplicate check only, **not** collusion or plagiarism detection.

## 5. Signed digest (verifier signature)

The verifier approves one proof by signing (Ed25519) this 32-byte message:

```
sha256(
  "EdgeORE/receipt-settlement/v3"      29 bytes ASCII, no terminator
  program_id                           32
  job (PDA address)                    32
  job.job_id                           u64 LE
  job.created_slot                     u64 LE
  job.terms_hash                       32
  section                              u16 LE
  assignment.node                      32
  assignment.consent_hash              32
  assignment.limits_hash               32
  assignment.receipt_id                32
  assignment.input_hash                32
  assignment.output_hash               32
  assignment.model_hash                32
  assignment.quoted_price              u64 LE
  assignment.actual_charge             u64 LE
)
```

`verify_and_settle` takes **no proof arguments**: every field is read from the
Job and Assignment accounts, so the verifier's signature covers exactly what
is stored. Binding program id / job / created_slot prevents replay against
another deployment, another job, or a job later re-created at the same address
(re-creation always gets a larger `created_slot`, §6 close_job).

**Ed25519 instruction.** The instruction immediately before
`verify_and_settle` must be an Ed25519 native-program instruction
(`Ed25519SigVerify111111111111111111111111111`) with: no accounts; exactly one
signature (`data[0] == 1`, `data[1] == 0`); the signature, public-key and
message instruction indexes all `0xFFFF` (= this same instruction's data;
explicit indexes are rejected); all three regions in bounds; message size 32;
public key `== job.verifier`; message `== digest above`. The program reads it
through the instructions sysvar (address-constrained, `_checked` loaders). An
invalid signature fails the whole transaction inside the Ed25519 program
(`InstructionError(0, Custom(2))`). Fee: 5 000 lamports per transaction
signature **plus** 5 000 for the precompile signature.

## 6. Instructions

Discriminators are Anchor's `sha256("global:<name>")[..8]`.

### create_job — `b282d96e641b5277`
Args: `job_id u64, terms_hash [u8;32], budget u64, section_count u16, verifier Pubkey, deadline_slots u64`.
Accounts: `creator` (signer, writable), `job` (init), `vault` (init), `system_program`.
Checks: terms_hash ≠ 0 (InvalidHash); budget > 0; section_count > 0; verifier ≠ default; `1 <= deadline_slots <= MAX_DEADLINE_SLOTS (10 000 000)` (InvalidDeadline).
Effects: `created_slot = Clock::slot`; `deadline_slot = created_slot + deadline_slots` (checked); transfers `budget` creator → vault; status Open. Event `JobCreated`.

### accept_job — `2bc97c0113bd600a`
Args: `section u16, terms_hash [u8;32], consent_hash [u8;32], limits_hash [u8;32]`.
Accounts: `node` (signer, writable, payer), `job` (writable), `assignment` (init, seeds use `section`), `system_program`.
Checks: Open; `slot <= deadline_slot`; `section < section_count`; `terms_hash == job.terms_hash` (TermsMismatch — the node states the terms it accepts); consent_hash, limits_hash ≠ 0; section not already accepted (`init` → system error `Custom(0)`).
Effects: records node, consent_hash, limits_hash, accepted_slot; accepted_count += 1, open_accounts += 1. Event `JobAccepted`.

### submit_proof — `36f12e5404d42e5e`
Args: `section u16, args ProofArgs { receipt_id, input_hash, output_hash, model_hash: [u8;32], quoted_price u64, actual_charge u64 }`.
Accounts: `node` (signer, writable; `has_one = node` on the assignment → Anchor 2001), `job` (writable), `assignment` (writable), `output_marker` (init), `system_program`.
Checks: Open; `slot <= deadline_slot`; no earlier proof (ProofAlreadySubmitted); `0 < actual_charge <= quoted_price` (no tolerance); `committed + actual_charge <= budget` (OverBudget); output hash unused in this job.
Effects: stores proof fields and proof_slot; **reserves** the charge (`committed += actual_charge`), so a recorded proof is always fundable; proof_count += 1, open_accounts += 1. Event `ProofSubmitted`.

### verify_and_settle — `d38e141686e84065`
Args: `section u16`.
Accounts: `settler` (signer — any key), `node` (writable; must equal `assignment.node`, else **PayoutMismatch**), `job` (writable), `vault` (writable), `assignment` (writable), `instructions_sysvar`.
Checks, in order: payout account = recorded node; Open; `slot <= deadline_slot`; proof_submitted (NoProof); `!settled` (AlreadySettled); Ed25519 instruction + digest (§5); `paid + charge <= committed <= budget`; vault keeps its rent minimum.
Effects: `settled = true`, settled_slot, settled_amount; paid += charge, settled_count += 1; moves `actual_charge` vault → node. Event `Settled`.

### refund_after_deadline — `afbd1a5ad171299f`
Args: none.
Accounts: `cranker` (signer — any key, the fee payer), `creator` (writable; must equal `job.creator`, else CreatorMismatch), `job` (writable), `vault` (writable, closed to creator).
Checks: Open; `slot > deadline_slot` (DeadlineNotReached).
Effects: status Refunded; the vault's entire balance (unspent budget, including reserved-but-unsettled charges, plus vault rent) goes to the creator. Event `JobRefunded` (records the cranker).

### rotate_verifier — `10c9149b0c4bbd55`
Args: `new_verifier Pubkey`. Accounts: `creator` (signer), `job` (writable).
Checks: Open; `accepted_count == 0` (VerifierLocked); non-default; differs from current. Event `VerifierRotated`.

### close_assignment — `b47abbc5a69801de`
Args: `section u16`. Accounts: `closer` (signer: node or creator), `node` (writable; = assignment.node), `job` (writable), `assignment` (closed to node), `output_marker` (optional; closed to node).
Checks: status Refunded (JobStillOpen); closer is node or creator (Unauthorized); output marker passed **iff** a proof exists (OutputMarkerMismatch).
Effects: rent → node; open_accounts −= 1 or 2. Event `AccountsClosed`.

### close_job — `5a64b4c8c8a378b6`
Accounts: `creator` (signer, writable), `job` (closed to creator).
Checks: Refunded; `open_accounts == 0`; `Clock::slot > created_slot` (CloseTooEarly — already implied by Refunded, since refund needs `slot > deadline_slot >= created_slot + 1`). Event `JobClosed`.

## 7. Errors

Program errors are `6000 + index` (`InstructionError(i, Custom(code))`).

| Code | Name | Code | Name |
|---|---|---|---|
| 6000 | InvalidBudget | 6016 | InvalidEd25519Instruction |
| 6001 | InvalidSectionCount | 6017 | VerifierMismatch |
| 6002 | InvalidVerifier | 6018 | DigestMismatch |
| 6003 | InvalidDeadline | 6019 | AlreadySettled |
| 6004 | InvalidHash | 6020 | PayoutMismatch |
| 6005 | JobNotOpen | 6021 | CreatorMismatch |
| 6006 | DeadlinePassed | 6022 | InsufficientVault |
| 6007 | DeadlineNotReached | 6023 | MathOverflow |
| 6008 | SectionOutOfRange | 6024 | SameVerifier |
| 6009 | TermsMismatch | 6025 | VerifierLocked |
| 6010 | ProofAlreadySubmitted | 6026 | Unauthorized |
| 6011 | NoProof | 6027 | JobStillOpen |
| 6012 | ZeroCharge | 6028 | OutputMarkerMismatch |
| 6013 | ChargeExceedsQuote | 6029 | OpenAccountsRemain |
| 6014 | OverBudget | 6030 | CloseTooEarly |
| 6015 | MissingEd25519Instruction | | |

Also observed: Anchor 2001 ConstraintHasOne, 2006 ConstraintSeeds, 3012
AccountNotInitialized; System program `Custom(0)` (account already in use) for
a duplicate accept or duplicate output hash; Ed25519 program `Custom(2)` for an
invalid signature.

## 8. Events

Anchor `emit!` (base64 `Program data:` log line), discriminator
`sha256("event:<Name>")[..8]`.

| Event | Discriminator | Fields |
|---|---|---|
| JobCreated | `306ea2b1434a9f83` | job, creator, terms_hash, verifier, budget u64, section_count u16, created_slot u64, deadline_slot u64 |
| JobAccepted | `2f36983b76c3fb72` | job, assignment, section u16, node, consent_hash, limits_hash, slot u64 |
| ProofSubmitted | `a0335546f959058b` | job, assignment, section u16, node, actual_charge u64, slot u64 |
| Settled | `e8d228118e7c91ee` | job, assignment, section u16, node, amount u64, settler, slot u64 |
| JobRefunded | `9cab6f2511ae08a7` | job, creator, cranker, refunded_lamports u64, unsettled_proofs u16, slot u64 |
| VerifierRotated | `4c5e904999f94f62` | job, old_verifier, new_verifier |
| AccountsClosed | `e74ca33cd13d51ac` | job, assignment, rent_to, lamports u64 |
| JobClosed | `7b3ff399aed40237` | job, creator, lamports u64 |

No event carries a transaction signature: a program cannot observe its own
transaction's signature. Indexers take it from the transaction.

## 9. Time model and the two test environments (J-1)

**Deadlines only from the on-chain Clock.** No instruction takes an absolute
slot or timestamp. `create_job` takes a *duration* (`deadline_slots`); the
absolute `deadline_slot` is `Clock::slot` of that transaction plus the
duration. Every comparison (`accept_job`, `submit_proof`,
`verify_and_settle`, `refund_after_deadline`, `close_job`) reads
`Clock::get()?.slot` at execution. `unix_timestamp` is not used.

**What a transaction's Clock is.** All instructions of one transaction execute
in one bank, so they read the same `Clock::slot`. Two *separate* transactions
read the slots of whatever banks they land in.

**v1 test that ran only in LiteSVM: `close_job_rejected_in_creation_slot`.**
It sent `create_job`, then `cancel_job`, then `close_job` as three
transactions and required `close_job` to fail with `CloseTooEarly`
(`Clock::slot > job.created_slot` false). LiteSVM's clock only moves on
`warp_to_slot`, so all three read the same slot. On `solana-test-validator`
the leader produces a new bank every ~400 ms regardless of the test, and a
client cannot choose the bank its transaction lands in: it can only send and
wait. Each transaction was confirmed before the next was sent, which already
takes more than one slot, so `close_job` executed in a later slot than
`create_job`, the guard passed, and the expected failure could not be
produced. There is no RPC to hold the leader on a slot or to warp a running
validator's clock (`--warp-slot` only applies at ledger creation).

**v2 resolution.** The equivalent check is a single transaction
`[create_job, refund_after_deadline, close_job]`
(`create_and_refund_in_one_transaction_is_rejected`): every instruction sees
the creation slot, so the refund fails deterministically with
`DeadlineNotReached` at instruction 1 on both backends, and atomicity rolls
back the creation. `CloseTooEarly` itself is now unreachable by construction
(refund needs `slot > deadline_slot >= created_slot + 1`) and is kept as a
defensive guard. All 25 scenarios run on both backends.

**v1 claim that passed simulation and failed on-chain (`ClaimWindowExpired`,
validator slot 54).** Scenario `claim_at_deadline_allowed_after_deadline_rejected`
submitted receipt *a* in slot 12 and receipt *b* in slot 13 (two
transactions, so on a validator two different slots; in LiteSVM both were in
the same slot). With a 40-slot window, `a.deadline = 52`, `b.deadline = 53`.
The test waited until the *confirmed* slot was `a.deadline + 1 = 53` and then
claimed **b**, expecting expiry. `simulateTransaction` at `confirmed`
commitment executes against the bank of slot 53: `53 <= b.deadline`, claim
allowed, simulation OK. The real transaction then landed in the working bank
of slot 54: `54 > 53`, `ClaimWindowExpired`. Mechanism: (1) a test bug — the
deadline of *a* was used for a claim of *b*; (2) simulation and execution run
in different banks, and the simulated bank's slot is never later than the one
the transaction executes in. The test passed only because execution landed one
slot later; landing in slot 53 would have paid the claim and failed the test.

**Should validator tests send rather than simulate? Yes, and v2 does.** The
validator backend now sends every transaction with `skip_preflight`, waits for
`confirmed`, and asserts the error, logs, fee and slot from `getTransaction`
— the result of the bank the transaction actually executed in. Simulation is
used only when a transaction never lands (none did in the v2 run; each
transaction is listed in `validator-transactions.log`). Failed transactions
cost the local faucet's fee lamports only. Deadline tests are written so the
assertion holds for any execution slot: "after deadline" waits until the
confirmed slot is `> deadline_slot` (any later bank has a slot at least that
high), and "before deadline" runs with a deadline far in the future. Landing on
the exact boundary slot is asserted in LiteSVM only, inside
`deadline_from_clock_is_inclusive_then_closes`.

## 10. Requirements checklist (J-3) and tests

Each test name below runs in both `tests/settlement.rs` (LiteSVM) and
`tests/validator.rs` (local validator).

| Requirement | Where | Tests |
|---|---|---|
| settle-once is a structural flag on the account | `Assignment.settled` @299; PDA per (job, section) | `duplicate_settle_moves_zero_lamports_on_every_account`, `happy_path_create_accept_prove_settle_refund_close` |
| consent_hash + limits_hash recorded at accept | `Assignment` @74, @106 | `accept_records_node_consent_and_limits`, `accept_rejects_wrong_terms_zero_hashes_range_and_duplicate` |
| terms_hash, deadline, verifier bound at create_job | `Job` @48, @152, @80 | `create_job_binds_terms_verifier_and_clock_deadline`, `create_job_rejects_invalid_parameters`, `rotate_verifier_allowed_before_accept_and_locked_after`, `accept_rejects_…` (TermsMismatch), `settle_signature_over_different_values_is_rejected` (terms in digest) |
| permissionless refund crank with a defined fee payer | `refund_after_deadline` (cranker signs + pays fee) | `refund_crank_is_permissionless_after_deadline_only`, `refund_returns_unsettled_reservations` |
| deadlines only from the on-chain Clock | §9 | `create_job_binds_terms_verifier_and_clock_deadline`, `deadline_from_clock_is_inclusive_then_closes`, `create_and_refund_in_one_transaction_is_rejected` |
| settle pays the node recorded at accept; substituted payout fails | `verify_and_settle` `node` constraint | `settle_pays_recorded_node_and_substituted_payout_fails` |
| duplicate settle moves zero lamports on every account | | `duplicate_settle_moves_zero_lamports_on_every_account` (node, job, vault, assignment, output marker, creator, verifier, sysvar, Ed25519 program: lamports and data unchanged; the separate fee payer loses exactly the 10 000-lamport network fee) |

Other scenarios: `submit_proof_only_by_accepted_node`,
`submit_proof_rejects_bad_charge_second_proof_and_duplicate_output`,
`submit_proof_over_budget_is_rejected`, `settle_without_proof_is_rejected`,
`settle_bad_signature_is_rejected_by_ed25519_program`,
`settle_missing_ed25519_instruction_is_rejected`,
`settle_ed25519_offsets_into_other_instruction_are_rejected`,
`settle_wrong_verifier_is_rejected`,
`rotate_verifier_rejects_non_creator_default_and_same`,
`close_assignment_only_after_refund_rent_to_node`,
`close_job_requires_refund_and_no_open_accounts`,
`recreated_job_rejects_replay_of_old_signature`.

## 11. Known limitations

- Unaudited prototype; do not hold real value.
- One node per section; no re-assignment if an accepted node never proves.
- The budget is locked until the deadline (no early cancel), and a proof not
  settled by the deadline is refunded to the creator — the verifier must sign
  in time.
- consent_hash / limits_hash / terms_hash are opaque commitments: the program
  checks they are non-zero (and that terms match), not what they mean.
- The verifier is a single key; trust in it is total for settlement.
- Output-hash duplicate check is exact-match only.
- Rent of assignments/markers is locked until the refund.
- On devnet the program will be upgradeable: the upgrade authority can
  replace this logic (see DEPLOY-DEVNET.md).

## 12. Changes from v1 (commit 823e649)

Renamed/restructured instructions (§1); `Receipt` + `SectionMarker` replaced by
`Assignment`; per-receipt claim windows replaced by one job deadline
(`deadline_slot`); `claim` + `cancel_job` replaced by `verify_and_settle` +
`refund_after_deadline`; digest domain `v2 → v3` with terms, consent and
limits hashes; charges reserved at proof time; verifier rotation locked after
first accept; all error codes renumbered.
