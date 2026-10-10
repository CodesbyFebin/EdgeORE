# Gate evidence: feature/ux-from-clearance @ 4b953874fb14

Tested commit: `4b953874fb14` (source + re-recorded screenshot baselines). This evidence was committed on top of it, so the branch head differs only by `evidence/`.
JVM/build results only. No phone, wallet, owned AI host, camera or on-device node session is part of any gate here.

| Gate | Command | Exit | Result | Files |
|---|---|---|---|---|
| Build gate | `SCREENS=1 bash scripts/qualify.sh` (clean testDebugUnitTest, lintDebug, assembleDebug, assembleDebugAndroidTest, processReleaseMainManifest, then release-manifest check and verifyRoborazziDebug) | **0** | see the rows below | `build/summary.md`, `build/build.log` |
| Android unit tests | (part of qualify) | **0** | **25 suites, 254 tests: 253 passed, 1 skipped** (`NodeAgentIntegrationTest.pairObserveRevokeAgainstRealAgent`, needs a live agent), 0 failed | `build/junit/` |
| Lint (debug) | (part of qualify) | **0** | No issues found | `build/lint-debug.txt` |
| Screenshot suite | `./gradlew :app:verifyRoborazziDebug -Pscreens` (from qualify) | **0** | **9 suites, 25 tests: 25 passed** (20 before this branch, plus onboarding, onboarding full, onboarding at fontScale 1.5, review filled and plain-language card) | `screens/junit/` |
| Receipt checker (standalone JVM) | `bash gradlew -p tools/receipt-checker --no-daemon --console=plain clean test` | **0** | **1 suite, 14 tests: 14 passed** | `receipt-checker/` |
| Release-manifest check | `python3 scripts/test_check_manifest.py && python3 scripts/check-manifest.py <merged release manifest>` | **0** | PASS. The merged manifest now adds `CAMERA` (runtime-requested on tap) and `uses-feature android.hardware.camera required=false`; `QrScanActivity` is `exported=false`. | `release-manifest/` |
| Settlement program | not run: `onchain/` is unchanged on this branch | n/a | n/a | n/a |

## Debug APK

- File (outside the repo): `EdgeORE-0.2.9-review-4b953874fb14-debug.apk`
- sha256 `76de0c3f303e6a0c109468c41f1078db0fce1f613af661d734f6393e9ec86ce5`
- size 57,631,928 bytes
- `BuildConfig.GIT_COMMIT` (About stamp) `4b953874fb14`
- versionName `0.2.9-review` (unchanged), versionCode 11, signed with the Android debug key (`c9b46665…7ae4b93`)

## Dependencies

`deps-diff.txt`: the resolved release and debug runtime classpaths differ from `main` `6f7b600` by exactly one added artifact, `com.google.zxing:core:3.5.3`. No existing resolved version changed.

## Other files

- `contrast.txt`: WCAG contrast ratios for the EdgeORE palette and the previous Clearance palette.
- `previous-app-build/`: the build attempt on the Clearance zip. Gradle 9.3.1 / AGP 9.1.1: compile and unit tests exit 0 (2 suites, 11 tests, 11 passed); `assembleDebug` fails because `debug.keystore` is missing.
- `before-after-jvm-renders.png`: contact sheet of JVM renders, not device screenshots.

Not claimed: device behaviour of the camera scanner, onboarding or any wallet flow. Those are still NOT_RUN.
