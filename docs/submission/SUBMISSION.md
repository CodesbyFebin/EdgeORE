# EdgeORE: CLOCK IN submission fields (copy-paste)

Deadline: 13 Oct 2026, 05:29 IST. Category: Mobile. No other tracks are claimed. Registered solo.

**Project name:** EdgeORE

**One-liner:** EdgeORE is an Android ORE client for Solana Seeker. Every ORE deploy is reviewed, capped, signed in your own wallet, and receipted.

**Description:**
EdgeORE is a Kotlin/Compose Android app for ORE v3 on Solana. It shows the live mainnet ORE board. It lets you deploy SOL into a round only through an opt-in Mainnet switch with a risk tick and a hard 0.01 SOL per-deploy cap. Every deploy goes through an exact-message review, is signed in your own wallet via Mobile Wallet Adapter (the app never holds keys), is checked byte-for-byte on return, and is submitted once with a separate tap. There is no auto-retry and no background spending. Each outcome becomes an Android Keystore signed receipt, and an open-source checker verifies the integrity of signed receipt contents. A daily check-in keeps a local streak and points with no token reward. The optional AI advisor, node and storage show *Not configured* until you set them up.

**Status:**
- Not production-ready. No reward, income or earnings claims. Deployed SOL may be lost.
- Fork sim against the live mainnet ORE program: two runs (rounds 437646 and 437756), program sha256 `b16a1002…a7dd5bd`. Both passed. Nothing was sent.
- Devnet evidence: `evidence/devnet-session/` (sent from a laptop CLI, not the app).
- Device mainnet MWA signing: [FILL IN after recording: "shown in the demo video, tx <signature>" OR "UNVERIFIED"].
- Tests (separate, never summed): Android 386 run / 379 passed / 7 skipped · receipt checker 17 · LiteSVM 27 · ORE fork sim 2.

**APK link:** [FILL IN: publish the draft release (assets EdgeORE-76eea59-demo-debug.apk / .zip) or link another public APK. A draft link is not installable by judges.]

**Repo link:** https://github.com/CodesbyFebin/EdgeORE (public, or private with the Radiants Align GitHub app installed)

**Pitch deck link:** [FILL IN]

**Demo video link:** [FILL IN: real-device recording, about 3 minutes]

**Wallet for payouts:** already linked on the event site (73Cc…R93n).
