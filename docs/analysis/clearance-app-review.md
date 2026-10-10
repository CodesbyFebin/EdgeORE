# Review of the earlier "Clearance" Android app, and what EdgeORE took from it

**Subject:** the owner's (CodesbyFebin's) own earlier, half-finished Android prototype, delivered as a zip
(`prevapp`, 78 files, about 11,100 lines of source, docs and config). Namespace `com.example`, application id `com.aistudio.edgeore.sdwk`,
README title "Clearance — Verifiable Payments & DePIN Evidence on Solana Mobile Seeker". It is credited here as
the owner's prior work. Nothing in it is third-party code that needs a separate licence notice.

**Reviewed against:** EdgeORE `main` at `6f7b600` (README image change on top of `2a169ff`).
**Branch with the changes:** `feature/ux-from-clearance`.

Every source file in the zip was read: 37 Kotlin files under `app/`, the duplicate `android/app/.../SecurityUtils.kt`,
`services/cooldownSync.ts`, the Gradle files, manifest and resources, `scripts/doctor.sh`, `.env.example`, `metadata.json`,
`README.md`, `SPRINT_PLAN.md`, `PITCH_DECK_OUTLINE.md` and `docs/index.html`.

## 1. Headline findings

1. **No real secret is in the zip.** `.env.example` line 7 is `GEMINI_API_KEY=MY_GEMINI_API_KEY`, which is a placeholder.
   `app/build.gradle.kts` lines 35 and 37 hold `storePassword = "android"` / `keyPassword = "android"` for a
   `debug.keystore` that is not in the zip. That is the standard Android debug-key password, not a secret. No keystore,
   `google-services.json`, keypair or token file is present. Nothing from these files was copied.
2. **The wallet is simulated.** There is no Mobile Wallet Adapter dependency in `libs.versions.toml` or `app/build.gradle.kts`.
   `services/MwaWalletService.kt` starts "connected" to a hard-coded address (line 19). `signTransaction` returns random
   bytes formatted to look like a signature (lines 50–52), and `signDevnetMemo` returns `"5memo"+hash` (line 58). So every
   "signed" receipt in the app is fabricated.
3. **Success is the fallback.** `services/SolanaRpcService.kt` returns 1.25 SOL when the balance call fails (lines 43–52),
   a fixed slot `318492041` (lines 69–74) and `CONFIRMED` when the status call fails (line 121 and the catch block).
   `ClearanceSpec.observeSettlement` always returns `CONFIRMED` (line 390). `ProofScreen.kt` line 693 hard-codes
   "32 confirmations · finalized".
4. **Keystore verification accepts forgeries.** `security/KeystoreService.kt` falls back to a `"sig_secp256r1_"+sha256(...)`
   string when the Keystore key is missing (lines 78, 82). `verifySignature` then returns `true` for any string with that
   prefix (lines 88–89). It labels itself "TEE Hardware Protected" unconditionally (line 103). `SecurityUtils.kt` sets
   `isTeeBacked = true` without checking `KeyInfo` (line 123).
5. **The cloud audit sends wallet data off the phone.** `ai/GeminiAuditService.kt` posts the recipient, mint, amount, network,
   evidence digest and part of the attestation to `generativelanguage.googleapis.com` (gemini-3.1-pro-preview). The API key
   travels in the URL query (line 52), and the build would compile the key into `BuildConfig` through the secrets plugin,
   where anyone can extract it from the APK. The score is a keyword match on the model's prose
   ("CRITICAL" → 85, "WARNING" → 45, otherwise 12). Mints that start with "SKR" are treated as verified (line 157). The
   recommendation can read "Safe to authorize via Seeker Seed Vault" (line 138).
6. **SKR staking, claims and DePIN rewards are all invented state.** `SkrStakingService.kt` has a hard-coded 26.2% APY
   (line 65) and in-memory "claims" that add SKR to a fake balance. DePIN tasks show "Reward: … SKR". Evidence capture
   draws a synthetic JPEG and fake "video" bytes (`EvidenceCaptureDialog.kt` lines 53–78). The repository fabricates an
   "SKR seal" signature (`ClearanceRepository.kt` line 181).
7. **"Review binding" hashes a private text format, not the signed bytes.** `ReviewBinding.serializeMessagePayload` hashes a
   `SOLANA_CLEARANCE_MSG_V1` text blob built from the `Action`. Then `authorizeAndSettle` rebuilds the same blob from the same
   `Action` and compares. That always matches unless the debug "tamper simulation" toggle injects a change
   (`ClearanceRepository.kt` lines 138–149). Nothing the wallet returns is ever compared, so it does not bind review to signature.
8. **"QR scan" has no camera.** `ScanQrDialog.kt` is a preset list plus a JSON paste box. When fields are missing it fills in
   a default recipient and amount (`optString("recipient", "7xKX…")`). `CAMERA` is declared in the manifest, but CameraX is
   commented out and nothing uses the camera.
9. **The docs make unsupported claims.** The README marks about 30 features "Shipped", including Seed Vault connection, live
   balances, SKR staking, a memo receipt "sealed by real SKR transfer", hardware-attested evidence and "26.2% APY". It also
   advertises an Apache-2.0 licence badge, but there is no LICENSE file. `PITCH_DECK_OUTLINE.md` slide 9 says
   "Production-Grade… Zero Mock Data" and "not a concept or a prototype… full integration with the Seeker Seed Vault", and
   slide 8 targets the "$10,000 SKR Integration Prize". `SPRINT_PLAN.md` promises a "production-grade" app "ready for the
   Solana dApp Store". `docs/index.html` shows USD balances ($387.22), mainnet in its mock UI, APY comparisons and a salary
   target line. None of this is supported by the code (findings 2–8).
10. **The theme is EdgeORE's own design.** `EdgeOreHomeComponents.kt` and `ui/theme/Color.kt` are a re-implementation of the
    EdgeORE Mine screen with its copper/mint palette. Most of its buttons only show a `Toast`.

## 2. Build status of the zip (measured on this box, 2026-10-10)

| Step | Result |
|---|---|
| Wrapper | None in the zip (`gradle-wrapper.properties` only, Gradle 9.3.1). A downloaded Gradle 9.3.1 was used. AGP 9.1.1, Kotlin 2.2.10, compileSdk 36.1 (installed for this run). |
| `:app:compileDebugKotlin :app:testDebugUnitTest` | **EXIT 0**. JUnit XML: suites=2, tests=11, passed=11, failures=0, skipped=0. |
| `:app:assembleDebug` | **FAILED** at `validateSigningDebug`: `Keystore file '…/debug.keystore' not found for signing config 'debugConfig'`. The release config needs `my-upload-key.jks` plus environment passwords, which are also absent. |
| `android/app/…/SecurityUtils.kt` | A byte-identical duplicate outside any Gradle module (`doctor.sh` checks that both copies exist). Dead code. |
| `services/cooldownSync.ts` | A TypeScript file using `@solana/web3.js` with no `package.json`. Not part of any build. |

So the zip compiles and its unit tests pass, but it does not produce an APK as shipped.

## 3. Component verdicts

"Equivalent in EdgeORE" names the EdgeORE file and gives the evidence for the "better" call.

| Component (Clearance) | What it does / quality | Security issues | EdgeORE equivalent and which is better | Verdict |
|---|---|---|---|---|
| `GeminiAuditService` | Cloud LLM "risk score" for a pending transfer, plus a local heuristic fallback. Scores come from keyword matching on model prose. | Sends recipient, mint, amount, network and evidence data to a cloud API. Key in the URL query and compiled into the APK. Treats "SKR*" mints as verified. Can say "Safe to authorize". | None, and none is wanted: EdgeORE's AI is on-device or an owned host, and the AI screen states "Cloud fallback OFF". An LLM verdict is not a safety check. EdgeORE's decoder refuses anything it cannot decode exactly (`SolanaMessage.decode`). | **REJECT.** It breaks EdgeORE's privacy boundary and gives non-deterministic "safety" advice. Even an opt-in version would need a key in the app or a proxy EdgeORE does not have. |
| `SkrStakingService`, `SkrStakePosition`, season claims, `cooldownSync.ts` | In-memory SKR stake, unstake, withdraw and claims with a fixed 26.2% APY. | Fabricated balances and claims. Unverified program IDs and mints. | None. EdgeORE claims no SKR integration. | **REJECT.** SKR staking and reward claims are out of scope and unsupported. |
| `MwaWalletService` | Simulated wallet that starts connected and returns random "signatures". | Every downstream receipt is fabricated. | `wallet/WalletConnection.kt`, `WalletAuthorization.kt`, `WalletCoordinator.kt` use real MWA clientlib-ktx 2.0.3 with confirmed deauthorization. **EdgeORE is better.** | **REJECT** |
| `SolanaRpcService` | JSON-RPC balance, slot, airdrop and signature status. | Returns invented success on every failure (finding 3). | `solana/SolanaRpc.kt` with `RpcObservation` (Fresh/Stale/Unavailable). Failures stay failures. **EdgeORE is better.** | **REJECT** |
| `SolanaDecoder` / `ClearanceSpec.decodeInstruction` | Typed decode of SPL Token TransferChecked/Transfer/SetAuthority/Approve, Stake and Memo from (programId, keys, data). Discriminator offsets are right. | Works on instruction tuples, never on a full message. Labels keys by position without checking the account layout. Has hard-coded SKR mints. Includes a mock payload builder. | `solana/SolanaMessage.kt` decodes the **whole legacy message** and accepts only one shape (single signer, one System transfer, exact account layout); anything else is `Unsupported`. **EdgeORE is better** for signing safety. Clearance's token decoders would only matter if EdgeORE ever supported SPL tokens, which it does not. | **REJECT** for now (research note: its discriminator table is a useful reference if SPL transfers are ever scoped). |
| `TransactionExplainer` | Plain-language headline, balance impact, permissions and fee for an `Action`. | Built from the **form inputs**, not the bytes. Labels recipients by address prefix ("Helium Certified Microcell #4892" for `9WzD…`). Fee is a fixed "0.000005 SOL". `decodeArbitraryPayload` falls back to a default recipient and amount. | EdgeORE had no plain-language layer, only exact fields. The *idea* is good; the implementation is unsafe. | **ADAPT** → `solana/PlainLanguage.kt`: built only from `TransferReview.Draft.decoded` (the decoded message bytes). It refuses to explain unsupported bytes, never labels an address and uses the RPC-priced fee or says it is unknown. It is shown **above** the exact-message fields, which stay. |
| `ReviewBinding` / `ClearanceSpec` | SHA-256 of a canonical text form of the action. `assertUnchanged` throws `MESSAGE_CHANGED`. `ClearanceSpec` also bundles 40 helpers. | Tautological binding (finding 7). Many helpers return constants (`connect()`, `latestDevnetBlockhash()`, `observeSettlement()`, `getSkrRawBalance()`). | `TransferReview.prepare` hashes the **real message bytes**. `verifyWalletReturn` refuses unless the wallet returns those exact bytes with a valid Ed25519 signature from the fee payer. Durable operations then stop resends. **EdgeORE is strictly better.** | **REJECT** (only the "MESSAGE_CHANGED" wording idea; EdgeORE already refuses with a reason). |
| `ScanQrDialog` | Preset DePIN "tasks" plus a JSON paste box called "Scan / Custom QR". | No scanning. Presets carry reward amounts. Missing fields get default recipient and amount. | EdgeORE had no QR input. The Review form prefilled the user's own address and `0.001`, which caused the garbled-recipient problem on Appetize. | **ADAPT** → a real scanner. `scan/AddressQr.kt` accepts a bare address or a Solana Pay *transfer* link (`amount`, `label`, `message`) and **refuses** `spl-token`, `reference`, `memo`, transaction-request URLs, repeated or unknown fields. `scan/QrDecoder.kt` (zxing-core) decodes, and `scan/QrScanActivity.kt` (platform Camera2) scans live; there is also a photo-picker path. It fills the form only. |
| `EvidenceCaptureDialog`, `Evidence`, `SHA256Service.buildAndHashCanonicalManifest` | Builds a "photo/video/document" evidence manifest and hashes it. | The media is synthetic (finding 6). It would need CAMERA (photo/video), RECORD_AUDIO (video with sound) and READ_MEDIA_* (picking documents on API 33+). The manifest canonicalisation is simple concatenated JSON. | EdgeORE's receipts and vault already hash and sign real contents (`receipts/Receipts.kt`, `storage/LocalVault.kt`). EdgeORE makes no physical-work claims. | **REJECT.** Physical-evidence claims are out of scope, and adding camera or microphone capture for them would widen permissions without a qualified use. |
| `KeystoreService`, `SecurityUtils` | P-256 key in AndroidKeyStore; sign and verify a hex digest string. | Forgeable fallback that verifies as true (finding 4). Unconditional "TEE" label. Signs the hex *string*, not the digest bytes. Duplicated across two paths. | `device/Keystore.kt` names the signer it actually uses ("Android Keystore P-256", and states where Ed25519 runs in software) without claiming TEE/StrongBox; the About dialog shows `receiptSignerProtection`. Verification never accepts a placeholder signature, and the receipt checker verifies real ECDSA/Ed25519 signatures. **EdgeORE is better.** | **REJECT** |
| Room (`ClearanceDatabase`, DAO, entities, repository) | Room 2.7 with four tables, a seeding callback and Flows. | `fallbackToDestructiveMigration(true)` deletes all data on any schema change. `exportSchema = false`. `allowBackup="true"` with empty backup rules, so cloud backup would include the DB. | EdgeORE uses an append-only, per-line signed receipt log that reports damaged lines instead of dropping them (`ReceiptLog`, schemas `edgeore.receipt.v1/v2`), a durable `OperationStore` that survives force-stop, and an AES-GCM vault with `allowBackup="false"`. For receipts and operations those are **better**: tamper-evident, never silently dropped. Room would only add value for a large queryable index. | **REJECT** (no migration; adding Room would also add kapt/KSP and new androidx versions). |
| `ui/theme` (Color, Type, Theme) | Copper/mint palette copied from EdgeORE, darker canvas `#0C1010`, monospace titles. | n/a | EdgeORE `ui/theme/Theme.kt`. **Contrast measured** (WCAG ratio): Clearance `TextMuted #677974` is 4.16 on its background, 3.83 on `surfaceDark` and 3.61 on `surfaceCard`, so it **fails AA** for body text. EdgeORE `textMuted #A6B2AC` is 8.47 / 7.29 / 8.13 on background / surface / inset, and every EdgeORE text token is ≥ 6.6 on every surface. **EdgeORE is better.** | **REJECT.** EdgeORE's palette is kept unchanged. |
| `EdgeOreHomeComponents`, `NowScreen` | EdgeORE-styled home: wallet card, Edge Mode, dial, ORE/Compute split, chart, toggles. | The buttons only show Toasts. "Preview session" toggles a local flag. The chart is illustrative. | EdgeORE `MineScreen.kt` already has these with real state and stated effects (Enforced/Saved only/Unavailable). The useful part of "Now" is *a single place showing what needs attention*. | **ADAPT** → a "Now" card at the top of Mine: wallet, operations awaiting an outcome, receipts, vault. Each row is a ≥ 56dp button to where it is handled, with no invented numbers. |
| `ClearanceComponents`, `ClearScreen`, `ProofScreen`, `DePinTasksScreen`, `MainScreen` | Review card, receipts list and detail, task hub, four-tab shell. | Hard-coded "finalized/32 confirmations". AI verdict card. Reward labels. Several icon buttons at 24dp with 16dp icons (below the 48dp touch target). Monospace 11sp body text. | EdgeORE `ReviewScreen`, `ReceiptsScreen`, `NodesScreen` and the `EdgeOreApp` shell. EdgeORE's receipts screen already has a real empty state ("No receipts yet.") and damage reporting. | **REJECT** the screens. **ADAPT** the empty-state and first-run explanation patterns (below). |
| `SPRINT_PLAN.md`, `PITCH_DECK_OUTLINE.md`, `docs/index.html`, README feature table | Planning and pitch material. | Unsupported claims (finding 9). | EdgeORE's README and handoff keep an evidence ledger with NOT_RUN states. | **REJECT** as content. Nothing was copied. |
| `scripts/doctor.sh` | Prints java/gradle versions, pings devnet and checks the duplicate file exists. Ends with "System Ready" regardless. | Reports "Ready" even after a failed check (`set -e` does not catch the `echo` branches). | `scripts/qualify.sh` produces evidence with exit codes. **EdgeORE is better.** | **REJECT** |
| `metadata.json`, `.env.example`, secrets and google-services plugins | AI Studio scaffolding with a Gemini capability flag. | Would compile secrets into BuildConfig. | n/a | **REJECT** |

## 4. What changed in EdgeORE (branch `feature/ux-from-clearance`)

- **First-run introduction** (`ui/screens/OnboardingScreen.kt`, `ui/Onboarding.kt`). Shown once before the tabs and
  reopened from About ("Show introduction"). It covers what EdgeORE does, what it does not do (no mining income, ORE
  rewards, SKR payments or token; no mainnet; not store-ready; never sees wallet keys), devnet-only, and that a wallet is
  required. It also states the device-bound vault boundary. Section titles are TalkBack headings.
- **Now overview on Mine** (`MineScreen.kt`). Wallet, operations awaiting an outcome, receipt log and vault. Copper marks
  rows that need attention, and each row is a merged, labelled ≥ 56dp button.
- **Review route**:
  - Destination and amount now **start empty** (`ReviewState.amount = ""`; `resetReview` no longer prefills the user's own
    address). Self-transfer is still available as an explicit button.
  - **Inline validation** (`ui/ReviewInput.kt`) explains each field. "Prepare" stays disabled until both fields are valid and
    a wallet is connected; `TransferReview.prepare` still makes the real decision on the bytes.
  - **Scan QR** (live Camera2 + zxing) and **QR from image** (system photo picker, no permission). CAMERA is requested only
    when the user taps Scan QR; if it is denied, the form says so and offers the alternatives. Frames and images are decoded
    in memory and never saved or sent. The scanner only fills the form and shows what it filled. A refused QR says why.
  - **"In plain words"** (`solana/PlainLanguage.kt`, `PlainLanguageCard`) over the exact-message card, derived from
    the decoded bytes. Its tests tamper the decoded transfer and show the explanation follows the bytes.
- **Accessibility:** the Nodes scope checkbox row is now one ≥ 48dp toggle with `Role.Checkbox`, so the label is read with
  the state. The About button's click label now says "About this build". New titles are headings. A JVM render at
  `fontScale = 1.5` checks that the introduction wraps rather than clips.
- **Attribution:** README and NOTICE credit Clearance as the owner's prior work and list ZXing core (Apache-2.0).
- **README:** restores the checker wording "verifies the integrity of signed receipt contents", which was not present on
  `main`. It also adds the device-bound vault boundary line and describes the new UX.

### New dependency

`com.google.zxing:core:3.5.3` (Apache-2.0, pure Java, **no runtime dependencies**). The resolved
`releaseRuntimeClasspath` and `debugRuntimeClasspath` differ from `main` by exactly that one line
(`evidence/ux-from-clearance/<commit>/deps-diff.txt`). No existing version changed. Alternatives considered:

- **ML Kit barcode, bundled model:** ships a native model per ABI and pulls ML Kit/GMS base artifacts, which is much larger than one jar.
- **Google code scanner:** needs Play Services. Not usable on de-Googled Seeker-style setups.
- **CameraX:** its POMs reference older androidx floors, so it would likely not bump the pinned versions, but it adds several camera artifacts plus concurrent-futures/listenablefuture. That was not measured and not needed for one scan screen.

Platform Camera2 drives the camera instead, with no extra dependency.

## 5. Things deliberately not ported

Cloud transaction auditing; SKR staking, claims or APY; DePIN reward tasks; synthetic evidence capture; the simulated wallet
and RPC fallbacks; the forgeable Keystore fallback; Room with destructive migration; `allowBackup="true"`; the pitch,
sprint and site claims; and the AI Studio secrets scaffolding.

## 6. Still open (not changed by this branch)

- Nothing here is device evidence. The scanner, onboarding and Now card are covered by JVM tests and Robolectric renders
  only. The live camera path (`QrScanActivity`) has **not run on a phone**; the decode path is unit-tested with zxing-generated codes.
- The navigation label "Mine" is kept because the design spec (`docs/design-spec.md`) and the device runbook use it. It
  arguably suggests mining; renaming it (for example to "Home") is the owner's call.
- The device runbook (`docs/DEVICE-RUNBOOK.md`) still describes the 0.2.9-review APK, which has no onboarding. A build
  from this branch shows the introduction first: tap **I understand · continue** before step 2 of that runbook.
