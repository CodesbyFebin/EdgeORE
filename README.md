# EdgeORE

**Your Edge. Your AI. Your Proof.** · *Review ORE. Control your edge.*

A native Kotlin / Jetpack Compose Android workspace for Solana Mobile: pair a host you own, run private AI on it, review Solana actions byte-for-byte before your wallet signs, and keep tamper-evident receipts.

Built by **CodesbyFebin**. Candidate: **`0.2.6-review` (versionCode 8), signed release APK, Solana devnet only.**

> **Decision: NO-GO for “fully functional” or “ORE earning.”** The source and signed artifact are recorded. Wallet authorization, a confirmed devnet transfer, on-device AI, and a phone walkthrough are **NOT_RUN**. ORE rewards are **Not observed**. Do not treat this repository as a completed mining product.

> **What this app does not do:** it does not mine, earn or promise ORE rewards. It does not issue a token (`$EdgeORE` is a product name). It does not make SKR payments. It never sees your wallet keys. Missing observations are shown as missing, never as zero.

---

## Hackathon submission

| Item | Link |
|---|---|
| Demo video | **TODO** — add link (record from the installed APK, not from mockups) |
| Pitch deck | **TODO** — add link |
| Debug APK | **TODO** — attach `app-debug.apk` to a GitHub release and link it here |
| Track / event | **TODO** |

### Demo journey and coverage

What the code does today. "Verified" names the environment where each step was actually run.

| # | Journey step | Status | What is real | Verified |
|---|---|---|---|---|
| 1 | **Pair host** | **Implemented** | Scoped pairing with the [DeProof node agent](https://github.com/CodesbyFebin/DeProof--EdgeORE/tree/main/node-agent). It uses an expiring single-use challenge, an Ed25519-signed pairing payload and a pinned TLS certificate SHA-256. The fingerprint is shown for you to confirm. | JVM integration test against the real Go agent (pair, replay refused, wrong pin refused). **Not yet run on Android:** whether Android's TLS stack accepts the agent's Ed25519 certificate is unverified (an instrumented test is included). |
| 2 | **Local AI over a private document** | **Partial** | Chat with an Ollama-compatible model server on **your own host** (`/api/tags`, `/api/chat`). A document is read on the phone and sent only to that host. The receipt keeps SHA-256 digests only. "Cloud fallback off" is enforced: public endpoints are refused. | Unit-tested request and endpoint policy. **Not run against a live model.** On-device inference is **not implemented** (no runtime is bundled). |
| 3 | **Review a supported Solana action** | **Implemented (devnet)** | Builds a System Program transfer. Decodes the exact message bytes for review (program, accounts, amount, fee via `getFeeForMessage`, blockhash, message SHA-256). Unknown instructions disable approval, and the daily budget comes from `EdgeOreCore.eligible`. MWA `signTransactions` follows. The returned bytes must equal the reviewed message (`sameMessage`) and the Ed25519 signature must verify, or the result is refused. Optional devnet submit and status observation. | Unit tests for encode/decode, mismatch, forged signature and budget. **The MWA wallet round-trip has not been run on a device with a wallet.** |
| 4 | **Export receipt** | **Implemented** | Append-only JSONL receipt log. Each receipt stores its exact body bytes, the body's SHA-256, a hash-chain link and an Android Keystore P-256 signature. Export goes through `FileProvider` as JSON with a bundle digest. The preview lists what is included and what is excluded. | Unit tests (JVM software key). Keystore signing and the share sheet are **not device-run**. |
| 5 | **Reject tampering** | **Implemented** | The verifier rejects edited bodies, recomputed digests (the signature then fails), a different signing key, removed or reordered receipts (bundle digest and chain), and mismatches with the local copy. The "Run tamper test" button edits a real export and shows the rejection. | 11 unit tests. |
| 6 | **Revoke node access** | **Implemented** | A signed `revoke` command. After revocation the node refuses the session (`SESSION_REVOKED_OR_UNKNOWN`) and the local key is destroyed. If the node is offline, the app shows "Revocation pending" and offers "Forget locally" with an explicit caveat. | JVM integration test against the real agent. |
| – | Storage vault | **Implemented, unverified on a phone** | AES-256-GCM with an Android Keystore key. Device storage totals come from `StatFs`. Traffic since boot is not shared bandwidth. | Unit test of CPU/disk parsers only. Encrypt/decrypt **NOT_RUN** on a device. |
| – | ORE participation | **Not qualified** | Shown as "Not observed". No deploy, no claim, no reward multiplier. | – |
| – | SKR payment | **Not implemented** | Out of scope. SKR is not CPU-mineable in this app. | – |
| – | VPN, cloud sync, bandwidth earning | **Unavailable** | Controls do not start a tunnel, upload, or share traffic. | – |

Build, test and lint results, plus what was **not** run, are recorded in [`evidence/qualification.md`](evidence/qualification.md). No step has been run on a physical device or with a wallet app yet.

## Screens (Compose, design spec v1.0)

All four destinations use the same token and component system: graphite surfaces, copper primary actions, mint accents and a natively drawn three-bar E mark.

- **Mine** — Edge Mode card. Pause/resume ring with states (paused, idle and others; it is never "active" without a qualified workload). Wallet, ORE rewards and device-load tiles; device load comes from live battery, charging and thermal readings. A measured-samples empty state. "Review a supported action" and "Preview session (concept)". Safety controls (charge-only, thermal guard, battery reserve, CPU limit, daily devnet budget) are persisted. The gates fail closed when a reading is missing.
- **AI** — Model and execution card, owned-host endpoint, explicit model choice (Send is disabled without one), private document, chat bubbles, session-only retention, cancel.
- **Nodes** — Host card with freshness and stale states, scoped pairing form, Read health / Review logs / Revoke access, health details, storage and bandwidth cards. No arbitrary remote shell.
- **Receipts** — All/Reviews/Node filters, list, detail (immutable ID, operation ID, cluster, source, digests, chain link, exact body), four evidence dimensions with payment status kept separate, export preview, verify a file, tamper test.
- **Review route** — A dedicated screen and the only path to a wallet signature.

## Build

Requirements: JDK 17, Android SDK platform 35 (AGP 8.7.3 also pulls build-tools 34), and Gradle 8.9 via the wrapper (`gradle-wrapper.jar` SHA-256 `498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17`, the official 8.9 jar).

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
# or: bash scripts/qualify.sh   (writes logs + APK digest under evidence/)
adb install app/build/outputs/apk/debug/app-debug.apk
```

- **Minimum Android:** 8.0 (API 26). Target and compile SDK: 35. Thermal status needs Android 10+; on older versions it shows "unavailable" and the thermal gate fails closed.
- `gradle.properties` uses conservative memory settings (`-Xmx2g`, in-process Kotlin compiler, 2 workers).

### Pair a node (Ubuntu host)

```bash
# On the host, from CodesbyFebin/DeProof--EdgeORE:
cd node-agent && go build -o deproof-node ./cmd/deproof-node
./deproof-node -pair-scopes READ_NODE            # prints challenge JSON, single-use code, TLS cert SHA-256
# The agent listens on loopback only. Reach it from a USB-connected phone:
adb reverse tcp:9843 tcp:9843
```

In **Nodes**, enter `https://127.0.0.1:9843`, the certificate SHA-256, the challenge JSON and the code. Confirm the fingerprint, then pair. Challenges expire after 2 minutes.

Real-agent integration test (JVM): `DEPROOF_NODE=/path/to/deproof-node bash scripts/node-agent-it.sh`.

### Private AI on your host

Run an Ollama-compatible server on your host, then `adb reverse tcp:11434 tcp:11434` and connect to `http://127.0.0.1:11434`. Cleartext is allowed only to loopback (`network_security_config.xml`). LAN hosts need https, and public hosts are refused.

## Tests

| Suite | Tests | Covers |
|---|---|---|
| `EdgeOreCoreTest` | 17 | All 10 Kotlin Playground PASS checks (exact decimal units, whole SOL, excess precision, negative, overflow, all squares charged, high mask bit, exposure overflow, mutation, daily budget) plus edge cases |
| `TrustTest` | 6 | Original starter `MessageBinding` / `SpendGuard` tests |
| `SolanaMessageTest` | 11 | Base58, transfer encode/decode, unknown program/trailing/v0 refused, review budget, wallet-return mismatch and forged-signature refusal |
| `ReceiptTamperTest` | 11 | Edited body, recomputed digest, re-signed with another key, removed/reordered, local-copy mismatch, empty/garbage, exclusions |
| `NodeAgentProtocolTest` | 7 | Challenge parsing/expiry, Go-identical pairing payload, command payload, escaping, endpoint/pin validation |
| `PolicyAndEndpointTest` | 6 | Cloud-fallback endpoint policy; fail-closed safety gates; never ACTIVE |
| `NodeAgentIntegrationTest` | 1 | Real Go node agent: pair → observe → forged key refused → scope refused → revoke → refused (skipped unless `EDGEORE_NODE_IT` is set) |
| `androidTest/*` | 3 | Instrumented (compiled, **not yet run**): Keystore-signed receipts + tamper; node agent over Android TLS via `adb reverse`; Compose smoke walk of all four tabs. Run with `./gradlew connectedDebugAndroidTest` |

## Where the code came from

- **`CodesbyFebin/EdgeORE`** (this repo) contained only a README. The app was added here.
- **User-supplied `EdgeORE-Kotlin-App` starter (zip)** provided `EdgeOreCore`, `Trust` (`MessageBinding`, `SpendGuard`), `TrustTest`, the MWA `WalletCoordinator`, the devnet RPC observation, the FileProvider export, `playground/` and `research/native-spike/`. These are kept and extended. Two changes:
  - `BigInteger.longValueExact()` (API 31) was replaced with an equivalent `bitLength()` check, because lint flagged that it would crash on Android 8–11.
  - The Room receipt table was replaced by an append-only signed JSONL log. This keeps reviewed bytes immutable and makes the log unit-testable.
- **`CodesbyFebin/DeProof--EdgeORE`** provided the node-agent wire protocol (`/pair`, `/command`, `deproof-pair-v1`, `node-policy-v1`). It is implemented here as a Kotlin client. No DeProof source files were copied into this repo.

## Security notes

- Wallet keys stay in the wallet (Mobile Wallet Adapter). The app verifies wallet-returned bytes before any optional broadcast.
- The node pairing key is Ed25519 in software (BouncyCastle). Its seed is encrypted with an Android Keystore AES-GCM key. Receipt signatures use a Keystore P-256 key.
- A device-key signature shows that this app install wrote the bytes. It is **not** independent verification, provider acknowledgement or payment.
- `allowBackup=false`, and data-extraction rules exclude app data.
- Threat model and qualification reports from DeProof are not re-run here.

## License

This repository does not yet contain a `LICENSE` file, so all rights are reserved by default until the owner adds one. **TODO (owner):** choose a license. The related `CodesbyFebin/DeProof--EdgeORE` is MIT-licensed. Third-party dependencies keep their own licenses: AndroidX and Jetpack Compose (Apache-2.0), Solana Mobile Wallet Adapter clientlib (Apache-2.0), Bouncy Castle (MIT-style Bouncy Castle License).
