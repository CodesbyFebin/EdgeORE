# Contribution scheduler: evidence for daab56aded93

Branch `feature/contribution-scheduler`, based on `origin/main` 2442ba1. The tested commit is `daab56aded93`. The evidence commit on top of it changes no app source.
**These are JVM and build results only. No phone, emulator or Android JobScheduler run is part of this folder.**

## What was built
- An opt-in scheduler, **off by default** (`ResourceSettings.contributionOptIn = false`). It schedules a unique periodic WorkManager job (`edgeore.contribution`, 15 min) only while the user has opted in **and** Edge Mode is resumed. Pausing Edge Mode or opting out cancels the job. That includes "Pause compute during chat", which goes through `updateSettings`.
- Android constraints: `NetworkType.UNMETERED`, `setRequiresCharging(true)`, `setRequiresBatteryNotLow(true)`.
- When Android starts the job, it re-reads the settings and re-checks pause and the existing `EdgePolicy` gates (charge-only, thermal, battery reserve) and the qualification gate. `EdgePolicy.QUALIFIED_WORKLOAD_AVAILABLE = false` and `ContributionPolicy.REGISTERED_WORKLOADS = 0`, so it records "No qualified workload. Nothing ran." and exits. It does no AI training and no compute, and it reports no progress, measurement or reward.
- The Mine screen gets a "Contribution scheduler" card. It shows one state: Not scheduled, Paused by you, Waiting for Wi-Fi, Waiting for charger, Waiting for battery, Paused by policy, No qualified workload, or Scheduler unavailable. It also shows each constraint as met, not met or not observed, and the last check outcome. A new `Control.CONTRIBUTION_SCHEDULER` (Enforced) is shown with its effect note.

## Commands and results (separate runs, never combined)
| Run | Command | Exit | Counts |
|---|---|---|---|
| Android gate | `SCREENS=1 bash scripts/qualify.sh` on clean daab56aded93 | Gradle `EXIT=0`. Script exit **1**, from the screenshot step only | Unit **184 tests: 183 passed, 0 failed, 1 skipped** (`NodeAgentIntegrationTest`, as on main). That is 164 + 20 new: `ContributionPolicyTest` 11 and `ContributionWorkTest` 9. Lint: no issues. Logs: `evidence/build-daab56aded93/` |
| Screenshot suite | `./gradlew :app:verifyRoborazziDebug -Pscreens` (inside qualify) | 1 | **13 tests: 11 passed, 2 failed: `ScreenshotTest.review`, `FullScreenshotTest.review`.** These two already fail on main 2442ba1 (see `evidence/wallet-authorization/3676094ad487` on `fix/mwa-authorization`, which has byte-identical 06-review renders), so the failure is pre-existing and not hidden. Both Mine screenshots pass: `full/01-mine-full.png` was re-recorded in `cfed139` for the new card, and `01-mine.png` was unchanged. (`screens/`) |
| Receipt checker (standalone) | `bash gradlew -p tools/receipt-checker --no-daemon test installDist` | 0 | **7 tests: 7 passed** (`receipt-checker/`) |
| Resolved dependencies | `./gradlew -q :app:dependencies --configuration <c>` on main 2442ba1 and on daab56aded93, compared with `deps/compare-resolved.py` | 0 | **0 existing versions changed, 0 removed**, in debug, release, unitTest and androidTest runtime classpaths (`deps/COMPARISON.txt`) |

### Earlier gate attempt (no result claimed)
The first `qualify.sh` run on `cfed139` hung in `TransportHardeningTest.nodeRedirectIsRefusedNotFollowed`, so it was killed. The thread dump showed `ConscryptEngineSocket.doHandshake`. Cause: `ContributionWorkTest` was the first Robolectric test in the default unit run, and Robolectric installed Conscrypt as the JVM-wide TLS provider. Fixed in `daab56a` with `@ConscryptMode(OFF)` on that class. The two classes were then run together (22/22 passed) before this gate.

## Dependency added
- `androidx.work:work-runtime-ktx:2.10.0` (implementation) and `androidx.work:work-testing:2.10.0` (testImplementation only).
- Why 2.10.0: it compiles against compileSdk 35 / AGP 8.7.3 / Kotlin 2.0.21. Its transitive requirements (lifecycle ≤ 2.8.7, coroutines ≤ 1.9.0, core ≤ 1.15.0) are already met, so it only **adds** modules: room 2.6.1, sqlite 2.4.0, lifecycle-livedata/service 2.8.7 (same version as the existing lifecycle), tracing-ktx 1.2.0, concurrent-futures-ktx 1.1.0, and kotlin-stdlib-jdk7/jdk8 1.8.22 (empty compatibility artifacts since Kotlin 1.8). 2.9.1 was also checked and also changes nothing, but 2.10.0 is the newer line.

## APK
- Box path `/workspace/EdgeORE-daab56aded93-scheduler-debug.apk` (copy of the gate's `app-debug.apk`). sha256 `9d857aadb77962a8e1963202c79323b9850f68431342a79f0f236ec6a572a6bd`, 15,526,588 bytes. `BuildConfig.GIT_COMMIT = daab56aded93` (About). Debug signer `c9b46665…b7ae4b93`.

## Tests (test doubles / Robolectric only; not a device run)
- `ContributionPolicyTest` (pure JVM) covers:
  - off by default
  - user pause wins over everything
  - Wi-Fi unmet or not observed shows "Waiting for Wi-Fi"
  - charger unmet or not observed shows "Waiting for charger"
  - battery low shows "Waiting for battery"
  - existing safety gates still apply
  - the qualification gate means nothing runs, and no reachable state is "Checking"
  - scheduler unavailable or not found is reported
  - job outcomes never claim work
  - "not observed" wording
  - the control is labelled Enforced, and the copy contains no reward/earn/APR/boost/zero-knowledge wording
- `ContributionWorkTest` (Robolectric API 28 + WorkManager `TestDriver`) covers:
  - the request carries all three constraints
  - the worker skips when not opted in, when paused, and when a gate blocks
  - with all gates met, it reports no qualified workload
  - the job does not run until the constraints are met, then only records the outcome and is re-enqueued
  - opt-out or pause cancels the job
  - the worker cancels its own job if the user paused meanwhile
  - settings round-trip

## NOT_RUN
Real Android JobScheduler or WorkManager on a phone or emulator, real Wi-Fi/charger transitions, Doze/standby behaviour, and the scheduler card on a device.
