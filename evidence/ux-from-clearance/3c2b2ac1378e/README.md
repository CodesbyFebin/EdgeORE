# Gate evidence: docs/runbook-ux-from-clearance @ 3c2b2ac1378e

Tested commit: `3c2b2ac1378e` = merged `main` `4e70d28` (PR #11 merge commit) plus one docs-only commit (device runbook, `docs/device-qr/` QR test images, one line in the Clearance review). This evidence is committed on top of it, so the branch head differs only by `evidence/`.
JVM/build results only. No phone, wallet, camera, owned AI host or on-device node session is part of any gate here.

| Gate | Command | Exit | Result | Files |
|---|---|---|---|---|
| Build gate | `SCREENS=1 bash scripts/qualify.sh` | **0** | see the rows below | `build/summary.md`, `build/build.log` |
| Android unit tests | (part of qualify) | **0** | **25 suites, 254 tests: 253 passed, 1 skipped** (`NodeAgentIntegrationTest.pairObserveRevokeAgainstRealAgent`, needs a live agent), 0 failed | `build/junit/` |
| Lint (debug) | (part of qualify) | **0** | No issues found | `build/lint-debug.txt` |
| Screenshot suite | `verifyRoborazziDebug -Pscreens` (from qualify) | **0** | **9 suites, 25 tests: 25 passed**; no baseline re-recorded (docs-only change) | `screens/junit/`, `build/screens-verify.log` |
| Receipt checker (standalone JVM) | `bash gradlew -p tools/receipt-checker --no-daemon --console=plain clean test` | **0** | **14 tests: 14 passed** | `receipt-checker/` |
| Release-manifest check | `python3 scripts/test_check_manifest.py && python3 scripts/check-manifest.py <merged release manifest>` | **0** | checker self-tests 9/9 OK; `MANIFEST CHECK: PASS` | `release-manifest/` |
| Settlement program | not run: `onchain/` unchanged | n/a | n/a | n/a |

## Debug APK

- File (outside the repo): `EdgeORE-0.2.9-review-3c2b2ac1378e-debug.apk`
- sha256 `17b6209e2839162fdb72a9d9125bd10a2f41119aa9dc228358f9e5f2f807eb6b`
- size 57,631,928 bytes
- About stamp: `EdgeORE 0.2.9-review (3c2b2ac1378e) · Solana devnet only.` (`BuildConfig.GIT_COMMIT` in the APK's dex is `3c2b2ac1378e`, no `-dirty`; the APK was assembled before any evidence file existed. A later test-only recompile in the same run saw the untracked `evidence/` folder and regenerated a `-dirty` BuildConfig in `app/build/generated`, which is not in the APK.)
- versionName `0.2.9-review`, versionCode 11, Android debug key (cert sha256 `c9b4666505b1de4c34b18bb4a0c2a5795931a20e77035af2cc9e9c37b7ae4b93`)

## Other files

- `qr-images-decode-check.txt`: each `docs/device-qr/*.png` was decoded back with ZXing 3.5.3 and matched its intended payload.

Not claimed: any device behaviour. Every runbook gate, including the new 1a (introduction), 5a (live camera QR) and 5b (QR from image), is NOT_RUN.
