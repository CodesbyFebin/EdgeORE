# EdgeORE

<!-- commit-stamp:begin -->
Canonical source commit: `76cb0e8d53a7c6d51e18e36ad459c98b568396e7` (stamped by `scripts/commit-stamp.sh`; the same value is in `evidence/COMMIT-STAMP.json` and the APK as `BuildConfig.SOURCE_STAMP`).
<!-- commit-stamp:end -->
<img width="2172" height="724" alt="image" src="https://github.com/user-attachments/assets/52f6707b-7af5-4092-9bea-6e21f45054f7" />

**EdgeORE is an Android ORE client for Solana Seeker. Every ORE deploy is reviewed, capped, signed in your own wallet, and receipted.**

Version `0.1.0-clockin` (versionCode 12) · package `com.edgeore.app` · Kotlin / Jetpack Compose · Solana Mobile Wallet Adapter · Built by **[CodesbyFebin](https://github.com/CodesbyFebin)**

Hackathon package: [`docs/pitch/HACKATHON.md`](docs/pitch/HACKATHON.md) · [`docs/pitch/EdgeORE-pitch.md`](docs/pitch/EdgeORE-pitch.md) · [`docs/submission/`](docs/submission/)

## What is built

| Feature | What it does |
|---|---|
| Live ORE v3 board | Reads the mainnet Board and Round accounts and shows a 5×5 grid with per-square SOL, round and motherlode. |
| Opt-in Mainnet switch | ORE Deploy starts on devnet. Mainnet only after you tap **Mainnet** and tick "I understand this deploy uses real SOL that I may lose". |
| 0.01 SOL cap | Each mainnet deploy is capped at 0.01 SOL in total across the selected squares. An over-cap amount is refused before any message or wallet request exists. Account rent and network fees are additional; this is not a total-wallet-debit cap. |
| Exact-message review | The review screen decodes the exact bytes: program, round, squares, amount per square, total, fee, blockhash and message SHA-256. Mainnet shows a copper warning panel. |
| MWA signing | Your wallet signs through Mobile Wallet Adapter. The app never holds wallet private keys. The returned bytes must match the review, or the result is refused. |
| Single submit | Submit is a separate tap. The attempt is recorded before sending. A timeout stays "outcome unknown". No automatic retry. |
| Signed receipts | Every outcome becomes a receipt signed with an Android Keystore key (labelled weaker software fallback if Keystore is unavailable). |
| Receipt checker | A standalone JVM checker verifies the integrity of signed receipt contents. It does not prove an on-chain outcome. |
| Daily check-in | Streak and points saved on your phone. |
| Optional extras | AI advisor host, node agent and storage gateway need your own setup. Until then they show a *Set up* step with the guidance. The AI advisor never signs or deploys. |

## Verified evidence

- **Tested against the live ORE mainnet program (local fork, LiteSVM), two runs.** `tools/ore-fork-sim` dumps the live mainnet ORE v3 program (sha256 `b16a10029a35e709956cfe42c4dc51cf2c59522f477eba10816a40bcaa7dd5bd`) and a read-only account snapshot. It then runs the app's exact Deploy bytes. Round 437646 and round 437756 both passed: +400,000 lamports on each of 25 squares for a 0.01 SOL deploy, and a stale-round deploy was rejected. The app's bytes match an independent Rust derivation from [regolith-labs/ore](https://github.com/regolith-labs/ore) @ `48c203bd`. This is a local fork run, not a live transaction: nothing was sent to any cluster. Details: [`docs/ore/MAINNET-DEPLOY.md`](docs/ore/MAINNET-DEPLOY.md).
- **Devnet evidence:** [`evidence/devnet-session/`](evidence/devnet-session/README.md) has the receipt-settlement program, a receipt checkpoint, and a verified v3 receipt. That transfer and checkpoint were sent from a laptop CLI, not from the app.
- **Pending device recording:** MWA signing of a mainnet deploy on a real device is pending device recording.

### Historical tests

These recorded counts describe prior source revisions, not the consolidated submission candidate. Fresh candidate results are recorded in `evidence/submission-hardening/`.

| Suite | Result |
|---|---|
| Android unit tests | 390 run · 382 passed · 8 skipped · 0 failed |
| Receipt checker (JVM) | 17 passed |
| LiteSVM receipt-settlement suite | 27 passed |
| Live ORE mainnet program, local fork (`tools/ore-fork-sim`) | 2 passed |

Each suite is counted on its own. The counts are not added together.

## Rules the app keeps

- One wallet approval per transaction. Every deploy is reviewed and signed by you.
- You approve every deploy. No automatic retry or resend.
- No reward, income or earnings claims. Deployed SOL may be lost entirely.
- Hackathon build 0.1.0-clockin.

## Next

- ORE checkpoint and claim, through the same review → sign → submit → receipt path.
- More real-device testing across wallets and Seeker.

## Research (exploratory; no timelines, no partnerships)

- An ORE SDK layer for other Android apps.
- A Reserve viewer.
- DePIN workloads on hardware the user owns.
- TEE / ZK attestation of receipts.

## Build and install

Requirements: **JDK 17**, Android SDK platform **35**, and the repository's **Gradle 8.9** wrapper. Minimum Android version: **8.0 / API 26**. Target and compile SDK: **35**.

```bash
git clone https://github.com/CodesbyFebin/EdgeORE.git
cd EdgeORE
# Configure the SDK through Android Studio or your local.properties file.
bash scripts/qualify.sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`qualify.sh` records the build log, parsed JUnit results, lint and APK identity under `evidence/build-<commit>/`. A build pass is not a wallet or device pass. Keep the tested source commit distinct from a later commit that only stores evidence.

For instrumented tests on a connected Android runtime:

```bash
./gradlew connectedDebugAndroidTest
```

A debug-signed APK cannot update an installation signed with another certificate. Preserve any device-bound vault data before changing installations; uninstalling removes app data and keys.

## Verify a receipt outside Android

The JVM checker compiles the **existing Kotlin verifier**, rather than maintaining a separate verification implementation. It needs Java 17 and initial dependency bootstrap; no Android SDK is required.

```bash
bash gradlew -p tools/receipt-checker test installDist
bash scripts/verify-receipt.sh original.json
bash scripts/verify-receipt.sh receipt-tampered.json
# Optional: pin a DER SubjectPublicKeyInfo obtained through a trusted channel.
bash scripts/verify-receipt.sh original.json --trusted-key device-spki.der
```

Exit codes: **0 accepted**, **1 rejected**, **2 invocation/read/build error**. After bootstrap, the installed checker can run without Gradle or network access; see [checker instructions](tools/receipt-checker/README.md).

For a meaningful tamper demo, preserve the original and change `lamports` inside one receipt's signed `body` in a copy, keeping valid JSON and leaving the recorded digest and signature unchanged.

**Verification boundary:** signatures protect signed receipt contents, not every descriptive field in the export. Self-contained integrity under an included key does not establish trusted provenance. Offline verification does not confirm a transaction on Solana or prove physical work, provider acknowledgement or payment.

## License and attribution

EdgeORE is licensed under the [Apache License 2.0](LICENSE) (added in the hardening-20 pass, Oct 2026). This was the reviewer-recommended default; the owner (CodesbyFebin) may still choose a different license before release. Dependencies retain their own licenses; see [`NOTICE`](NOTICE) for Google AI Edge Gallery (Apache-2.0), LiteRT-LM (Apache-2.0) and ZXing core (Apache-2.0). The first-run introduction, Now overview, QR address entry and plain-language review layer are adapted from **Clearance**, CodesbyFebin's own earlier prior-work Android prototype; nothing from it that sends wallet data off the phone, claims SKR staking or rewards, or simulates signatures was carried over. Upstream node-agent provenance is pinned separately: the `deproof-node` agent is built from [CodesbyFebin/DeProof--EdgeORE](https://github.com/CodesbyFebin/DeProof--EdgeORE) (MIT-licensed upstream) at the revision recorded in [`scripts/node-agent.pin`](scripts/node-agent.pin).

---

**Built by CodesbyFebin.** EdgeORE is not affiliated with Regolith Labs or Solana Mobile.
