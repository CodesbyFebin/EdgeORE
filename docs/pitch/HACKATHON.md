# HACKATHON.md: EdgeORE (CLOCK IN, Solana Mobile)

**EdgeORE is an Android ORE client for Solana Seeker. Every ORE deploy is reviewed, capped, signed in your own wallet, and receipted.**

**Category:** Mobile. No other tracks are claimed.

**Built:** a live ORE v3 mainnet board and grid. An opt-in Mainnet switch with a risk tick. A 0.01 SOL per-deploy cap. Exact-message review, MWA signing (the app never holds keys), and a single submit with no auto-retry. Android Keystore signed receipts. A receipt checker that verifies the integrity of signed receipt contents. A daily check-in streak and points (no token reward). The optional AI advisor, node and storage need your own setup and show *Not configured* without it.

**Evidence:** a LiteSVM fork sim against the live mainnet ORE program (two runs, rounds 437646 and 437756, program sha256 `b16a1002…a7dd5bd`). Devnet evidence is in [`evidence/devnet-session/`](../../evidence/devnet-session/README.md). Device mainnet signing is UNVERIFIED until the owner records it.

**Tests (separate, never summed):** Android 386 run / 379 passed / 7 skipped · receipt checker 17 · LiteSVM 27 · ORE fork sim 2.

**Rules:** one approval per transaction, no background spending, no reward or income claims, not production-ready.

**Next:** claim/checkpoint and more device testing. **Research** (no timelines, no partnerships): ORE SDK, Reserve viewer, DePIN workloads, TEE/ZK.

Full package: [`EdgeORE-pitch.md`](EdgeORE-pitch.md) · submission fields: [`../submission/SUBMISSION.md`](../submission/SUBMISSION.md) · demo: [`../submission/DEMO-SCRIPT.md`](../submission/DEMO-SCRIPT.md).
