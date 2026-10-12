# HACKATHON.md — EdgeORE

**Status:** Solana Mobile hackathon submissions closed 8 Oct 2026. This file is a retrospective and a dApp Store launch note (12 Oct 2026), not a live submission.

**Elevator pitch.** Every wallet action on Seeker produces a signed receipt you can verify offline — including the ones you refuse. EdgeORE is a native Kotlin/Compose Android app: review exact bytes → sign via Mobile Wallet Adapter → verify returned bytes → submit separately → export a receipt. A standalone JVM checker verifies the integrity of signed receipt contents.

**Fit (generic, no tracks claimed).** Consumer mobile security · DePIN tooling on infrastructure the user owns · read-only ORE v3 board analysis.

**What's different.** Signing and submission are separate; unknown outcomes are never auto-resent; refusals produce receipts too.

**Evidence (84a788cbd926, counts never summed).** Android 330 tests (325 passed, 5 skipped) · receipt checker 14 · gateway 17 · extension kit 22 · build-policy tests 12 · release-manifest checker tests 9 · lint clean. Device gates: NOT_RUN.

**Boundaries.** Devnet transactions only. No ORE/SKR rewards, mining income or token. ORE board is read-only; no deploy path. Not production-ready.

Full package: [`EdgeORE-pitch.md`](EdgeORE-pitch.md).
