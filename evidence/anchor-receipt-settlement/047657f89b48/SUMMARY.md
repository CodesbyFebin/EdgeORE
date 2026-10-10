# Evidence — receipt-settlement spec v2 @ 047657f89b48

Branch `feature/settlement-spec-v2` (off `feature/anchor-receipt-settlement`
823e649). Commit tested: `047657f89b485f3f20a8b91d79d9c9e9fbdd38ba`.
Clean run: `cargo clean`, then build and both suites, Agave 4.3.0 first on
`PATH` for this run only. Program not deployed anywhere; no funds spent; no
keypair committed.

| Command (from `onchain/`) | Exit | Result |
|---|---|---|
| `cargo clean && anchor build` (SBPF v3, ELF flags 0x3) | 0 | `receipt_settlement.so` 274 352 bytes, sha256 `9ce9addf14b1b432305bbcdfaabe25c174b32f00f27e18534e0b996009bf78a7` |
| `anchor test --skip-build --skip-local-validator --skip-deploy` (LiteSVM) | 0 | **25 passed, 0 failed** |
| `anchor test --skip-build --validator legacy --script validator --provider.wallet <throwaway>` (local solana-test-validator 4.3.0) | 0 | **25 passed, 0 failed** |

The two environments run the same 25 scenarios (1:1, `per-test-results.txt`).

## Validator mode: every transaction sent, results read on-chain

`validator-transactions.log`: 149 transactions, **149 landed**, 0 recovered
from simulation; 89 succeeded, 60 failed as asserted (error, fee and slot taken
from `getTransaction` at `confirmed`). Examples:

- `create_and_refund_in_one_transaction_is_rejected`: slot 10,
  `InstructionError(1, Custom(6007 DeadlineNotReached))`.
- `duplicate_settle_moves_zero_lamports_on_every_account`: slot 18,
  `InstructionError(1, Custom(6019 AlreadySettled))`; all accounts unchanged,
  fee payer charged exactly 10 000 lamports (tx signature + Ed25519 signature).
- `settle_bad_signature_is_rejected_by_ed25519_program`: slot 40,
  `InstructionError(0, Custom(2))` from the Ed25519 program — lands on-chain
  and charges the fee.

`validator-program-logs.log` has every landed program transaction with logs;
`solana-test-validator.log.gz` is the validator's own log.

## Notes

- An identical run at `2eae468` (same program bytes, same sha256) also passed
  25/25 + 25/25 with exit 0; its logs were discarded because concurrent test
  threads could interleave lines of the harness's transaction log. Fixed in
  `047657f` (tests only) and re-run here.
- Mechanisms behind the v1 LiteSVM-only test and the v1
  simulated-OK/executed-fail claim: `docs/SETTLEMENT-SPEC.md` section 9.
- SBPF v3 on devnet: active since epoch 1069 (slot 461 808 000), read through
  a third-party devnet RPC whose genesis hash matches devnet;
  `api.devnet.solana.com` itself was unreachable (TLS reset). See
  `docs/DEPLOY-DEVNET.md`.
