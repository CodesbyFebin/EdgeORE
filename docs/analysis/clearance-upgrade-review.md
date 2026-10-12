# Review of the "upgraded" Clearance zip (Google AI Studio output), and what EdgeORE takes from it

**Subject:** the zip the owner got back from Google AI Studio after our handoff (`upzip`, 83 files). It is the
Clearance prototype again (namespace `com.example`), not EdgeORE.
**Compared with:** the earlier Clearance zip (`prevapp`), reviewed in [`clearance-app-review.md`](clearance-app-review.md).
**Reviewed against:** EdgeORE `main` at `268fd74`.
**Outcome for EdgeORE:** nothing ported. This document is the only change (see section 6).

Line numbers below refer to files in the upgraded zip unless marked `prevapp`.

## 1. Summary

The upgrade puts EdgeORE-style screens (Nodes, Private AI, Receipts, Storage, a wallet dialog) and a
"DEVNET LIVE" badge on top of the same simulated core. **None of the eight earlier problems is fixed.**
One got smaller (balance fallback 1.25 SOL became 0) and one new fake was added (airdrop signatures). The new
screens are mostly hard-coded numbers presented as live readings. The Mobile Wallet Adapter dependency was
added to Gradle but **no source file imports it**.

- Builds as shipped: `compileDebugKotlin` + `testDebugUnitTest` **pass** (11 tests, all from `prevapp`, none
  covering changed or new code). `assembleDebug` **fails** in `validateSigningDebug`:
  `Keystore file '.../debug.keystore' not found for signing config 'debugConfig'`.
- Code-quality score as shipped: **2.3 / 10** (section 4).
- Can it reach 10/10 as-is? **No** (section 5).

## 2. Earlier problems: fixed or not

| Earlier problem | Status in upgraded zip | Evidence |
|---|---|---|
| Simulated MWA signatures (random bytes) | **Not fixed** | `services/MwaWalletService.kt` 111–116: `signTransaction` fills `SecureRandom().nextBytes` and returns it as a signature; 119–122: `signDevnetMemo` returns `"5memo"+hash+"dev"`. `mobile-wallet-adapter-clientlib:2.0.3` was added in `app/build.gradle.kts` and a `<queries>` block in the manifest, but `grep -r mobilewalletadapter app/src` finds nothing: the library is never called. |
| Hard-coded wallet | **Worse** | Same file 29–32: three invented addresses (Seeker/Phantom/Solflare) picked by wallet name in `connect` (52–54); starts connected to one (37). `generateFreshDevnetKeypair` (74–79) draws the "public key" from random bytes unrelated to the seed, so it is not a keypair. `ANCHOR_EDGEORE_PROGRAM_ID = "EdgeORE111…"` (32) contains `O`, which is not base58. |
| RPC fallback 1.25 SOL / CONFIRMED | **Partly** | `services/SolanaRpcService.kt` 46–56: balance failure now returns `0L` (indistinguishable from an empty account). Slot failure still returns `318492041` (75–80). Status failure still returns `CONFIRMED` (172, 189–191). `ui/ClearanceViewModel.kt` 55/58/100 still seed `1.2500` SOL, `48.50` SKR and slot `318492041`; 126 and 296 fall back to 1.25. |
| Fake airdrop | **New fake** | `SolanaRpcService.kt` 114–117: on any faucet error returns `"4devAirdrop"+UUID+"sol"` as a signature. `ClearanceViewModel.kt` 126–127 then adds 1.0 SOL to the displayed balance. |
| Keystore `verifySignature` accepts `sig_secp256r1_*` | **Not fixed** | `security/KeystoreService.kt` 78, 82 produce the placeholder; 88 accepts any string with that prefix. `SecurityUtils.kt` 123 still sets `isTeeBacked = true` unconditionally. |
| Fake QR / evidence capture | **Partly** | `ui/screens/EvidenceCaptureDialog.kt` 85 now uses `TakePicturePreview` (real camera). But 110–124 draw a placeholder bitmap and use it when no photo is taken, so evidence can still be "captured" with no camera; 127–139 and 307/342–346 fabricate a "video" and a "telemetry.csv". The task-preset "QR" import is unchanged. |
| Self-comparing review binding | **Not fixed** | `data/ClearanceRepository.kt` 144–150 re-serialize the same in-memory `action` and compare it with the hash taken from that `action`; only the tamper switch can make it differ. No wallet-signed bytes are bound. |
| Gemini key compiled into the APK | **Not fixed** | `secrets` plugin still applied (`app/build.gradle.kts` 7, 67–71) and `ai/GeminiAuditService.kt` still used. The generated `BuildConfig.java` contains `GEMINI_API_KEY` (the `.env.example` placeholder in this zip; a real key in `.env` would be compiled in the same way). Transaction details still go to the cloud model. |
| SKR 26.2% APY | **Not fixed** | `services/SkrStakingService.kt` 65, 128; `domain/model/SkrStakePosition.kt` 34. |
| `debug.keystore` build failure | **Not fixed** | `assembleDebug` fails as quoted above. |
| README "Shipped" claims | **Not fixed** | Still badges "Devnet / Mainnet" and "Seed Vault + TEE" and lists Seed Vault, SKR and memo-receipt features as shipped. |

## 3. Per-file verdicts

Diff counts are removed / added lines against `prevapp`.

| File | Change (−/+) | What changed | Verdict |
|---|---|---|---|
| `app/build.gradle.kts` | 7 / 5 | Adds MWA clientlib 2.0.3, enables CameraX, drops Retrofit/logging/location. Keeps secrets + google-services plugins, Firebase, and a debug signing config pointing at a missing `debug.keystore`. | **Reject.** MWA unused, CameraX unused (capture uses `TakePicturePreview`), build still fails. |
| `AndroidManifest.xml` | 0 / 12 | Camera `uses-feature` (not required), MWA `<queries>`. Keeps `VIBRATE`, `allowBackup="true"`. | **Mixed.** `<queries>` is correct but serves no code. |
| `security/SHA256Service.kt` | 7 / 93 | Streaming `hashInputStream` with progress, `verifyChecksum`, canonical manifest helper. | **Real but not better.** EdgeORE `io/SafeFiles.sha256Hex` and `ModelDownloader.copyVerified` already stream with a byte cap and progress. |
| `services/ClearanceSpec.kt` | 1 / 27 | Public `decodeBase58`, new `encodeBase58`. | **Real, minor.** EdgeORE `crypto/Bytes.kt` already has base58. |
| `services/MwaWalletService.kt` | 9 / 73 | Wallet presets, fake keypair generator, local Anchor discriminator. Signing still random. | **Reject.** Simulated throughout. |
| `services/SolanaRpcService.kt` | 7 / 58 | Real-looking airdrop call with fake-signature fallback; balance fallback now 0. | **Reject.** Still invents slot/status/signatures. |
| `ui/ClearanceViewModel.kt` | 0 / 38 | Connect/disconnect/keypair/Anchor actions; seeded balances. | **Reject.** Displays invented balances. |
| `ui/MainScreen.kt` | 125 / 139 | Review route hides bottom nav; wallet dialog state. | **UX idea only.** See section 6. |
| `ui/components/EdgeOreHomeComponents.kt` | 39 / 106 | "CONCEPT · SAMPLE DATA" (prevapp 98) became a pulsing "DEVNET LIVE" (122); wallet card shows SOL/SKR/ORE. | **Reject.** Honesty regression: the label claims live data that is seeded. |
| `ui/screens/EvidenceCaptureDialog.kt` | 198 / 212 | Real camera preview capture + placeholder fallback + fake video/CSV. | **Reject as-is.** Only the `TakePicturePreview` call is real; EdgeORE has no evidence-photo feature that needs it. |
| `ui/screens/NowScreen.kt` | 81 / 119 | Shows seeded balances, slot, airdrop button. | **Reject.** |
| `ui/screens/NodesScreen.kt` (new, 482) | — | Hard-coded "24%" CPU (322), "3.2 GB / 8 GB" RAM (339), "12 Mbps" (356), endpoint `http://192.168.1.23:8080` (56), "Storage (encrypted)" (270). Start/stop/revoke only change local state. | **Reject.** Fabricated metrics. EdgeORE's node screen reads a real node agent. |
| `ui/screens/PrivateAiScreen.kt` (new, 602) | — | Four model cards with invented sizes (70–73), a pre-written model reply (82), "Connected" toggle without a request (302–322), Gemini cloud-fallback toggle (351), "Model verified" on checksum match (437), "Memory limit 4 GB" (468). The SAF model picker + checksum compare is real. | **Reject.** EdgeORE already runs real on-device models with pinned checksums. |
| `ui/screens/ReceiptsScreen.kt` (new, 451) | — | Sample `ReceiptModel` list (67, 78); "Offline verification verified with Keystore key" toast without verifying (374). | **Reject.** EdgeORE receipts are real and checked by `verify-receipt.sh`. |
| `ui/screens/SolanaWalletDialog.kt` (new, 451) | — | Preset wallets, paste-a-pubkey, airdrop, Anchor/memo buttons all wired to the simulated service. | **Reject.** |
| `ui/screens/StorageScreen.kt` (new, 443) | — | Sample files (60–61), fixed "86 GB / 170 GB / 256 GB" (195–212), "Encrypted and added" toast with no encryption (84), fake export (235). | **Reject.** EdgeORE's Storage vault is real (`StatFs` in `device/DeviceResources.kt` 41, durable writes in `io/SafeFiles.kt`). |
| `README.md` | unchanged | Shipped claims as above. | **Reject.** |

Secrets: no real key, keystore, `google-services.json` or token is in the zip. Permissions: `INTERNET`,
`ACCESS_NETWORK_STATE`, `CAMERA`, `VIBRATE`. `CAMERA` is requested at capture time (acceptable); `allowBackup="true"`
would back up the Room database including evidence and receipts.

## 4. Code-quality score (as shipped)

| Dimension | Score | Evidence |
|---|---|---|
| Correctness / honesty of displayed data | 1 | Random-byte signatures, `CONFIRMED` on error, seeded balances, fixed node/storage metrics, "DEVNET LIVE" label. |
| Security & privacy | 2 | Keystore forgery accepted; cloud transaction audit; key compiled into `BuildConfig`; `allowBackup="true"`. Positive: camera permission requested at use. |
| Wallet integration | 1 | MWA dependency added but never called. |
| Build reproducibility | 3 | Compiles and tests pass; `assembleDebug` fails on a missing keystore; no Gradle wrapper. |
| Tests | 2 | 11 tests, all pre-existing; 0 for the 11 changed and 5 new files. |
| Dependency hygiene | 3 | MWA and CameraX unused; Firebase AI/App Check still pulled in. |
| Architecture | 4 | Room + repository + ViewModel is reasonable; new screens hold their own fake state instead of using the repository. |
| UI / UX | 5 | Clean, consistent dark theme and navigation; undermined by fake values. |
| Docs accuracy | 0 | README claims Seed Vault, SKR and mainnet features that do not exist. |
| **Overall (mean)** | **2.3** | |

## 5. Can this zip reach 10/10 as-is?

**No.** Every money- or proof-related path is simulated, the wallet library is not wired, and the README
describes a different app. Polishing the UI cannot change that.

A corrected copy was produced separately (`Clearance-fixed.zip`, outside this repo). In it every simulated
path is replaced by a real call or an explicit "not implemented"/"Not observed" state: real MWA `authorize`
via `clientlib-ktx` 2.0.3, null-returning devnet RPC reads, real faucet result, real ECDSA Keystore verification
with `KeyInfo` protection level, camera-only evidence, on-device rule checks instead of Gemini, seeded data and
the SKR staking service removed, `StatFs` storage numbers, and a README that matches the code. It builds
(`testDebugUnitTest`: 22 tests, 0 failures; `assembleDebug` succeeds with AGP's generated debug key, none committed).
Even so it is a well-labelled prototype, not a 10/10 dApp, because **it still cannot sign or send anything**:
no transaction message is built, so there is nothing for a review binding to bind. Getting further means
re-implementing what EdgeORE already has (exact-message review, MWA sign-and-send, durable operations,
receipts with an independent checker).

## 6. What EdgeORE takes from it

Nothing. Each candidate was checked against EdgeORE `main` `268fd74`:

| Candidate | Real? | Better than EdgeORE? | Decision |
|---|---|---|---|
| Streaming SHA-256 with progress (`SHA256Service.hashInputStream`) | Yes | No: `SafeFiles.sha256Hex` (byte cap) and `ModelDownloader.copyVerified` (progress, cancel, temp file) already exist. | Not ported |
| Base58 encode (`ClearanceSpec.encodeBase58`) | Yes | No: `crypto/Bytes.kt` covers it with tests. | Not ported |
| `TakePicturePreview` capture | Yes | No EdgeORE feature needs a photo; adding it would widen camera use beyond QR scanning. | Not ported |
| Hide bottom nav during review | UX idea | Unclear. EdgeORE's review is a full-screen route with the bottom bar still visible (`ui/EdgeOreApp.kt` 86–117); tapping a tab leaves review without signing, and Back does the same. Hiding the bar is a style choice, not a fix, and would change the review screenshot baselines. | Not ported; noted as a backlog idea |
| MWA dependency + `<queries>` | Unused | EdgeORE already has working MWA (`wallet/WalletCoordinator.kt`). | Not ported |
| Node / Private AI / Receipts / Storage / wallet screens | No (fabricated) | — | Not ported |

No code changed, so the EdgeORE gates (`SCREENS=1 qualify.sh`, receipt checker, release manifest) were not re-run
for this docs-only change. EdgeORE's README phrase, counts, pinned dependencies and claims are untouched.
