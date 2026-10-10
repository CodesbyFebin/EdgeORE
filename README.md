# EdgeORE
<img width="1942" height="809" alt="EdgeORE github readme hero" src="https://github.com/user-attachments/assets/55d989d9-b9d7-49cd-8bf9-86f1f3a35571" />

**Your Edge. Your AI. Your Proof.**

### Know what your wallet signs. Keep evidence of what happened.

EdgeORE is a native **Kotlin / Jetpack Compose Android app** for reviewing a supported Solana transaction before signing, submitting it as a separate action, and exporting a receipt that can be checked outside the app. It also includes clients for AI and nodes on infrastructure you own.

Built by **[CodesbyFebin](https://github.com/CodesbyFebin)** · **Solana Mobile Wallet Adapter** · **Devnet**

**Candidate:** `0.2.8-review` · versionCode `10` · debug APK  
**Current `main`:** `111a3dba48c3` (evidence-only merge of PR #4) · **Tested source / APK stamp:** `d675002bd701` (`d675002bd701f94809cf776aaa0580b3cb9e83ef`).
**Integration candidate (merged into `main` with its device gates still NOT_RUN):** branch `integration/0.2.9-candidate` (`0.2.9-review`, versionCode `11`) combines the wallet-authorization fix, Storage vault, on-device AI path and the `onchain/` prototype; see [`evidence/integration-0.2.9/`](evidence/integration-0.2.9/). The submitted candidate above is unchanged.

> **Qualification:** source-side work has progressed; the wallet journey still needs runtime evidence. No confirmed transfer through EdgeORE, installed-app demo video or physical-device walkthrough is recorded here yet. This is a review candidate, not a production or ORE-earning release.

[Explore source](https://github.com/CodesbyFebin/EdgeORE) · [Qualification ledger](docs/qualification-status.md) · [Releases](https://github.com/CodesbyFebin/EdgeORE/releases)

## The problem

A wallet approval is a critical decision, but users often have to connect three different views: what the app said it would do, what the wallet signed, and what the network eventually observed. A timeout makes that harder: retrying blindly can send an action twice, while an optimistic success label can hide an unknown outcome.

EdgeORE connects those steps with an exact-message review, durable operation history and portable verification. Its core question is simple: **did the signed bytes match the action you reviewed, and what evidence supports the outcome?**

## The core journey

1. **Connect your wallet** through Mobile Wallet Adapter. Wallet private keys stay in the wallet app.
2. **Review a devnet SOL transfer.** Inspect the sender, recipient, integer lamports, supported program, fee information, blockhash and message SHA-256. Unsupported messages are refused.
3. **Sign the reviewed message.** Before opening the wallet, the app checks block height again. Returned message bytes must match the review, and the wallet's Ed25519 signature must verify.
4. **Submit explicitly.** Signing and sending are separate actions. A durable submission-attempt record is written before the RPC send.
5. **Observe and export.** Unknown outcomes remain unknown until observation resolves them. Export a receipt and check its signed contents with the in-app verifier or JVM checker.

This journey is implemented in source. **Its end-to-end wallet run remains NOT_RUN.** The supported action is a devnet System Program transfer, not an ORE deploy, checkpoint, claim or SKR payment.

## What makes the implementation different

| Boundary | Implementation |
|---|---|
| Review → wallet | Bind approval to the exact serialized message; reject changed bytes and invalid signatures. |
| Signing → submission | Separate user actions; no broadcast implied by a wallet signature. |
| Submission → recovery | Persist an attempt before sending. Restart recovery observes status and never automatically resends. |
| Timeout → UI | Report outcome unknown rather than success or failure without evidence. |
| Expiry → reservations | A sent operation that later expires stays unknown and retains its reservation. |
| State → screen | A state transition is exposed only after persistence succeeds; damaged operation storage disables transfers. |
| Receipt → verification | Check digests, app signatures, wallet evidence and chain structure; report provenance and completeness separately. |

## Submission evidence

Only link artifacts that actually exist. Missing entries below are submission work, not completed features.

| Artifact | Current status |
|---|---|
| Installable APK | `0.2.8-review` debug candidate supplied; attach the final tested APK to a Release with its SHA-256. |
| Demo video | **Not supplied.** Record the installed Android app; label emulator footage explicitly. |
| Pitch deck | **Not supplied.** Keep demonstrated capabilities separate from roadmap. |
| On-chain action | **Not supplied.** Add the actual devnet signature, RPC confirmation result and Explorer link after the session. |
| Real-transfer receipt | **Not supplied.** Export from that transaction and preserve original-PASS / signed-content-tamper-FAIL output. |
| Hosted Android emulator — interactive APK preview | **Launch / navigation PASS** on Appetize (Pixel 7, Android 13 / **API 33**). Public link: [appetize.io/app/l63ubf6tbb4fei2fpezkz5fvla](https://appetize.io/app/l63ubf6tbb4fei2fpezkz5fvla). Session notes and screenshots: [`evidence/appetize-d675002bd701/SESSION.md`](evidence/appetize-d675002bd701/SESSION.md). **Launch and tab navigation only** — not wallet, transfer, recovery, or real-receipt evidence. |
| Repository | [CodesbyFebin/EdgeORE](https://github.com/CodesbyFebin/EdgeORE) |

### Automated and manual checks

The following latest results are **maintainer-reported**. Preserve their logs and JUnit XML against the tested source revision before presenting them as reproducible release evidence.

| Check | Reported result | Scope |
|---|---|---|
| Android qualification suite | **164 tests: 163 passed, 0 failed, 1 skipped**; lint clean | JVM/build checks. Skipped test requires a live node agent. |
| Standalone JVM checker | **7 tests, 7 passed** | Separate Gradle project; these tests are not included in the Android count. |
| Export verification on `d675002` | Original **PASS**; copy with signed-body `lamports + 1` **FAIL**, exit `1` | Export content check; not yet an export from a demonstrated real transfer. |
| Hosted Android emulator (Appetize) | Launch **PASS**; Mine/AI/Storage/Nodes/Receipts navigation **PASS**; About stamp observed `d675002bd701` | Pixel 7, Android 13 / **API 33**. Interactive APK preview only. See [`evidence/appetize-d675002bd701/SESSION.md`](evidence/appetize-d675002bd701/SESSION.md). |
| Runtime wallet / recovery / video | **NOT_RUN** | Requires Android runtime, wallet and recorded session. |

### Hosted Android emulator — interactive APK preview

Judges (and anyone else) can open the verified `d675002bd701` APK in a browser through Appetize:

- **Public link:** [https://appetize.io/app/l63ubf6tbb4fei2fpezkz5fvla](https://appetize.io/app/l63ubf6tbb4fei2fpezkz5fvla)
- **Runtime label:** Hosted Android emulator (Pixel 7, Android 13 / **API 33**)
- **What passed:** launch (no crash) and navigation of all five tabs; About text observed as `EdgeORE 0.2.8-review (d675002bd701). Solana devnet only.`
- **What did not run:** wallet authorization (MWA), review → sign → separate submit, restart recovery, and real-transfer receipt verification remain **NOT_RUN**.
- **Evidence:** [`evidence/appetize-d675002bd701/SESSION.md`](evidence/appetize-d675002bd701/SESSION.md) and the screenshots in that directory.

The previously reported single-byte mutation experiment has no preserved script and output in the repository and is **not cited as evidence**.

## Five native destinations

| Destination | Purpose and current boundary |
|---|---|
| **Mine** | Edge state, wallet observations and resource controls. No mining workload or qualified earnings. |
| **Private AI** | Ollama-compatible chat and document attachment to an owned host. On the integration candidate: an on-device LiteRT-LM path after [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery) (download after consent, see [docs/on-device-ai.md](docs/on-device-ai.md)); not run on a device yet. |
| **Storage** | Local AES-GCM vault, bounded imports and capacity allowance. Remote backup is *Not configured* (no backend exists; nothing is uploaded). Android Keystore behavior still needs device qualification. |
| **Nodes** | Scoped pairing, health reads and revocation against a pinned upstream agent. No arbitrary remote shell. |
| **Receipts** | Inspect, export and verify operation records with evidence dimensions kept separate. |

A dedicated **Review** route controls wallet signing. Controls display their actual effect: **Enforced**, **Saved only** or **Unavailable**. A saved value is not presented as an enforced hardware limit.

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

## Owned infrastructure

**AI:** run an Ollama-compatible server on your computer, then:

```bash
adb reverse tcp:11434 tcp:11434
```

Configure `http://127.0.0.1:11434` in the app. Cleartext is allowed only to loopback; LAN hosts require HTTPS. Endpoint policy refuses public destinations. Prompts and attached document content go to the configured owned host; this is **private hosting, not on-phone inference**. A live reply and refusal session still need runtime evidence.

**Nodes:** use the [pinned build script](scripts/build-node-agent.sh), [pin record](scripts/node-agent.pin) and [integration runner](scripts/node-agent-it.sh). The dependency binary is named `deproof-node`; its upstream lineage is retained for provenance. Android pairing and TLS behavior still need runtime qualification. Forward the actual configured agent port and use its live challenge, single-use code and certificate fingerprint.

## Architecture and trust boundaries

The Compose app coordinates four distinct paths: MWA wallet signing, devnet RPC submission/observation, local receipt and vault storage, and owned-host AI/node clients.

- **Wallet:** keeps private keys; returns signed bytes that EdgeORE verifies.
- **Operation ledger:** persists reviewed bytes, submission attempts and observations. Unknown outcomes never trigger automatic resend.
- **Receipt log:** appends exact body strings with digests, signatures and chain links. Full-chain exports carry a signed checkpoint; selected subsets do not claim completeness.
- **JVM verifier:** checks exported records outside Android and separates integrity, key provenance, completeness and wallet-signature results.
- **Owned host:** receives only explicitly requested AI or scoped node traffic; it is a separate trust boundary from the phone.

Android Keystore protects device-held keys where available. Any software fallback is labeled as weaker protection. Vault keys are device-bound; an export is not a cross-device restore guarantee.

The device-bound vault is a design decision, not a defect: local vault keys never leave the device; cross-device restore would need a separate passphrase-wrapped backup envelope, which is not built yet.

## Deliberate limits and next milestones

**Unavailable:** ORE deploy/checkpoint/claim, ORE earnings, SKR payments, VPN tunnel, cloud sync, bandwidth sharing and bandwidth rewards. EdgeORE issues no token and promises no income.

The next milestone is a recorded Android session: wallet authorization → reviewed transfer → confirmed observation → restart recovery → real receipt export → second-machine verification. After that, qualify Keystore behavior, owned-host AI and Android node pairing. Broader export-signature coverage and embedded inference belong to later work.

## License and attribution

No repository `LICENSE` was found in the inspected source snapshot. The owner must confirm or add a license before claiming MIT licensing for this project. Dependencies retain their own licenses; see [`NOTICE`](NOTICE) for Google AI Edge Gallery (Apache-2.0) and LiteRT-LM (Apache-2.0). Upstream node-agent provenance is pinned separately: the `deproof-node` agent is built from [CodesbyFebin/DeProof--EdgeORE](https://github.com/CodesbyFebin/DeProof--EdgeORE) (MIT-licensed upstream) at the revision recorded in [`scripts/node-agent.pin`](scripts/node-agent.pin).

---

**Built by CodesbyFebin.**  
*A useful edge starts with clear permissions and inspectable results.*
