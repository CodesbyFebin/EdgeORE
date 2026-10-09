# Acceptance matrix

| Gate | Status | Evidence |
|---|---|---|
| P0 rebuild of 3929edb | PASS for compile and certificate. Outer hash differs by the git stamp only. | docs/qualification-report.md |
| P1 phone | NOT_RUN | No adb device |
| P2 devnet transfer | NOT_RUN | No wallet |
| P3 workload | NOT_RUN on device. Local rule: no qualified workload, so resume is not Active. | WorkloadGateTest |
| P4 AI session | NOT_RUN. On-device BLOCKED. | InferenceClaimTest labels only |
| P5 node | NOT_RUN | Live test skipped |
| P6 vault on phone | NOT_RUN | |
| P7 real receipt | NOT_RUN. False chain claims without a signature are rejected in code. | FalseBroadcastTest |
| P8 settings as its own screen | NOT_STARTED | Controls remain on the existing screens |
| P9 ORE and the rest | NOT_STARTED | Left unavailable |
| P10 full-scope release | NO-GO | Frozen APK has no phone session |

Local unit run after these edits: `./gradlew :app:testDebugUnitTest` exited 0 (`BUILD SUCCESSFUL in 47s`). JUnit XML: 80 tests, 0 failures, 1 skipped live-node test. Gradle still logged the worker socket error and then exited 0. This run is not a phone session, and it does not cover the frozen APK.
