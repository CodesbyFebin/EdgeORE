# EdgeORE principles

This page only lists what EdgeORE does **now**, with a link to the code or doc you can check. Anything planned is marked **Not yet**. EdgeORE is a hackathon build and not production-ready. It is not affiliated with Regolith Labs (ORE) or Solana Mobile. It makes no claim about yield, APR, income or rewards. Nothing in it is quantum-safe.

## Core Protocol

- EdgeORE is a client for the ORE v3 program by Regolith Labs. It builds the Deploy instruction layout pinned to upstream commit `48c203bd`. See [`OreV3.kt`](../app/src/main/java/com/edgeore/app/ore/OreV3.kt) and [`OreDeployMessage.kt`](../app/src/main/java/com/edgeore/app/solana/OreDeployMessage.kt).
- Round rules (RNG from `slot_hash`, winning square, motherlode check, fees, solo/split mask) are re-implemented in [`RoundRules.kt`](../app/src/main/java/com/edgeore/app/edge/RoundRules.kt) and documented in [`docs/edge/M1-ORE-GROUND-TRUTH.md`](edge/M1-ORE-GROUND-TRUTH.md).
- At startup the app compares the on-chain program and Config with what this build expects. If they differ, it shows a "protocol changed" banner. See [`ProtocolGuard.kt`](../app/src/main/java/com/edgeore/app/edge/ProtocolGuard.kt).
- **Not yet:** support for any later ORE program version. On a mismatch the app warns instead of adapting.

## Privacy & Consent

- No EdgeORE account and no EdgeORE server. Receipts, history and crew plans stay on the phone. Crew plans are shared only by QR, link or file ([`Crew.kt`](../app/src/main/java/com/edgeore/app/edge/Crew.kt)).
- Android backup is off (`android:allowBackup="false"` in [`AndroidManifest.xml`](../app/src/main/AndroidManifest.xml)).
- Notifications are opt-in. On Android 13+ the app asks for `POST_NOTIFICATIONS`. Alerts come from read-only polls while the app is open ([`RoundAlertNotifier.kt`](../app/src/main/java/com/edgeore/app/notifications/RoundAlertNotifier.kt)).
- Privacy policy: [`docs/legal/PRIVACY.md`](legal/PRIVACY.md).
- **Not yet:** an independent privacy audit.

## Security

- The app never holds wallet keys. Every transaction is signed in your wallet through Mobile Wallet Adapter ([`wallet/`](../app/src/main/java/com/edgeore/app/wallet/)).
- Before you sign, the review screen decodes the exact message bytes ([`ReviewScreen.kt`](../app/src/main/java/com/edgeore/app/ui/screens/ReviewScreen.kt)).
- Mainnet ORE deploys are capped at 0.01 SOL (`MAINNET_CAP_LAMPORTS` in [`OreDeployReview.kt`](../app/src/main/java/com/edgeore/app/solana/OreDeployReview.kt)). They also need a risk tick, and Rounds stays at 1. Nothing deploys automatically.
- Key safety guidance: see [README · Key safety](../README.md#key-safety).
- CI runs CodeQL, a MobSF static scan, detekt and ktlint ([`.github/workflows/`](../.github/workflows/)).
- **Not yet:** a third-party security audit, or any post-quantum or hash-based signatures.

## Futures Worth Building

- **Not yet:** a hash-based backup signature for receipts and Round Proofs. This only makes sense if and when Solana supports such signatures. Nothing like it is implemented or claimed.
- **Not yet:** on-device verification of the slot-hash source itself. Today's Round Proof leaves this unverified ([`docs/edge/round-proof.md`](edge/round-proof.md)).
- **Not yet:** support for future ORE program versions behind the protocol guard.

## Users, Builders & Agents

- The AI explainer gives plain-language explanations and can never sign. If no model is configured it says "Not configured" and uses a rule-based explanation ([`EdgeInsights.kt`](../app/src/main/java/com/edgeore/app/edge/EdgeInsights.kt)).
- Strategy presets only pre-fill the review. You still review and sign each deploy yourself.
- Builders can check exported proofs offline with [`tools/receipt-checker`](../tools/receipt-checker/). A Rust reference for the round rules is in [`tools/ore-rules-reference`](../tools/ore-rules-reference/).
- **Not yet:** a public API or SDK for other apps.

## Rights, Freedoms & Governance

- Crew Pool is non-custodial: no deposit and no shared wallet. Each member deploys their own share from their own wallet. Any settlement is done manually by the members ([`Crew.kt`](../app/src/main/java/com/edgeore/app/edge/Crew.kt)).
- EdgeORE has no token, and `$EdgeORE` is only a product name. Terms: [`docs/legal/TERMS.md`](legal/TERMS.md).
- **Not yet:** any governance process. EdgeORE is a solo hackathon project.

## Applied Cryptography

- Receipts are signed with a key in Android Keystore. If Keystore isn't available, the app falls back to a software key and says so ([`Receipts.kt`](../app/src/main/java/com/edgeore/app/receipts/Receipts.kt)).
- Wallet signatures are checked with Ed25519 ([`Ed25519.kt`](../app/src/main/java/com/edgeore/app/crypto/Ed25519.kt)). Crew plans are signed, so tampering can be detected.
- Round Proofs use canonical JSON plus SHA-256 digests and verify the integrity of signed receipt contents ([`OreProofV0.kt`](../app/src/main/java/com/edgeore/app/edge/OreProofV0.kt)). They make no fairness guarantee beyond the steps listed as verified.
- **Not yet:** quantum-safe signatures. Ed25519 is not post-quantum.

## Permissionless Networks

- The app reads Solana mainnet and devnet over public JSON-RPC ([`SolanaRpc.kt`](../app/src/main/java/com/edgeore/app/solana/SolanaRpc.kt)). On-chain ORE reads are read-only ([`AccountReader.kt`](../app/src/main/java/com/edgeore/app/solana/AccountReader.kt)).
- Transfers use devnet. The only mainnet action is the capped ORE deploy that you sign yourself.
- The website's live round ticker reads RPC in the browser and falls back gracefully if that fails.
- **Not yet:** choosing your own RPC endpoint in the UI.

## Open & Verifiable Stack

- Source is Apache-2.0 ([`LICENSE`](../LICENSE)); upstream attribution is in [`NOTICE`](../NOTICE).
- Every build is stamped with its source commit ([`evidence/COMMIT-STAMP.json`](../evidence/COMMIT-STAMP.json)), and CI checks the stamp.
- Screenshots are JVM renders kept as Roborazzi reference images that CI compares ([`docs/screenshots/`](screenshots/)).
- **Not yet:** reproducible release builds, or a signed release in a store.
