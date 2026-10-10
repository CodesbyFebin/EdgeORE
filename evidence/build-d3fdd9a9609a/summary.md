# Build gate for d3fdd9a9609a

JVM/build results only. No phone, wallet, owned AI host or on-device node session is part of this gate.

- Command: `./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`
- Gradle exit: EXIT=0
- Started: 2026-10-10T00:50:38Z · Finished: 2026-10-10T00:52:11Z
- Toolchain: openjdk version "17.0.20.1" 2026-08-18; gradle-8.9

## Unit tests (JUnit XML in `junit/`, parsed by attribute name)
```
suites=20 tests=187 passed=186 failures=0 errors=0 skipped=1
SKIPPED com.edgeore.app.NodeAgentIntegrationTest.pairObserveRevokeAgainstRealAgent (no reason given)
```

## Lint (debug)
```
No issues found.
```

## Debug APK
```
package: name='com.edgeore.app' versionCode='10' versionName='0.2.8-review' platformBuildVersionName='15' platformBuildVersionCode='35' compileSdkVersion='35' compileSdkVersionCodename='15'
sha256 f5fe5345923581ecd80ca3913a7524e46632a475abb8ebba985bc34e18bc36e8
size   15130117 bytes
BuildConfig.GIT_COMMIT = d3fdd9a9609a 
signer c9b4666505b1de4c34b18bb4a0c2a5795931a20e77035af2cc9e9c37b7ae4b93 (Android debug key unless you configured release signing)
```

## Screenshot suite
verifyRoborazziDebug exit: EXIT=0

## Separate suites (never combined)
- Android `qualify.sh` unit tests: 187 (186 passed, 1 skipped). Baseline on main 2442ba1: 164 (163 passed, 1 skipped). New: StorageVaultTest 10 + BackupCoordinatorTest 13 = 23.
- Screenshot suite (`-Pscreens`, verifyRoborazziDebug): 19 tests, 19 passed (includes 6 new StorageRenderTest JVM renders). EXIT=0.
- Receipt checker (`tools/receipt-checker`, `gradle --no-daemon test --rerun-tasks`): 7 tests, 7 passed. EXIT=0. Log: `receipt-checker-test.log`.

## Resolved dependencies vs main 2442ba1
`deps/comparison.txt`: debugRuntimeClasspath, debugUnitTestRuntimeClasspath and debugAndroidTestRuntimeClasspath are byte-identical to main. No dependency was added or changed.

JVM only. Android Keystore, SAF providers on a phone, remote upload/pin/restore: NOT_RUN.
