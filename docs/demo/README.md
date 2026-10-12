# Mint review APK and video

This candidate preserves the mint redesign and ORE deployment adapter merged in `main` at `6ba12a50e42d4d120cf085537354b5226e73175d`. It is a **debug-signed review APK**, not a signed production release.

## Downloads

The Android gate artifact on this branch includes `app/build/outputs/apk/debug/app-debug.apk`, its instrumented test APK, and build evidence. Download the ZIP from the linked Actions run and extract the debug APK. The ZIP hash is not the APK hash; use the build summary inside for that artifact's APK identity.

The accompanying video is a **JVM UI preview**, assembled from fresh Robolectric renders. Every shot is labelled. It is not a phone screen recording or evidence of wallet authorization, a transaction, a payout, notifications, a paired provider, or an active mining session. Its final media link and provenance are recorded in `DELIVERY.md` after export.

## What is wired, and what remains gated

- Native Mobile Wallet Adapter connection and exact-message review paths are retained; transactions are devnet-only. No current physical-wallet session has been demonstrated here.
- SOL, ORE and SKR observations use the merged cluster/token registry. Testnet/mainnet selection is read-only. RPC failures and unavailable mints must stay unavailable, never become zero balances.
- ORE deployment is gated by the presence of its program and required accounts on devnet. An adapter in source does not mean a deployed devnet ORE service or a successful transaction.
- Daily check-in uses local one-hour slots and non-spendable points. It does not pay free ORE; it is not a server-authoritative reward campaign.
- Owned-node proof jobs and storage-gateway sessions require pairing/configuration. Fresh physical-device provider gates are not claimed. Bandwidth relay and cloud hosting require separate providers.
- ORE Bot help and reminders retain their existing boundaries. Notification delivery, physical Seeker, real wallet transfer, confirmed receipt and release signing remain unqualified.

## Installed-app recording still needed

This environment has no KVM or physical phone. Android 15 failed to reach a usable app runtime. A software Android 9 emulator installed an earlier review APK and rendered onboarding, but repeated System UI freezes and input/display problems prevented a usable walkthrough. Those attempts do not qualify the latest merged-source APK or close any wallet/backend gate.

On a stable phone or hardware-accelerated emulator:

1. Install the APK from the matching Android artifact and record its SHA-256 and About stamp.
2. Cold launch, read onboarding, then show Mine, network selection and the wallet chooser. Keep absent wallets and unread balances visible.
3. Show actual check-in state and actual worker configuration. Start only a configured owned session; show its real results, then stop it.
4. Visit AI, Storage, Nodes, Receipts, settings and transaction review. A successful transaction requires separate authorization for its exact reviewed message.
5. Keep raw recording and environment details. Label every unrun feature. Export an actual receipt only after its corresponding action happened.

Publishing this preview or downloading an APK does not change those evidence gates. dApp Store publication remains separate from the hackathon submission.
