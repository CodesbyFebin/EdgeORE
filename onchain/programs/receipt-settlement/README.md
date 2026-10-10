# receipt-settlement (devnet-only prototype)

> **Status: devnet-only, unaudited, NOT deployed anywhere, NOT integrated into
> the EdgeORE Android app.** There is no token, no reward, no yield and no
> mining in this program. It has not been reviewed by any security auditor and
> must not hold real funds. It is not an ORE integration and makes no claim
> about ORE.

**The canonical reference is [`docs/SETTLEMENT-SPEC.md`](../../../docs/SETTLEMENT-SPEC.md) (v2)**:
instructions, account layouts, PDA seeds, the signed digest, errors, events,
the time model and the test-environment notes. This README only covers how
to build and test.

Flow (spec vocabulary, 1:1 with the program's instructions):
`create_job` -> `accept_job` -> `submit_proof` -> `verify_and_settle` ->
`refund_after_deadline` (permissionless crank), plus `rotate_verifier`
(before the first accept only), `close_assignment` and `close_job` for rent.

Deployment to devnet is prepared but not run: `scripts/deploy-devnet.sh` and
`docs/DEPLOY-DEVNET.md` (the deployed program would be upgradeable).

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

The v3 feature (`5cC3foj77CWun58pC51ebHFUWavHWKarWyR5UUik7dnC`) is active on
mainnet-beta (since epoch 993) and on devnet (since epoch 1069, checked via a
third-party devnet RPC because `api.devnet.solana.com` was unreachable from the
build box; see `docs/DEPLOY-DEVNET.md`).

## Build and test

From `onchain/`, with the Agave 4.3.0 binaries first on `PATH`:

```sh
anchor build                                    # SBPF v3

# 1) LiteSVM, in-process (25 scenarios)
anchor test --skip-build --skip-local-validator --skip-deploy
                                                # runs `cargo test --test settlement`

# 2) Local solana-test-validator (the same 25 scenarios)
anchor test --skip-build --validator legacy --script validator \
  --provider.wallet <throwaway local keypair outside the repo>
```

In mode 1, `--skip-deploy` is required. With `--skip-local-validator` alone,
Anchor first tries to deploy to the configured localnet URL (127.0.0.1).

Mode 2 starts `solana-test-validator` with the program loaded at genesis
(`--bpf-program`), points `ANCHOR_PROVIDER_URL` at it and runs
`cargo test --test validator -- --ignored`. The harness refuses any non-local
RPC URL. All SOL comes from the local test faucet; no real funds are involved.

In validator mode every transaction, including expected failures, is **sent**
(`skip_preflight`) and its result, logs, fee and slot are read back from
`getTransaction` after `confirmed`; each one is appended to
`target/tmp/validator-transactions.log`. Simulation is only a fallback for a
transaction that never lands. Why: see SETTLEMENT-SPEC.md section 9.

Scenarios live once in `tests/common/mod.rs`; `tests/settlement.rs` and
`tests/validator.rs` instantiate the same list.
