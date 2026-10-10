# Wallet authorization fix: evidence for 3676094ad487

Branch `fix/mwa-authorization`, based on `origin/main` 2442ba1. The tested commit is `3676094ad487`, which is the fix commit. Evidence is committed on top of it and changes no app source.
These are JVM, build and source-inspection results only. **No run in this folder used a wallet, phone or emulator.**

## Observed failure (runtime, before this fix)
This happened on Appetize (Pixel 7, Android 13, API 33). The EdgeORE APK was `d675002bd701`. Mock MWA Wallet was built from solana-mobile/mock-mwa-wallet `d444aff0c72d` with a throwaway devnet key. The runs used Build A (unmodified source plus the test key, sha256 `ff543111…4c81`, per `session-evidence/appetize-wallet/WALLET-BUILD.md`), as inferred from the session context: the next planned step was to retry with the build that skips the login prompt. Confirm this on retry. Both apps ran in one Appetize app-group session.
The steps were: Authenticate in the wallet, then EdgeORE Connect. The wallet sheet showed "EdgeORE wants to connect" with address `2GUk3Jm4…7Rxk`. The operator tapped Connect, and a fingerprint prompt appeared and was satisfied in about 2 s. EdgeORE then showed "Not connected" with no error. This happened twice.
The screenshots show the Connect button going from "Waiting…" (connect coroutine running) back to enabled "Connect wallet" with no address. That fits the connect coroutine finishing with a non-authorized outcome, or throwing. The pre-fix build cannot rule out process recreation, which also resets to the default state, because both draw the same "Not connected".

## Root cause
### Verified from code: why the failure was silent
1. `ui/screens/MineScreen.kt:74` (2442ba1) is `Text(if (wallet.address == null) "Not connected" else wallet.status)`. Every non-authorized state was drawn as "Not connected", including the reason string the ViewModel had stored.
2. `EdgeOreViewModel.connectWallet` collapsed every thrown exception into `WalletState(status = "Wallet unavailable")`. It logged nothing and kept no exception type.
3. `WalletCoordinator.connect` turned `TransactionResult.Failure` into a string from the SDK message. In clientlib-ktx **2.0.3** (`MobileWalletAdapter.associate`), the inner `catch (e: ExecutionException)` also catches errors from `authorize()`. So a wallet **decline** (JSON-RPC `-1`) surfaces as "Failed establishing local association with wallet", and the SDK's own cause mapping for that case is unreachable.
4. clientlib-ktx 2.0.3 runs `launch { throw InterruptedException() }` when the wallet activity returns `RESULT_CANCELED`. Mock MWA Wallet never calls `setResult`, so it always returns `RESULT_CANCELED`. If that result arrives before the session finishes, `connect()` **throws** instead of returning a Failure.

### Not verified: why the wallet did not authorize (leading hypothesis)
The pre-fix build hid the reason, so the runtime cause is still **unconfirmed**. Mock wallet source and earlier session notes point to one likely path:
- `MobileWalletAdapterViewModel.authorizeDapp` → `getKeypairSafe()`. The wallet's private key is wrapped by an Android Keystore AES key that needs user authentication (`setUserAuthenticationParameters(900 s, BIOMETRIC_STRONG|DEVICE_CREDENTIAL)`).
- A fingerprint prompt *during Connect* means `getKeypair()` threw `UserNotAuthenticatedException` even though Authenticate had been done beforehand. In other words, the Keystore did not treat the emulated fingerprint as authentication.
- After the prompt it retries `getKeypair()`. If that fails again, `onFailure` → `completeWithDecline()` → walletlib `RequestDeclinedException` → JSON-RPC `ERROR_AUTHORIZATION_FAILED (-1)`.
- `WALLET-BUILD.md` already says "Appetize emulated fingerprint did not unlock the time-bound key". That is why Build B (Keystore user-auth disabled) was made.
With this fix, that path appears as **`WALLET_DECLINED` · stage `AUTHORIZATION` · rpc `-1`**. The wallet's own logcat shows `getKeypairSafe: Failed to authenticate`. The retry below decides between this and the other codes.

## What changed (`fix.diff`, 3 modified and 4 new files)
- `wallet/WalletAuthorization.kt`: maps every `TransactionResult` type (Success, NoWalletFound, Failure) and every exception the 2.0.3 SDK returns or throws to a code with a stage. The codes are NO_COMPATIBLE_WALLET, ASSOCIATION_FAILED, SESSION_TIMEOUT, SESSION_INTERRUPTED, SESSION_IO, WALLET_DECLINED, AUTH_TOKEN_REJECTED, CHAIN_NOT_SUPPORTED, WALLET_ERROR, INVALID_AUTHORIZATION_RESPONSE, ACCOUNT_VALIDATION_FAILED and INTERNAL_ERROR.
  - It unwraps the SDK's `ExecutionException` cause.
  - JSON-RPC `-1` on a first authorize is WALLET_DECLINED, and on a reauthorize is AUTH_TOKEN_REJECTED. The SDK cannot tell tapping Cancel apart from a wallet-side failure, so neither can EdgeORE.
  - It validates the account the wallet actually returned: accounts[0], 32 bytes, not all zero. There is no hard-coded address.
- `wallet/WalletConnection.kt`: the only owner of the shown connection state. Each attempt publishes one outcome. Results from a stale attempt are discarded. An error stays visible until the next attempt starts. A second Connect while one is running is refused. Cancelling the caller's own coroutine propagates and leaves no "Waiting…".
- `wallet/WalletDisplay.kt` and `MineScreen.kt`: show the failure, the next action and the code/stage/UTC time. Exception types appear **only in debug builds**. A failed balance request reads "Balance unavailable" (never zero) and does not change the connection.
- `WalletCoordinator.kt`: catches thrown SDK exceptions. Only cancellation of the caller's own coroutine propagates. `disconnect` returns whether the wallet confirmed deauthorization, and the UI shows that separately.
- `EdgeOreViewModel.kt`: wired to WalletConnection. Sanitized logcat tag `EdgeORE.Wallet` (e.g. `wallet_connect_failed code=WALLET_DECLINED stage=AUTHORIZATION at=…Z exception=java.util.concurrent.ExecutionException cause=…JsonRpc20RemoteException rpc_code=-1`). No auth token, key, wallet payload or wallet message text is logged.
- Not changed: dependencies, Storage, AI, signing verification, separate submission, durable operations, receipts, and the wallet's authentication requirements.
- **Persistence:** 2442ba1 has no wallet-connection persistence. The account is held in ViewModel memory, and the auth token only inside the `MobileWalletAdapter` instance (checked by `git grep`). So no persistence stage or persistence-failure category exists, and none was invented. The "persistence failure is surfaced" test is **not applicable**.

## Commands and results (separate runs, never combined)
| Run | Command | Exit | Counts |
|---|---|---|---|
| Android gate | `SCREENS=1 bash scripts/qualify.sh` on clean 3676094ad487 | Gradle `EXIT=0`. Script exit **1**, from the screenshot step only | Unit **177 tests: 176 passed, 0 failed, 1 skipped** (`NodeAgentIntegrationTest`, as on main). This is 164 + 13 new `WalletAuthorizationTest`. Lint: no issues. |
| Screenshot suite (branch) | `./gradlew :app:verifyRoborazziDebug -Pscreens` (inside qualify) | 1 | 13 tests: 11 passed, **2 failed: `ScreenshotTest.review`, `FullScreenshotTest.review`** |
| Screenshot suite (main 2442ba1, control) | same, clean worktree of 2442ba1 | 1 | 13 tests: 11 passed, same 2 failed. The `06-review` renders are **byte-identical** between main and branch (sha256 `7803f6c2…` / `0c3e4cd4…`), so this drift predates the fix. The Mine screenshot (default "Not connected" state) passes unchanged. |
| Receipt checker (standalone) | `bash gradlew -p tools/receipt-checker --no-daemon test installDist` | 0 | **7 tests: 7 passed** |
| Resolved dependencies | `./gradlew -q :app:dependencies --configuration <c>` on main, then on the branch | 0 | debug/release/unitTest/androidTest runtime classpaths **identical** (`deps/COMPARISON.txt`) |
| Targeted | `./gradlew :app:testDebugUnitTest --tests com.edgeore.app.WalletAuthorizationTest` | 0 | 13 passed |

Gate logs, JUnit XML and lint are in `evidence/build-3676094ad487/`.

## Candidate APK
- Box path: `/workspace/EdgeORE-3676094ad487-mwa-fix-debug.apk` (copy of `app/build/outputs/apk/debug/app-debug.apk` from the gate)
- sha256 `bb43d7688fc785442c12413ba6b01f1441f57b2439de1b3a5b7100e421c45b08`, 15,097,349 bytes
- `com.edgeore.app` 0.2.8-review (versionCode 10). `BuildConfig.GIT_COMMIT` = `3676094ad487` (About screen). Debug signer `c9b46665…b7ae4b93`.

## Regression tests (`WalletAuthorizationTest`, test doubles only; does not prove the real wallet flow)
These cover:
- A valid authorization reaches the connected state, using the returned account.
- An empty, null or malformed account list produces a visible error.
- A failed connect is never drawn as "Not connected".
- Every SDK result or exception maps to a non-success code (including the wrapped -1 decline, RESULT_CANCELED InterruptedException, timeouts, IO and JSON-RPC errors).
- A thrown SDK exception becomes a visible failure.
- An error persists until the next attempt.
- Cancelling the caller propagates and leaves no Waiting state.
- A second Connect while busy is refused.
- A failed balance shows "Balance unavailable" and authorization stays intact.
- A late result from an older attempt cannot overwrite the current state.
- Disconnect is reported separately.
- Log and UI text carry code, stage, UTC time and type but never wallet-supplied text.

## Operator retry procedure (disposable devnet funds only; never enter a seed phrase or private key anywhere)
1. In one Appetize app-group session (or one device), install **exactly** the candidate APK above, sha256 `bb43d768…5b08`, plus Mock MWA Wallet. Record the wallet build used (A `ff543111…` or B `078be369…`, B being locally patched with Keystore user-auth disabled), the device model and the API level.
2. Confirm and record each of these: a **secure lock screen** (PIN/pattern/password) is set in Settings; Mock MWA Wallet was opened and **Authenticate** was tapped; authentication **completed** (toast "Authentication succeeded!"); EdgeORE Connect is started **within 15 minutes** of that. A fingerprint prompt appearing later during Connect is *not* evidence that this step succeeded.
3. Open EdgeORE → About and confirm the commit `3676094ad487`. Go to Mine → Connect wallet → in the wallet sheet tap Connect → complete any prompt.
4. Screenshot the Mine card. On success it shows the short address and "Authorized on devnet". On failure it shows "Connection failed", the reason, the action and `Code … · stage … · <UTC>` (plus a debug-only "Debug:" line).
5. Save logcat filtered to `EdgeORE.Wallet` and to the wallet's `MobileWalletAdapterViewModel` / `getKeypairSafe` lines. These lines are sanitized: the EdgeORE lines carry no token or key. Do not save wallet debug lines that print decrypted key material (mock wallet `EncryptionUseCase` logs "Decrypted information"). Redact or skip them.
6. Authorization passes **only** if the wallet approved **and** EdgeORE keeps the returned account in this session: still shown after switching tabs and after returning to Mine. A balance failure must read "Balance unavailable" with the address still shown.
7. If the result is `WALLET_DECLINED · rpc -1` right after a fingerprint prompt, repeat with wallet Build B. Label that evidence "official testing wallet (locally patched: Keystore user-auth disabled)".
8. Test **Disconnect** on its own and record whether it says the wallet confirmed deauthorization.
9. Cross-process reconnection (force-stop and reopen) is a separate check. EdgeORE does not persist the connection, so "Not connected" after a restart is the expected result, not a regression.
10. Only after step 6 passes, run a separately reviewed small devnet transfer. Signing and submission need the operator's explicit approval. Keep the real signature and the `getSignatureStatuses` response.

## Gate status after this change
| Gate | Status |
|---|---|
| Failure exposure and classification (code) | Implemented; JVM-tested (13/13) |
| Android build gate (unit/lint/assemble) | PASS (EXIT=0, 176/177, 1 skipped) |
| Screenshot suite | FAIL, 2 tests (`06-review`). Fails identically on main 2442ba1; not caused by this change |
| Receipt checker | PASS 7/7 (separate run) |
| Dependency versions | Unchanged (resolved classpaths identical) |
| Wallet authorization on a device or emulator | **ATTEMPTED, incomplete** (2 runs on d675002bd701). Candidate 3676094ad487 is **NOT_RUN** |
| Wallet-side cause of the decline | **Hypothesis only** (Keystore auth not satisfied by emulated fingerprint) |
| Disconnect on a device | NOT_RUN |
| Cross-process reconnection | NOT_RUN (no persistence by design in 2442ba1) |
| Devnet transfer (sign/submit/confirm) | NOT_RUN |
| Interrupted-submit recovery | NOT_RUN |
| Real-transfer receipt verification | NOT_RUN |
