# Re-audit of `integration/0.2.9-candidate` against the 9 Oct 2026 source audit (5.4/10)

- **Candidate:** tested commit `37f1e05a61fa`, `0.2.9-review` versionCode 11, debug APK sha256 `b8af2576…5b7000` (see `README.md` here).
- **Method:** same seven weighted dimensions as the original audit, so the two scores can be compared. This is a bounded source and evidence review. It is not the hackathon rubric, a security audit or a penetration test.
- **Status words:** **fixed** means implemented and covered by a named test or check. **partially fixed** means implemented with a named gap. **open** means not implemented. **NOT_RUN** means it needs a device, wallet, host or backend run that has not happened. JVM results are never counted as device results.
- **Runtime evidence:** this exact APK has **not** run on any device or emulator. The only runtime evidence is from the hosted emulator (Appetize, Pixel 7, Android 13, API 33) with wallet Build A (the official mock-mwa-wallet, unpatched). It used the earlier build `3676094ad487` (`fix/mwa-authorization`), whose wallet code is in this candidate unchanged apart from the Disconnect wording.
  - Authorization: PASS in-session, including a tab switch (`/workspace/session-evidence/appetize-wallet/phase3/README.md`).
  - Disconnect: PASS in-session (`…/phase4/README.md`).
  - These files are on the build box and are not committed.

## Score

| Dimension | Weight | Audit | Now | Why it moved (and why it is not higher) |
|---|---|---|---|---|
| Source architecture | 20% | 7 | **8** | Durable operations, single-flight coordinator, pure display/policy objects (`WalletDisplay`, `ContributionPolicy`, `BatteryDrain`), the LiteRT-LM runtime isolated in `:ondevice-llm`, and the on-chain prototype isolated in `onchain/`. Held back because `EdgeOreViewModel.kt` is 1,015 lines and owns seven features. |
| Wallet and transfer | 20% | 6 | **6.5** | Durable ops, recovery and expiry policy pass on the JVM (main `cc31714`). Authorization failures are now surfaced, and authorization plus Disconnect passed on a hosted emulator. Held back because no transfer has been signed, submitted or confirmed through EdgeORE, interrupted-submit recovery has never run on a device, and cross-process reconnect is NOT_RUN. |
| Security and data durability | 15% | 5 | **7** | EOV2 vault with AAD, atomic publication, bounded SAF import, corruption-aware receipt log, key epochs, a release-manifest gate (new), unsigned export fields labelled (new). Held back because Android Keystore is unproven on a device, there is no signed release, unsigned export fields are only labelled (not signed), and storage receipts are rejected by older checkers. |
| Test evidence | 15% | 5 | **7** | Fresh, separately recorded exit-0 runs: 234 unit tests (1 skipped), 19 screens, 10 checker tests, 9 manifest tests, 17 Anchor/LiteSVM tests. Resolved dependency diff recorded. Held back because almost every run is JVM; the device evidence is 2 in-session checks on a hosted emulator with a different build. |
| AI, node and resource functionality | 10% | 4 | **5** | An on-device LiteRT-LM path with a pinned allowlist and consented download now exists, plus an opt-in WorkManager scheduler that runs no workload, and the live loopback node test. Held back because no on-device generation, no owned-host reply from a phone, and no phone-to-node pairing has run. |
| Release qualification | 15% | 3 | **4** | The APK has a commit stamp and a versioned identity, the gate is reproducible, and the release manifest is now policy-checked. Held back because there is no release keystore, no signed release, no LICENSE file, this APK has not been installed anywhere, and there is no demo video from this build. |
| Honest product boundaries | 5% | 8 | **8.5** | Every new feature ships with NOT_RUN/Not configured labels. The ORE doc is DESIGN ONLY - NOT BUILT. The Anchor program is labelled unaudited and not deployed. The 32-bit-device and ABI trade-offs are documented. The AI card title still reads "On-device execution" before any model is downloaded (H5, partly addressed by its new subtitle). |
| **Weighted** | 100% | **5.35 → 5.4** | **6.525 → 6.5/10** | 0.2·8 + 0.2·6.5 + 0.15·7 + 0.15·7 + 0.1·5 + 0.15·4 + 0.05·8.5 |

The audit's conditional path to "about 7" was: a clean build, durable operations, bounded vault handling **and a real physical-device transaction**. The first three are now met on the JVM. The fourth is not, which is the main reason the score stops at 6.5.

## Item-by-item status (audit sections and backlog table)

### P0
| Item | Status | Evidence |
|---|---|---|
| Clean build evidence | **fixed** | `gate-37f1e05a61fa/summary.md` (EXIT=0, parsed JUnit, lint clean, assemble, androidTest APK) |
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
| Current screenshot suite | **fixed (JVM render)** | 19/19. The stale review baselines are re-recorded with a traced reason (`docs/screenshots/README.md`). These are JVM renders, not device screenshots. |
| Settings enforcement labels | **fixed (JVM)** | `ControlEffectsTest`, including the new `Control.CONTRIBUTION_SCHEDULER` |
| Reproducible agent setup | **fixed (live, loopback)** on main | `scripts/node-agent.pin`. Not re-run for this candidate (`NODE_IT=1` was not set), so the skipped `NodeAgentIntegrationTest` is not counted as passed. |
| Release/document truth pass | **partially fixed** | Ledger, README and docs updated for this branch. The signed-release half is open. |
| §5 Wallet authorization result handling (new defect found after the audit) | **fixed**; runtime PASS (hosted emulator, build `3676094ad487`) | `WalletConnection.kt`, `WalletDisplay.kt`; `WalletAuthorizationTest` (14). Phase 3 and 4 READMEs. |
| Disconnect reports whether the wallet confirmed | **fixed (wording)** in `8ca556d` | `WalletConnection.kt:61`; `confirmedDisconnectSaysTheWalletConfirmed`. Phase 4 showed a bare "Disconnected", which came from the confirmed (`TransactionResult.Success`) branch, so the behaviour matched intent and only the wording was ambiguous. |
| Cross-process reconnect | **NOT_RUN** | No connection persistence by design. "Not connected" after a restart is expected. |
| §7 Unsigned bundle/descriptive fields (H4) | **partially fixed** in `836f486` | `ReceiptChecker.kt:8`; `labelsUnsignedDescriptiveFieldsEvenWhenTheyWereEdited` shows that an edited `note`/top-level `payment` still passes and is now labelled. Open: a signed envelope and the in-app label. |
| STORAGE receipt kind accepted by the checker | **fixed** in `34faa2e` | `acceptsStorageReceiptAndRejectsItsTamperedCopy`; `rejectsSignedStorageReceiptThatClaimsPayment` (storage records cannot carry a payment). Older checker builds still reject STORAGE. |
| §8 Software-key fallback and key continuity | **fixed (JVM)**; device NOT_RUN | `KeyRegistry` epochs; `ReceiptV2Test` |
| §9 Vault name collisions, quota, disk-full, permission loss | **fixed (JVM)** | `StorageVaultTest`, `BackupCoordinatorTest`. Remote backup is *Not configured*: no backend exists and nothing is uploaded. |
| §9 Device-bound recovery copy | **fixed** | `docs/storage-vault.md`, export and delete warnings in `StorageScreen.kt` |
| §10 On-device inference | **partially fixed / NOT_RUN** | `OnDeviceAiController.kt:68`, LiteRT-LM 0.8.0, allowlist JSON; `OnDeviceAiTest`. No download or generation has run on a device. No weights are in the APK. |
| §10 Trusted model manifest | **partially fixed** | Pinned allowlist with digest, size and licence (`assets/ai/ondevice-model-allowlist.json`). The publisher's provenance beyond the HF revision pin is not established. |
| §11 Battery current as drain | **fixed** in `38e8bb6` | `DeviceResources.kt:76` `BatteryDrain`; `batteryDrainOnlyWhileKnownDischarging`, `batteryDrainUnavailableInputsStayNull` |
| §11 Freshness/stale badges on every observation | **open** | Telemetry still lacks a uniform observed-at/stale marker per reading on screen |
| §12 Node pairing from a phone, expiry, replay, scope refusal | **NOT_RUN** on a phone | JVM and loopback only |
| §14 Accessibility (large text, TalkBack, rotation) | **NOT_RUN** | No device session |
| §19 H1 debuggable / H3 release signing | **open** (owner action) | No release keystore on the box. Debug-signed only. |
| §19 H2 exported test activities | **fixed for release** in `eeafad0` and `37f1e05` | `app/src/release/AndroidManifest.xml`; `scripts/check-manifest.py` with 9 tests, run in `qualify.sh`. The debug manifest keeps them, which is expected. |
| §19 Root LICENSE | **open** (owner decision) | `NOTICE` covers the Google AI Edge Gallery and LiteRT-LM attribution. There is no project licence. |
| §21 Contribution scheduler | **built (JVM)**; device NOT_RUN | `contribution/ContributionWork.kt:81`; `ContributionPolicyTest` (11), `ContributionWorkTest` (9). There are no registered workloads, so nothing runs. |
| §21 ORE/SKR adapter | **open, by design** | `docs/design/ore-integration-future.md` (DESIGN ONLY - NOT BUILT). No ORE integration exists. |
| On-chain receipt settlement | **prototype (local simulator)** | `onchain/`, 17/17 LiteSVM tests. Unaudited, not deployed, not wired to the app. Known gaps: no claim timeout, fixed verifier, rent not refunded (later branch commits not merged). |

## What remains for 10/10

The audit's own bar is that the agreed journey must work on hardware, survive lifecycle failures, and have clear release provenance. In dependency order:

1. **Device transfer journey.** On a phone, or at minimum on the hosted emulator with this APK (`37f1e05a61fa`): authorize, review, sign, submit separately, and observe confirmation on devnet. Keep the real signature and the `getSignatureStatuses` response. *Needs the operator's approval to sign.* (Wallet and transfer, Test evidence.)
2. **Interrupted-submit recovery on a device.** Force-stop during or after submit, then reopen. The operation must come back as `OUTCOME_UNKNOWN` or confirmed with no resend.
3. **Real-transfer receipt** exported from the device and checked on a second machine with `scripts/verify-receipt.sh`, including a tampered copy.
4. **Android Keystore on a device.** Vault import, export and tamper; receipt key continuity across restart; node key wrapping.
5. **On-device LLM.** One consented download, digest check, one airplane-mode generation and one cancel on a named arm64 phone. Record the runtime and model identity.
6. **Owned-host AI from a phone.** One real reply, one cancel, and one public-endpoint refusal.
7. **Backup backend.** Remote backup stays *Not configured* until an authenticated EdgeORE backend and an owned IPFS node exist. Upload, pin and restore-verify then need real runs.
8. **Release provenance.** An owner-held release key, a signed `0.2.9-review` release APK with its certificate SHA-256, the release lint run, a LICENSE decision, and install, rotation, large-text and TalkBack checks on the signed build.
9. **Device-free leftovers.**
   - Stale and freshness markers on telemetry.
   - A signed export envelope (H4).
   - The "On-device execution" title before any model is installed (H5).
   - Splitting `EdgeOreViewModel`.
   - ABI splits or an AAB if APK size matters.
   - On-chain follow-ups (claim deadline, verifier rotation, rent reclaim) once that branch is final.
10. **Cross-process wallet reconnect.** This is a product decision. Today it is "Not connected" after a restart by design.

Until items 1–8 have recorded runtime results, the categories that depend on them cannot reach full marks. A plausible next step is about 7.5 after items 1–3 pass on this build.

No mining income, rewards, ORE/SKR integration or production readiness is claimed here. The receipt checker claim stays: it verifies the integrity of **signed receipt contents**, not every field of the export. The unpublished single-byte mutation experiment is not cited.
