# HACKATHON.md: EdgeORE (CLOCK IN, Solana Mobile)

**EdgeORE is an Android ORE client for Solana Seeker. Every ORE deploy is reviewed, capped, signed in your own wallet, and receipted.**

**Category:** Mobile. No other tracks are claimed.

**Built:** a live ORE v3 mainnet board and grid. An opt-in Mainnet switch with a risk tick. A 0.01 SOL per-deploy cap. Exact-message review, MWA signing (the app never holds keys), and a single submit with no auto-retry. Android Keystore signed receipts. A receipt checker that verifies the integrity of signed receipt contents. A daily check-in: streak and points saved on your phone. The optional AI advisor, node and storage need your own setup and show a *Set up* step until configured.

**Evidence:** tests against the live ORE mainnet program (local fork, LiteSVM, not live transactions) (two runs, rounds 437646 and 437756, program sha256 `b16a1002…a7dd5bd`). Devnet evidence is in [`evidence/devnet-session/`](../../evidence/devnet-session/README.md). Device mainnet signing: pending device recording.

**Tests (separate, never summed):** Android 390 run / 382 passed / 8 skipped · receipt checker 17 · LiteSVM 27 · live ORE mainnet program, local fork 2.

**Rules:** one approval per transaction, you approve every deploy, no reward or income claims, Hackathon build 0.1.0-clockin.

**Next:** claim/checkpoint and more device testing. **Research** (no timelines, no partnerships): ORE SDK, Reserve viewer, DePIN workloads, TEE/ZK.

Full package: [`EdgeORE-pitch.md`](EdgeORE-pitch.md) · submission fields: [`../submission/SUBMISSION.md`](../submission/SUBMISSION.md) · demo: [`../submission/DEMO-SCRIPT.md`](../submission/DEMO-SCRIPT.md).
