# EdgeORE handoff for Google AI Studio

**Written:** 2026-10-10 (IST, UTC+5:30), by the build agent, from repository files and recorded evidence only.
**Repository:** https://github.com/CodesbyFebin/EdgeORE
**Final `main`:** ``38fb71d5c25ef944152edecfd128ab12e0fce2e0` is the integration merge (PR #8); `0f58ed97303a` then merged PR #6; this handoff document, `evidence/settlement-status.md` and a staleness note for `docs/judges/` are added by the docs PR that follows (see the final report for that merge SHA)` (verified with `gh api repos/CodesbyFebin/EdgeORE/branches/main`).
**Tested source commit for the gates below:** `250be029b35c`.
**Status in one line:** a JVM-, build- and local-simulator-qualified Android review candidate (`0.2.9-review`, debug-signed) plus an unaudited, undeployed devnet Anchor prototype. Almost nothing has run on a real device. It is not production-ready, it earns nothing and it does not integrate ORE or SKR.

---

## (a) Project summary and hard rules

EdgeORE is a native Kotlin / Jetpack Compose Android app (package `com.edgeore.app`, minSdk 26, target/compile SDK 35) for reviewing a supported Solana devnet transaction before signing it through Mobile Wallet Adapter, submitting it as a separate action, recovering safely after a restart (observe, never resend), and exporting a receipt that can be checked outside the app. It also has clients for AI and nodes on infrastructure the user owns, an AES-256-GCM local storage vault, an optional on-device LLM path (LiteRT-LM, after Google AI Edge Gallery), an opt-in contribution scheduler that runs no workload, and, in `onchain/`, a devnet-only Anchor program that settles verifier-signed work receipts in SOL lamports.

The five app destinations are **Mine, Private AI, Storage, Nodes, Receipts**, plus a dedicated **Review** route that controls wallet signing.

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

---

## (b) Repository map and architecture

```
EdgeORE/
├── app/                        Android app module (:app), Kotlin + Compose
│   └── src/main/java/com/edgeore/app/
│       ├── MainActivity.kt, EdgeOreViewModel.kt (920 lines, owns 7 features), UiState.kt,
│       │   EdgeOreCore.kt, Trust.kt, BuildConfigInfo.kt
│       ├── wallet/     WalletAuthorization, WalletConnection, WalletCoordinator, WalletDisplay (MWA)
│       ├── solana/     SolanaMessage, SolanaRpc, TransferReview, TransferCoordinator, Operations
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
│       └── ui/         EdgeOreApp, screens/{Mine,Ai,Storage,Nodes,Receipts,Review}Screen, components, theme
│   ├── src/main/assets/ai/ondevice-model-allowlist.json   pinned model allowlist (digest, size, licence)
│   ├── src/release/AndroidManifest.xml                    removes exported test activities in release
│   └── src/test/java/com/edgeore/app/                     24 JVM test classes + screens/ (Robolectric, Roborazzi)
├── ondevice-llm/               Android library isolating LiteRT-LM 0.8.0 (litertlm-android)
├── tools/receipt-checker/      standalone JVM Gradle project; compiles the app's Kotlin verifier
├── scripts/                    qualify.sh (build gate), verify-receipt.sh, check-manifest.py (+ tests),
│                               junit-summary.py, build-node-agent.sh, node-agent-it.sh, node-agent.pin
├── onchain/                    Anchor workspace: programs/receipt-settlement (devnet-only prototype)
├── docs/                       DEVICE-RUNBOOK.md, storage-vault.md, on-device-ai.md, known-limitations.md,
│                               qualification-status.md, design/ore-integration-future.md, screenshots/ …
├── evidence/                   gate logs, JUnit XML, re-audit (integration-0.2.9/REAUDIT.md), Appetize session
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
- Key pinned dependencies: Compose BOM `2024.12.01`, activity-compose `1.9.3`, core-ktx `1.15.0`, lifecycle `2.8.7`, coroutines `1.9.0`, `work-runtime-ktx` `2.10.0`, MWA `mobile-wallet-adapter-clientlib-ktx` `2.0.3`, BouncyCastle `bcprov-jdk18on` `1.78.1`, LiteRT-LM `litertlm-android` `0.8.0`; tests: JUnit `4.13.2`, Robolectric `4.14.1`, Roborazzi `1.36.0`, `work-testing` `2.10.0`, `org.json` `20240303`.

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
| Screenshot suite (Robolectric/Roborazzi renders, fixed device readings) | `app/src/test/.../screens/`, `docs/screenshots/` | `a973cf8` | 20 screenshot tests (separate `-Pscreens` run) | JVM renders, not device captures |
| ORE integration design | `docs/design/ore-integration-future.md` | `docs/ore-integration-design` @ `22370c5` | — | **DESIGN ONLY - NOT BUILT** |
| Settlement program v2 (escrow, accept with `consent_hash`/`limits_hash`, proof, verifier-signed settle-once, permissionless refund crank, Clock-only deadlines) | `onchain/`, `docs/SETTLEMENT-SPEC.md`, `docs/DEPLOY-DEVNET.md`, `scripts/deploy-devnet.sh` | `feature/settlement-spec-v2` @ `305f1e7` (tested source `047657f89b48`); merged via PR #8 (`38fb71d5c25e`) | **LiteSVM: 25/25 passed, exit 0. Local solana-test-validator, Agave 4.3.0: 25/25 passed, exit 0.** (v1 at `823e649` had 29 LiteSVM / 28 validator; the mechanism of the v1 delta is in SETTLEMENT-SPEC §9) | Not deployed; not wired to the app; no real SOL payout. Status: `evidence/settlement-status.md` and wiki page `Settlement-Status` |

Unit-test numbers per class come from `evidence/integration-0.2.9/gate-250be029b35c/junit/`. `EdgeOreViewModel.kt` is still 920 lines and owns seven features (controller split deferred until a device run can catch lifecycle regressions).

---

## (e) Evidence ledger

### Final gate run on `250be029b35c` (each suite run separately)

| Suite | Command | Exit | Counts | Evidence |
|---|---|---|---|---|
| Android gate | `SCREENS=1 bash scripts/qualify.sh` | **0** | Unit: 24 suites, **245 tests: 244 passed, 0 failed, 0 errors, 1 skipped** (`NodeAgentIntegrationTest`). Lint: no issues | `evidence/integration-0.2.9/gate-250be029b35c/summary.md`, `junit/` |
| Screenshot suite | `./gradlew :app:verifyRoborazziDebug -Pscreens` | **0** | **20/20 passed** | `…/screens-junit/`, `…/screens-verify.log` |
| Release-manifest check | `python3 -m unittest scripts/test_check_manifest.py` + `check-manifest.py` | **0** | 9/9 tests; MANIFEST CHECK: PASS | `…/release-manifest/standalone-run.log` |
| Receipt checker (separate project) | `bash gradlew -p tools/receipt-checker … clean test` | **0** | **14/14 passed** | `…/receipt-checker/` |
| Settlement, LiteSVM (re-run on the merge) | `anchor test --skip-build --skip-local-validator --skip-deploy` | **0** | **25/25 passed** | `…/anchor/anchor-test-litesvm.log` |
| Settlement, local solana-test-validator, Agave 4.3.0 (re-run on the merge) | `anchor test --skip-build --validator legacy --script validator …` | **0** | **25/25 passed** | `…/anchor/anchor-test-validator.log` |
| Settlement branch ledger | tested source `047657f89b48` (evidence-only head `305f1e7`) | **0 / 0** | **LiteSVM: 25/25 passed, exit 0. Local solana-test-validator, Agave 4.3.0: 25/25 passed, exit 0.** | `evidence/anchor-receipt-settlement/047657f89b48/` |

**APK identity:** `com.edgeore.app` `0.2.9-review` versionCode 11, sha256 `c547d7dfe5402d4f390a87571d9d31028506b11194742fd1c154b2b842a88d5b`, 57,286,793 bytes, About stamp `EdgeORE 0.2.9-review (250be029b35c)`, Android debug signer `c9b46665…b7ae4b93`. Settlement `.so`: SBPF v3, 274,352 bytes, sha256 `9ce9addf…09bf78a7` (same in branch evidence and the merge re-run).

**Settlement, plainly:** the tests passing do **not** establish deployment, Android integration or a real SOL payout. Those stay unqualified until demonstrated.

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

The candidate APK in this handoff (``250be029b35c`, sha256 `c547d7df…42a88d5b``) has **not run on any device or emulator**.

### Device runbook gates (`docs/DEVICE-RUNBOOK.md`) on the current APK

| Gate | What | Status | Evidence files to save |
|---|---|---|---|
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
| **No backend / IPFS node.** | Remote backup is *Not configured*; nothing uploads. Gates 12a–d cannot run. | Owner: decide on and provision an authenticated backend + owned IPFS node. |
| **Passphrase-wrapped backup envelope not built.** | Restore after key loss is impossible by design today. | Engineering (Phase B), after Phase H. |
| **No release key.** | Only debug-signed APKs exist; no signed release, no provenance attestation. | Owner: create and hold a release keystore (never commit it). |
| **No LICENSE decision.** | Repo has `NOTICE` but no project licence; MIT must not be claimed. | Owner. |
| **Legal / store-policy review.** | Any money-handling, settlement or store listing language needs review before publishing. | Owner + counsel. |
| **Settlement status page.** | The wiki page `Settlement-Status` (linked from the wiki sidebar) and `evidence/settlement-status.md` carry the settlement ledger and its limits. | — |
| **Android freeze during Phase V.** | Kotlin/Compose/build changes stay frozen until the owner's device evidence is triaged. Rust (`onchain/`) work may proceed on a branch. | Owner sends `RESULTS.md` + evidence folder + `SHA256SUMS`. |
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

**Prerequisites:** `adb`, `curl`, `python3`; a checkout of this repo at the APK's commit with JDK 17 (for `scripts/verify-receipt.sh`); the EdgeORE APK ``EdgeORE-0.2.9-review-250be029b35c-debug.apk` (sha256 `c547d7dfe5402d4f390a87571d9d31028506b11194742fd1c154b2b842a88d5b`)` (in the master zip and not yet attached to any GitHub release — the existing draft release `v0.2.9-review-device-test` carries the older `28acd193fc7c` APK; a new draft needs the owner's go-ahead); a mock wallet you build yourself from `solana-mobile/mock-mwa-wallet` with a **throwaway devnet key** in its gitignored `local.properties` (`privateKey=`), as its README describes. Never fund it with real value. Gate 9 needs an arm64 device with ≥ 6 GB RAM and ~2 GB free.

1. **Gate 0 — kit integrity and device identity.** `sha256sum -c SHA256SUMS`; `adb devices -l`; read `ro.product.model`, `ro.build.version.release`, `ro.build.version.sdk`, `ro.product.cpu.abi`, `ro.kernel.qemu`. PASS: every checksum line ends `OK`. Files: `00-*`.
2. **Gate 1 — install.** `adb install -r` both APKs; `dumpsys package com.edgeore.app | grep version`; screenshot About. PASS: `Success`, `versionName=0.2.9-review`, `versionCode=11`, About stamp equals the APK commit with no `-dirty`. If `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, uninstall the old one only after exporting anything you need (uninstall destroys its vault and receipts). Files: `01-*`.
3. **Gate 2 — secure lock screen.** Set a PIN/pattern/password; `dumpsys lock_settings`. Files: `02-lockscreen.txt`, `02-lockscreen.png`.
4. **Gate 3 — logcat.** `adb logcat -c`; capture `EdgeORE.Wallet:V '*:S'` and a private full log; afterwards strip `Decrypted information` lines. PASS: filtered log has Connect, Disconnect and signing lines; remaining count `0`.
5. **Gate 4 — Connect.** Authenticate in the wallet first, then Mine → Connect wallet → Authorize. PASS: connected address shown, no error, matching authorize log line. FAIL evidence: exact error text + screenshot.
6. **Gate 5 — Disconnect.** PASS: `Disconnected. The wallet confirmed deauthorization.` Reconnect for Gate 6.
7. **Gate 6 — the one approved transfer + force-stop recovery.** Record balances; replace **both** prefilled fields with `73Cc84sjGtk3RNSVoEres4PLMcZZtg4n46kQUzJnR93n` and `0.01`; check the decoded review (devnet, sender, recipient, 10,000,000 lamports, fee); sign; tap **Submit to devnet** once and force-stop within ~1 s; reopen and record which case happened (sent → observe only, no Submit button; not sent → check Explorer first, then submit the same bytes once; expired → discard and stop). Save signature, `getSignatureStatuses`, `getTransaction`, Explorer URL + screenshot, balances after. **Submit exactly once; never prepare a second transfer.** PASS: `err:null`, confirmed/finalized, recipient +10,000,000 lamports, never offered a resend.
8. **Gate 7 — real receipt.** Export the Gate 6 receipt, pull it with `run-as`, make the lamports+1 copy with the runbook's Python snippet, run `scripts/verify-receipt.sh` on both. PASS: original `exit=0` with the envelope line; lamports+1 `exit=1`. Exit 2 is a toolchain error, not a result.
9. **Gate 8 — Keystore.** About must read `Android Keystore P-256 · key <16 hex>` (software fallback = FAIL); export key protection matches; vault verify reports intact; `/data/data/com.edgeore.app` is `Permission denied` from the shell.
10. **Gate 9 — on-device AI (arm64 only).** Download `Qwen2.5-1.5B-Instruct (q8)` (1,597,931,520 bytes) after consent, verify checksum, enable airplane mode, run one prompt. PASS: reply generated with `airplane_mode_on=1` and no active network, title `On-device model loaded`. On x86_64 record `NOT_RUN (no arm64)`.
11. **Gate 10 — close-out.** Stop captures, filter, `dumpsys package`, write `SHA256SUMS`, fill `RESULTS.md`.
12. **J-4 — settlement program deploy gate (owner decision first; one-time).** Only after J-1…J-3 are accepted and the interface is frozen:
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

### Latest re-audit score

From `evidence/integration-0.2.9/REAUDIT.md` (round 3, scored on `28acd193fc7c`; not re-scored after the settlement v2 merge, which changed no Android code):

| Dimension | Weight | Audit (9 Oct) | Now | Main gap |
|---|---|---|---|---|
| Source architecture | 20% | 7 | 8 | `EdgeOreViewModel.kt` 920 lines, feature-controller split deferred |
| Wallet and transfer | 20% | 6 | 6.5 | No transfer signed/submitted/confirmed; recovery never on a device; reconnect NOT_RUN |
| Security and data durability | 15% | 5 | 7.75 | Keystore unproven on a device; no signed release; `envelopeRequired` flag unsigned; older checkers reject STORAGE receipts |
| Test evidence | 15% | 5 | 7.75 | Almost all JVM/local; device evidence is 2 hosted-emulator checks on a different build |
| AI, node and resource functionality | 10% | 4 | 5 | No on-device generation, owned-host reply from a phone or phone-node pairing |
| Release qualification | 15% | 3 | 4 | No release keystore, signed release, LICENSE, install, or demo video |
| Honest product boundaries | 5% | 8 | 9 | — |
| **Weighted** | 100% | **5.4** | **6.8/10** | Re-audit says about 7.5 is plausible after runbook items 1–3 (transfer, recovery, real receipt) pass on the current build |

### Owner's 10-point scorecard

| # | Point | Status now | Missing for 10/10 |
|---|---|---|---|
| 1 | Build truth | **Partial.** Reproducible JVM gate (`qualify.sh`) exits 0 with separate counts; APK identity recorded. | Clean-clone build from a release tag (Gate 16b), signed release, provenance. |
| 2 | Wallet journey | **Partial.** Connect + Disconnect PASS in one hosted-emulator session on `3676094ad487`. | Sign, separate submit, confirm on devnet, force-stop recovery (runbook Gates 4–6) on the current APK. |
| 3 | Receipts | **Partial.** Checker tests pass (JVM); a pre-envelope export from `d675002bd701` verifies (original PASS, lamports+1 FAIL). | Real-transfer receipt export verified on a second machine (Gate 7); envelope flag binding needs a new checkpoint version. |
| 4 | On-device AI | **Built, NOT_RUN.** LiteRT-LM path, allowlist, consented download, JVM tests. | One download + airplane-mode generation + cancel on a named arm64 phone (Gate 9). |
| 5 | Owned host node | **Partial.** Owned-host AI client and node pairing client; live loopback node test on `main` earlier. Phase H host build not started. | Phase H host build; owned-host reply, cancel and public-endpoint refusal from a phone; Gates 11a–c. |
| 6 | Backup / restore after key loss | **Not built.** Device-bound vault is deliberate; no passphrase-wrapped envelope exists in the repo; no backend. | Phase B envelope + host storage + restore rehearsal; Gates 12a–d. |
| 7 | Funded job settlement | **Prototype (local only).** Anchor program 29/29 LiteSVM, 28/28 local validator in v1; **v2 (spec vocabulary): LiteSVM 25/25 passed, exit 0; local solana-test-validator, Agave 4.3.0, 25/25 passed, exit 0** on tested source `047657f89b48`. Not deployed, not wired to the app. | J-1…J-3 accepted, J-4 deploy, J-5 app integration, Gates 14a–d on devnet from the app. |
| 8 | Trust surface | **Partial.** Evidence dimensions separated in the verifier; `ExecutionLabel`; controls labelled Enforced/Saved only/Unavailable. | Three-label evidence (`KeyProtection` / `ExecutionVerification` / `SettlementState`), job-receipt-only reputation, capability registry; Gates 15a–b. |
| 9 | Permissions / consent | **Partial.** Opt-in scheduler (off by default), consent dialog before model download, explicit endpoint choice. `consent_hash`/`limits_hash` on-chain fields: recorded at `accept_job` in v2 (`accept_records_node_consent_and_limits`); the program checks they are non-zero, not what they mean, and the app does not produce them yet. | Phase P consent flow; Gates 13a–b. |
| 10 | Release readiness | **Open.** Debug-signed only; release manifest policy check passes; no LICENSE; no store kits. | Release key, signed APK/AAB, claims lint, store kits, final evidence bundle; Gates 16a–b. |

### Phase ladder (owner's plan; V → H → B → P → J → T → R)

| Phase | Tag | Tasks | Gates |
|---|---|---|---|
| **V** — device verification triage | `0.2.10` | Owner runs `DEVICE-RUNBOOK.md` gates 0–10; one ticket per FAIL; Android changes resume only after this. | Runbook 0–10 |
| **H** — owned host node | `0.3.0-alpha.1` | Host node build and pairing from the phone. | 11a–c (definitions in the owner's plan, not yet in the repo) |
| **B** — backup / restore | `0.3.0-alpha.2` | Passphrase-wrapped envelope integrated with reserved host storage; capacity re-measurement hook; sync; restore-rehearsal UI. Sequenced after H. | 12a–d |
| **P** — permissions / consent | `0.3.0-alpha.3` | Consent capture whose hash is recorded on-chain at accept. | 13a–b (definitions in the owner's plan) |
| **J** — funded jobs / settlement | `0.3.0-alpha.4` | **Status: J-1, J-2 and J-3 done on `feature/settlement-spec-v2` (merged via PR #8; see `docs/SETTLEMENT-SPEC.md` §1, §9, §10); J-4 prepared (`scripts/deploy-devnet.sh`, `docs/DEPLOY-DEVNET.md`), NOT RUN; J-5 not started.** Tasks: J-1 test-delta mechanism; J-2 one vocabulary (`create_job / accept_job / submit_proof / verify_and_settle / refund_after_deadline`) in `docs/SETTLEMENT-SPEC.md` v2; J-3 layout checks (structural settle-once flag, `consent_hash`, `limits_hash`, terms hash + deadline + verifier at create, permissionless refund crank, on-chain `Clock`, payout to the node recorded at accept, zero-lamport-movement on duplicate settle); J-4 deploy once with byte-identity; J-5 IDL client, resolver, job-receipt second ledger, funded-jobs views; first job type bounded and deterministic (OCR or embedding). | 14a lifecycle (settled SOL = escrow, Explorer link), 14b second settle fails on-chain, 14c rejection refunds exactly, 14d timeout → OUTCOME_UNKNOWN, no resend. Evidence: `14-lifecycle.json`, `14-duplicate-failed.txt`, `14-refund.json`, `14-observe-only.png` |
| **T** — trust surface | `0.3.0-review` | Three-label evidence on every receipt and node card (labels cannot cross-derive; a node can never set its own execution verification); reputation from job receipts only; capability registry with `self-reported` / `independently-checked` and freshness. | 15a labels + cross-derivation unit test in CI; 15b reputation script matches the app |
| **R** — release | `0.3.0-review` → `1.0.0-candidate` | Claims lint in CI over `strings.xml` and `PITCH.md` (trustless, spoof, guaranteed, mining, passive income, projected, proof of work, …); docs truth pass; Play + dApp Store kits; final evidence bundle. | 16a lint green + simulated-success grep clean; 16b clean-clone build from the tag, APK sha256 matches `APK-IDENTITY.txt` |

**Definition of done (owner):** a stranger can install the release APK, pair their own host, back up a file, destroy the phone, restore it from passphrase alone, accept one customer-funded devnet job, watch it settle exactly once, and walk away with a receipt whose three labels and settlement they verified offline themselves.

---

## (i) Decisions needed from the owner

1. **Run the device runbook** (own phone or local emulator) and send `RESULTS.md` + evidence folder + `SHA256SUMS`. This is the front of the queue.
2. **Use or withdraw the single approved 0.01 devnet SOL transfer** to `73Cc84sjGtk3RNSVoEres4PLMcZZtg4n46kQUzJnR93n` (approved once, never used).
3. **Appetize:** upgrade the plan (costs money) or stop using hosted sessions.
4. **Release key:** create and hold a release keystore; decide APK vs AAB / ABI splits (APK is ~57 MB because LiteRT-LM ships arm64 and x86_64 native libs).
5. **LICENSE:** choose a project licence (MIT must not be claimed until a file exists).
6. **Backup backend + IPFS node:** whether to build them, and who operates them.
7. **Settlement program:** accept the J-1…J-3 results, freeze the interface, then approve the one-time J-4 devnet deploy (and who holds the upgrade authority).
8. **Cross-process wallet reconnect:** keep "Not connected after restart" (current, by design) or add persistence.
9. **Legal / store-policy review** before any money-handling or store listing.
10. **The stale PR #6 walkthrough page** (`docs/judges/`, describes 0.2.8-review): merged as-is (PR #6, it is explicitly labelled as describing `0.2.8-review` / `d675002bd701` and performs no operations); a note added to `docs/judges/README.md` says its numbers are from 0.2.8. Decide whether to refresh it for 0.2.9 or remove it.

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

---

## (k) Ready-to-paste prompt for Google AI Studio

```text
You are continuing work on EdgeORE (https://github.com/CodesbyFebin/EdgeORE), a native Kotlin/Jetpack Compose
Android app for reviewing a Solana devnet transfer before signing via Mobile Wallet Adapter, submitting it as a
separate action, recovering after a restart without resending, and exporting a receipt that can be verified
offline. The repo also contains an unaudited, undeployed devnet Anchor prototype in onchain/.

Read docs/HANDOFF-GOOGLE-AI-STUDIO.md first, then evidence/integration-0.2.9/REAUDIT.md, docs/DEVICE-RUNBOOK.md,
docs/known-limitations.md and onchain/programs/receipt-settlement/README.md, docs/SETTLEMENT-SPEC.md (canonical settlement reference), docs/DEPLOY-DEVNET.md and evidence/settlement-status.md.

Hard rules — never break them:
1. No mining-income, reward, APR, boost, yield or "passive income" claims. No token.
2. No ORE or SKR integration claims; docs/design/ore-integration-future.md stays "DESIGN ONLY - NOT BUILT".
3. No production-readiness claims. Builds are debug-signed review candidates.
4. Keep upstream attribution (NOTICE: Google AI Edge Gallery, LiteRT-LM; deproof-node from DeProof--EdgeORE).
5. Describe the receipt checker only as: it "verifies the integrity of signed receipt contents".
6. Never cite the unpublished single-byte mutation experiment.
7. Report Android (:app) and receipt-checker test counts separately, never combined. Same for LiteSVM vs
   local-validator Anchor counts.
8. Preserve tested dependency versions; any version change needs a resolved-classpath comparison
   showing 0 existing versions changed.
9. Never commit secrets: keypairs, id.json, privateKey values, local.properties, keystores, API tokens.
10. A gate is PASS only with saved evidence tied to a commit/APK. JVM or simulator results are never device
    results. Unknown = NOT_RUN.
11. Android (Kotlin/Compose/build) changes stay frozen until the owner's device evidence (Phase V) is triaged;
    on-chain Rust work may continue on a branch. Never deploy the program or sign/send a transaction without the
    owner's explicit, specific approval. Never resend a transaction whose outcome is unknown.

Current state: main = 38fb71d5c25e (integration merge, PR #8) plus later docs-only merges; tested commit 250be029b35c; gates: Android unit 245 tests (244 passed, 1 skipped) exit 0; screenshots 20/20 exit 0; release-manifest 9/9 + PASS exit 0; receipt checker 14/14 exit 0; settlement LiteSVM 25/25 exit 0; settlement local validator 25/25 exit 0.
Runtime evidence: Appetize Pixel 7/API 33 Connect PASS and Disconnect PASS on build 3676094ad487 (mock wallet
Build A); the 0.01 SOL transfer was never signed; d675002bd701 launch/navigation PASS. Everything else NOT_RUN.

Your next task: <owner fills in, e.g. "triage RESULTS.md from the attached evidence folder into one ticket per
FAIL using the TICKET format in the handoff", or "J-3 layout checks on a branch under onchain/ only">.
Before writing code, list the files you will change and the gates you will re-run. After changes, run each
gate separately (SCREENS=1 bash scripts/qualify.sh; receipt checker; release-manifest check; onchain LiteSVM
and local validator with --skip-deploy) and report counts and exit codes per gate.
```
