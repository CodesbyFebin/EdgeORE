# Audit backlog: acceptance mapping (source/JVM level)

Status words: **DONE (JVM)** = implemented and exercised by unit tests on the build box;
**PARTIAL** = implemented, but part of the acceptance criterion needs a device or live service;
**NOT_RUN** = needs the user's phone, wallet, owned host or agent. Nothing here is a device result.

| Priority | Item | Status | Where | Tests that exercise the criterion |
|---|---|---|---|---|
| P0 | Clean build evidence | DONE | `evidence/phase-a/`, `evidence/p0-p1/build-*.md` | exit codes captured directly; XML parsed by `scripts/junit-summary.py` |
| P0 | Durable operation state | DONE (JVM) | `solana/Operations.kt` | `DurableOperationTest`: `reviewedOperationRecordsEveryRequiredField`, `deathBeforeSendKeepsSignedBytesAndNeverSendsOnRestart`, `submitAttemptIsDurableBeforeTheNetworkCall`, `deathAfterRpcAcceptanceBeforeReceiptLeavesUnreceiptedSubmitted`, `damagedStoreFailsClosedInsteadOfLookingEmpty` |
| P0 | Single-flight transaction actions | DONE (JVM) | `solana/TransferCoordinator.kt`, `EdgeOreViewModel` | `repeatedSubmitTapsProduceExactlyOneSend` (25 concurrent taps → 1 send), `repeatedApproveTapsOpenOneSigningSession` |
| P0 | Unknown-outcome recovery | DONE (JVM) | `TransferCoordinator.observe/reconcileAll/recoverAfterRestart` | `timeoutIsUnknownAndIsNeverRetransmitted`, `restartObservesTheKnownSignatureWithoutResending`, `notFoundBeforeExpiryStaysUnknownAfterExpiryNeedsFreshReview`, `unavailableHeightNeverExpires`, `cancellationDuringSendIsUnknown`, `rpcSignatureMismatchIsUnknownNotSuccess` |
| P0 | Bounded file reads | DONE (JVM) | `io/SafeFiles.kt` (`BoundedInput`) used by vault import, AI document, receipt import, model checksum | `oversizeRejectedBeforeUnboundedAllocation` (endless stream, ≤ limit+1 bytes pulled), `exactLimitAcceptedOneMoreRejected`, `textReadsAreByteBoundedStrictUtf8AndCharLimited` |
| P0 | Atomic vault publication | DONE (JVM) | `AtomicFiles`, `storage/LocalVault.kt` | `interruptedWriteIsNeverAnObjectAndIsReclaimed`, `atomicWriteFailureKeepsOldContentAndNoTemp`, `oversizeAllowanceAndDiskFullAreSpecificAndLeaveNothing` |
| P0 | Real device transfer | **NOT_RUN** | — | needs a phone, an MWA wallet and devnet SOL |
| P1 | Durable spend reservations | DONE (JVM) | `OperationStore.reserve/exposure` | `DurableSpendTest` (restart, account/cluster partition, abandoned draft, day rollover, overflow, concurrency, legacy receipts) + `confirmedThenFinalizedCountsOnce`, `chainErrorIsFailedAndReleasesPrincipal`, `walletRefusalAndRejectedBytesReleaseTheReservation` |
| P1 | Wallet-aware receipt verifier | PARTIAL | `receipts/Receipts.kt` (`ReceiptVerifier`) | `walletSignatureVerifiesIndependentlyOverTheReviewedMessage`, `appSignedButWalletForgedIsRejected`, `walletDigestAndSignatureMismatchesAreRejected`. Real-export validation needs a device transfer (NOT_RUN). |
| P1 | Receipt key epochs | DONE (JVM) | `KeyRegistry`, `SoftwareReceiptSigner.persisted`, `signerKeyId` per record | `oldRecordsVerifyAfterKeyRotationAndRestart`, `persistedSoftwareKeyKeepsItsIdentityAcrossRestart`, `missingKeyEpochIsReported`. Keystore continuity across app upgrade on a phone: NOT_RUN. |
| P1 | Corruption-aware receipt reads | DONE (JVM) | `ReceiptLog.read`, Receipts screen damage card | `truncatedTailIsReportedValidHistoryKeptAndNextAppendLinksToLastValid`, `modifiedLineInTheMiddleIsReportedNotDropped` |
| P1 | Vault version/AAD | DONE (JVM) | `LocalVault` EOV2 envelope | `tamperedIvCiphertextAndMetadataFailAuthentication`, `truncatedTrailingAndForeignObjectsAreCorrupt`, `wrongOrMissingKeyIsReportedNotEmpty`, `pathsAreValidatedAtTheBoundary`, `legacyObjectsStayReadable`. Android Keystore behaviour: NOT_RUN. |
| P1 | AI transport cancellation | DONE (JVM) for local cancel | `OwnedHostModelClient.cancelActive` | `localCancelClosesTheSocketPromptly` (real loopback socket), `cancelledClientRefusesNewRequests`. Host-side stop is not available in Ollama's non-streaming API and is labelled as not acknowledged. Live host: NOT_RUN. |
| P1 | Bound endpoint resolution | DONE (JVM) | `EndpointPolicy.checkAll/boundUrl` | `everyResolvedAddressMustPass`, `connectionTargetsTheValidatedAddressNotASecondLookup`, `fragmentsAndUnresolvableHostsRefused` |
| P1 | Node redirect refusal | DONE (JVM) | `NodeAgentClient.post` | `nodeRedirectIsRefusedNotFollowed` over real pinned TLS (keytool cert), `pinnedTlsStillWorksForANormalAnswer`, `wrongPinIsUnreachableNotTrusted` |
| P1 | Disk sample continuity | DONE (JVM) | `DiskRateTracker`, `CpuRateTracker` | `missingDiskSampleDoesNotInflateTheRate`, `counterResetAndClockStallAreUnavailableNotZero`. Which /proc files a given phone exposes: NOT_RUN. |
