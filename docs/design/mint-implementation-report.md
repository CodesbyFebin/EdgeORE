# Mint holographic interface — implementation and verification

Date: 12 October 2026. This report describes source work and recorded checks, not production qualification.

## Source and scope

Branch: `feature/mint-holographic-design`. Original workspace base: `260222e318d8a1deccff15abcdbdbe11ca4e11e4`. Current `main` (`bca02efea46a19b928c127eb13fcbd7156a095a4`) was integrated before final qualification. Initial implementation commit: `cbc4b395ef03de0ae4e9958e449e5203a4b4dd45`; final tested implementation: `164f04e4b846c158d2cd782c5f235c218aefa0d8`. The upstream `0.1.0-clockin` / versionCode 12 and explicit read-only ORE unavailability are retained. Later commits only carry screenshots, documentation and evidence.

The user explicitly requested the design implementation; this authorizes the Android edits despite the historical handoff's device freeze. The uploaded specification is a desired target, not a record of completed work. Exact-message binding, durable submission and observation-only recovery remain unchanged. No transaction, deployment, reward payout or release publication was performed.

## What changed

- Shared obsidian/graphite/mint palette, orange wordmark/Mine selection, gradient cards, consistent rounded controls and inputs, outlined environment badge, review/settings shortcuts and updated five-destination navigation.
- Mine now groups one wallet connection, three asset states, a network observation selector, a contained holographic mining ring, four native resource cards, per-worker readiness, daily campaign readiness and the real read-only 5×5 ORE observer.
- AI bubbles, Nodes, Receipts, review, storage, dialogs and empty/loading states inherit the same theme and shared controls. Receipt empty states use the common component.
- Native MWA connection sheet discovers installed candidates and explains authorization; no hardcoded wallet-app success. RainbowKit is not used: it is an Ethereum web stack, whereas this app is native Solana Android.
- Public balance reader verifies network genesis, reads integer-safe SOL, validates the pinned mainnet ORE mint and sums matching owner token accounts. Failed reads retain stale amounts and their original timestamps; account/network/generation guards refuse obsolete responses.
- Android telemetry uses CPU runtime capacity, ActivityManager memory, StatFs app-filesystem capacity, connection type and app UID traffic counters. No fixture CPU/memory/storage values or invented rates reach production.
- Mining waits for the first worker round result, freezes outside ACTIVE, offscreen/background, reduced motion and power saving. Decorative particles never touch balances.
- Deterministic ORE Bot help reads current app state. Optional WorkManager reminders require consent and notification permission; quiet hours and daily deduplication apply. Lock-screen text omits wallet and job details.

## Integration matrix

| Feature | Implementation | Runtime qualification |
|---|---|---|
| MWA discovery/authorization sheet | Implemented; existing MWA transport retained | NOT_RUN — no Android runtime attached |
| Devnet reviewed SOL transfer, durable submission, recovery | Existing path preserved | NOT_RUN for this artifact |
| SOL observation on Devnet/Testnet/Mainnet | Implemented with genesis validation | BLOCKED here — public RPC denied by environment policy |
| Mainnet ORE owner balance | Implemented from pinned upstream mint; on-chain owner/decimal checks | BLOCKED here — public RPC denied |
| ORE/SKR on development networks | No verified deployment; explicit unavailable state | NOT_RUN |
| SKR owner balance | NOT_IMPLEMENTED — verified authoritative mint registry needed | NOT_RUN |
| Mainnet/Testnet wallet transactions | NOT_IMPLEMENTED; selector is visibly read-only | NOT_RUN |
| Node proof + owned-gateway session | Existing real execution path retained | NOT_RUN on phone |
| Bandwidth relay / billable cloud hosting | NOT_IMPLEMENTED; Setup required | Providers not supplied |
| Daily five-start/one-hour server policy | NOT_IMPLEMENTED; no local eligibility counter | Server/auth/lease integration needed |
| Daily check-in / official ORE payout | NOT_IMPLEMENTED; Rewards unavailable | Campaign rules, sponsor treasury and verified payout path needed |
| 25-cell ORE mainnet board | Existing validated observer retained; unknown cells stay unknown | BLOCKED here — public RPC denied |
| Interactive ORE transaction deployment | NOT_IMPLEMENTED; explicit unavailable label | Protocol adapter and full review/integration gates needed |
| Local reminder scheduling | Implemented; opt-in, permission, quiet hours, deduplication | Delivery NOT_RUN on device |
| Backend notification inbox / push | NOT_IMPLEMENTED; explicitly disclosed | Provider/configuration needed |
| ORE Bot basic help | Implemented, deterministic, no financial authority | JVM interaction checks; device NOT_RUN |
| Enhanced bot conversation | NOT_IMPLEMENTED in help bubble; existing AI screen remains separate | Owned-host/local AI runtime NOT_RUN |
| Official token logo assets | Not present; neutral outlines used | Brand asset integration remains open |

Wallet/platform capability matrix: Android/Seeker MWA candidates are discovered at runtime; specific wallets are **not yet tested** on this artifact. Web Wallet Standard/EVM/RainbowKit are not part of this native application. No claim of universal wallet compatibility.

## Repeatable setup and checks

Use JDK 17, Android SDK 35 and the pinned Gradle wrapper. No dependencies or catalog versions were added or changed.

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:processReleaseMainManifest
./gradlew --no-daemon :app:recordRoborazziDebug -Pscreens
./gradlew --no-daemon :app:verifyRoborazziDebug -Pscreens
./gradlew --no-daemon -p tools/receipt-checker test --rerun-tasks
python3 scripts/build-policy.py
python3 scripts/test_check_manifest.py
python3 scripts/check-manifest.py app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml
```

Screenshot baselines are intentionally updated for this design; they are Robolectric renders, **not phone captures**. The unavailable-telemetry, fixed help-clock and clearly labelled JVM build-stamp seams are test-only. Runtime Settings still shows the real APK build stamp. No successful balance or reward fixture is used on the live interface.

On a device, install the exact debug APK, compare its digest and Settings build stamp, then run the wallet/device runbook. Tap **Signer identity** to reveal the receipt key identity for Gate 8; the default Settings disclosure shows protection without the changing key ID. Approve a specific devnet transfer separately before signing or submitting. Public balance observations require reachable Solana RPC endpoints; this environment's CONNECT policy denies them. Never work around that policy.

For node/storage sessions, pair the pinned node in Nodes and configure the HTTPS/SPKI-pinned gateway in Storage before Mine. Set limits, enable Edge Mode and satisfy the existing Wi-Fi/charge/thermal/battery gates. Additional bandwidth/cloud providers cannot be started by a UI alone.

Reminders: Settings → Optional daily reminder → Android permission. Disable the toggle to cancel the unique periodic job. Android may delay delivery. These are general local help reminders, not eligibility notifications or evidence of earning ORE.

## Recorded verification

Final commands, counts, logs and APK identity are recorded in `evidence/mint-holographic/RESULTS.md` after the final gates finish. A successful compile or render does not close the integration matrix above.

The existing Android CI evidence artifact now also includes the debug APK and instrumented-test APK; it is not a release publication. CI artifacts carry their own build digest/stamp and must not be conflated with the build-host APK.
