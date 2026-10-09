# Build gate for f6907e75941f

JVM/build results only. No phone, wallet, owned AI host or on-device node session is part of this gate.

- Command: `./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`
- Gradle exit: EXIT=0
- Started: 2026-10-09T15:57:42Z · Finished: 2026-10-09T15:59:10Z
- Toolchain: openjdk version "17.0.20.1" 2026-08-18; gradle-8.9

## Unit tests (JUnit XML in `junit/`, parsed by attribute name)
```
suites=17 tests=148 passed=147 failures=0 errors=0 skipped=1
SKIPPED com.edgeore.app.NodeAgentIntegrationTest.pairObserveRevokeAgainstRealAgent (no reason given)
```

## Lint (debug)
```
No issues found.
```

## Debug APK
```
package: name='com.edgeore.app' versionCode='9' versionName='0.2.7-review' platformBuildVersionName='15' platformBuildVersionCode='35' compileSdkVersion='35' compileSdkVersionCodename='15'
sha256 8fe0880b5106616973b823813e09d4e5f2ea2c99ab51854cb4a8260311d896ff
size   15064581 bytes
BuildConfig.GIT_COMMIT = f6907e75941f 
signer c9b4666505b1de4c34b18bb4a0c2a5795931a20e77035af2cc9e9c37b7ae4b93 (Android debug key unless you configured release signing)
```

## Screenshot suite
verifyRoborazziDebug exit: EXIT=0

## Live node agent
scripts/node-agent-it.sh exit: EXIT=0
