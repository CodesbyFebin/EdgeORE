# Re-audit of `integration/0.2.9-candidate` against the 9 Oct 2026 source audit (5.4/10)

- **Candidate (round 3):** tested commit `28acd193fc7c`, `0.2.9-review` versionCode 11, debug APK sha256 `6d5eaf48…7fdf1761`, 57,286,793 bytes, About stamp `28acd193fc7c` (see `README.md` here). Round 1 scored `37f1e05a61fa` at 6.5 and round 2 scored `077c76503d47` at 6.7; this revision re-scores after the `origin/main` merge, two device-free items and the device runbook.
- **Method:** same seven weighted dimensions as the original audit, so the two scores can be compared. This is a bounded source and evidence review. It is not the hackathon rubric, a security audit or a penetration test.
- **Status words:** **fixed** means implemented and covered by a named test or check. **partially fixed** means implemented with a named gap. **open** means not implemented. **NOT_RUN** means it needs a device, wallet, host or backend run that has not happened. JVM results are never counted as device results.
- **Runtime evidence:** this exact APK has **not** run on any device or emulator. `docs/DEVICE-RUNBOOK.md` and `/workspace/device-kit/` now give the operator an exact procedure and kit for that run; a runbook is not evidence, so nothing below moves because of it. The only runtime evidence is from the hosted emulator (Appetize, Pixel 7, Android 13, API 33) with wallet Build A (the official mock-mwa-wallet, unpatched). It used the earlier build `3676094ad487` (`fix/mwa-authorization`), whose wallet code is in this candidate unchanged apart from the Disconnect wording.
  - Authorization: PASS in-session, including a tab switch (`/workspace/session-evidence/appetize-wallet/phase3/README.md`).
  - Disconnect: PASS in-session (`…/phase4/README.md`).
  - These files are on the build box and are not committed.

## Score

| Dimension | Weight | Audit | Now | Why it moved (and why it is not higher) |
|---|---|---|---|---|
| Source architecture | 20% | 7 | **8** | Durable operations, single-flight coordinator, pure display/policy objects (`WalletDisplay`, `ContributionPolicy`, `BatteryDrain`, `ReadingFreshness`, `ExecutionLabel`), the LiteRT-LM runtime isolated in `:ondevice-llm`, and the on-chain prototype isolated in `onchain/`. The UI state types moved to `UiState.kt` (`ea2ec1b`). Held back because `EdgeOreViewModel.kt` is still 920 lines and owns seven features; the feature-controller split was not attempted (see below). |
| Wallet and transfer | 20% | 6 | **6.5** | Durable ops, recovery and expiry policy pass on the JVM (main `cc31714`). Authorization failures are now surfaced, and authorization plus Disconnect passed on a hosted emulator. Held back because no transfer has been signed, submitted or confirmed through EdgeORE, interrupted-submit recovery has never run on a device, and cross-process reconnect is NOT_RUN. |
| Security and data durability | 15% | 5 | **7.75** | EOV2 vault with AAD, atomic publication, bounded SAF import, corruption-aware receipt log, key epochs, a release-manifest gate, a signed export envelope over the descriptive fields (`38b55bb`), and now an `envelopeRequired` flag so that removing the envelope from a new export is rejected (`cf88c44`). Held back because Android Keystore is unproven on a device, there is no signed release, the flag itself is unsigned (removing envelope **and** flag still yields the legacy label, never a signed result), and storage receipts are rejected by older checkers. |
| Test evidence | 15% | 5 | **7.75** | Fresh, separately recorded exit-0 runs at `28acd19`: 245 unit tests (1 skipped), 20 screens, 14 checker tests (one on the real pre-envelope export from `d675002bd701`), 9 manifest tests; 29 Anchor tests in LiteSVM and 28 on a real local `solana-test-validator` from round 2 (`onchain/` unchanged since). Screenshots no longer depend on the build machine, and every shot is back at the 0.1% threshold (`a973cf8`). Held back because almost every run is JVM or local; the device evidence is 2 in-session checks on a hosted emulator with a different build. |
| AI, node and resource functionality | 10% | 4 | **5** | An on-device LiteRT-LM path with a pinned allowlist and consented download now exists, plus an opt-in WorkManager scheduler that runs no workload, and the live loopback node test. Held back because no on-device generation, no owned-host reply from a phone, and no phone-to-node pairing has run. |
| Release qualification | 15% | 3 | **4** | The APK has a commit stamp and a versioned identity, the gate is reproducible, and the release manifest is now policy-checked. Held back because there is no release keystore, no signed release, no LICENSE file, this APK has not been installed anywhere, and there is no demo video from this build. |
| Honest product boundaries | 5% | 8 | **9** | Every new feature ships with NOT_RUN/Not configured labels. The ORE doc is DESIGN ONLY - NOT BUILT. The Anchor program is labelled unaudited and not deployed. The AI title now names what can run (H5 fixed). Stale device readings are marked stale. |
| **Weighted** | 100% | **5.35 → 5.4** | **6.775 → 6.8/10** (round 1: 6.5, round 2: 6.7) | 0.2·8 + 0.2·6.5 + 0.15·7.75 + 0.15·7.75 + 0.1·5 + 0.15·4 + 0.05·9 |

The audit's conditional path to "about 7" was: a clean build, durable operations, bounded vault handling **and a real physical-device transaction**. The first three are now met on the JVM. The fourth is not, which is the main reason the score stops at 6.8. Round 3 adds only device-free hardening; the runbook prepares the device run but does not count as one.

## Item-by-item status (audit sections and backlog table)

### P0
| Item | Status | Evidence |
|---|---|---|
| Clean build evidence | **fixed** | `gate-28acd193fc7c/summary.md` (EXIT=0, parsed JUnit, lint clean, assemble, androidTest APK) |
| Durable operation state | **fixed (JVM)**; device NOT_RUN | `solana/Operations.kt:120` `OperationStore`; `DurableOperationTest` |
| Single-flight transaction actions | **fixed (JVM)** | `TransferCoordinator.kt:133` `submit`; `repeatedSubmitTapsProduceExactlyOneSend`, `repeatedApproveTapsOpenOneSigningSession` |
| Unknown-outcome recovery | **fixed (JVM)**; device NOT_RUN | `TransferCoordinator.kt:61` `recoverAfterRestart`, `:181` `observe`; `DurableOperationTest`, `StoreFailureAndSigningGateTest` |
| Bounded file reads | **fixed (JVM)** | `io/SafeFiles.kt` `BoundedInput`; `BoundedIoAndVaultTest`, `StorageVaultTest` (unknown length and oversized SAF import) |
| Atomic vault publication | **fixed (JVM)** | `LocalVault.kt:102` `AtomicFiles.write`; `StorageVaultTest` (cancellation before publication, UI only after publication) |
| Real device transfer | **NOT_RUN** | Authorization PASS on the hosted emulator (phase 3). Sign, submit and confirm have not run. |

### P1
| Item | Status | Evidence |
|---|---|---|
| Durable spend reservations | **fixed (JVM)** | `OperationStore.reserve/exposure`; `DurableSpendTest` |
| Wallet-aware receipt verifier | **partially fixed** | `ReceiptVerifier` + `ReceiptV2Test` on generated fixtures. A real-transfer export is NOT_RUN. |
| Receipt key epochs | **fixed (JVM)**; Keystore continuity NOT_RUN | `Receipts.kt:103` `KeyRegistry`; `ReceiptV2Test` |
| Corruption-aware receipt reads | **fixed (JVM)** | `Receipts.kt:205` `read()`; `ReceiptV2Test` |
| Vault version/AAD | **fixed (JVM)**; Keystore NOT_RUN | `LocalVault.kt:44` EOV2 header; `BoundedIoAndVaultTest`, `StorageVaultTest` (tampered, truncated, wrong or unavailable key) |
| AI transport cancellation | **partially fixed** | The local socket closes (`TransportHardeningTest`). A host-side stop is UNAVAILABLE in the Ollama API. |
| Bound endpoint resolution | **fixed (JVM)** | `EndpointPolicy.checkAll/boundUrl`; `TransportHardeningTest` |
| Node redirect refusal | **fixed (JVM, pinned TLS on loopback)** | `TransportHardeningTest.nodeRedirectIsRefusedNotFollowed` (no hang with the scheduler's `@ConscryptMode(OFF)`) |
| Disk sample continuity | **fixed (JVM)** | `DiskRateTracker`; `TransportHardeningTest`, `DeviceMetersTest` |

### P2 and other sections
| Item | Status | Evidence |
|---|---|---|
| ViewModel size | **partially fixed** (round 2, `ea2ec1b`) | UI state types moved verbatim to `UiState.kt` (1,017 → 920 lines); covered by the full suites. Extracting the review, node and AI controllers was **skipped as not low-risk**: they share `viewModelScope`, the receipt log and the operation store, and the wallet/transfer path has no device run to catch a lifecycle regression. |
| Current screenshot suite | **fixed (JVM render)** | 20/20 at 0.1%. Renders use `FixedDeviceReadings` (every device reading "not observed") through the test-only seam `DeviceResourcesReader.testSource`, so the build machine's `/proc` counters no longer reach a baseline; `storageRenderUsesNoHostReadings` asserts it (round 3, `a973cf8`). Re-records are traced in `docs/screenshots/README.md`. These are JVM renders, not device screenshots. |
| Settings enforcement labels | **fixed (JVM)** | `ControlEffectsTest`, including the new `Control.CONTRIBUTION_SCHEDULER` |
| Reproducible agent setup | **fixed (live, loopback)** on main | `scripts/node-agent.pin`. Not re-run for this candidate (`NODE_IT=1` was not set), so the skipped `NodeAgentIntegrationTest` is not counted as passed. |
| Release/document truth pass | **partially fixed** | Ledger, README and docs updated for this branch. The signed-release half is open. |
| §5 Wallet authorization result handling (new defect found after the audit) | **fixed**; runtime PASS (hosted emulator, build `3676094ad487`) | `WalletConnection.kt`, `WalletDisplay.kt`; `WalletAuthorizationTest` (14). Phase 3 and 4 READMEs. |
| Disconnect reports whether the wallet confirmed | **fixed (wording)** in `8ca556d` | `WalletConnection.kt:61`; `confirmedDisconnectSaysTheWalletConfirmed`. Phase 4 showed a bare "Disconnected", which came from the confirmed (`TransactionResult.Success`) branch, so the behaviour matched intent and only the wording was ambiguous. |
| Cross-process reconnect | **NOT_RUN** | No connection persistence by design. "Not connected" after a restart is expected. |
| §7 Unsigned bundle/descriptive fields (H4) | **fixed** (round 2, `38b55bb`); missing envelope on a new export **rejected** (round 3, `cf88c44`); removing envelope and flag together is a labelled legacy downgrade | Optional signed `envelope` in `ReceiptLog.export`; schema v2, receipt lines and checkpoint unchanged, so older checkers ignore it. `ReceiptV2Test.editingAnyDescriptiveFieldBreaksTheEnvelope`, `envelopeSignedByAnotherKeyIsRejected`, `strippedEnvelopeIsALabelledDowngradeNotASignedResult`; `ReceiptCheckerTest.signedEnvelopeDetectsEditedDescriptiveFields`. Round 3: every new export writes `envelopeRequired` (`true` with an envelope, `false` with the device-key privacy control on). `envelopeRequired: true` without an envelope, a non-boolean flag, or a non-object envelope is rejected (`ReceiptV2Test.strippingTheEnvelopeFromANewExportIsRejected`, `malformedEnvelopeOrFlagIsRejected`; `ReceiptCheckerTest.newExportWithEnvelopeRemovedIsRejected`, `nonBooleanEnvelopeRequiredIsRejected`). Exports with neither field are legacy: they verify and are labelled *legacy export without envelope* (`legacyExportWithoutEnvelopeOrFlagStillVerifiesAndIsLabelled`; the committed real export from `d675002bd701` in `realPreEnvelopeExportFromBuildD675002StillVerifies`). Round-2 exports (envelope, no flag) still verify as signed. The flag is not inside a signature: signing it would need the checkpoint format older checkers rely on to change. |
| STORAGE receipt kind accepted by the checker | **fixed** in `34faa2e` | `acceptsStorageReceiptAndRejectsItsTamperedCopy`; `rejectsSignedStorageReceiptThatClaimsPayment` (storage records cannot carry a payment). Older checker builds still reject STORAGE. |
| §8 Software-key fallback and key continuity | **fixed (JVM)**; device NOT_RUN | `KeyRegistry` epochs; `ReceiptV2Test` |
| §9 Vault name collisions, quota, disk-full, permission loss | **fixed (JVM)** | `StorageVaultTest`, `BackupCoordinatorTest`. Remote backup is *Not configured*: no backend exists and nothing is uploaded. |
| §9 Device-bound recovery copy | **fixed** | `docs/storage-vault.md`, export and delete warnings in `StorageScreen.kt` |
| §10 On-device inference | **partially fixed / NOT_RUN** | `OnDeviceAiController.kt`, LiteRT-LM 0.8.0, allowlist JSON; `OnDeviceAiTest`. No download or generation has run on a device. No weights are in the APK. The card title (H5) now reads "Owned-host AI · on-device not set up" until a model is on the phone (`ExecutionLabel`, `OnDeviceAiTest.executionTitleNamesWhatCanActuallyRun`, round 2 `5c891b6`). |
| §10 Trusted model manifest | **partially fixed** | Pinned allowlist with digest, size and licence (`assets/ai/ondevice-model-allowlist.json`). The publisher's provenance beyond the HF revision pin is not established. |
| §11 Battery current as drain | **fixed** in `38e8bb6` | `DeviceResources.kt:76` `BatteryDrain`; `batteryDrainOnlyWhileKnownDischarging`, `batteryDrainUnavailableInputsStayNull` |
| §11 Freshness/stale badges on device readings | **fixed** for the Storage page device readings (round 2, `3553c82`) | `DeviceResources.observedAtElapsedMs` (monotonic), `StorageState.deviceReadFailed`, `ReadingFreshness`; `DeviceMetersTest.freshnessDistinguishesNotReadFreshStaleAndFailed`, `freshnessLabelsNeverPresentOldNumbersAsCurrent`. The label is computed when the card is drawn; there is no ticking timer (a timer loop would stall the Compose test clock), so an unchanged screen updates its label on the next refresh. The Mine edge-gate snapshot is re-read every 15 s and has no failure path to mark. |
| §12 Node pairing from a phone, expiry, replay, scope refusal | **NOT_RUN** on a phone | JVM and loopback only |
| §14 Accessibility (large text, TalkBack, rotation) | **NOT_RUN** | No device session |
| §19 H1 debuggable / H3 release signing | **open** (owner action) | No release keystore on the box. Debug-signed only. |
| §19 H2 exported test activities | **fixed for release** in `eeafad0` and `37f1e05` | `app/src/release/AndroidManifest.xml`; `scripts/check-manifest.py` with 9 tests, run in `qualify.sh`. The debug manifest keeps them, which is expected. |
| §19 Root LICENSE | **open** (owner decision) | `NOTICE` covers the Google AI Edge Gallery and LiteRT-LM attribution. There is no project licence. |
| §21 Contribution scheduler | **built (JVM)**; device NOT_RUN | `contribution/ContributionWork.kt:81`; `ContributionPolicyTest` (11), `ContributionWorkTest` (9). There are no registered workloads, so nothing runs. |
| §21 ORE/SKR adapter | **open, by design** | `docs/design/ore-integration-future.md` (DESIGN ONLY - NOT BUILT). No ORE integration exists. |
| On-chain receipt settlement | **prototype (local only)** at the final branch head `823e649` | `onchain/`, SBPF v3. 29/29 in LiteSVM and 28/28 on a local `solana-test-validator` (round 2). Includes the per-job claim window, verifier rotation and rent reclaim. Unaudited, not deployed (no devnet run), not wired to the app. Devnet SBPF-v3 feature activation was not checked. |

## What remains for 10/10

The audit's own bar is that the agreed journey must work on hardware, survive lifecycle failures, and have clear release provenance. In dependency order:

1. **Device transfer journey.** On a phone or local emulator with this APK (`28acd193fc7c`), following `docs/DEVICE-RUNBOOK.md` Gates 4–6: authorize, review, sign, submit separately, and observe confirmation on devnet. Keep the real signature and the `getSignatureStatuses` response. *Needs the operator's approval to sign.* (Wallet and transfer, Test evidence.)
2. **Interrupted-submit recovery on a device.** Force-stop during or after submit, then reopen. The operation must come back as `OUTCOME_UNKNOWN` or confirmed with no resend.
3. **Real-transfer receipt** exported from the device and checked on a second machine with `scripts/verify-receipt.sh`, including a tampered copy.
4. **Android Keystore on a device.** Vault import, export and tamper; receipt key continuity across restart; node key wrapping.
5. **On-device LLM.** One consented download, digest check, one airplane-mode generation and one cancel on a named arm64 phone. Record the runtime and model identity.
6. **Owned-host AI from a phone.** One real reply, one cancel, and one public-endpoint refusal.
7. **Backup backend.** Remote backup stays *Not configured* until an authenticated EdgeORE backend and an owned IPFS node exist. Upload, pin and restore-verify then need real runs.
8. **Release provenance.** An owner-held release key, a signed `0.2.9-review` release APK with its certificate SHA-256, the release lint run, a LICENSE decision, and install, rotation, large-text and TalkBack checks on the signed build.
9. **Device-free leftovers.**
   - Split the review, node and AI controllers out of `EdgeOreViewModel` (deferred until a device run can catch lifecycle regressions).
   - ~~Screenshot independence from the build machine~~ done in round 3 (`a973cf8`).
   - ~~Reject a missing envelope on new exports~~ done in round 3 (`cf88c44`) as a flag rule. Binding the flag inside a signature remains, and needs a new checkpoint version that older checkers would reject.
   - ABI splits or an AAB if APK size matters.
   - Check SBPF v3 activation on devnet before any devnet deployment decision.
10. **Cross-process wallet reconnect.** This is a product decision. Today it is "Not connected" after a restart by design.

Until items 1–8 have recorded runtime results, the categories that depend on them cannot reach full marks. A plausible next step is about 7.5 after items 1–3 pass on this build.

No mining income, rewards, ORE/SKR integration or production readiness is claimed here. The receipt checker claim stays: it verifies the integrity of **signed receipt contents**, not every field of the export. The unpublished single-byte mutation experiment is not cited.
