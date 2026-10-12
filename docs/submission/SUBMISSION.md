# EdgeORE: CLOCK IN submission fields (copy-paste)

Deadline: 13 Oct 2026, 05:29 IST. Category: Mobile. No other tracks are claimed. Registered solo.

**Project name:** EdgeORE

**One-liner:** EdgeORE is an Android ORE client for Solana Seeker. Every ORE deploy is reviewed, capped, signed in your own wallet, and receipted.

**Description:**
EdgeORE is a Kotlin/Compose Android app for ORE v3 on Solana. It shows the live mainnet ORE board. It lets you deploy SOL into a round only through an opt-in Mainnet switch with a risk tick and a hard 0.01 SOL per-deploy cap. Every deploy goes through an exact-message review, is signed in your own wallet via Mobile Wallet Adapter (the app never holds keys), is checked byte-for-byte on return, and is submitted once with a separate tap. You approve every deploy, and there is no auto-retry. Each outcome becomes an Android Keystore signed receipt, and an open-source checker verifies the integrity of signed receipt contents. A daily check-in keeps a streak and points saved on your phone. The optional AI advisor, node and storage show a *Set up* step until you configure them.

**Status:**
- Hackathon build 0.1.0-clockin. No reward, income or earnings claims. Deployed SOL may be lost.
- Tested against the live ORE mainnet program (local fork, not a live transaction): two runs (rounds 437646 and 437756), program sha256 `b16a1002…a7dd5bd`. Both passed. Nothing was sent.
- Devnet evidence: `evidence/devnet-session/` (sent from a laptop CLI, not the app).
- Device mainnet MWA signing: [FILL IN after recording: "shown in the demo video, tx <signature>" OR "pending device recording"].
- Tests (separate, never summed): Android 390 run / 382 passed / 8 skipped · receipt checker 17 · LiteSVM 27 · live ORE mainnet program, local fork 2.

**APK link:** [FILL IN: publish the draft release (assets EdgeORE-76eea59-demo-debug.apk / .zip) or link another public APK. A draft link is not installable by judges.]

**Repo link:** https://github.com/CodesbyFebin/EdgeORE (public, or private with the Radiants Align GitHub app installed)

**Pitch deck link:** [FILL IN]

**Demo video link:** [FILL IN: real-device recording, about 3 minutes]

**Wallet for payouts:** already linked on the event site (73Cc…R93n).
