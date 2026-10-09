# Qualification status: `0.2.8-review` (branch `fix/audit-p0`)

This is the **current** ledger. Every claim names the source that implements it and the evidence that checked it.
Older documents (`final-qualification.md`, `qualification-report.md`, `release-baseline.md`, `submission-checklist.md`,
`device-commands.md`, `device-walkthrough.md`, `acceptance-matrix.md`, `roadmap.md`, `demo-storyboard.md`) describe the
frozen `0.2.6-review` candidate and are kept as history.

Status words:
- **PASS (JVM)**: ran on the build box and passed. This is not a phone result.
- **PASS (live, loopback)**: ran against a real external program on the build box.
- **NOT_RUN**: needs the owner's phone, wallet, model host or a deployed agent. No result exists.
- **UNAVAILABLE**: the mechanism does not exist in this build.

## Candidate

| Item | Value | Evidence |
|---|---|---|
| Package | `com.edgeore.app` `0.2.8-review`, versionCode 10 | `app/build.gradle.kts`, aapt badging in `evidence/build-*/summary.md` |
| Source link | `BuildConfig.GIT_COMMIT` (12-char hash, `-dirty` if tracked files changed), shown in the app's About text | `app/build.gradle.kts`, `BuildConfigInfo.kt` |
| Signing | Debug APK, signed with the Android **debug** key. No `0.2.7`/`0.2.8` release APK was signed: the release keystore is not on the build box. | `apksigner` line in the gate summary |
| Cluster | Solana devnet only | `SolanaRpc`, `Operations.kt` |

## Build gate

`bash scripts/qualify.sh` runs a clean `testDebugUnitTest`, `lintDebug`, `assembleDebug` and `assembleDebugAndroidTest`.
Results are written to `evidence/build-<commit>/`. `SCREENS=1` also verifies the screenshot suite, and `NODE_IT=1` also runs the live agent test.

| Run | Exit | Unit tests (JUnit XML) | Lint | Evidence |
|---|---|---|---|---|
| `main` `ab009ff` (baseline) | 0 | 63 tests: 62 passed, 0 failed, 0 errors, 1 skipped | No issues found | `evidence/phase-a/` |
| `5bfabab` (P0/P1 source) | 0 | 143 tests: 142 passed, 0 failed, 0 errors, 1 skipped | No issues found | `evidence/p0-p1/` |
| `f6907e7` (final gate: P0–P2) | 0 | 148 tests: 147 passed, 0 failed, 0 errors, 1 skipped. Also `verifyRoborazziDebug -Pscreens` exit 0; `node-agent-it.sh` exit 0 (1 test, 0 skipped) | No issues found | `evidence/build-f6907e75941f/` |
| `0.2.8-review` (store/status/signing/expiry fixes) | the gate for this commit writes `evidence/build-<commit>/summary.md` in its clone; the numbers are recorded in PR #2 | | | — |

In the normal suite, the skipped test is always `NodeAgentIntegrationTest`, which needs a live agent. That test runs separately in `scripts/node-agent-it.sh`, which fails if the test is skipped.

## Audit backlog

| Pri | Item | Status | Source | Evidence |
|---|---|---|---|---|
| P0 | Clean build evidence | PASS (JVM) | `scripts/qualify.sh`, `scripts/junit-summary.py` | `evidence/phase-a/`, `evidence/p0-p1/`, `evidence/build-*/` |
| P0 | Durable operation state | PASS (JVM) | `solana/Operations.kt` | `DurableOperationTest` |
| P0 | Single-flight transaction actions | PASS (JVM) | `solana/TransferCoordinator.kt` | `repeatedSubmitTapsProduceExactlyOneSend`, `repeatedApproveTapsOpenOneSigningSession` |
| P0 | Unknown-outcome recovery | PASS (JVM) | `TransferCoordinator.observe/reconcileAll/recoverAfterRestart` | `DurableOperationTest` (timeout, restart, expiry, cancellation, mismatch) |
| P0 | Bounded file reads | PASS (JVM) | `io/SafeFiles.kt` | `BoundedIoAndVaultTest` |
| P0 | Atomic vault publication | PASS (JVM) | `AtomicFiles`, `storage/LocalVault.kt` | `BoundedIoAndVaultTest` |
| P0 | Store write failure: memory == disk, latched refusal, no send | PASS (JVM) | `OperationStore.commit` (copy-on-write; `unavailableReason`), `TransferCoordinator.stored/afterSend` | `StoreFailureAndSigningGateTest` (failure injected at reserve, every transition, submit attempt, post-send, observe, markReceipted) |
| P0 | Strict signature-status parsing | PASS (JVM) | `parseStatus` -> `ChainStatus.Unavailable` | `TransportHardeningTest.malformedStatusIsUnavailableNeverNotFound`, `StoreFailureAndSigningGateTest.unavailableStatusNeverExpiresOrReleases` |
| P0 | Block-height check before the wallet opens | PASS (JVM) | `TransferCoordinator.beginSigningChecked`, `EdgeOreViewModel.approveAndSign` | `StoreFailureAndSigningGateTest` (height unavailable, expired, near expiry, concurrent taps) |
| P0 | Expiry policy (below) | PASS (JVM) | `TransferCoordinator.observe` | `DurableOperationTest.notFoundAfterExpiryOfASentOperationStaysUncertainAndKeepsItsReservation`, `signedButUnsentExpiresWithoutAnySend` |
| P0 | Real device transfer | **NOT_RUN** | — | needs a phone, an MWA wallet and devnet SOL |
| P1 | Durable spend reservations | PASS (JVM) | `OperationStore.reserve/exposure` | `DurableSpendTest` |
| P1 | Wallet-aware receipt verifier | PASS (JVM) on generated fixtures; a real export is NOT_RUN | `ReceiptVerifier` | `ReceiptV2Test` |
| P1 | Receipt key epochs | PASS (JVM); Keystore continuity on a phone NOT_RUN | `KeyRegistry` | `ReceiptV2Test` |
| P1 | Corruption-aware receipt reads | PASS (JVM) | `ReceiptLog.read` | `ReceiptV2Test` |
| P1 | Vault version/AAD | PASS (JVM); Android Keystore NOT_RUN | `LocalVault` EOV2 | `BoundedIoAndVaultTest` |
| P1 | AI transport cancellation | PASS (JVM) for the local socket; host-side stop UNAVAILABLE (not acknowledged by the API) | `OwnedHostModelClient.cancelActive` | `TransportHardeningTest` |
| P1 | Bound endpoint resolution | PASS (JVM) | `EndpointPolicy.checkAll/boundUrl` | `TransportHardeningTest` |
| P1 | Node redirect refusal | PASS (JVM, real pinned TLS on loopback) | `NodeAgentClient` | `TransportHardeningTest` |
| P1 | Disk sample continuity | PASS (JVM) | `DiskRateTracker`, `CpuRateTracker` | `TransportHardeningTest`, `DeviceMetersTest` |
| P2 | Current screenshot suite | PASS (JVM render): six pages, API 28, from this source. These are **not** device screenshots. | `app/src/test/.../screens/` | `docs/screenshots/`, `evidence/p2/screens-*.log` |
| P2 | Settings enforcement labels | PASS (JVM): all 15 controls labelled Enforced, Saved only or Unavailable | `settings/ControlEffects.kt`, `EffectNote` | `ControlEffectsTest` |
| P2 | Reproducible agent setup | PASS (live, loopback): pinned revision, two builds with identical SHA-256, integration exit 0 with 0 skipped | `scripts/node-agent.pin`, `build-node-agent.sh`, `node-agent-it.sh` | `evidence/p2/node-agent-*.log` |
| P2 | Release/document truth pass | Done for the documents listed above. The release half is NOT_RUN: no signed `0.2.7`/`0.2.8` release exists. | this file, README, `known-limitations.md` | — |

## Expiry policy

A reservation is released on expiry only when EdgeORE can show the bytes never left the phone.

| Situation | Result | Reservation | Why |
|---|---|---|---|
| Approve tapped; block height past `lastValidBlockHeight` or within 20 blocks of it | `EXPIRED` before the wallet opens | released | nothing was signed or sent; the message is not rebuilt, a fresh review is required |
| Approve tapped; block height cannot be read | `REFUSED` | released | the blockhash cannot be shown to be valid, so nothing is signed |
| `SIGNED`, never submitted, block height past `lastValidBlockHeight` | `EXPIRED` | released | the bytes were never handed to any RPC and can no longer be accepted |
| `SUBMIT_ATTEMPTED`/`SUBMITTED`/`OUTCOME_UNKNOWN`, RPC says NOT_FOUND after `lastValidBlockHeight` | `OUTCOME_UNKNOWN` (stays uncertain) | **kept** | one RPC not finding a signature is strong but not conclusive evidence; the app does not say "can no longer land" or "safe to retry", never resends, and keeps observing |
| Any state; status response malformed (empty or extra array items, wrong types, missing `result`/`value`/`err`/`confirmationStatus`, unknown status) or RPC unavailable | unchanged; observation recorded | kept | `ChainStatus.Unavailable` never counts as NOT_FOUND |
| Status confirmed, finalized or failed | `CONFIRMED`/`FINALIZED`/`FAILED` | kept/kept/released | observed on chain |

If the operation store cannot write a transition, memory is left equal to disk, the store latches unavailable, and every financial action is refused until restart; the Review screen shows the storage error. A failed write of `SUBMIT_ATTEMPTED` means nothing is sent. A failed write after a send leaves `SUBMIT_ATTEMPTED` on disk, which restart recovery turns into `OUTCOME_UNKNOWN`.

## Runbook corrections

- Do not uninstall `0.2.6-review` to install a debug build: its Keystore-bound vault, receipt and node keys are destroyed on uninstall. Use another phone or an emulator.
- `scripts/node-agent-it.sh` runs its agent on `127.0.0.1:19843` (`NODE_IT_PORT`). Manual pairing uses the agent default `9843` with `adb reverse tcp:9843 tcp:9843`.
- Ollama on the host: `adb reverse tcp:11434 tcp:11434`, then `http://127.0.0.1:11434`. Do not use `10.0.2.2`: it is not loopback, so cleartext to it is refused.

## Pinned external pieces

| Piece | Pin |
|---|---|
| DeProof node agent | `CodesbyFebin/DeProof--EdgeORE@7431f0896f4fb5409970f3aa3f64c42f6f877757`; Go toolchain `go1.26.4`; linux/amd64 binary SHA-256 `c90125f35508c7ddf59040c2a6c22a533e92c57a7f7262bfb36862d2ded3e921` (`CGO_ENABLED=0 -trimpath -buildvcs=false -ldflags=-buildid=`) |
| Mobile Wallet Adapter | `com.solanamobile:mobile-wallet-adapter-clientlib-ktx:2.0.3` |
| Gradle wrapper | 8.9, `gradle-wrapper.jar` SHA-256 `498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17` |
| Screenshot stack | Robolectric 4.14.1, Roborazzi 1.36.0 (test classpath only) |
| AI model | None pinned. No weights are in the APK. |

## Not run (no result exists)

| Check | Why |
|---|---|
| Install on a physical phone, cold launch, five tabs, rotation, large text, TalkBack | no phone attached to the build box |
| MWA authorize, then sign, submit and confirm a devnet transfer | needs a wallet app and devnet SOL |
| Process death during sign or submit on a device, then recovery on restart | JVM simulation passes; device NOT_RUN |
| Receipt from a real transfer, verified on a second machine | no real transfer exists |
| Android Keystore: vault, receipt and node keys | Robolectric does not provide AndroidKeyStore |
| Android TLS accepting the agent's Ed25519 certificate | the instrumented test compiles; it has not run |
| `androidTest` (AppSmokeTest, DeviceIntegrationTest) | compiled by the gate; no device. `DefaultStateWalkTest` runs the same walk under Robolectric. |
| Owned Ollama host: reply, cancel and public-host refusal on a phone | no host reachable from the build box |
| On-device inference | UNAVAILABLE: no runtime or weights in the APK |
| ORE, SKR, VPN, cloud sync, bandwidth sharing | UNAVAILABLE on purpose |

Decision: **NO-GO** for "fully functional", ORE earning or a store release. This candidate is suitable for a supervised devnet test on the owner's phone.
