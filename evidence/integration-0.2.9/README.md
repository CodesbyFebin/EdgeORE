# Integration candidate `integration/0.2.9-candidate`: build evidence

**Tested commit: `37f1e05a61fa`.** The evidence commit on top of it adds only this folder and a wording edit in `docs/hardening-backlog.md`; no app, script or `onchain/` source changes after the tested commit.
**JVM, build and local-simulator results only.** This exact APK has not been installed on any phone or emulator.

## Merges (in order, off `origin/main` 2442ba1)

| # | Branch @ head | Merge commit | Conflicts |
|---|---|---|---|
| 1 | `fix/mwa-authorization` @ `5009e08` | `cf5419e` | none |
| 2 | `feature/storage-vault` @ `51a61cc` | `68ffe96` | `EdgeOreViewModel.kt` **import block only**: wallet `ConnectFailure`/`WalletConnection` vs storage `StorageController`/`StorageEvent`/`VaultFileView`. Kept both sets; no function body touched. |
| 3 | `feature/ai-edge-gallery` @ `6579e61` | `716ed10` | none (README and `known-limitations.md` auto-merged; both branches' lines kept) |
| 4 | `feature/anchor-receipt-settlement` @ `72647c4` | `2c644bd` | none (`onchain/` and its evidence only). Head re-checked with `git fetch` just before this report: still `72647c4`; later follow-up commits (claim deadline, verifier rotation, rent reclaim) are **not** included. |
| 5 | `feature/contribution-scheduler` @ `18484f4` (tested `daab56a`) | `b75dfa0` | none. `daab56a`'s `@ConscryptMode(OFF)` on `ContributionWorkTest` is kept; the full suite ran without the `TransportHardeningTest.nodeRedirectIsRefusedNotFollowed` hang. |
| 6 | `docs/ore-integration-design` @ `22370c5` | `7be3dbf` | none (one file, `docs/design/ore-integration-future.md`, marked DESIGN ONLY - NOT BUILT) |

## Suites (separate runs, never combined)

| Suite | Command | Exit | Counts | Files |
|---|---|---|---|---|
| Android gate | `SCREENS=1 bash scripts/qualify.sh` on clean `37f1e05a61fa` | **0** | Unit: **24 suites, 234 tests: 233 passed, 0 failed, 0 errors, 1 skipped** (`NodeAgentIntegrationTest`, needs a live agent, as on main). Lint debug: no issues. | `gate-37f1e05a61fa/` |
| Release-manifest policy (new step in qualify.sh) | `python3 scripts/test_check_manifest.py && python3 scripts/check-manifest.py <merged release manifest>` | **0** | 9 tests: 9 passed; release manifest PASS | `gate-37f1e05a61fa/release-manifest.log` |
| Screenshot suite | `./gradlew :app:verifyRoborazziDebug -Pscreens` (inside qualify) | **0** | **4 suites, 19 tests: 19 passed** (ScreenshotTest 6, FullScreenshotTest 6, StorageRenderTest 6, DefaultStateWalkTest 1) | `screens-junit/`, `gate-37f1e05a61fa/screens-verify.log` |
| Receipt checker (standalone JVM) | `bash gradlew -p tools/receipt-checker --no-daemon --console=plain clean test` | **0** | **1 suite, 10 tests: 10 passed** (7 existing + 3 new) | `receipt-checker/` |
| Anchor program (`onchain/`) | `anchor build --arch v0` then `anchor test --skip-build` | **0 / 0** | **17 passed, 0 failed** (LiteSVM, in-process; nothing deployed, no cluster contacted) | `anchor/` |

The anchor run was made on merge commit `2c644bd`; `onchain/` is byte-identical at the tested commit (`git rev-parse <commit>:onchain` = `fc68b499…` for `72647c4`, `2c644bd` and `37f1e05`). The fresh clone has no committed program keypair, so `anchor build` generated one and printed *Program ID mismatch detected* (warning, exit 0); the built `receipt_settlement.so` still has sha256 `107118b3…1642ff`, 244,328 bytes, identical to the branch's recorded build.

Earlier gate attempts on intermediate commits (not results for the candidate): `2c644bd` screenshot step failed on `ScreenshotTest.ai`/`FullScreenshotTest.ai` (fixed by re-record `42420d5`); `8ca556d` release-manifest step failed on WorkManager's permission-guarded `SystemJobService`/`DiagnosticsReceiver` (rule refined in `37f1e05`).

## Screenshot baselines
See the re-record log in `docs/screenshots/README.md`. `06-review*` (stale since `cc31714`'s intentional expiry copy) came re-recorded from `feature/storage-vault` `8f9633d`; `02-ai*` re-recorded in `42420d5` for the AI branch's intentional section; `full/01-mine-full.png` came re-recorded from the scheduler branch (`cfed139`). Nothing else re-recorded.

## Dependencies (`deps/COMPARISON.md`)
Resolved classpaths vs main 2442ba1, debug/release/unitTest/androidTest: **0 existing versions changed, 0 removed.** Added: `litertlm-android` 0.8.0, `gson` 2.13.2, `kotlin-reflect` 2.0.21 and `:ondevice-llm` (AI branch); `work-runtime(-ktx)` 2.10.0 with room 2.6.1, sqlite 2.4.0, lifecycle-livedata/service 2.8.7, tracing-ktx 1.2.0, concurrent-futures-ktx 1.1.0, kotlin-stdlib-jdk7/jdk8 1.8.22 (scheduler); `work-testing` 2.10.0 on the unit-test path only.

## Debug APK
- `package: com.edgeore.app versionCode=11 versionName=0.2.9-review`
- sha256 `b8af257630c680398b2bf621e87075201fb3b16e0f34c159174981c2e65b7000`, **57,286,793 bytes**
- `BuildConfig.GIT_COMMIT = 37f1e05a61fa` (About text shows this stamp)
- Signer: Android **debug** key `c9b46665…7ae4b93`. No release signing.
- Box copy: `/workspace/EdgeORE-0.2.9-review-37f1e05a61fa-debug.apk` (not committed).

### ABI decision: unchanged, on purpose
LiteRT-LM 0.8.0 already ships only `arm64-v8a` (18,658,168 B) and `x86_64` (21,829,880 B) `liblitertlm_jni.so`, stored uncompressed. The only other native code is `libandroidx.graphics.path.so` (7–11 KB per ABI). So `abiFilters "arm64-v8a","x86_64"` would save about 16 KB and would make 32-bit devices unable to install the app at all (today they install, and a native-load failure is caught in `OnDeviceAiController` and shown as an error). Real savings (~19–22 MB per APK) need ABI splits or an App Bundle, which change the artifact from one `app-debug.apk` to several and would break `qualify.sh`'s APK identity step and the single-file Appetize upload. Left for a release-packaging change with its own review; the x86_64 emulator path is unaffected.
