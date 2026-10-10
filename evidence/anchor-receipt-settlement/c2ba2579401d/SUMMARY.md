# Anchor receipt-settlement — evidence for c2ba2579401d

Tested commit: `c2ba2579401d…` (full SHA in `commands-and-exit-codes.txt`),
branch `feature/anchor-receipt-settlement`. Clean run (`cargo clean`,
`.anchor/` removed) on the committed `onchain/` tree, Agave 4.3.0 first on PATH.

| Step (workdir `onchain/`) | Exit code |
|---|---|
| `anchor build` (SBPF v3, ELF flags 0x3) | 0 |
| `anchor test --skip-build --skip-local-validator --skip-deploy` (LiteSVM) | 0 |
| `anchor test --skip-build --validator legacy --script validator --provider.wallet <throwaway local key>` | 0 |

| Suite | Passed | Failed | Ignored |
|---|---|---|---|
| `tests/settlement.rs`, LiteSVM 0.18.0 | 29 | 0 | 0 |
| `tests/validator.rs`, local `solana-test-validator` (agave-validator 4.3.0) | 28 | 0 | 0 |

Per-test lines: `per-test-results.txt`. Intermediate commits were re-run
separately (`per-commit-checks.txt`): 53ebcd2 17/17, 1647338 22/22,
6879bd9 24/24, 5fc8716 29/29, all build and test exit 0.

## Notes

- Validator mode: the program was loaded at genesis (`--bpf-program`) on a
  validator started by Anchor on 127.0.0.1. All SOL came from the local test
  faucet. Nothing was deployed to devnet or mainnet, and no funds were spent.
  The wallet Anchor requires was a fresh throwaway keypair outside the repo.
- Validator mode checks expected failures with `simulateTransaction`
  (sigverify on, `confirmed`) and does not submit them. Of 87 landed top-level
  program invocations (`validator-program-logs.log`), 86 succeeded and 1
  expected-rejection claim passed simulation but then failed on-chain with
  `ClaimWindowExpired`. That is still a rejection, but it shows the slot timing
  between simulation and execution is not exact.
- `close_job_rejected_in_creation_slot` runs only in LiteSVM (it needs two
  transactions in the creation slot). Slot-exact deadline checks run only in
  LiteSVM; on the validator those scenarios check "well inside the window" and
  "after the deadline" instead.
- SBPF v3 on devnet was not verified: the box could not reach
  `api.devnet.solana.com` (TLS reset). Mainnet-beta reports the v3 feature
  active since epoch 993 (read-only query).
- Unaudited; not integrated into the Android app; Android files untouched, so
  `qualify.sh` was not re-run.
