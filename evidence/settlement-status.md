# Settlement program status

**Program:** `onchain/programs/receipt-settlement/` — devnet-only prototype, unaudited. Canonical reference: [`docs/SETTLEMENT-SPEC.md`](https://github.com/CodesbyFebin/EdgeORE/blob/38fb71d5c25ef944152edecfd128ab12e0fce2e0/docs/SETTLEMENT-SPEC.md) (v2: `create_job / accept_job / submit_proof / verify_and_settle / refund_after_deadline`).

**Merged into `main`:** PR [#8](https://github.com/CodesbyFebin/EdgeORE/pull/8), merge commit [`38fb71d5c25e`](https://github.com/CodesbyFebin/EdgeORE/commit/38fb71d5c25ef944152edecfd128ab12e0fce2e0) (`38fb71d5c25ef944152edecfd128ab12e0fce2e0`), which brought in `feature/settlement-spec-v2` @ `305f1e7` via the integration branch.

## Test ledger

Tested source `047657f89b48` (evidence-only head `305f1e7`).

| Environment | Result |
|---|---|
| LiteSVM | **25/25 passed, exit 0** |
| Local solana-test-validator, Agave 4.3.0 | **25/25 passed, exit 0** |

Program artifact: SBPF v3, `receipt_settlement.so` 274,352 bytes, sha256 `9ce9addf14b1b432305bbcdfaabe25c174b32f00f27e18534e0b996009bf78a7`.

Preserved logs (pinned to the merge commit):
- Branch evidence for `047657f89b48`: [`evidence/anchor-receipt-settlement/047657f89b48/`](https://github.com/CodesbyFebin/EdgeORE/tree/38fb71d5c25ef944152edecfd128ab12e0fce2e0/evidence/anchor-receipt-settlement/047657f89b48) — [SUMMARY.md](https://github.com/CodesbyFebin/EdgeORE/blob/38fb71d5c25ef944152edecfd128ab12e0fce2e0/evidence/anchor-receipt-settlement/047657f89b48/SUMMARY.md), [commands-and-exit-codes.txt](https://github.com/CodesbyFebin/EdgeORE/blob/38fb71d5c25ef944152edecfd128ab12e0fce2e0/evidence/anchor-receipt-settlement/047657f89b48/commands-and-exit-codes.txt), [anchor-test-litesvm.log](https://github.com/CodesbyFebin/EdgeORE/blob/38fb71d5c25ef944152edecfd128ab12e0fce2e0/evidence/anchor-receipt-settlement/047657f89b48/anchor-test-litesvm.log), [anchor-test-validator.log](https://github.com/CodesbyFebin/EdgeORE/blob/38fb71d5c25ef944152edecfd128ab12e0fce2e0/evidence/anchor-receipt-settlement/047657f89b48/anchor-test-validator.log), [validator-transactions.log](https://github.com/CodesbyFebin/EdgeORE/blob/38fb71d5c25ef944152edecfd128ab12e0fce2e0/evidence/anchor-receipt-settlement/047657f89b48/validator-transactions.log), [per-test-results.txt](https://github.com/CodesbyFebin/EdgeORE/blob/38fb71d5c25ef944152edecfd128ab12e0fce2e0/evidence/anchor-receipt-settlement/047657f89b48/per-test-results.txt).
- Re-run on the integration merge `250be029b35c` (same .so sha256; LiteSVM 25/25, exit 0; local validator 25/25, exit 0): [`evidence/integration-0.2.9/gate-250be029b35c/anchor/`](https://github.com/CodesbyFebin/EdgeORE/tree/38fb71d5c25ef944152edecfd128ab12e0fce2e0/evidence/integration-0.2.9/gate-250be029b35c/anchor).

## What these results do not establish

Passing tests in LiteSVM and on a local validator do **not** establish:

- **Deployment.** The program has not been deployed to devnet or mainnet. The J-4 deploy gate ([`docs/DEPLOY-DEVNET.md`](https://github.com/CodesbyFebin/EdgeORE/blob/38fb71d5c25ef944152edecfd128ab12e0fce2e0/docs/DEPLOY-DEVNET.md), `scripts/deploy-devnet.sh`) is prepared and **NOT RUN**. Once deployed on devnet, the program will be upgradeable.
- **Android integration.** The EdgeORE app has no client for this program; no program ID is embedded and nothing in the app calls it.
- **A real SOL payout.** All SOL in the tests came from LiteSVM or the local test faucet. No settlement has paid anyone on any public cluster.

Deployment, Android integration and real SOL payout remain unqualified until demonstrated with saved evidence. No token, reward, yield or mining is involved, and no ORE integration is claimed.
