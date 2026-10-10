# Integration candidate `integration/0.2.9-candidate`: build evidence

**Tested commit: `077c76503d47`** (second round). The evidence commit on top of it adds only files under this folder; no app, script or `onchain/` source changes after the tested commit. The first-round gate for `37f1e05a61fa` is kept in `gate-37f1e05a61fa/`.
**JVM, build and local-simulator results only.** This exact APK has not been installed on any phone or emulator.

## Merges (in order, off `origin/main` 2442ba1)

| # | Branch @ head | Merge commit | Conflicts |
|---|---|---|---|
| 1 | `fix/mwa-authorization` @ `5009e08` | `cf5419e` | none |
| 2 | `feature/storage-vault` @ `51a61cc` | `68ffe96` | `EdgeOreViewModel.kt` **import block only**: wallet `ConnectFailure`/`WalletConnection` vs storage `StorageController`/`StorageEvent`/`VaultFileView`. Kept both sets; no function body touched. |
| 3 | `feature/ai-edge-gallery` @ `6579e61` | `716ed10` | none (README and `known-limitations.md` auto-merged; both branches' lines kept) |
| 4 | `feature/anchor-receipt-settlement` @ `72647c4` | `2c644bd` | none (`onchain/` and its evidence only) |
| 5 | `feature/contribution-scheduler` @ `18484f4` (tested `daab56a`) | `b75dfa0` | none. `daab56a`'s `@ConscryptMode(OFF)` on `ContributionWorkTest` is kept; the full suite ran without the `TransportHardeningTest.nodeRedirectIsRefusedNotFollowed` hang. |
| 6 | `docs/ore-integration-design` @ `22370c5` | `7be3dbf` | none (one file, `docs/design/ore-integration-future.md`, marked DESIGN ONLY - NOT BUILT) |
| 7 | `feature/anchor-receipt-settlement` final head @ **`823e649`** (SBPF v3, claim deadline, verifier rotation, rent reclaim; tested `c2ba257`) | `3486dc7` | none (`onchain/` and `evidence/anchor-receipt-settlement/` only) |

## Suites (separate runs, never combined) — round 2, tested commit `077c76503d47`

| Suite | Command | Exit | Counts | Files |
|---|---|---|---|---|
| Android gate | `SCREENS=1 bash scripts/qualify.sh` on clean `077c76503d47` | **0** | Unit: **24 suites, 241 tests: 240 passed, 0 failed, 0 errors, 1 skipped** (`NodeAgentIntegrationTest`, needs a live agent). Lint debug: no issues. | `gate-077c76503d47/` |
| Screenshot suite | `./gradlew :app:verifyRoborazziDebug -Pscreens` (inside qualify) | **0** | **4 suites, 19 tests: 19 passed** | `screens-junit/`, `gate-077c76503d47/screens-verify.log` |
| Release-manifest check (standalone run) | `python3 scripts/test_check_manifest.py`; `python3 scripts/check-manifest.py <merged release manifest>` | **0 / 0** | 9 tests: 9 passed; release manifest PASS (also run inside qualify: EXIT=0) | `release-manifest/standalone-run.log` |
| Receipt checker (standalone JVM) | `bash gradlew -p tools/receipt-checker --no-daemon --console=plain clean test` | **0** | **1 suite, 11 tests: 11 passed** | `receipt-checker/` |
| Anchor build (`onchain/`, Agave 4.3.0 first on PATH for this run only) | `anchor build` (SBPF v3) | **0** | program `.so` sha256 `330b19b8…6235de`, 278,344 bytes (identical to the branch's recorded build) | `anchor/anchor-build.log` |
| Anchor LiteSVM | `anchor test --skip-build --skip-local-validator --skip-deploy` | **0** | **29 passed, 0 failed** | `anchor/anchor-test-litesvm.log` |
| Anchor local validator | `anchor test --skip-build --validator legacy --script validator --provider.wallet <throwaway keypair in a mktemp dir, deleted after>` | **0** | **28 passed, 0 failed** (local `solana-test-validator` only; the harness refuses non-local RPC URLs; nothing deployed to devnet) | `anchor/anchor-test-validator.log` |

Anchor toolchain: rustc 1.99.0, anchor-cli 1.2.1, solana-cli/cargo-build-sbf 4.3.0 (from `releases/4.3.0` placed first on `PATH`). The box's active Solana release was `releases/3.1.10` before and after the run (recorded in `anchor/commands-and-exit-codes.txt`). The anchor run was on merge `3486dc7`; `onchain/` is byte-identical at the tested commit (`git rev-parse <c>:onchain` = `b30f882e…` for both). The fresh clone has no committed program keypair, so `anchor build` printed *Program ID mismatch detected* (warning, exit 0).

Earlier gate attempts on intermediate commits (not candidate results):
- Round 1: `2c644bd` screenshot step failed on `ai` (fixed by `42420d5`); `8ca556d` release-manifest step failed on WorkManager components (rule refined in `37f1e05`).
- Round 2: `ea2ec1b` screenshot step failed on `FullScreenshotTest.storage` (0.109% pixels; the build machine's CPU and disk-rate lines both varied), kept in `evidence/build-ea2ec1b1ab05/`. Fixed by a per-shot threshold in `077c765` (test-only; see that commit).

## Screenshot baselines
See the re-record log in `docs/screenshots/README.md`. `06-review*` (stale since `cc31714`'s intentional expiry copy) came re-recorded from `feature/storage-vault` `8f9633d`; `02-ai*` re-recorded in `42420d5` for the AI branch's intentional section; `full/01-mine-full.png` came re-recorded from the scheduler branch (`cfed139`). Nothing else re-recorded in round 1. Round 2: `full/03-storage-full.png` in `3553c82` (new freshness line in the Resource telemetry card) and `02-ai.png` / `full/02-ai-full.png` in `5c891b6` (new execution title). Both are intentional and traced to those commits.

## Dependencies (`deps/COMPARISON.md`, generated in round 1; no `*.gradle.kts`, `gradle/` or `ondevice-llm/` change since)
Resolved classpaths vs main 2442ba1, debug/release/unitTest/androidTest: **0 existing versions changed, 0 removed.** Added: `litertlm-android` 0.8.0, `gson` 2.13.2, `kotlin-reflect` 2.0.21 and `:ondevice-llm` (AI branch); `work-runtime(-ktx)` 2.10.0 with room 2.6.1, sqlite 2.4.0, lifecycle-livedata/service 2.8.7, tracing-ktx 1.2.0, concurrent-futures-ktx 1.1.0, kotlin-stdlib-jdk7/jdk8 1.8.22 (scheduler); `work-testing` 2.10.0 on the unit-test path only.

## Debug APK
- `package: com.edgeore.app versionCode=11 versionName=0.2.9-review`
- sha256 `65efcd4f33e60aa6285f0a24266ec873f4977e07f4c551936f652ebe29cf50b1`, **57,286,793 bytes**
- `BuildConfig.GIT_COMMIT = 077c76503d47` (About text shows this stamp)
- Signer: Android **debug** key `c9b46665…7ae4b93`. No release signing.
- Box copy: `/workspace/EdgeORE-0.2.9-review-077c76503d47-debug.apk` (not committed). Round 1 APK (`37f1e05a61fa`, sha256 `b8af2576…5b7000`) is superseded.

### ABI decision: unchanged, on purpose
LiteRT-LM 0.8.0 already ships only `arm64-v8a` (18,658,168 B) and `x86_64` (21,829,880 B) `liblitertlm_jni.so`, stored uncompressed. The only other native code is `libandroidx.graphics.path.so` (7–11 KB per ABI). So `abiFilters "arm64-v8a","x86_64"` would save about 16 KB and would make 32-bit devices unable to install the app at all (today they install, and a native-load failure is caught in `OnDeviceAiController` and shown as an error). Real savings (~19–22 MB per APK) need ABI splits or an App Bundle, which change the artifact from one `app-debug.apk` to several and would break `qualify.sh`'s APK identity step and the single-file Appetize upload. Left for a release-packaging change with its own review; the x86_64 emulator path is unaffected.
