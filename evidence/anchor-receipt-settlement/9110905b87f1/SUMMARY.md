# Anchor receipt-settlement — test evidence for 9110905b87f1

Tested commit: `9110905b87f1a651a67e821490f46074fb32a6df` (branch
`feature/anchor-receipt-settlement`). Program: `onchain/programs/receipt-settlement`.

| Step (workdir `onchain/`) | Exit code |
|---|---|
| `anchor build --arch v0` | 0 |
| `anchor test --skip-build` (runs `cargo test`) | 0 |

Suites (from `anchor-test.log`):

| Suite | Passed | Failed | Ignored |
|---|---|---|---|
| `receipt_settlement` lib unit tests | 0 | 0 | 0 (none defined) |
| `tests/settlement.rs` (LiteSVM) | 17 | 0 | 0 |
| doc-tests | 0 | 0 | 0 (none defined) |

Per-test results: `per-test-results.txt`. Toolchain: `toolchain.txt`. Build
artifact hash and program id: `commands-and-exit-codes.txt`.

## What this does and does not show

- Tests run **in-process in LiteSVM 0.10.0** (Agave 3.1 runtime) with the
  Ed25519 native program loaded (`precompiles` feature). The bad-signature test
  is rejected by that program itself (`InstructionError(0, Custom(2))`).
- **Not** run against `solana-test-validator`, devnet or mainnet. Nothing was
  deployed and no funds were used. No wallet keypair was needed.
- Build uses `--arch v0`: Anchor 1.2.1's default SBPF v3 artifact was refused
  by LiteSVM 0.10.0 (`add_program` → `InvalidAccountData`). v3 was not
  investigated further.
- Before this run, a manual mutation check (not committed) removed the
  verifier-pubkey and digest comparisons from `digest.rs`; the
  `wrong_verifier_is_rejected` and `tampered_receipt_fields_after_signing_are_rejected`
  tests then failed (15 passed / 2 failed), and passed again after restoring.
- The program is unaudited and is not integrated into the Android app. Android
  files were not touched, so `qualify.sh` was not re-run.
