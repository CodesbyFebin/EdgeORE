# Gates for fc7f549b9946 (Start Mining contribution session)

Build host: EdgeORE box (loopback only). JVM/Gradle/Python results only; phones and emulators: NOT_RUN.
Each suite is run and counted separately; counts are never summed.

| Gate | Result |
|---|---|
| `SCREENS=1 scripts/qualify.sh` | exit 0; `summary.md` here. Unit: 37 suites, 310 tests, 306 passed, 0 failed, 4 skipped (three need a live agent/gateway, one is preview-only). Lint: no issues. Release manifest: PASS. `verifyRoborazziDebug`: exit 0 |
| Receipt checker (`bash gradlew -p tools/receipt-checker --no-daemon clean test installDist`) | exit 0; 14/14 |
| storage-gateway (`python3 -m unittest discover`) | 17/17 (13 server + 4 OpenAPI contract) |
| edgeore-kit | 22/22 |
| Build policy (`scripts/build-policy.py`) | PASS; policy tests 12/12 |
| Release manifest self-tests (`scripts/test_check_manifest.py`) | 9/9 |
| End-to-end session (`scripts/session-e2e.sh evidence/contribution-session/e2e`) | exit 0; ContributionSessionE2ETest 1/1, not skipped. See `evidence/contribution-session/e2e/summary.txt` |

Debug APK: sha256 `fb94b0f638f9a016f6fb7486f4d53be36529366fa897a740f02a53d0d52f65c3`, 57,862,387 bytes, GIT_COMMIT fc7f549b9946,
signed with the build host's Android debug key. Not a release artifact; nothing published.

Preview (Robolectric renders of the real `MiningSessionPanel`; the active frames use `evidence/contribution-session/e2e/active-snapshot.json`):
`docs/design/preview/mine-session.gif` (idle with Start Mining held 2 s, then the ACTIVE swing), `mine-session-idle.png`, `mine-session-active.png`.
Logs of the separate gates: `separate-gates/`.
