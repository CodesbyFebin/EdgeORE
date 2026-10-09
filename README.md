# EdgeORE

**Your Edge. Your AI. Your Proof.** · *Review ORE. Control your edge.*

A native Kotlin / Jetpack Compose Android workspace for Solana Mobile: pair a host you own, run private AI on it, review Solana actions byte-for-byte before your wallet signs, and keep tamper-evident receipts.

Built by **CodesbyFebin**. Candidate: **`0.2.8-review` (versionCode 10), debug-signed APK, Solana devnet only.** The current status of every claim is in [`docs/qualification-status.md`](docs/qualification-status.md).

> **Decision: NO-GO for “fully functional” or “ORE earning.”** Build, test and lint results are recorded. Wallet authorization, a confirmed devnet transfer, on-device AI, and a phone walkthrough are **NOT_RUN**. ORE rewards are **Not observed**. Do not treat this repository as a completed mining product.

> **What this app does not do:** it does not mine, earn or promise ORE rewards. It does not issue a token (`$EdgeORE` is a product name). It does not make SKR payments. It never sees your wallet keys. Missing observations are shown as missing, never as zero.

---

## Hackathon submission

| Item | Link |
|---|---|
| Demo video | **TODO** — add link (record from the installed APK, not from mockups) |
| Pitch deck | **TODO** — add link |
| Debug APK | **TODO** — attach `app-debug.apk` to a GitHub release tagged **`0.2.8-review`** (the APK's versionName; not `v1.0.0`) with its SHA-256, and link it here |
| Track / event | **TODO** |

### Demo journey and coverage

What the code does today. "Verified" names the environment where each step was actually run.

| # | Journey step | Status | What is real | Verified |
|---|---|---|---|---|
| 1 | **Pair host** | **Implemented** | Scoped pairing with the [DeProof node agent](https://github.com/CodesbyFebin/DeProof--EdgeORE/tree/main/node-agent). It uses an expiring single-use challenge, an Ed25519-signed pairing payload and a pinned TLS certificate SHA-256. The fingerprint is shown for you to confirm. | JVM integration test against the real Go agent (pair, replay refused, wrong pin refused). **Not yet run on Android:** whether Android's TLS stack accepts the agent's Ed25519 certificate is unverified (an instrumented test is included). |
| 2 | **Local AI over a private document** | **Partial** | Chat with an Ollama-compatible model server on **your own host** (`/api/tags`, `/api/chat`). A document is read on the phone and sent only to that host. The receipt keeps SHA-256 digests only. "Cloud fallback off" is enforced: public endpoints are refused. | Unit-tested request and endpoint policy. **Not run against a live model.** On-device inference is **not implemented** (no runtime is bundled). |
| 3 | **Review a supported Solana action** | **Implemented (devnet)** | Builds a System Program transfer. Decodes the exact message bytes for review (program, accounts, amount, fee via `getFeeForMessage`, blockhash, message SHA-256). Unknown instructions disable approval, and the daily budget comes from `EdgeOreCore.eligible`. MWA `signTransactions` follows. The returned bytes must equal the reviewed message (`sameMessage`) and the Ed25519 signature must verify, or the result is refused. Optional devnet submit and status observation. | Unit tests for encode/decode, mismatch, forged signature and budget. Each transfer is a durable operation, written to disk before any network call. Taps are single-flight. A timeout is recorded as *outcome unknown* and resolved later by reading the signature status, never by resending. The daily budget is a durable reservation per signer, cluster and UTC day. **The MWA wallet round-trip has not been run on a device with a wallet.** |
| 4 | **Export receipt** | **Implemented** | Append-only JSONL receipt log (schema v2). Each receipt stores its exact body bytes, the body's SHA-256, a hash-chain link, an Android Keystore P-256 signature and the id of the key that signed it. The wallet's evidence is stored too, and the wallet's Ed25519 signature is verified independently. A damaged line is reported; it is not silently dropped. Export goes through `FileProvider` as JSON with a bundle digest. The preview lists what is included and what is excluded. | Unit tests (JVM software key). Keystore signing and the share sheet are **not device-run**. |
| 5 | **Reject tampering** | **Implemented** | The verifier rejects edited bodies, recomputed digests (the signature then fails), a different signing key, removed or reordered receipts (bundle digest and chain), and mismatches with the local copy. It reports integrity, key provenance (pinned or unpinned) and completeness (full chain with a signed checkpoint, or a marked subset) separately. To see a rejection, keep the original export, copy it, change one byte **inside a receipt body** of the copy, and open the copy with *Verify offline* (or `scripts/verify-receipt.sh`). Descriptive export fields outside the receipts and checkpoint (for example `note`, `exclusions`) are not signed; see [Second-machine receipt verification](#second-machine-receipt-verification). | `ReceiptTamperTest`, `ReceiptV2Test`. |
| 6 | **Revoke node access** | **Implemented** | A signed `revoke` command. After revocation the node refuses the session (`SESSION_REVOKED_OR_UNKNOWN`) and the local key is destroyed. If the node is offline, the app shows "Revocation pending" and offers "Forget locally" with an explicit caveat. | JVM integration test against the real agent. |
| – | Storage vault | **Implemented, unverified on a phone** | AES-256-GCM with an Android Keystore key. The versioned EOV2 header is authenticated as AAD. Each object is published atomically (temp file, fsync, rename). Imports are byte-bounded and the vault allowance is enforced. Traffic since boot is not shared bandwidth. | `BoundedIoAndVaultTest` (JVM key). Keystore encrypt/decrypt **NOT_RUN** on a device. |
| – | ORE participation | **Not qualified** | Shown as "Not observed". No deploy, no claim, no reward multiplier. | – |
| – | SKR payment | **Not implemented** | Out of scope. SKR is not CPU-mineable in this app. | – |
| – | VPN, cloud sync, bandwidth earning | **Unavailable** | Controls do not start a tunnel, upload, or share traffic. | – |

Build, test and lint results, plus what was **not** run, are recorded in [`docs/qualification-status.md`](docs/qualification-status.md) and `evidence/build-*/summary.md`. No step has been run on a physical device or with a wallet app yet.

## Screens (Compose, design spec v1.0)

All five destinations (Mine, AI, Storage, Nodes, Receipts) and the Review route use the same token and component system. Rendered images (Robolectric, not a phone) are in [`docs/screenshots/`](docs/screenshots/). They share graphite surfaces, copper primary actions, mint accents and a natively drawn three-bar E mark.

- **Mine** — Edge Mode card. Pause/resume ring with states (paused, idle and others; it is never "active" without a qualified workload). Wallet, ORE rewards and device-load tiles; device load comes from live battery, charging and thermal readings. A measured-samples empty state. "Review a supported action" and "Preview session (concept)". Safety controls (charge-only, thermal guard, battery reserve, CPU limit) are persisted. Every control on every screen is labelled **Enforced**, **Saved only** or **Unavailable** (`settings/ControlEffects.kt`). The gates fail closed when a reading is missing.
- **AI** — Model and execution card, owned-host endpoint, explicit model choice (Send is disabled without one), private document, chat bubbles, session-only retention, cancel.
- **Nodes** — Host card with freshness and stale states, scoped pairing form, Read health / Review logs / Revoke access, health details, storage and bandwidth cards. No arbitrary remote shell.
- **Receipts** — All/Reviews/Node filters, list, detail (immutable ID, operation ID, cluster, source, digests, chain link, exact body), four evidence dimensions with payment status kept separate, export preview, and *Verify offline* for an exported file. There is no in-app tamper button in this build; `runTamperTest` exists in the ViewModel but no screen calls it.
- **Storage** — Encrypted vault with allowance and recovery text, bandwidth consent (no protocol runs), VPN, cloud and kill-switch controls labelled Unavailable, telemetry, and an audit trail.
- **Review route** — A dedicated screen and the only path to a wallet signature. It lists operations awaiting an outcome, with a read-only *Observe status* action.

## Build

Requirements: JDK 17, Android SDK platform 35 (AGP 8.7.3 also pulls build-tools 34), and Gradle 8.9 via the wrapper (`gradle-wrapper.jar` SHA-256 `498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17`, the official 8.9 jar).

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
# or: bash scripts/qualify.sh   (clean gate; writes evidence/build-<commit>/summary.md with parsed JUnit, lint, aapt badging and APK SHA-256)
#     SCREENS=1 NODE_IT=1 bash scripts/qualify.sh   (also verifies screenshots and runs the live node-agent test)
adb install app/build/outputs/apk/debug/app-debug.apk
```

- **Minimum Android:** 8.0 (API 26). Target and compile SDK: 35. Thermal status needs Android 10+; on older versions it shows "unavailable" and the thermal gate fails closed.
- `gradle.properties` uses conservative memory settings (`-Xmx2g`, in-process Kotlin compiler, 2 workers).

### Pair a node (Ubuntu host)

The bundled agent is **`deproof-node`** (from `CodesbyFebin/DeProof--EdgeORE`, pinned in `scripts/node-agent.pin`). There is no `edgeore-node` binary.

```bash
# Pinned build (revision, Go toolchain and binary SHA-256 in scripts/node-agent.pin):
bash scripts/build-node-agent.sh            # -> build/node-agent/deproof-node, fails on a revision or hash mismatch
# Run it with its documented flags (defaults shown; -listen must stay on loopback):
build/node-agent/deproof-node -pair-scopes READ_NODE -listen 127.0.0.1:9843 -state .deproof-node
# It prints a live pairing challenge (JSON, expires after 2 minutes), a single-use code and its TLS certificate SHA-256.
# Reach the loopback listener from a USB-connected phone or emulator:
adb reverse tcp:9843 tcp:9843
```

In **Nodes**, enter `https://127.0.0.1:9843`, then the certificate SHA-256, challenge JSON and single-use code **exactly as the running agent printed them** (each run prints new ones; a code works once). Confirm the fingerprint, then pair. A hand-run `go build` of the same revision is not hash-checked; use the script.

Real-agent integration test (JVM): `bash scripts/node-agent-it.sh`. It builds `deproof-node` with the same pinned script and starts its own agent on `127.0.0.1:19843` (override with `NODE_IT_PORT`), not the pairing port 9843, so it does not collide with an agent you paired by hand. It builds the pinned agent, and exits non-zero unless the test actually ran and passed. A skipped test counts as a failure.

### Private AI on your host

Run an Ollama-compatible server on your host, then `adb reverse tcp:11434 tcp:11434` and connect to `http://127.0.0.1:11434` (not the emulator alias `10.0.2.2`, which is not loopback and is refused for cleartext). Cleartext is allowed only to loopback (`network_security_config.xml`). LAN hosts need https, and public hosts are refused.

## Second-machine receipt verification

`scripts/verify-receipt.sh` runs the app's **own** `ReceiptVerifier` (`app/src/main/java/com/edgeore/app/receipts/Receipts.kt`) on any machine with JDK 17. No Android SDK, device, emulator or wallet is needed. The standalone Gradle project `tools/receipt-checker` compiles that file and the pure helpers it imports (`crypto/Bytes.kt`, `crypto/Ed25519.kt`, `io/SafeFiles.kt`) by source reference; it does not copy or change app code. Details: [`tools/receipt-checker/README.md`](tools/receipt-checker/README.md).

```bash
git clone https://github.com/CodesbyFebin/EdgeORE && cd EdgeORE
bash gradlew -p tools/receipt-checker --no-daemon test installDist   # one-time build (needs network for dependencies)
bash scripts/verify-receipt.sh receipt.json                           # one file per call
bash scripts/verify-receipt.sh receipt.json --trusted-key device-spki.der   # optional: pin a DER SPKI obtained another way
# Fully offline after the build, without Gradle:
tools/receipt-checker/build/install/edgeore-receipt-checker/bin/edgeore-receipt-checker receipt.json
```

Output is `VERIFIED: PASS` or `VERIFIED: FAIL`, the verifier's summary line, and one `Finding:` line per problem (for example `Receipt #2: SHA-256 does not match body (modified)`). Exit status: 0 accepted, 1 rejected, 2 usage/read/build error (nothing accepted). It checks one file per call.

Tamper demo (keep the original, tamper a copy, change a byte inside a receipt body):

```bash
cp receipt.json receipt-tampered.json
off=$(grep -bo 'solanaSignature' receipt-tampered.json | head -1 | cut -d: -f1)   # a key inside the first receipt's signed body
printf 'X' | dd of=receipt-tampered.json bs=1 seek=$((off + 2)) conv=notrunc
bash scripts/verify-receipt.sh receipt.json            # VERIFIED: PASS, exit 0
bash scripts/verify-receipt.sh receipt-tampered.json   # VERIFIED: FAIL, Finding: ... SHA-256 does not match body, exit 1
```

What it checks, and what it does not:
- It checks **integrity and signatures of the exported records only**: each receipt body's SHA-256, the device-key (P-256) signature, sequence and chain links, the bundle digest, the signed checkpoint of a full-chain export, and any wallet Ed25519 signature carried in a receipt over the reviewed message.
- It does **not** query Solana RPC, so it is not chain verification. To check a transfer, take the signature EdgeORE recorded (Review screen, or the receipt's `solanaSignature`) and call `getSignatureStatuses` with `{"searchTransactionHistory":true}`.
- Without `--trusted-key`, a PASS means the file is consistent with the keys it carries itself (`key not pinned`). Someone who re-signs a whole file with a new key would also pass.
- Descriptive export fields outside the receipts and the checkpoint (`note`, `exclusions`, `payment`, `location`, top-level `exportedAt`, key-epoch `protection`/`firstUsedAt` labels) are **not signed**, and the top-level `deviceKey` is not used for receipts that name their key epoch. Changing those bytes is not detected. This is how the existing verifier works; it was not changed. A fixed offset such as `dd seek=50` is therefore not a valid tamper test.
- The checker uses the reference `org.json` library (the same one the app's JVM tests use); the phone uses Android's. No receipt from a real transfer has been verified yet: only generated exports.

## Tests

Counts come from the JUnit XML of a preserved gate run (`evidence/build-*/summary.md`). They are not hand-copied. Latest preserved app run: `cde5537df660`, 164 tests, 163 passed, 0 failed, 1 skipped (`NodeAgentIntegrationTest`, which ran and passed in `node-agent-it.sh`), lint clean (`evidence/build-cde5537df660/`). The receipt checker's own tests run separately: `bash gradlew -p tools/receipt-checker test`.

| Suite | Covers |
|---|---|
| `EdgeOreCoreTest`, `TrustTest` | Kotlin Playground PASS checks (exact units, overflow, masks, budget) and the starter `MessageBinding` / `SpendGuard` |
| `SolanaMessageTest` | Base58, transfer encode/decode, unknown program/trailing/v0 refused, wallet-return mismatch and forged-signature refusal |
| `DurableOperationTest`, `DurableSpendTest` | Durable operations across simulated process death, single flight, unknown outcome, expiry, reservations |
| `ReceiptTamperTest`, `ReceiptV2Test`, `FalseBroadcastTest` | Tampering, key epochs, damaged lines, wallet-signature verification, checkpoints, false chain claims |
| `BoundedIoAndVaultTest` | Bounded reads, atomic publication, EOV2 AAD tampering, allowance, path validation, legacy objects |
| `TransportHardeningTest`, `PolicyAndEndpointTest` | Socket cancel, redirects refused, pinned TLS, bound endpoint resolution, fail-closed gates |
| `NodeAgentProtocolTest`, `DeviceMetersTest`, `InferenceClaimTest`, `WorkloadGateTest`, `ControlEffectsTest` | Node wire protocol, telemetry continuity, AI execution labels, the never-ACTIVE rule, control effect labels |
| `NodeAgentIntegrationTest` | Real Go agent: pair, replay, wrong pin, forged key, scope, revoke. Skipped in the normal suite; run by `scripts/node-agent-it.sh` |
| `screens/*` (`-Pscreens` only) | Roborazzi renders of six pages plus a Robolectric walk of all five tabs |
| `tools/receipt-checker`: `ReceiptCheckerTest` | The second-machine checker: real `ReceiptLog.export` PASS (unpinned, offline limits stated), body tamper in a copy FAIL with the original untouched, pinned vs different key, malformed JSON and UTF-8, missing file, wrong arguments |
| `androidTest/*` | Instrumented (compiled by the gate, **not run**, no device): Keystore receipts, node agent over Android TLS, Compose smoke walk |

## Where the code came from

- **`CodesbyFebin/EdgeORE`** (this repo) contained only a README. The app was added here.
- **User-supplied `EdgeORE-Kotlin-App` starter (zip)** provided `EdgeOreCore`, `Trust` (`MessageBinding`, `SpendGuard`), `TrustTest`, the MWA `WalletCoordinator`, the devnet RPC observation, the FileProvider export, `playground/` and `research/native-spike/`. These are kept and extended. Two changes:
  - `BigInteger.longValueExact()` (API 31) was replaced with an equivalent `bitLength()` check, because lint flagged that it would crash on Android 8–11.
  - The Room receipt table was replaced by an append-only signed JSONL log. This keeps reviewed bytes immutable and makes the log unit-testable.
- **`CodesbyFebin/DeProof--EdgeORE`** provided the node-agent wire protocol (`/pair`, `/command`, `deproof-pair-v1`, `node-policy-v1`). It is implemented here as a Kotlin client. No DeProof source files were copied into this repo.

## Security notes

- Wallet keys stay in the wallet (Mobile Wallet Adapter). The app verifies wallet-returned bytes before any optional broadcast.
- File imports are read with a byte limit. Network clients refuse redirects, and the AI client connects only to the address it validated.
- The node pairing key is Ed25519 in software (BouncyCastle). Its seed is encrypted with an Android Keystore AES-GCM key. Receipt signatures use a Keystore P-256 key.
- A device-key signature shows that this app install wrote the bytes. It is **not** independent verification, provider acknowledgement or payment.
- `allowBackup=false`, and data-extraction rules exclude app data.
- Threat model and qualification reports from DeProof are not re-run here.

## License

This repository does not yet contain a `LICENSE` file, so all rights are reserved by default until the owner adds one. **TODO (owner):** choose a license. The related `CodesbyFebin/DeProof--EdgeORE` is MIT-licensed. Third-party dependencies keep their own licenses: AndroidX and Jetpack Compose (Apache-2.0), Solana Mobile Wallet Adapter clientlib (Apache-2.0), Bouncy Castle (MIT-style Bouncy Castle License).
