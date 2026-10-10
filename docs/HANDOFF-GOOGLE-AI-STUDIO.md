# EdgeORE handoff for Google AI Studio

- **Written:** 2026-10-10 (IST, UTC+5:30), by the build agent, from repository files and recorded evidence only. Updated the same day after PRs #11 and #12.
- **Repository:** https://github.com/CodesbyFebin/EdgeORE
- **`main` for this handoff:** `3b951575d51617322f430fe8fc81a4d45cbf79ae` (PR #12 merge) plus the docs-only PR that carries this revision. History: integration merge `38fb71d5c25e` (PR #8, settlement spec v2 + 0.2.9 candidate) → docs merges (PRs #6, #9, #10) → README image change `6f7b600` → **PR #11 merge `4e70d28f6876`** (Clearance UX port) → **PR #12 merge `3b951575d516`** (runbook gates 1a/5a/5b, QR test images, gate evidence). Check the current head with `gh api repos/CodesbyFebin/EdgeORE/branches/main --jq .commit.sha`.
- **Tested source commit for the latest Android gates:** `3c2b2ac1378e` (= `4e70d28` + the docs-only runbook commit). `main` `3b95157` differs from it only by `evidence/` files and the merge commit.
- **Current candidate APK:** `EdgeORE-0.2.9-review-3c2b2ac1378e-debug.apk`, sha256 `17b6209e2839162fdb72a9d9125bd10a2f41119aa9dc228358f9e5f2f807eb6b`, on the unpublished draft release [`untagged-1ed2b9003afb4d03ae79`](https://github.com/CodesbyFebin/EdgeORE/releases/tag/untagged-1ed2b9003afb4d03ae79) and in the build box's `/workspace/device-kit/`.
- **Master zip (build box, older):** `/workspace/EdgeORE-master-2a169ff03e9b.zip` on draft release `untagged-cc0a5e19a62e298076a2`. It predates PRs #11 and #12; regenerate it if a current snapshot is needed.
- **Status in one line:** a JVM-, build- and local-simulator-qualified Android review candidate (`0.2.9-review`, debug-signed) with a first-run introduction and QR address entry, plus an unaudited, undeployed devnet Anchor prototype. Almost nothing has run on a real device. It is not production-ready, it earns nothing and it does not integrate ORE or SKR.

---

## (a) Project summary and hard rules

EdgeORE is a native Kotlin / Jetpack Compose Android app (package `com.edgeore.app`, minSdk 26, target/compile SDK 35) for reviewing a supported Solana devnet transaction before signing it through Mobile Wallet Adapter, submitting it as a separate action, recovering safely after a restart (observe, never resend), and exporting a receipt that can be checked outside the app. It also has clients for AI and nodes on infrastructure the user owns, an AES-256-GCM local storage vault, an optional on-device LLM path (LiteRT-LM, after Google AI Edge Gallery), an opt-in contribution scheduler that runs no workload, and, in `onchain/`, a devnet-only Anchor program that settles verifier-signed work receipts in SOL lamports.

The five app destinations are **Mine, Private AI, Storage, Nodes, Receipts**, plus a dedicated **Review** route that controls wallet signing. Since PR #11 a one-time **first-run introduction** (devnet only, wallet required, what EdgeORE does and does not do, device-bound vault) appears before the tabs, Mine opens with a **Now** card (wallet, operations awaiting an outcome, receipt log, vault), and the Review form starts **empty** with inline validation, **Scan QR** (Camera2 + ZXing, CAMERA requested only on tap), **QR from image** (photo picker, no permission) and an **In plain words** card derived only from the decoded message bytes, shown above the exact-message fields.

### Hard rules (keep every one of them)

1. **No mining-income, reward, yield, APR, boost or "passive income" claims.** EdgeORE issues no token and promises no income. The Mine tab has no mining workload. The contribution scheduler records "No qualified workload. Nothing ran."
2. **No ORE or SKR integration claims.** No ORE client, program ID, instruction encoder or account reader exists. `docs/design/ore-integration-future.md` is marked `DESIGN ONLY - NOT BUILT`.
3. **No production-readiness claims.** Every build so far is a debug-signed review candidate. The Anchor program is unaudited and not deployed.
4. **Keep upstream attribution.** `NOTICE` covers Google AI Edge Gallery (Apache-2.0) and LiteRT-LM (Apache-2.0). The `deproof-node` agent comes from `CodesbyFebin/DeProof--EdgeORE` (MIT upstream) at the revision in `scripts/node-agent.pin`. Do not remove these.
5. **Receipt-checker wording, exactly:** the checker *verifies the integrity of signed receipt contents*. It does not verify every descriptive field of an export, it does not confirm anything on Solana, and it does not prove physical work, provider acknowledgement or payment.
6. **Keep the unpublished single-byte mutation experiment out of all claims.** It has no preserved script or output in the repository and is not cited as evidence.
7. **Report Android and receipt-checker test counts separately, never as one combined number.** They are separate Gradle projects (`:app` and `tools/receipt-checker`). The same applies to the two Anchor modes (LiteSVM and local validator).
8. **Preserve the tested dependency versions.** Every added dependency so far only *added* modules: "0 existing versions changed, 0 removed" (`evidence/integration-0.2.9/deps/COMPARISON.md`). Any change to a version needs its own comparison run.
9. **Never commit secrets:** no keypairs (`*keypair*.json`, `id.json`), no `privateKey` values, no `local.properties`, no keystores (`*.jks`, `*.keystore`), no API tokens. `.gitignore` already covers `local.properties`, `.env`, `*.jks`, `*.keystore`; the Anchor program keypair under `onchain/target/deploy/` is gitignored and was never committed.
10. **A gate is PASS only with evidence** (a saved log, JUnit XML, screenshot or RPC response tied to a named commit or APK). JVM results never count as device results. A runbook is not evidence. Unknown is reported as NOT_RUN, never as PASS.
11. **No cloud transaction auditing.** Transaction data never goes to a cloud LLM (the earlier Clearance app's `GeminiAuditService` was rejected for this). Plain-language text is derived from the decoded bytes only and never replaces the exact-message review.

---

## (b) Repository map and architecture

```
EdgeORE/
├── app/                        Android app module (:app), Kotlin + Compose
│   └── src/main/java/com/edgeore/app/
│       ├── MainActivity.kt, EdgeOreViewModel.kt (945 lines, owns 7 features), UiState.kt,
│       │   EdgeOreCore.kt, Trust.kt, BuildConfigInfo.kt
│       ├── wallet/     WalletAuthorization, WalletConnection, WalletCoordinator, WalletDisplay (MWA)
│       ├── scan/       AddressQr (bare address / Solana Pay transfer only; refuses spl-token, reference, memo,
│       │               transaction requests), QrDecoder (zxing-core), QrScanActivity (Camera2, not exported)
│       ├── solana/     SolanaMessage, SolanaRpc, TransferReview, TransferCoordinator, PlainLanguage, Operations
│       │               (durable OperationStore, single-flight submit, recoverAfterRestart/observe)
│       ├── receipts/   Receipts.kt (ReceiptLog, KeyRegistry key epochs, ReceiptVerifier, export envelope)
│       ├── storage/    LocalVault (EOV2 AES-256-GCM + AAD, atomic publication), Backup, StorageController
│       ├── crypto/     Ed25519, Bytes
│       ├── device/     DeviceResources (BatteryDrain, ReadingFreshness), DeviceMeters, DeviceObservations,
│       │               CpuBudget, EdgePolicy, Keystore, ResourceSettingsStore
│       ├── ai/         OwnedHostModel, InferenceClaim; ai/ondevice/ OnDeviceAiController, ModelDownloader,
│       │               OnDeviceModels, ExecutionLabel
│       ├── contribution/ ContributionPolicy, ContributionWork (WorkManager, opt-in, no workload)
│       ├── node/       NodeAgentClient, NodeAgentProtocol (scoped pairing/health/revocation)
│       ├── io/         SafeFiles (BoundedInput)
│       ├── settings/   ControlEffects (Enforced / Saved only / Unavailable labels)
│       └── ui/         EdgeOreApp, Onboarding, ReviewInput, screens/{Onboarding,Mine,Ai,Storage,Nodes,Receipts,Review}Screen,
│                       components, theme
│   ├── src/main/assets/ai/ondevice-model-allowlist.json   pinned model allowlist (digest, size, licence)
│   ├── src/release/AndroidManifest.xml                    removes exported test activities in release
│   └── src/test/java/com/edgeore/app/                     26 JVM test files + screens/ (Robolectric, Roborazzi)
├── ondevice-llm/               Android library isolating LiteRT-LM 0.8.0 (litertlm-android)
├── tools/receipt-checker/      standalone JVM Gradle project; compiles the app's Kotlin verifier
├── scripts/                    qualify.sh (build gate), verify-receipt.sh, check-manifest.py (+ tests),
│                               junit-summary.py, build-node-agent.sh, node-agent-it.sh, node-agent.pin
├── onchain/                    Anchor workspace: programs/receipt-settlement (devnet-only prototype)
├── docs/                       DEVICE-RUNBOOK.md, device-qr/ (runbook QR images), analysis/clearance-app-review.md,
│                               SETTLEMENT-SPEC.md, DEPLOY-DEVNET.md, storage-vault.md, on-device-ai.md,
│                               known-limitations.md, design/ore-integration-future.md, screenshots/ …
├── evidence/                   gate logs, JUnit XML, re-audit (integration-0.2.9/REAUDIT.md), Appetize session,
│                               ux-from-clearance/{4b953874fb14,3c2b2ac1378e}/, settlement-status.md
├── release/checksums.txt       0.2.8-review APK identity (older candidate)
├── research/native-spike/      research only, not shipped
└── playground/                 standalone Kotlin core example (10/10 checks on play.kotlinlang.org, 2026-10-08)
```

### Trust boundaries

- **Wallet** keeps private keys; EdgeORE verifies the returned signed bytes match the reviewed message and the Ed25519 signature is valid.
- **Operation ledger** persists reviewed bytes, submission attempts and observations. Unknown outcomes never trigger an automatic resend.
- **Receipt log** appends exact body strings with digests, signatures and chain links; full-chain exports carry a signed checkpoint and (since `38b55bb`/`cf88c44`) an optional signed envelope over descriptive fields plus an `envelopeRequired` flag. The flag itself is not signed.
- **JVM verifier** checks exports outside Android and reports integrity, key provenance, completeness and wallet-signature results separately.
- **Owned host** (Ollama-compatible AI, `deproof-node` agent) is a separate trust boundary. Cleartext only to loopback; LAN needs HTTPS; public endpoints are refused.
- **Vault** keys are Keystore-bound. *The device-bound vault is a design decision, not a defect: local vault keys never leave the device; cross-device restore would need a separate passphrase-wrapped backup envelope, which is not built yet.*

---

## (c) Build and test commands, toolchains, pinned versions

### Android (`:app`, `:ondevice-llm`)

- JDK 17 (box: `/workspace/tools/jdk-17.0.20.1+1`), Gradle wrapper **8.9**, AGP **8.7.3**, Kotlin **2.0.21**, compile/target SDK **35**, minSdk **26**.
- Key pinned dependencies: Compose BOM `2024.12.01`, activity-compose `1.9.3`, core-ktx `1.15.0`, lifecycle `2.8.7`, coroutines `1.9.0`, `work-runtime-ktx` `2.10.0`, MWA `mobile-wallet-adapter-clientlib-ktx` `2.0.3`, BouncyCastle `bcprov-jdk18on` `1.78.1`, LiteRT-LM `litertlm-android` `0.8.0`, ZXing `com.google.zxing:core` `3.5.3` (added in PR #11; resolved classpaths differ from the previous `main` by exactly that artifact, `evidence/ux-from-clearance/4b953874fb14/deps-diff.txt`); tests: JUnit `4.13.2`, Robolectric `4.14.1`, Roborazzi `1.36.0`, `work-testing` `2.10.0`, `org.json` `20240303`.

```bash
source /workspace/tools/env.sh           # box only: JAVA_HOME, ANDROID_HOME, GRADLE_USER_HOME
SCREENS=1 bash scripts/qualify.sh        # clean unit tests + lint + debug APK + androidTest APK
                                         # + release-manifest policy + screenshot verify
                                         # evidence → evidence/build-<commit>/
NODE_IT=1 bash scripts/qualify.sh        # also the live node-agent integration test (needs scripts/build-node-agent.sh)
./gradlew connectedDebugAndroidTest      # only with a connected device/emulator (never run so far)
```

### Receipt checker (separate project; never add its count to the Android count)

```bash
bash gradlew -p tools/receipt-checker --no-daemon --console=plain clean test
bash gradlew -p tools/receipt-checker test installDist
bash scripts/verify-receipt.sh EXPORT.json [--trusted-key DEVICE-SPKI.der]   # exit 0 accepted, 1 rejected, 2 error
```

### Release-manifest policy

```bash
python3 -m unittest scripts/test_check_manifest.py
python3 scripts/check-manifest.py app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml
```

### On-chain (`onchain/`)

- Host rustc **1.99.0** (`onchain/rust-toolchain.toml`), Anchor CLI / `anchor-lang` **1.2.1**, Solana/Agave CLI **4.3.0** (`cargo-build-sbf` 4.3.0, platform-tools v1.57), LiteSVM **0.18.0**, artifact **SBPF v3**. The box's *active* Solana release is 3.1.10; put `~/.local/share/solana/install/releases/4.3.0/solana-release/bin` first on `PATH` for the run only.

```bash
cd onchain
anchor build
anchor test --skip-build --skip-local-validator --skip-deploy           # LiteSVM (in-process)
anchor test --skip-build --validator legacy --script validator \
  --provider.wallet <throwaway keypair in a mktemp dir, outside the repo>  # local solana-test-validator only
```

`--skip-deploy` is required in the LiteSVM mode. The validator harness refuses non-local RPC URLs. Nothing has ever been deployed to devnet or mainnet.

---

## (d) What's built

All test results below are **JVM / Robolectric / local simulator** unless stated otherwise. "Device: NOT_RUN" means no phone or emulator run exists for that feature.

| Feature | Where | Key commit(s) | Tests (JVM unless noted) | Device |
|---|---|---|---|---|
| Exact-message transfer review, single-flight sign/submit, durable operations, restart recovery (observe, never resend), spend reservations, expiry policy | `solana/`, `ui/screens/ReviewScreen.kt` | main `cc31714` | `DurableOperationTest` 21, `DurableSpendTest` 8, `StoreFailureAndSigningGateTest` 15, `SolanaMessageTest` 11, `FalseBroadcastTest` 4 | NOT_RUN (transfer never signed) |
| MWA wallet authorization result handling; Disconnect reports wallet confirmation | `wallet/` | `fix/mwa-authorization` @ `5009e08` (tested `3676094ad487`); wording `8ca556d` | `WalletAuthorizationTest` 14 | Connect + Disconnect PASS in-session on Appetize (build `3676094ad487`) |
| Receipts: append-only log, key epochs, corruption-aware reads, signed export envelope + `envelopeRequired` flag, STORAGE kind | `receipts/Receipts.kt` | `38b55bb`, `cf88c44`, `34faa2e` | `ReceiptV2Test` 22, `ReceiptTamperTest` 11, `TrustTest` 6 | NOT_RUN (no real-transfer export) |
| Standalone JVM receipt checker (separate project) | `tools/receipt-checker/`, `scripts/verify-receipt.sh` | PR #3; `cf88c44` | `ReceiptCheckerTest` **14** (separate count) | n/a (offline tool); real export from `d675002bd701` verifies |
| Local vault (EOV2 AES-256-GCM + AAD, atomic publication, bounded SAF import, quota/disk-full/permission loss); backup *Not configured* | `storage/`, `io/SafeFiles.kt` | `feature/storage-vault` @ `51a61cc` (gate `d3fdd9a9609a`) | `StorageVaultTest` 10, `BoundedIoAndVaultTest` 14, `BackupCoordinatorTest` 13 | NOT_RUN (Keystore unproven) |
| On-device AI (LiteRT-LM 0.8.0, pinned allowlist, consented download, execution label) | `ai/ondevice/`, `:ondevice-llm`, `assets/ai/ondevice-model-allowlist.json` | `feature/ai-edge-gallery` @ `6579e61` (gate `f4651f83532f`); title `5c891b6` | `OnDeviceAiTest` 12 | NOT_RUN (no download or generation on a device; no weights in the APK) |
| Owned-host AI client (Ollama-compatible), endpoint policy, transport hardening | `ai/OwnedHostModel.kt`, `ai/InferenceClaim.kt` | main | `InferenceClaimTest` 5, `PolicyAndEndpointTest` 6, `TransportHardeningTest` 13 | NOT_RUN from a phone |
| Node pairing / health / revocation client (pinned `deproof-node` agent) | `node/`, `scripts/node-agent.*` | main | `NodeAgentProtocolTest` 7; `NodeAgentIntegrationTest` 1 (**skipped** without `NODE_IT=1`; live loopback run recorded on main earlier) | NOT_RUN on a phone |
| Device readings: battery drain, freshness/stale labels, disk-rate continuity; controls labelled Enforced / Saved only / Unavailable | `device/`, `settings/ControlEffects.kt` | `38e8bb6`, `3553c82` | `DeviceMetersTest` 8, `ControlEffectsTest` 5, `EdgeOreCoreTest` 17, `WorkloadGateTest` 2 | NOT_RUN |
| Contribution scheduler (opt-in, off by default, Wi-Fi + charging + battery-not-low, no workload) | `contribution/` | `feature/contribution-scheduler` @ `18484f4` (tested `daab56aded93`) | `ContributionPolicyTest` 11, `ContributionWorkTest` 9 | NOT_RUN |
| Release-manifest policy (no exported test activities in release) | `app/src/release/AndroidManifest.xml`, `scripts/check-manifest.py` | `eeafad0`, `37f1e05` | `test_check_manifest.py` 9 (Python, separate) | n/a |
| Screenshot suite (Robolectric/Roborazzi renders, fixed device readings) | `app/src/test/.../screens/`, `docs/screenshots/` | `a973cf8` | 25 screenshot tests (separate `-Pscreens` run) | JVM renders, not device captures |
| First-run introduction (once, reopenable from About), Now card on Mine | `ui/Onboarding.kt`, `ui/screens/OnboardingScreen.kt`, `ui/screens/MineScreen.kt` | PR #11 (tested `4b953874fb14`, merged `4e70d28`) | `UxScreenshotTest` (onboarding, full, fontScale 1.5), screenshot baselines | NOT_RUN (runbook Gate 1a) |
| Review form starts empty; inline validation; Scan QR (Camera2 + ZXing, CAMERA on tap); QR from image (photo picker); strict QR parser | `scan/`, `ui/ReviewInput.kt`, `ui/screens/ReviewScreen.kt` | PR #11 | `UxFromClearanceTest` (AddressQr, QrDecoder with zxing-generated codes, ReviewInput) | NOT_RUN (runbook Gates 5a, 5b, 6) |
| "In plain words" card from the decoded message bytes (never replaces the exact fields) | `solana/PlainLanguage.kt` | PR #11 | `UxFromClearanceTest` (tampered decode changes the text; unsupported bytes refused) | NOT_RUN |
| Accessibility: Nodes checkbox row as one toggle, About click label, heading semantics, 1.5× font render | `NodesScreen.kt`, `components/Components.kt` | PR #11 | screenshot suite | NOT_RUN (no TalkBack run) |
| ORE integration design | `docs/design/ore-integration-future.md` | `docs/ore-integration-design` @ `22370c5` | — | **DESIGN ONLY - NOT BUILT** |
| Settlement program v2 (escrow, accept with `consent_hash`/`limits_hash`, proof, verifier-signed settle-once, permissionless refund crank, Clock-only deadlines) | `onchain/`, `docs/SETTLEMENT-SPEC.md`, `docs/DEPLOY-DEVNET.md`, `scripts/deploy-devnet.sh` | `feature/settlement-spec-v2` @ `305f1e7` (tested source `047657f89b48`); merged via PR #8 (`38fb71d5c25e`) | **LiteSVM: 25/25 passed, exit 0. Local solana-test-validator, Agave 4.3.0: 25/25 passed, exit 0.** (v1 at `823e649` had 29 LiteSVM / 28 validator; the mechanism of the v1 delta is in SETTLEMENT-SPEC §9) | Not deployed; not wired to the app; no real SOL payout. Status: `evidence/settlement-status.md` and wiki page `Settlement-Status` |

Unit-test numbers per class come from `evidence/integration-0.2.9/gate-250be029b35c/junit/` (the latest per-class XML is in `evidence/ux-from-clearance/3c2b2ac1378e/build/junit/`). `EdgeOreViewModel.kt` is now 945 lines and owns seven features (controller split deferred until a device run can catch lifecycle regressions).

---

## (e) Evidence ledger

### Latest Android gate run on `3c2b2ac1378e` (each suite run separately)

| Suite | Command | Exit | Counts | Evidence |
|---|---|---|---|---|
| Android gate | `SCREENS=1 bash scripts/qualify.sh` | **0** | Unit: 25 suites, **254 tests: 253 passed, 0 failed, 1 skipped** (`NodeAgentIntegrationTest.pairObserveRevokeAgainstRealAgent`, needs a live agent). Lint: no issues | `evidence/ux-from-clearance/3c2b2ac1378e/build/summary.md`, `build/junit/` |
| Screenshot suite | `./gradlew :app:verifyRoborazziDebug -Pscreens` (from qualify) | **0** | **9 suites, 25/25 passed** (no baseline re-recorded for this docs-only commit) | `…/screens/junit/`, `…/build/screens-verify.log` |
| Receipt checker (separate project) | `bash gradlew -p tools/receipt-checker --no-daemon --console=plain clean test` | **0** | **14/14 passed** | `…/receipt-checker/` |
| Release-manifest check | `python3 scripts/test_check_manifest.py && python3 scripts/check-manifest.py <merged release manifest>` | **0** | 9/9 self-tests OK; MANIFEST CHECK: PASS (CAMERA is runtime-requested; `QrScanActivity` is `exported=false`) | `…/release-manifest/` |
| Settlement program | not re-run: `onchain/` unchanged since `250be029b35c` | n/a | see below | — |

PR #11's own tested commit `4b953874fb14` had the same counts (254 / 253 passed / 1 skipped, 25/25, 14/14, PASS): `evidence/ux-from-clearance/4b953874fb14/README.md`, where the five new and three re-recorded baselines are listed.

**APK identity (current):** `com.edgeore.app` `0.2.9-review` versionCode 11, sha256 `17b6209e2839162fdb72a9d9125bd10a2f41119aa9dc228358f9e5f2f807eb6b`, 57,631,928 bytes, About stamp `EdgeORE 0.2.9-review (3c2b2ac1378e) · Solana devnet only.` (no `-dirty` in the APK's dex), Android debug signer `c9b46665…7ae4b93`. Draft release `untagged-1ed2b9003afb4d03ae79` (planned tag `v0.2.9-review-3c2b2ac1378e`, unpublished): assets downloaded back and `sha256sum -c SHA256SUMS` = OK.

Older APKs, for reference only: `250be029b35c` (`c547d7df…42a88d5b`, master-zip draft), `28acd193fc7c` (`6d5eaf48…7fdf1761`, draft `v0.2.9-review-device-test`), `4b953874fb14` (`76de0c3f…9ec86ce5`, never released).

### Settlement ledger (unchanged since PR #8)

| Environment | Tested source | Result | Evidence |
|---|---|---|---|
| LiteSVM | `047657f89b48` (evidence-only head `305f1e7`) | **25/25 passed, exit 0** | `evidence/anchor-receipt-settlement/047657f89b48/` |
| Local solana-test-validator, Agave 4.3.0 | `047657f89b48` (evidence-only head `305f1e7`) | **25/25 passed, exit 0** | same |
| Re-run on integration merge `250be029b35c` | — | LiteSVM 25/25 exit 0; local validator 25/25 exit 0 | `evidence/integration-0.2.9/gate-250be029b35c/anchor/` |

Settlement `.so`: SBPF v3, 274,352 bytes, sha256 `9ce9addf…09bf78a7`. **Not deployed, not integrated with the app, no real SOL payout.** Passing tests do not establish any of the three (`evidence/settlement-status.md`, wiki page `Settlement-Status`).

### Runtime evidence (the only runtime facts that exist)

| Check | Build | Runtime | Result | Evidence |
|---|---|---|---|---|
| Launch + five-tab navigation, About stamp | `d675002bd701` (0.2.8-review) | Appetize hosted emulator, Pixel 7, Android 13 / API 33 | **PASS** | `evidence/appetize-d675002bd701/SESSION.md` + screenshots |
| MWA wallet authorization (Connect), retained across a tab switch | `3676094ad487` (`fix/mwa-authorization`) | Appetize Pixel 7 / API 33, wallet **Build A** (official mock-mwa-wallet `d444aff`, unpatched, throwaway devnet key) | **PASS** (in-session) | `EXTRAS/session-evidence/appetize-wallet/phase3/README.md` (in the master zip; not committed) |
| Disconnect | `3676094ad487` | same | **PASS** (in-session). Card showed bare "Disconnected"; wording later changed to "Disconnected. The wallet confirmed deauthorization." (`8ca556d`) — the new wording has not run anywhere | `…/phase4/README.md` |
| Unauthenticated wallet → "Connection failed / The wallet declined authorization" | `3676094ad487` | same | observed, **no screenshot** (operator report in phase4 README) | `…/phase4/README.md` |
| One approved 0.01 devnet SOL transfer to `73Cc84sjGtk3RNSVoEres4PLMcZZtg4n46kQUzJnR93n` | `3676094ad487` | same | **NOT_RUN — never signed, never submitted.** Review fields came prefilled (own address, 0.001 SOL), typed input garbled, free-tier 3-minute resets, then Appetize "You've reached your account's usage limit". The owner's approval for exactly one transfer is still unused. | `…/phase5/` screenshots + `EXTRAS/session-evidence/PHASE5-NOTES.md` |
| Cross-process reconnect after restart | — | — | **NOT_RUN** (by design there is no connection persistence; "Not connected" after restart is expected) | — |
| Everything else (force-stop recovery, real-transfer receipt, Keystore, on-device AI, owned-host AI from a phone, node pairing from a phone, backup, accessibility, release install) | — | — | **NOT_RUN** | — |

The current candidate APK (`3c2b2ac1378e`, sha256 `17b6209e…07eb6b`) has **not run on any device or emulator**. The Appetize prefill problem that stopped the transfer (fields prefilled with the wallet's own address and 0.001 SOL) is fixed in code by PR #11 (fields start empty, paste/QR entry), but that fix has not run on a device either.

### Device runbook gates (`docs/DEVICE-RUNBOOK.md`) on the current APK

Every gate below is **NOT_RUN** on `3c2b2ac1378e`. Order: 0, 1, 1a, 2, 3, 4, 5, 5a, 5b, 6, 7, 8, 9, 10. The full checklist with owners and prerequisites is in **ALL REMAINING TASKS**.

| Gate | What | Status | Evidence files to save |
|---|---|---|---|
| 0 | Kit integrity + device identity | NOT_RUN | `00-sha256sums-check.txt`, `00-adb-devices.txt`, `00-device-props.txt`, `00-sender-address.txt` |
| 1 | Install EdgeORE + your own mock wallet build | NOT_RUN | `01-install.txt`, `01-edgeore-version.txt`, `01-packages.txt`, `01-mock-wallet-sha256.txt` |
| 1a | First-run introduction, no reappearance after relaunch, About stamp | NOT_RUN | `01a-intro-*.png`, `01a-now-card.png`, `01a-relaunch.png`, `01-about.png` |
| 2 | Secure lock screen | NOT_RUN | `02-lockscreen.txt`, `02-lockscreen.png` |
| 3 | Filtered `EdgeORE.Wallet` logcat | NOT_RUN | `03-logcat-edgeore-wallet.txt`, `03-decrypted-lines-remaining.txt` (must be 0) |
| 4 | Authenticate + Connect | NOT_RUN on this APK (PASS on `3676094ad487`, hosted) | `04-wallet-authenticated.png`, `04-connected.png` |
| 5 | Disconnect, wallet-confirmed wording | NOT_RUN on this APK (PASS on `3676094ad487`, old wording) | `05-disconnected.png`, `05-reconnected.png` |
| 5a | Live camera QR (CAMERA only on tap, deny path, fill, SPL-token refusal) | NOT_RUN (needs a real camera; emulator = `NOT_RUN (emulator camera)`) | `05a-camera-before.txt`, `05a-review-empty.png`, `05a-denied.png`, `05a-filled.png`, `05a-refused.png`, `05a-camera-after.txt` |
| 5b | QR from image (photo picker, no permission prompt) | NOT_RUN | `05b-filled.png` |
| 6 | The one approved 0.01 SOL transfer + force-stop recovery | NOT_RUN | `06-balances-before.txt`, `06-form.png`, `06-review-*.png`, `06-signed.png`, `06-force-stop-time.txt`, `06-after-restart.png`, `06-signature.txt`, `06-getSignatureStatuses.json`, `06-getTransaction.json`, `06-explorer-url.txt`, `06-explorer.png`, `06-balances-after.txt`, `06-final.png` |
| 7 | Real receipt export, offline verify PASS / lamports+1 FAIL | NOT_RUN | `07-export-preview.png`, `07-receipt.json`, `07-receipt.sha256`, `07-receipt-lamports-plus-1.json`, `07-verify-original.txt`, `07-verify-lamports-plus-1.txt` (+ `.stderr`) |
| 8 | Android Keystore | NOT_RUN | `08-about-keystore.png`, `08-export-key-protection.txt`, `08-vault-verify.png`, `08-private-dir-shell.txt` |
| 9 | On-device AI, airplane mode (arm64) | NOT_RUN | `09-ai-before.png`, `09-downloaded.png`, `09-model-file.txt`, `09-airplane.txt`, `09-offline-reply.png` |
| 10 | Close-out | NOT_RUN | `10-final-package.txt`, `SHA256SUMS`, `RESULTS.md` |
| J-4 | Settlement program devnet deploy + byte-identity | NOT_RUN (never deployed) | see (g) |

---|---|---|---|
| 0 | Kit integrity + device identity | NOT_RUN | `00-sha256sums-check.txt`, `00-adb-devices.txt`, `00-device-props.txt` |
| 1 | Install both APKs, About stamp | NOT_RUN | `01-install.txt`, `01-edgeore-version.txt`, `01-packages.txt`, `01-about.png` |
| 2 | Secure lock screen | NOT_RUN | `02-lockscreen.txt`, `02-lockscreen.png` |
| 3 | Filtered `EdgeORE.Wallet` logcat | NOT_RUN | `03-logcat-edgeore-wallet.txt`, `03-decrypted-lines-remaining.txt` (must be 0) |
| 4 | Authenticate + Connect | NOT_RUN on this APK (PASS on `3676094ad487`, hosted) | `04-wallet-authenticated.png`, `04-connected.png` |
| 5 | Disconnect, wallet-confirmed wording | NOT_RUN on this APK (PASS on `3676094ad487`, old wording) | `05-disconnected.png`, `05-reconnected.png` |
| 6 | One 0.01 SOL transfer + force-stop recovery | NOT_RUN | `06-balances-before.txt`, `06-review-*.png`, `06-signed.png`, `06-force-stop-time.txt`, `06-after-restart.png`, `06-signature.txt`, `06-getSignatureStatuses.json`, `06-getTransaction.json`, `06-explorer-url.txt`, `06-explorer.png`, `06-balances-after.txt`, `06-final.png` |
| 7 | Real receipt export, offline verify PASS / lamports+1 FAIL | NOT_RUN | `07-export-preview.png`, `07-receipt.json`, `07-receipt.sha256`, `07-receipt-lamports-plus-1.json`, `07-verify-original.txt`, `07-verify-lamports-plus-1.txt` (+ `.stderr`) |
| 8 | Android Keystore | NOT_RUN | `08-about-keystore.png`, `08-export-key-protection.txt`, `08-vault-verify.png`, `08-private-dir-shell.txt` |
| 9 | On-device AI, airplane mode (arm64) | NOT_RUN | `09-ai-before.png`, `09-downloaded.png`, `09-model-file.txt`, `09-airplane.txt`, `09-offline-reply.png` |
| 10 | Close-out | NOT_RUN | `10-final-package.txt`, `SHA256SUMS`, `RESULTS.md` |
| J-4 | Settlement program devnet deploy + byte-identity | NOT_RUN (never deployed) | see (g) |

---

## (f) What's blocked, and why

| Blocker | Effect | Who can unblock |
|---|---|---|
| **No device access from the build box.** No phone is attached and no device or local-emulator session has been recorded for these gates. | Runbook gates 0–10 are NOT_RUN on the current APK. | Owner: run `docs/DEVICE-RUNBOOK.md` on their own phone or local emulator. |
| **Appetize quota.** Free tier resets sessions every 3 minutes, every session starts fresh, and the account hit "You've reached your account's usage limit". | Transfer, force-stop recovery and receipt pull cannot fit in a hosted session; no more hosted sessions at all until the quota resets or the plan is upgraded. | Owner: paid plan (costs money; not bought without approval) or own device. |
| **Devnet unreachable from this box.** `api.devnet.solana.com` TLS resets ("unexpected eof while reading", resolved to 198.18.0.1). | No airdrop, no devnet RPC reads, no SBPF-v3 feature check on devnet, no J-4 deploy from here. | Run devnet steps from the owner's machine or another network. |
| **Gates 11a–c, 12a–d, 13a–b have no definition in the repo.** | Phases H, B and P cannot be qualified until their gates are written down with expected results and evidence files. | Owner (definition) + agent (runbook text). |
| **No backend / IPFS node.** | Remote backup is *Not configured*; nothing uploads. Gates 12a–d cannot run. | Owner: decide on and provision an authenticated backend + owned IPFS node. |
| **Passphrase-wrapped backup envelope not built.** | Restore after key loss is impossible by design today. | Engineering (Phase B), after Phase H. |
| **No release key.** | Only debug-signed APKs exist; no signed release, no provenance attestation. | Owner: create and hold a release keystore (never commit it). |
| **No LICENSE decision.** | Repo has `NOTICE` but no project licence; MIT must not be claimed. | Owner. |
| **Legal / store-policy review.** | Any money-handling, settlement or store listing language needs review before publishing. | Owner + counsel. |
| **Settlement status page.** | The wiki page `Settlement-Status` (linked from the wiki sidebar) and `evidence/settlement-status.md` carry the settlement ledger and its limits. | — |
| **Android freeze during Phase V.** | Kotlin/Compose/build changes stay frozen until the owner's device evidence is triaged. The owner made one explicit exception: the Clearance UX port (PR #11), approved and merged. Rust (`onchain/`) work may proceed on a branch. | Owner sends `RESULTS.md` + evidence folder + `SHA256SUMS`. |
| **J-4 devnet deploy not approved / not run.** | Program not deployed; `scripts/deploy-devnet.sh` prepared only. | Owner: explicit approval naming the commit, a funded devnet deployer keypair outside the repo, and a program-id keypair decision (`docs/DEPLOY-DEVNET.md`). |

---

## (g) How to open each closed gate

General rule: work from the folder `$EV=~/edgeore-device-evidence/<YYYYMMDD-HHMM>`, save every file named exactly as below, then write `RESULTS.md` from the template at the end of `docs/DEVICE-RUNBOOK.md`. A mismatch is a FAIL — save it the same way. Send the folder (without `03-logcat-full.raw.txt`) for triage; each FAIL becomes one ticket:

```
TICKET: Gate <n> — <name>
Expected: <runbook expectation>
Observed: <exact text / screenshot ref>
Evidence: <file names>
Verdict: FIX (code) | DOC (expectation wrong) | RETEST (unclear evidence)
```

**Prerequisites:** `adb`, `curl`, `python3`; a checkout of this repo at the APK's commit (`3c2b2ac1378e`) with JDK 17 (for `scripts/verify-receipt.sh`); the device kit (`EdgeORE-0.2.9-review-3c2b2ac1378e-debug.apk`, sha256 `17b6209e2839162fdb72a9d9125bd10a2f41119aa9dc228358f9e5f2f807eb6b`, from `/workspace/device-kit/` or draft release `untagged-1ed2b9003afb4d03ae79`; `qr/*.png`; `APK-IDENTITY.txt`; `SHA256SUMS`); a mock wallet **you build yourself** from `solana-mobile/mock-mwa-wallet` (MWA was tested against `d444aff0c72d`) with a **throwaway devnet key** in its gitignored `local.properties` (`privateKey=`). Its address is `$SENDER`; fund it from the devnet faucet only. The kit contains no wallet. Gate 5a needs a real camera; Gate 9 needs an arm64 device with ≥ 6 GB RAM and ~2 GB free.

1. **Gate 0 — kit integrity and device identity.** `sha256sum -c SHA256SUMS`; `adb devices -l`; read `ro.product.model`, `ro.build.version.release`, `ro.build.version.sdk`, `ro.product.cpu.abi`, `ro.kernel.qemu`; record `$SENDER`. PASS: every checksum line ends `OK`. Files: `00-*`.
2. **Gate 1 — install.** `adb install -r` EdgeORE and `$MOCK_APK`; record the mock wallet's sha256; `dumpsys package com.edgeore.app | grep version`. PASS: both `Success`, `versionName=0.2.9-review`, `versionCode=11`. If `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, uninstall the old one only after exporting anything you need (uninstall destroys its vault and receipts). Files: `01-*`.
3. **Gate 1a — first-run introduction.** Fresh install; the Welcome screen shows four cards (what EdgeORE does / does not do / devnet only / wallet required); tap **I understand · continue**; Mine shows the Now card; force-stop + relaunch must not show the intro again; About stamp = `3c2b2ac1378e`, no `-dirty`. Files: `01a-*`, `01-about.png`.
4. **Gate 2 — secure lock screen.** Set a PIN/pattern/password; `dumpsys lock_settings`. Files: `02-lockscreen.txt`, `02-lockscreen.png`.
5. **Gate 3 — logcat.** `adb logcat -c`; capture `EdgeORE.Wallet:V '*:S'` and a private full log; afterwards strip `Decrypted information` lines. PASS: filtered log has Connect, Disconnect and signing lines; remaining count `0`.
6. **Gate 4 — Connect.** Authenticate in the wallet first, then Mine → Connect wallet → Authorize. PASS: `$SENDER` shown as connected, no error, matching authorize log line. FAIL evidence: exact error text + screenshot.
7. **Gate 5 — Disconnect.** PASS: `Disconnected. The wallet confirmed deauthorization.` Reconnect for Gates 5a–6.
8. **Gate 5a — live camera QR (form fill only).** CAMERA not granted before the tap; **Scan QR** → deny → "Camera permission was not granted, so nothing was scanned…"; allow → scan `qr/qr-recipient-address.png` → destination = `73Cc84sjGtk3RNSVoEres4PLMcZZtg4n46kQUzJnR93n`; scan `qr/qr-refused-spl-token.png` → "QR not used: The link asks for an SPL token transfer…". Never tap Prepare. Files: `05a-*`.
9. **Gate 5b — QR from image.** `adb push` the QR PNGs to `/sdcard/Pictures/` and media-scan them; **QR from image** opens the photo picker with no permission prompt; `qr-solana-pay-0.01.png` fills destination and amount `0.01` (hint `10000000 lamports.`). Never tap Prepare. File: `05b-filled.png`.
10. **Gate 6 — the one approved transfer + force-stop recovery.** Record balances; the fields start **empty**: type/paste `73Cc84sjGtk3RNSVoEres4PLMcZZtg4n46kQUzJnR93n` and `0.01`, or fill them from `qr/qr-solana-pay-0.01.png`; read both back (`06-form.png`). Prepare, then **check the review screen before signing**: the In plain words card is a summary; the exact decoded section (devnet, fee payer/sender `$SENDER`, recipient, 10,000,000 lamports, fee) is the authority. Sign; tap **Submit to devnet** once and force-stop within ~1 s; reopen and record which case happened (sent → observe only, no Submit button; not sent → check Explorer first, then submit the same bytes once; expired → discard and stop). Save signature, `getSignatureStatuses`, `getTransaction`, Explorer URL + screenshot, balances after. **Submit exactly once; never prepare a second transfer.** PASS: `err:null`, confirmed/finalized, recipient +10,000,000 lamports, never offered a resend.
11. **Gate 7 — real receipt.** Export the Gate 6 receipt, pull it with `run-as`, make the lamports+1 copy with the runbook's Python snippet, run `scripts/verify-receipt.sh` on both. PASS: original `exit=0` with the envelope line; lamports+1 `exit=1`. Exit 2 is a toolchain error, not a result.
12. **Gate 8 — Keystore.** About must read `Android Keystore P-256 · key <16 hex>` (software fallback = FAIL); export key protection matches; vault verify reports intact; `/data/data/com.edgeore.app` is `Permission denied` from the shell.
13. **Gate 9 — on-device AI (arm64 only).** Download `Qwen2.5-1.5B-Instruct (q8)` (1,597,931,520 bytes) after consent, verify checksum, enable airplane mode, run one prompt. PASS: reply generated with `airplane_mode_on=1` and no active network, title `On-device model loaded`. On x86_64 record `NOT_RUN (no arm64)`.
14. **Gate 10 — close-out.** Stop captures, filter, `dumpsys package`, write `SHA256SUMS`, fill `RESULTS.md`.
15. **J-4 — settlement program deploy gate (owner decision first; one-time).** Only after J-1…J-3 are accepted and the interface is frozen:
    1. Confirm SBPF v3 (feature `5cC3foj77CWun58pC51ebHFUWavHWKarWyR5UUik7dnC`) is active on devnet (`solana feature status` against devnet), or rebuild with `--arch v0`.
    2. Clean clone at the tagged commit → `anchor build` → record `sha256sum target/deploy/receipt_settlement.so` and size.
    3. Generate a **new** program keypair and deployer wallet outside the repo; never commit them.
    4. Deploy to devnet; record program ID, deploy signature, slot and upgrade authority.
    5. `solana program dump <PROGRAM_ID> deployed.so -u devnet` and compare its sha256 with the local `.so` — they must be byte-identical.
    6. Pin the program ID in repo config and docs; state that the devnet program is **upgradeable** (no implied immutability).
    7. Evidence files: `J4-so-sha256.txt`, `J4-deploy.txt` (ID, signature, slot, authority), `J4-dump-sha256.txt`, `J4-explorer.png`.

    The prepared script `scripts/deploy-devnet.sh` (PREPARED, NOT RUN) implements steps 1–6 and refuses to run unless `EDGEORE_DEPLOY_CONFIRM=devnet`, a 40-hex `DEPLOY_COMMIT`, `DEPLOYER_KEYPAIR` and `PROGRAM_KEYPAIR` (both outside the repo) are set; it checks the devnet genesis hash `EtWTRABZaYq6iMfeYKouRu166VU2xqa1wcaWoxPkrZBG`, refuses mainnet-looking RPC URLs, and checks Anchor 1.2.1 / Solana 4.3.0. Run it as documented in `docs/DEPLOY-DEVNET.md`, then copy `OUT_DIR` to `evidence/anchor-receipt-settlement/<commit>/devnet-deploy/`. SBPF v3 was reported active on devnet since epoch 1069 via a third-party devnet RPC (OnFinality) whose genesis hash matched devnet; `api.devnet.solana.com` itself was unreachable. Program-data rent is roughly 1.9 SOL; have ≥ 4 devnet SOL.

---

## (h) Remaining for 10/10

### Latest re-audit score: re-score after PRs #11 and #12

The last written re-audit is `evidence/integration-0.2.9/REAUDIT.md` (round 3, `28acd193fc7c`, **6.8/10**). The settlement v2 merge changed no Android code. This handoff re-scores the same seven weighted dimensions after the Clearance UX port (PR #11) and the runbook update (PR #12), using only repository files and the evidence on `3c2b2ac1378e`:

| Dimension | Weight | Round 3 | Now | Why (evidence) |
|---|---|---|---|---|
| Source architecture | 20% | 8 | **8** | New code is in small, tested units (`scan/AddressQr`, `scan/QrDecoder`, `solana/PlainLanguage`, `ui/ReviewInput`, `ui/Onboarding`); but `EdgeOreViewModel.kt` grew from 920 to 945 lines and the controller split is still deferred. Net unchanged. |
| Wallet and transfer | 20% | 6.5 | **6.75** | The Appetize blocker (fields prefilled with the wallet's own address and 0.001 SOL; garbled typing) is removed in code: empty fields, inline validation, paste or QR entry, a plain-language summary above the exact fields. Still no transfer signed, submitted or confirmed, recovery never run on a device, reconnect NOT_RUN. +0.25 only. |
| Security and data durability | 15% | 7.75 | **7.75** | CAMERA is a runtime permission requested only on tap; frames/images are decoded in memory; `QrScanActivity` is not exported (manifest check PASS); the QR parser refuses SPL-token, reference, memo and transaction-request links. Keystore still unproven on a device, no signed release, `envelopeRequired` still unsigned. Unchanged. |
| Test evidence | 15% | 7.75 | **7.75** | More JVM coverage (254 unit tests, 25 screenshots, checker 14/14, manifest PASS, each exit 0 and recorded separately), but still no device run on the current APK. Unchanged. |
| AI, node and resource functionality | 10% | 5 | **5** | No change in this area. |
| Release qualification | 15% | 4 | **4** | A new unpublished draft release with a verified asset exists, but it is debug-signed; no release key, LICENSE, install record or demo video. Unchanged. |
| Honest product boundaries | 5% | 9 | **9** | The first-run introduction states what EdgeORE does not do (no mining income, ORE rewards, SKR payments or token; no mainnet; not store-ready) and the Clearance cloud-audit/SKR/simulation code was rejected, not ported. "ORE rewards: Not observed" cards remain on Mine/Storage/AI for the claims lint (R-1) to review. Held at 9. |
| **Weighted** | 100% | **6.8** | **6.825 → 6.8/10** | 0.2·8 + 0.2·6.75 + 0.15·7.75 + 0.15·7.75 + 0.1·5 + 0.15·4 + 0.05·9 |

**Score: 6.8/10, unchanged.** UX quality is not a separate dimension in this rubric; its effect shows up in Wallet and transfer, where it cannot count for much until the transfer actually runs. The round-3 estimate still holds: about 7.5 is plausible once runbook Gates 4–7 (connect, transfer, force-stop recovery, real receipt) pass on this APK. Beyond that, release signing, a LICENSE, Phases H/B/P and the settlement deploy + integration are needed. A score is an assessment, not a result.

### Owner's 10-point scorecard

| # | Point | Status now | Missing for 10/10 |
|---|---|---|---|
| 1 | Build truth | **Partial.** Reproducible JVM gate (`qualify.sh`) exits 0 with separate counts; APK identity recorded. | Clean-clone build from a release tag (Gate 16b), signed release, provenance. |
| 2 | Wallet journey | **Partial.** Connect + Disconnect PASS in one hosted-emulator session on `3676094ad487`. The prefill problem that blocked the transfer is fixed in code (PR #11), not yet on a device. | Sign, separate submit, confirm on devnet, force-stop recovery (runbook Gates 4–6) on the current APK. |
| 3 | Receipts | **Partial.** Checker tests pass (JVM); a pre-envelope export from `d675002bd701` verifies (original PASS, lamports+1 FAIL). | Real-transfer receipt export verified on a second machine (Gate 7); envelope flag binding needs a new checkpoint version. |
| 4 | On-device AI | **Built, NOT_RUN.** LiteRT-LM path, allowlist, consented download, JVM tests. | One download + airplane-mode generation + cancel on a named arm64 phone (Gate 9). |
| 5 | Owned host node | **Partial.** Owned-host AI client and node pairing client; live loopback node test on `main` earlier. Phase H host build not started. | Phase H host build; owned-host reply, cancel and public-endpoint refusal from a phone; Gates 11a–c. |
| 6 | Backup / restore after key loss | **Not built.** Device-bound vault is deliberate; no passphrase-wrapped envelope exists in the repo; no backend. | Phase B envelope + host storage + restore rehearsal; Gates 12a–d. |
| 7 | Funded job settlement | **Prototype (local only).** Anchor program 29/29 LiteSVM, 28/28 local validator in v1; **v2 (spec vocabulary): LiteSVM 25/25 passed, exit 0; local solana-test-validator, Agave 4.3.0, 25/25 passed, exit 0** on tested source `047657f89b48`. Not deployed, not wired to the app. | J-1…J-3 accepted, J-4 deploy, J-5 app integration, Gates 14a–d on devnet from the app. |
| 8 | Trust surface | **Partial.** Evidence dimensions separated in the verifier; `ExecutionLabel`; controls labelled Enforced/Saved only/Unavailable. | Three-label evidence (`KeyProtection` / `ExecutionVerification` / `SettlementState`), job-receipt-only reputation, capability registry; Gates 15a–b. |
| 9 | Permissions / consent | **Partial.** Opt-in scheduler (off by default), consent dialog before model download, explicit endpoint choice. `consent_hash`/`limits_hash` on-chain fields: recorded at `accept_job` in v2 (`accept_records_node_consent_and_limits`); the program checks they are non-zero, not what they mean, and the app does not produce them yet. | Phase P consent flow; Gates 13a–b. |
| 10 | Release readiness | **Open.** Debug-signed only (latest draft release `untagged-1ed2b9003afb4d03ae79`, unpublished); release manifest policy check passes; no LICENSE; no store kits. | Release key, signed APK/AAB, claims lint, store kits, final evidence bundle; Gates 16a–b. |

### Phase ladder (owner's plan; V → H → B → P → J → T → R)

| Phase | Tag | Tasks | Gates |
|---|---|---|---|
| **V** — device verification triage | `0.2.10` | Owner runs `DEVICE-RUNBOOK.md` gates 0, 1, 1a, 2–5, 5a, 5b, 6–10; one ticket per FAIL; Android changes resume only after this. | Runbook 0–10 incl. 1a, 5a, 5b |
| **H** — owned host node | `0.3.0-alpha.1` | Host node build and pairing from the phone. | 11a–c — **NEEDS DEFINITION** (no definition in the repo) |
| **B** — backup / restore | `0.3.0-alpha.2` | Passphrase-wrapped envelope integrated with reserved host storage; capacity re-measurement hook; sync; restore-rehearsal UI. Sequenced after H. | 12a–d — **NEEDS DEFINITION** |
| **P** — permissions / consent | `0.3.0-alpha.3` | Consent capture whose hash is recorded on-chain at accept. | 13a–b — **NEEDS DEFINITION** |
| **J** — funded jobs / settlement | `0.3.0-alpha.4` | **Status: J-1, J-2 and J-3 done on `feature/settlement-spec-v2` (merged via PR #8; see `docs/SETTLEMENT-SPEC.md` §1, §9, §10); J-4 prepared (`scripts/deploy-devnet.sh`, `docs/DEPLOY-DEVNET.md`), NOT RUN; J-5 not started.** Tasks: J-1 test-delta mechanism; J-2 one vocabulary (`create_job / accept_job / submit_proof / verify_and_settle / refund_after_deadline`) in `docs/SETTLEMENT-SPEC.md` v2; J-3 layout checks (structural settle-once flag, `consent_hash`, `limits_hash`, terms hash + deadline + verifier at create, permissionless refund crank, on-chain `Clock`, payout to the node recorded at accept, zero-lamport-movement on duplicate settle); J-4 deploy once with byte-identity; J-5 IDL client, resolver, job-receipt second ledger, funded-jobs views; first job type bounded and deterministic (OCR or embedding). | 14a lifecycle (settled SOL = escrow, Explorer link), 14b second settle fails on-chain, 14c rejection refunds exactly, 14d timeout → OUTCOME_UNKNOWN, no resend. Evidence: `14-lifecycle.json`, `14-duplicate-failed.txt`, `14-refund.json`, `14-observe-only.png` |
| **T** — trust surface | `0.3.0-review` | Three-label evidence on every receipt and node card (labels cannot cross-derive; a node can never set its own execution verification); reputation from job receipts only; capability registry with `self-reported` / `independently-checked` and freshness. | 15a labels + cross-derivation unit test in CI; 15b reputation script matches the app |
| **R** — release | `0.3.0-review` → `1.0.0-candidate` | Claims lint in CI over `strings.xml` and `PITCH.md` (trustless, spoof, guaranteed, mining, passive income, projected, proof of work, …); docs truth pass; Play + dApp Store kits; final evidence bundle. | 16a lint green + simulated-success grep clean; 16b clean-clone build from the tag, APK sha256 matches `APK-IDENTITY.txt` |

The production kit's `docs/production/GATES.json` groups the same work as phases V, H, B, P, J, T, R (all `NOT_RUN`, `NOT_PRODUCTION_QUALIFIED`) with one-line acceptance criteria; they are folded into **ALL REMAINING TASKS** below and do not renumber the runbook gates.

**Definition of done (owner):** a stranger can install the release APK, pair their own host, back up a file, destroy the phone, restore it from passphrase alone, accept one customer-funded devnet job, watch it settle exactly once, and walk away with a receipt whose three labels and settlement they verified offline themselves.

---

## (i) Decisions needed from the owner

The full list with what each one blocks is D-1…D-12 in **ALL REMAINING TASKS**. In short:

1. **Run the device runbook** on the current APK (`3c2b2ac1378e`) and send `RESULTS.md` + evidence folder + `SHA256SUMS`. This is the front of the queue.
2. **Use the single approved 0.01 devnet SOL transfer** to `73Cc84sjGtk3RNSVoEres4PLMcZZtg4n46kQUzJnR93n` in Gate 6 (approved once, never used), or withdraw it.
3. **Settlement design:** verifier-lock rule (rotation only before the first accept) and no early cancel (budget locked until the deadline). Both await your OK before the interface is frozen for J-4.
4. **J-4 deploy approval** naming the commit, with keypairs outside the repo, a reachable devnet RPC, and who holds the upgrade authority.
5. **Mine tab name**, **LICENSE**, **release key**, **backup backend**.
6. **Reconnect after restart:** keep "Not connected after restart" (current, by design) or add persistence.
7. **Treasury / rewards / mining:** confirm they stay research-only.
8. **Appetize** plan, **legal/store review**, and whether to refresh or remove the stale PR #6 walkthrough in `docs/judges/`.
9. **Definitions for Gates 11a–c, 12a–d, 13a–b.**

---

## (j) Explicitly rejected ideas, and why

| Idea | Why rejected |
|---|---|
| **Guessed ORE instruction discriminators** (from a pasted draft) | ORE's own source (`regolith-labs/ore` @ `48c203b`) shows the instruction codes changed meaning between versions; guessed bytes would sign the wrong instruction. No ORE encoder exists; the design doc is DESIGN ONLY. |
| **Fake / placeholder transaction IDs or "simulated success"** | Violates "PASS only with evidence". The resolver reports OUTCOME_UNKNOWN until observation resolves it; the planned release Gate 16a greps for simulated success. |
| **Reward, APR, earnings or 2.0x boost UI for ORE stakers** | There is no income source and no ORE integration; showing projected rewards would be a false financial claim and breaks Play policy. |
| **Treasury "grid mining" bridge / "AI Research Contribution" framing for mining** | ORE "mining" is placing SOL on a 25-square board each round (ORE source); phone CPU, storage or bandwidth counts for nothing there. It would also be gambling-like money flow with legal risk. Kept research-only. |
| **"Zero-knowledge" badge** | Nothing in EdgeORE is a zero-knowledge proof; the receipts are signed records checked for integrity of signed contents. |
| **Treating LiteSVM / JVM results as device gates** | The 10/10 definition ends at device evidence. |
| **Rewriting a single combined test count** | Android and checker counts are separate projects. |
| **Clearance `GeminiAuditService` (cloud LLM transaction audit)** | Sends recipient, mint, amount and evidence data to a cloud API; key in the URL query and compiled into the APK; keyword-scored "risk" that can say "Safe to authorize". Breaks the privacy boundary (`docs/analysis/clearance-app-review.md` §3). |
| **Clearance `SkrStakingService`, SKR claims, DePIN rewards** | In-memory staking with a hard-coded 26.2% APY and invented claims. No SKR integration exists or is claimed. |
| **Clearance simulated wallet/RPC/Keystore** | `MwaWalletService` returns random "signatures"; `SolanaRpcService` reports 1.25 SOL and `CONFIRMED` on failure; `KeystoreService.verifySignature` accepts any `sig_secp256r1_*` string and claims TEE unconditionally. |
| **Clearance `ReviewBinding`, `SolanaDecoder`, evidence capture, Room, theme, pitch/sprint/site claims** | Binding compared a private text blob with itself; decoder worked on instruction tuples, not the signed message; evidence media was synthetic and would need CAMERA/RECORD_AUDIO/READ_MEDIA_*; Room used destructive migration with `allowBackup="true"`; muted text failed contrast (3.6–4.2:1); docs claimed "Production-Grade… Zero Mock Data", "Shipped" SKR/APY features and an Apache badge with no LICENSE. Only the *ideas* of a plain-language layer, a real QR input and a home overview were adapted. |

---

## (k) Production kit import notes

The owner's **EdgeORE Master Production Kit** (`EdgeORE-Master-Production-Kit.zip`, sha256 `8447bbc9…5c2569`, 692 files under `EdgeORE/`) and a separate 9 Oct blueprint were added to `main` in this docs PR. Index and hashes: `docs/production/README.md`.

**Kit diff (measured with `diff -r` against `git archive`):**

- Against `250be029b35c` (the kit's stated tested source): `README.md` differs (hero image only, the `6f7b600` change); `docs/HANDOFF-GOOGLE-AI-STUDIO.md`, `docs/judges/`, `evidence/integration-0.2.9/gate-250be029b35c/` and `evidence/settlement-status.md` are added, all byte-identical to what later docs merges put on `main`; and ten kit files are new: `AGENTS.md`, `design.md`, `SOURCE-PROVENANCE.json`, `START-HERE.md`, `MASTER-BUILD-PROMPT.md`, `docs/production/{GATES.json, EVIDENCE-TEMPLATE.md, FILE-HASHES.json, ui-preview.html}`. Nothing is removed.
- Against `6f7b600` (the kit's `source_commit`): only those ten files are new. **The kit changes no app source, Gradle or resource file**, so nothing needed porting and no Android gate was re-run for the import (docs-only).
- `ui-preview.html` matches the owner's `EdgeORE-UI-Preview.html` hash (`3982a250…873689c8`); `START-HERE.md` matches `d33f3740…8248493d`. `KIT-VALIDATION.json` (in the owner's SHA256 list) is in neither the zip nor the attachments.
- Imported unchanged: the ten kit files plus `docs/production/master-blueprint-2026-10-09.md` (sha256 `78ded760…9842f1`). Added: `docs/production/README.md` (index + provenance note).

**Issues found in the imported files (not edited; listed here for the owner):**

1. **Provenance is older than `main`.** `SOURCE-PROVENANCE.json`, `GATES.json` and `FILE-HASHES.json` describe source `6f7b600` / tested `250be029b35c`. `main` now includes PR #11 and PR #12; the current tested commit is `3c2b2ac1378e`. `FILE-HASHES.json` will not match files changed since `6f7b600`.
2. **`START-HERE.md`** says the kit files "are not pushed upstream" (no longer true) and tells you to `git checkout 6f7b600` and branch from it. Following that would drop PRs #11 and #12; branch from current `main` instead.
3. **`design.md` §13** says "Retain existing 20 screenshot checks"; there are now 25 (five added in PR #11, re-records logged in `docs/screenshots/README.md`).
4. **`design.md` §5** orders Mine as heading → status → wallet → …, and the 9 Oct blueprint §26 says "Mine opens with the wallet widget". PR #11 put a Now card first. Reconcile in X-5.
5. **`design.md` §3/§9/§12** items not yet in the app: navigation rail ≥ 600 dp, "Devnet · Review candidate" strip, Mine/Receipts header copy, 200% font matrix (1.5× is rendered), failure panel with code/stage/UTC (X-5, X-6, R-6).
6. **`MASTER-BUILD-PROMPT.md` Phase V** asks to "restart and reconnect". There is no connection persistence by design, so "Not connected after restart" is expected; that is owner decision D-7, not a defect. It also says not to ask an operator for repeated adb commands "when hosted access is the chosen path"; hosted access (Appetize) is out of quota, so the device run is owner-side.
7. **Blueprint (9 Oct) §2, §23, §33** describe conditional future work: a read-only ORE observer, a transactional ORE adapter, SKR as a separate capability, and "native mining" needing "a separate, verified extraction plan". They make **no** integration or income claim and require pinned official sources, but they go beyond the current research-only ruling for treasury/rewards/mining (D-8). Until the owner decides otherwise, those sections are roadmap/research only.
8. **Blueprint §4** describes an older baseline (`ab009ff`, `0.2.6-review`, versionCode 8, a provisional 5.4/10 audit) and refers to an "audit report", "two supplied screen reference images" and a "source-context text file" that are not in the repo. Treat §4 as historical.
9. **No rule violations found:** none of the imported files claims mining income, ORE/SKR integration or production readiness (`GATES.json`: `NOT_PRODUCTION_QUALIFIED`; `SOURCE-PROVENANCE.json`: `production_qualified: false`; `design.md` §14), and none contradicts the receipt wording. `design.md` uses "externally verifiable signed receipt contents" and "PASS original / FAIL copy establishes signed-content integrity only", which is consistent with "verifies the integrity of signed receipt contents".
10. The kit's copy of `docs/HANDOFF-GOOGLE-AI-STUDIO.md` (sha256 `49c8a446…5c93c7`) is the previous revision of this file and is superseded by this one.

---

## (l) Clearance app review (PR #11) — findings and rejections

Full review: `docs/analysis/clearance-app-review.md` (78 files, every source file read).

- **No real secret in the zip**: `.env.example` line 7 is a placeholder `GEMINI_API_KEY`; `app/build.gradle.kts` lines 35/37 hold the standard `android` debug-key password for a keystore not in the zip.
- **Most of it was simulated**: random "signatures" (`MwaWalletService` lines 50–52), success-on-failure RPC (1.25 SOL, `CONFIRMED`), a Keystore verifier that accepted any `sig_secp256r1_*` string, a "QR scan" with no camera, synthetic evidence media, and a review "binding" that compared a private text blob with itself.
- **Rejected:** `GeminiAuditService` (cloud LLM audit, breaks the privacy boundary), `SkrStakingService` (26.2% APY, invented claims), the simulated wallet/RPC/Keystore, `SolanaDecoder` (its SPL table kept as a research note), `ReviewBinding`, evidence capture, Room, the theme (muted text 3.6–4.2:1), other screens, docs/pitch/site claims, `doctor.sh`, AI Studio secrets scaffolding.
- **Adapted (rebuilt to EdgeORE's rules):** plain-language explanation from decoded bytes, a real QR scanner with a strict parser, a Now overview card, and the first-run explanation pattern.
- **Build status of the zip:** compiles and passes its 11 unit tests under Gradle 9.3.1, but `assembleDebug` fails (missing `debug.keystore`).
- New dependency `com.google.zxing:core:3.5.3` only; no pinned version changed.

---

## ALL REMAINING TASKS

One prioritized checklist. Status for every item is open unless marked. **Owner** = who has to act (human = the project owner on their own phone, machine or accounts; agent = a build agent working in the repo). **Gate** = the check that closes it. **Evidence** = the file(s) that must exist before it can be marked PASS. Device evidence files are named as in `docs/DEVICE-RUNBOOK.md` and live in `$EV=~/edgeore-device-evidence/<YYYYMMDD-HHMM>/`; repo evidence lives under `evidence/`. Every gate is **NOT_RUN** today. Each item also notes which production-kit phase (`docs/production/GATES.json`: V, H, B, P, J, T, R) and which `design.md` section it serves; the kit's phases are acceptance groupings and do not renumber the runbook gates (GATES.json: "historical runbook numbers are not redefined").

Current baseline: `main` `3b951575d516` + this docs PR; tested commit `3c2b2ac1378e`; APK sha256 `17b6209e2839162fdb72a9d9125bd10a2f41119aa9dc228358f9e5f2f807eb6b`; draft release `untagged-1ed2b9003afb4d03ae79`; device kit `/workspace/device-kit/`.

### P0 — Phase V: run the device runbook on the current APK (kit phase V)

Run in this order on your own phone (preferred) or a local emulator. Gates 1a, 5a and 5b never prepare, sign or send.

| # | Task | Owner | Prerequisite | Gate (PASS when) | Evidence |
|---|---|---|---|---|---|
| V-0 | Kit integrity + device identity | human | device kit copied to your computer; `adb`, `curl`, `python3`; a device | Runbook Gate 0: every `sha256sum -c` line `OK`; device props recorded | `00-sha256sums-check.txt`, `00-adb-devices.txt`, `00-device-props.txt`, `00-sender-address.txt` |
| V-1 | Build your own mock wallet, install it and EdgeORE | human | `solana-mobile/mock-mwa-wallet` (tested against `d444aff0c72d`) with a **throwaway devnet key** in `local.properties`; `$SENDER` recorded and faucet-funded | Gate 1: both installs `Success`; `versionName=0.2.9-review`, `versionCode=11` | `01-install.txt`, `01-edgeore-version.txt`, `01-packages.txt`, `01-mock-wallet-sha256.txt` |
| V-1a | First-run introduction | human | fresh EdgeORE install (V-1) | Gate 1a: four intro cards, **I understand · continue**, Now card, no intro after force-stop + relaunch, About stamp `3c2b2ac1378e` without `-dirty` | `01a-intro-*.png`, `01a-now-card.png`, `01a-relaunch.png`, `01-about.png` |
| V-2 | Secure lock screen | human | V-1 | Gate 2: PIN/pattern/password set | `02-lockscreen.txt`, `02-lockscreen.png` |
| V-3 | Filtered wallet logcat (running through Gate 8) | human | V-2 | Gate 3: `EdgeORE.Wallet` lines for Connect/Disconnect/signing; `Decrypted information` count `0` | `03-logcat-edgeore-wallet.txt`, `03-decrypted-lines-remaining.txt` |
| V-4 | Authenticate in the wallet, then Connect | human | V-3 | Gate 4: `$SENDER` connected, no error, matching authorize log line | `04-wallet-authenticated.png`, `04-connected.png` |
| V-5 | Disconnect (wallet-confirmed wording), reconnect | human | V-4 | Gate 5: `Disconnected. The wallet confirmed deauthorization.` | `05-disconnected.png`, `05-reconnected.png` |
| V-5a | Live camera QR scan | human | V-5; a **real camera** (emulator → `NOT_RUN (emulator camera)`); `qr/*.png` on a second screen | Gate 5a: no CAMERA prompt before **Scan QR**; deny path message; scan fills `73Cc84…R93n` exactly; SPL-token QR refused | `05a-camera-before.txt`, `05a-review-empty.png`, `05a-denied.png`, `05a-filled.png`, `05a-refused.png`, `05a-camera-after.txt` |
| V-5b | QR from image | human | V-5; QR PNGs pushed to `/sdcard/Pictures/` | Gate 5b: photo picker, no permission prompt; destination + amount `0.01` filled | `05b-filled.png` |
| V-6 | **The one approved 0.01 devnet SOL transfer** to `73Cc84sjGtk3RNSVoEres4PLMcZZtg4n46kQUzJnR93n` (approval still unused; covers exactly one transfer) | human | V-5; `$SENDER` holds > 0.01 SOL + fee from the faucet; fields start empty | Gate 6: review checked before signing; `err:null`, confirmed/finalized; recipient +10,000,000 lamports; Explorer shows exactly one transfer | `06-balances-before.txt`, `06-form.png`, `06-review-*.png`, `06-signed.png`, `06-signature.txt`, `06-getSignatureStatuses.json`, `06-getTransaction.json`, `06-explorer-url.txt`, `06-explorer.png`, `06-balances-after.txt`, `06-final.png` |
| V-6r | Force-stop during submit; recovery observes, never resends | human | during V-6 | Gate 6: no Submit button for a possibly-sent op; case recorded in `RESULTS.md` | `06-force-stop-time.txt`, `06-after-restart.png` |
| V-7 | Export that real receipt; verify original PASS / lamports+1 FAIL | human | V-6 confirmed; repo checkout at `3c2b2ac1378e` with JDK 17 | Gate 7: original `exit=0` with envelope line; copy `exit=1` | `07-export-preview.png`, `07-receipt.json`, `07-receipt.sha256`, `07-receipt-lamports-plus-1.json`, `07-verify-original.txt`, `07-verify-lamports-plus-1.txt` (+ `.stderr`) |
| V-8 | Android Keystore + vault | human | V-7 | Gate 8: About `Android Keystore P-256 · key <16 hex>`; export key protection matches; vault verify intact; shell `Permission denied` | `08-about-keystore.png`, `08-export-key-protection.txt`, `08-vault-verify.png`, `08-private-dir-shell.txt` |
| V-9 | On-device AI in airplane mode | human | **arm64** device, ≥ 6 GB RAM, ~2 GB free, Wi-Fi (x86_64 → `NOT_RUN (no arm64)`) | Gate 9: Qwen2.5-1.5B q8 (1,597,931,520 bytes) verified; reply with `airplane_mode_on=1`, no network | `09-ai-before.png`, `09-downloaded.png`, `09-model-file.txt`, `09-airplane.txt`, `09-offline-reply.png` |
| V-10 | Close-out and hand over | human | V-0…V-9 attempted | Gate 10: filtered logs, `SHA256SUMS`, `RESULTS.md` (template in runbook); `03-logcat-full.raw.txt` excluded | `10-final-package.txt`, `SHA256SUMS`, `RESULTS.md` |
| V-T | **Phase V triage**: one ticket per FAIL (TICKET format in (g)); verdict FIX / DOC / RETEST | agent | V-10 folder received | every runbook gate has PASS, FAIL-with-ticket or NOT_RUN-with-reason | `evidence/device-3c2b2ac1378e/RESULTS.md`, `…/TRIAGE.md` (proposed paths), copied evidence + `SHA256SUMS` |
| V-F | Fix each FIX ticket; cut `0.2.10` if code changed; re-run the affected runbook gates | agent (code), human (re-run) | V-T | `SCREENS=1 bash scripts/qualify.sh`, receipt checker, release-manifest check, each exit 0 and recorded separately; affected device gates PASS | `evidence/build-<commit>/`, new `APK-IDENTITY.txt`, new device evidence folder |
| V-S | Re-audit after device evidence (round 5) | agent | V-T | scores updated only from saved evidence | `evidence/integration-0.2.9/REAUDIT.md` (or a new round file) |

Kit phase V also asks for "restart and reconnect" and for SDK failures preserved with stage/code/time. Reconnect after restart is not built by design (owner decision D-7 below); a failure panel with stable code/stage/UTC (`design.md` §4) is a TODO (X-6).

### P1 — Owner decisions (block the items after them)

| # | Decision | Owner | Blocks | Recorded in |
|---|---|---|---|---|
| D-1 | **Verifier-lock rule**: the verifier can be rotated only until the first node accepts, then it is locked (`rotate_verifier`, error `VerifierLocked`) | human | J-4 interface freeze | `docs/SETTLEMENT-SPEC.md` (§ instructions, `rotate_verifier_allowed_before_accept_and_locked_after`) |
| D-2 | **No early cancel**: the creator's budget stays locked until the deadline | human | J-4 interface freeze | `docs/SETTLEMENT-SPEC.md` ("There is no early cancel") |
| D-3 | **Mine tab name**: keep "Mine" (design spec, runbook) or rename (e.g. "Home"/"Overview"); `design.md` §1 calls a rename "a separate migration decision" | human | X-8 (UI rename + screenshot re-record + runbook update) | `docs/analysis/clearance-app-review.md` §6 |
| D-4 | **LICENSE** for the project (MIT must not be claimed until a file exists; upstream `NOTICE` stays) | human | R-2, store kits | — |
| D-5 | **Release key**: create and hold a release keystore outside the repo; signing via private environment config | human | R-3, R-5, Gate 16b | — |
| D-6 | **Backup backend** (and owned IPFS node or other store): build or not, who operates it | human | Phase B (B-1…B-3), Gates 12a–d | — |
| D-7 | **Reconnect after restart**: keep "Not connected after restart" (current, by design: no connection persistence) or add persistence | human | kit phase V "restart and reconnect" step | `docs/known-limitations.md`, runtime ledger |
| D-8 | **Treasury / rewards / mining = research-only** ruling (ORE grid mining, reward accounting, boosts, "AI Research Contribution" framing). Current stance: research-only; the 9 Oct blueprint §23/§33 describes conditional ORE adapters and "native mining" extraction as possible future work, which this ruling keeps out of scope | human | nothing in the current plan; prevents scope drift | (j) below |
| D-9 | **J-4 approval**: name the commit; provide a funded devnet deployer keypair and the program-id keypair outside the repo; a reachable devnet RPC; who holds the upgrade authority | human | J-4 | `docs/DEPLOY-DEVNET.md` |
| D-10 | **Appetize**: upgrade (costs money) or stop using hosted sessions | human | hosted re-runs only | — |
| D-11 | **Legal / store-policy review** before any money-handling feature or store listing | human + counsel | R-4, J-5 public use | — |
| D-12 | **Gate definitions** for 11a–c, 12a–d, 13a–b (see H/B/P below) | human (scope) + agent (runbook text) | Phases H, B, P | `docs/DEVICE-RUNBOOK.md` (to add) |

### P2 — Settlement (kit phase J; `design.md` §11)

| # | Task | Owner | Prerequisite | Gate | Evidence |
|---|---|---|---|---|---|
| J-4 | One-time devnet deploy with byte-identity (`scripts/deploy-devnet.sh`, PREPARED, NOT RUN) | human runs it (or agent on a machine that reaches devnet, after approval) | D-1, D-2, D-9; ≥ 4 devnet SOL on the deployer; SBPF v3 active (reported active via OnFinality, genesis matched); Anchor 1.2.1 + Agave 4.3.0 | `sha256_match PASS` (dumped == built); program owner = upgradeable loader; authority + last deploy slot recorded | `evidence/anchor-receipt-settlement/<commit>/devnet-deploy/` (`deploy-record.txt`, `deploy-devnet.log`, `anchor-build.log`, `built.so`, `dumped.so`, JSON) |
| J-4p | Pin the program ID in repo config/docs; state it is **upgradeable** | agent | J-4 | docs PR merged | `docs/DEPLOY-DEVNET.md`, `evidence/settlement-status.md`, wiki `Settlement-Status` |
| J-5 | App integration: IDL client types, resolver on the real instruction names, job-receipt second ledger, funded-jobs views (hidden or "Planned" until gates close, `design.md` §8/§11), exact-message review + durable operations for every settlement tx | agent | Phase V closed (Android freeze); J-4p | JVM gates exit 0 (separately); then 14a–d | `evidence/build-<commit>/` |
| J-14a | Full lifecycle on devnet from the app; settled SOL = escrow amount | human (device) | J-5 | Gate 14a (owner's plan; **not yet in the runbook**) | `14-lifecycle.json`, Explorer link |
| J-14b | Second `verify_and_settle` for the same job fails on-chain | human | J-5 | Gate 14b | `14-duplicate-failed.txt` (program log) |
| J-14c | Rejection refunds the customer exactly | human | J-5 | Gate 14c | `14-refund.json` |
| J-14d | Timeout shows OUTCOME_UNKNOWN, no resend button, observation resolves | human | J-5 | Gate 14d | `14-observe-only.png` |
| J-6 | First paying job type: one bounded, deterministic task (OCR or embedding), sampled re-execution + customer acceptance | agent + human | J-14a…d | to define with 14a–d | — |

### P3 — Phases H, B, P (kit phases H, B, P; `design.md` §6–§8)

| # | Task | Owner | Prerequisite | Gate | Evidence |
|---|---|---|---|---|---|
| H-1 | Owned host node build + pairing from the phone: expiring challenge, replay refusal, independent TLS-pin confirmation, scoped health/log reads, signed revoke ack, "Revocation pending" offline (`design.md` §8) | agent (client), human (host + device) | Phase V closed; an owned Linux host; pinned `deproof-node` (`scripts/node-agent.pin`) | **Gates 11a–c: NEEDS DEFINITION** (named in the owner's plan; no definition in the repo; MASTER-BUILD-PROMPT says kit H criteria are new and are not legacy 11a–c) | to define |
| H-2 | Owned-host AI from a phone: model discovery, bounded document, response, cancel, public-endpoint refusal (`design.md` §6) | human (device + host) | Ollama-compatible owned host | part of 11a–c (NEEDS DEFINITION) | to define |
| B-0 | **Backup envelope: NOT BUILT.** Design + implement a versioned, authenticated, passphrase-wrapped envelope distinct from vault encryption (KDF params, salt/nonce, bounded parsing, path-traversal defence, manifest integrity, rollback) | agent | D-6; after H (sealed container needs reserved host storage) | JVM tests incl. wrong passphrase / truncated / tampered / unsupported version fail before publication | `evidence/build-<commit>/` |
| B-1 | Upload to the configured owned backend, verify retrieval, record backup receipt; interruption/cancel/quota | agent + human | B-0, D-6 | **Gates 12a–d: NEEDS DEFINITION** | to define |
| B-2 | Restore on a **clean second device** with only backup + passphrase | human | B-1; disposable test profile; explicit approval for any wipe | part of 12a–d | to define (`design.md` §7: show "Restore tested" only with a saved second-device run) |
| B-3 | ~~README/About line: device-bound vault is deliberate; envelope is the answer for portable backup~~ **DONE** in PR #11 (README vault-boundary line; the first-run introduction states it) | agent | — | docs check | `README.md`, `ui/screens/OnboardingScreen.kt` |
| P-1 | Canonical consent payload (version, workload, host, data categories, byte/time/charge limits, retention, expiry, revocation); exact bytes + hash saved; limits enforced in scheduler and protocol; hash supplied as `consent_hash` / `limits_hash` at `accept_job` | agent | Phase V closed; J-5 for the on-chain hash | **Gates 13a–b: NEEDS DEFINITION** | to define |

### P4 — Phases T and R (kit phases T, R; `design.md` §4, §9, §12–§14)

| # | Task | Owner | Prerequisite | Gate | Evidence |
|---|---|---|---|---|---|
| T-1 | Three-label evidence (`KeyProtection` / `ExecutionVerification` / `SettlementState`) on every receipt and node card; unit test that labels cannot cross-derive and a node cannot set its own execution verification | agent | J-5 | Gate 15a (owner's plan; not yet in the runbook): labels visible; cross-derivation test green in CI | test XML + screenshot |
| T-2 | Reputation only from job receipts (acceptance rate), auditable by script | agent | J-14a…d | Gate 15b: script output = app number for a test node | script output + screenshot |
| T-3 | Capability registry: `self-reported` / `independently-checked` with freshness | agent | H-1 | covered by 15a/15b | — |
| R-1 | Claims lint in CI over `strings.xml` and pitch docs (trustless, spoof, guaranteed, mining, passive income, projected, proof of work, …); simulated-success grep | agent | — | Gate 16a: lint green, grep clean | CI log |
| R-2 | LICENSE file; docs truth pass (README commit pin, ledger wording) | agent | D-4 | docs check | — |
| R-3 | Signed, non-debuggable release APK/AAB; preview/test activities stripped; merged manifest + certificate verified | agent (build), human (key) | D-5 | release-manifest check + `apksigner verify --print-certs` | `evidence/release-<tag>/` |
| R-4 | Store kits (Play: title ≤ 30, description ≤ 80, no mining language, wallet-only financial declaration; dApp Store build); data-safety, model-licence, dependency and vulnerability review | agent + human | D-4, D-5, D-11 | owner review | store kit folder |
| R-5 | Clean-clone build from the release tag; APK sha256 matches `APK-IDENTITY.txt`; release manifest with full hashes, signer, environments, evidence links; draft release, publish only on explicit approval | agent | R-3 | Gate 16b | `evidence/release-<tag>/` |
| R-6 | `design.md` visual/accessibility matrix: 360×800, 412×915, 600×960 dp; 100 % and **200 %** font (only 1.5× is rendered today); disconnected/connected/error/unknown; dark mode; TalkBack; keyboard/switch access; reduced motion; measured contrast incl. disabled text | agent (JVM renders) + human (TalkBack on device) | Phase V closed | screenshot suite + device TalkBack notes | `docs/screenshots/`, device evidence |

### P5 — Device-free items (agent; Kotlin ones wait for the Android freeze to lift)

| # | Task | Prerequisite | Gate | Evidence |
|---|---|---|---|---|
| X-1 | **Larger ViewModel split** (`EdgeOreViewModel.kt` 945 lines, 7 features): extract review/node/AI controllers behavior-preservingly (`design.md` §13) | Phase V closed (a device run to catch lifecycle regressions) | all JVM gates exit 0, unchanged counts except added tests | `evidence/build-<commit>/` |
| X-2 | **Sign the `envelopeRequired` flag**: bind it in a new versioned checkpoint; keep legacy verification semantics (older checkers would reject the new version) | freeze lifted | `ReceiptV2Test` + `ReceiptCheckerTest` additions; receipt checker run separately | `evidence/build-<commit>/`, `tools/receipt-checker` XML |
| X-3 | **ABI splits / AAB**: APK is ~57 MB because LiteRT-LM ships arm64 and x86_64 native libs | D-5 for AAB signing | size + install check per ABI | build log, sizes |
| X-4 | **Refresh or remove the stale PR #6 judge walkthrough** (`docs/judges/`, describes 0.2.8-review `d675002bd701`; has a staleness note) | owner choice (refresh vs remove) | static content check | `docs/judges/` |
| X-5 | `design.md` shell items not in the app yet: navigation rail at ≥ 600 dp and two-column ≥ 840 dp; "Devnet · Review candidate" environment strip; Mine heading "Your edge, under your control"; Receipts header "Receipts whose signed contents verify" | freeze lifted; reconcile with the PR #11 Now card (design.md §5 order predates it) | screenshot re-record with notes; JVM gates | `docs/screenshots/README.md`, `evidence/build-<commit>/` |
| X-6 | `design.md` §4 failure panel (stable code, stage, UTC, safe retry, copy redacted diagnostics) wherever SDK/RPC failures surface | freeze lifted | JVM tests | `evidence/build-<commit>/` |
| X-7 | Regenerate the master zip from current `main` (the existing one is `2a169ff`) | — | `sha256sum` + secret scan | new draft release (owner approval for the upload) |
| X-8 | Apply the Mine rename if D-3 says so | D-3 | screenshots + runbook update | — |

### Latest re-audit score

See (h) "Re-score after PRs #11 and #12": **6.8/10** (6.825), unchanged. The UX port fixed the known Appetize prefill blocker in code and added useful, tested UX, but every dimension that limits the score is held back by missing device evidence, release signing and the LICENSE, which the UX work did not change.

---

## (m) Ready-to-paste prompt for Google AI Studio

```text
You are continuing work on EdgeORE (https://github.com/CodesbyFebin/EdgeORE), a native Kotlin/Jetpack Compose
Android app for reviewing a Solana devnet transfer before signing via Mobile Wallet Adapter, submitting it as a
separate action, recovering after a restart without resending, and exporting a receipt whose signed contents can be
verified offline. The repo also contains an unaudited, undeployed devnet Anchor prototype in onchain/.

Read first, in this order: docs/HANDOFF-GOOGLE-AI-STUDIO.md (especially "ALL REMAINING TASKS" and "Production kit
import notes"), AGENTS.md, design.md, MASTER-BUILD-PROMPT.md, docs/production/GATES.json and README.md,
docs/DEVICE-RUNBOOK.md, docs/SETTLEMENT-SPEC.md, docs/DEPLOY-DEVNET.md, evidence/settlement-status.md,
evidence/integration-0.2.9/REAUDIT.md, docs/analysis/clearance-app-review.md, docs/known-limitations.md.
Note: SOURCE-PROVENANCE.json and START-HERE.md describe the kit snapshot 6f7b600; main is newer. Branch from
current main, never from 6f7b600.

Hard rules — never break them:
1. No mining-income, reward, APR, boost, yield or "passive income" claims. No token.
2. No ORE or SKR integration claims; docs/design/ore-integration-future.md stays "DESIGN ONLY - NOT BUILT".
   Treasury/rewards/mining work stays research-only unless the owner rules otherwise.
3. No production-readiness claims. Builds are debug-signed review candidates.
4. Keep upstream attribution (NOTICE: Google AI Edge Gallery, LiteRT-LM, ZXing core; deproof-node from
   DeProof--EdgeORE; Clearance credited as the owner's prior work).
5. Describe the receipt checker only as: it "verifies the integrity of signed receipt contents".
6. Never cite the unpublished single-byte mutation experiment.
7. Report Android (:app), screenshot, receipt-checker and release-manifest results separately, never combined.
   Same for LiteSVM vs local-validator Anchor counts.
8. Preserve tested dependency versions; any change needs a resolved-classpath comparison showing 0 existing
   versions changed.
9. Never commit secrets: keypairs, id.json, privateKey values, local.properties, keystores, API tokens.
10. A gate is PASS only with saved evidence tied to a commit/APK. JVM or simulator results are never device
    results. Unknown = NOT_RUN. Gates 11a-c, 12a-d, 13a-b are NOT DEFINED yet: propose definitions, do not invent
    results.
11. No cloud transaction auditing. Plain-language text comes only from decoded bytes and never replaces the
    exact-message review.
12. Android (Kotlin/Compose/build) changes stay frozen until the owner's device evidence (Phase V) is triaged,
    except where the owner explicitly approves a change. On-chain Rust work may continue on a branch. Never
    deploy the program, sign or send a transaction, spend funds or publish a release without the owner's
    explicit, specific approval. Never resend a transaction whose outcome is unknown.

Current state: main = 3b951575d516 (PR #12 merge) plus the docs PR carrying the latest handoff; PR #11 (Clearance
UX port: onboarding, Now card, empty review fields, Scan QR / QR from image, plain-language card, a11y, ZXing
3.5.3) and PR #12 (runbook gates 1a/5a/5b, QR test images) merged. Latest gates on tested commit 3c2b2ac1378e,
each exit 0: Android unit 254 tests (253 passed, 1 skipped); lint clean; screenshots 25/25; receipt checker 14/14;
release-manifest 9/9 + PASS. APK 0.2.9-review sha256 17b6209e2839162fdb72a9d9125bd10a2f41119aa9dc228358f9e5f2f807eb6b,
About stamp 3c2b2ac1378e, on unpublished draft release untagged-1ed2b9003afb4d03ae79. Settlement: tested source
047657f89b48 (head 305f1e7): LiteSVM 25/25 exit 0; local solana-test-validator Agave 4.3.0 25/25 exit 0; not
deployed, not integrated, no real payout. Runtime evidence: Appetize Pixel 7/API 33 Connect PASS and Disconnect
PASS on build 3676094ad487; the one approved 0.01 SOL transfer was never signed; d675002bd701 launch/navigation
PASS. Every runbook gate on the current APK is NOT_RUN. Re-audit score: 6.8/10.

Your next task: <owner fills in, e.g. "triage RESULTS.md from the attached evidence folder into one ticket per
FAIL (V-T)", "propose definitions for Gates 11a-c (H-1)", or "X-2: bind envelopeRequired in a new checkpoint
version, on a branch">.
Before writing code, name the ALL REMAINING TASKS item(s), list the files you will change and the gates you will
re-run. After changes, run each gate separately (SCREENS=1 bash scripts/qualify.sh; receipt checker;
release-manifest check; onchain LiteSVM and local validator with --skip-deploy when onchain/ changes) and report
counts and exit codes per gate, using docs/production/EVIDENCE-TEMPLATE.md for any device gate.
```
