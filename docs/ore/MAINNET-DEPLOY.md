# ORE v3 mainnet Deploy (owner-approved, capped)

Owner decision 2026-10-12: EdgeORE may build a **mainnet** ORE v3 Deploy, capped at **0.01 SOL per deploy**
(10,000,000 lamports total across the selected squares), signed by the owner's own wallet through MWA.

Deploying risks the SOL: it may be lost entirely. EdgeORE makes no claim about mining income, returns or production readiness.

## Rules enforced in code
- Devnet is the default. Mainnet only after the owner taps **Mainnet** on the Mine card's network switch and ticks
  "I understand this deploy uses real SOL that I may lose". Switching resets the check, the amount and the tick.
- Cap: `OreDeployLimits.MAINNET_CAP_LAMPORTS = 10_000_000`. It is checked in `prepareOreDeploy` before a blockhash is
  fetched, again in `OreDeployReview.prepare` before any message is built, and on the rounded-down encoded total.
  Over-cap → no message bytes exist, so nothing can reach a wallet (`OreMainnetCapTest`).
- Same trust spine as a transfer: exact-message review (copper #FFA365 mainnet panel: amount in SOL, round id, squares),
  MWA sign by the owner's wallet (session authorized for `solana:mainnet` for this one request), returned-bytes check,
  separate Submit, durable ledger, receipts. No auto-deploy, no retry, no resend. The AI advisor cannot sign or trigger.
- An operation's recorded cluster decides where it is sent and observed (`TransferCoordinator` gateway per cluster);
  transfers stay devnet only.

## Verification
- Unit: `OreMainnetCapTest`, `OreDeployMessageTest`, `OreReadOnlyArchitectureTest`.
- Mainnet fork (LiteSVM): `tools/ore-fork-sim`. `fetch.py` takes a read-only mainnet snapshot (Board, Treasury, Config,
  entropy Var, current Round; program ELFs dumped with `solana program dump`). The app's unit test writes its exact
  Deploy bytes for that round; `cargo test` checks them against an independent Rust derivation of the ORE source
  (regolith-labs/ore @48c203bd sdk.rs `deploy()`), and runs them against the dumped program. Sends nothing to any cluster.
  ```sh
  cd tools/ore-fork-sim && python3 fetch.py
  (cd ../.. && ./gradlew :app:testDebugUnitTest --tests com.edgeore.app.OreMainnetCapTest)
  cargo test -- --nocapture
  ```
- **Not verified:** MWA signing of a mainnet transaction on a real device and wallet, and a real mainnet landing.
  Those happen only when the owner does a deploy.

## Notes
- A first deploy also creates the miner account (about 0.0045 SOL rent in the fork run) plus the network fee, on top of
  the deployed amount. MAX keeps 0.01 SOL back for this; you need at least about 0.02 SOL on mainnet.
- Rounds are short (about a minute). If the round ends between review and submit, the program rejects the deploy
  (the network fee may still be charged). Review again for the new round; never resend old bytes.
