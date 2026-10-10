# Build gate for daab56aded93

JVM/build results only. No phone, wallet, owned AI host or on-device node session is part of this gate.

- Command: `./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`
- Gradle exit: EXIT=0
- Started: 2026-10-10T10:07:19Z · Finished: 2026-10-10T10:08:37Z
- Toolchain: openjdk version "17.0.20.1" 2026-08-18; gradle-8.9

## Unit tests (JUnit XML in `junit/`, parsed by attribute name)
```
suites=20 tests=184 passed=183 failures=0 errors=0 skipped=1
SKIPPED com.edgeore.app.NodeAgentIntegrationTest.pairObserveRevokeAgainstRealAgent (no reason given)
```

## Lint (debug)
```
No issues found.
```

## Debug APK
```
package: name='com.edgeore.app' versionCode='10' versionName='0.2.8-review' platformBuildVersionName='15' platformBuildVersionCode='35' compileSdkVersion='35' compileSdkVersionCodename='15'
sha256 9d857aadb77962a8e1963202c79323b9850f68431342a79f0f236ec6a572a6bd
size   15526588 bytes
BuildConfig.GIT_COMMIT = daab56aded93 
signer c9b4666505b1de4c34b18bb4a0c2a5795931a20e77035af2cc9e9c37b7ae4b93 (Android debug key unless you configured release signing)
```

## Screenshot suite
verifyRoborazziDebug exit: EXIT=1
