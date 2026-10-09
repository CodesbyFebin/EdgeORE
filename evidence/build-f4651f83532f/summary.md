# Build gate for f4651f83532f

JVM/build results only. No phone, wallet, owned AI host or on-device node session is part of this gate.

- Command: `./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`
- Gradle exit: EXIT=0
- Started: 2026-10-09T22:49:07Z · Finished: 2026-10-09T22:50:29Z
- Toolchain: openjdk version "17.0.20.1" 2026-08-18; gradle-8.9

## Unit tests (JUnit XML in `junit/`, parsed by attribute name)
```
suites=19 tests=175 passed=174 failures=0 errors=0 skipped=1
SKIPPED com.edgeore.app.NodeAgentIntegrationTest.pairObserveRevokeAgainstRealAgent (no reason given)
```

## Lint (debug)
```
No issues found.
```

## Debug APK
```
package: name='com.edgeore.app' versionCode='10' versionName='0.2.8-review' platformBuildVersionName='15' platformBuildVersionCode='35' compileSdkVersion='35' compileSdkVersionCodename='15'
sha256 8ad3a2bc62ebec004a28a4f60d8f3afb976c72687fbbfb99bdddaa5f4e720f60
size   56742870 bytes
BuildConfig.GIT_COMMIT = f4651f83532f 
signer c9b4666505b1de4c34b18bb4a0c2a5795931a20e77035af2cc9e9c37b7ae4b93 (Android debug key unless you configured release signing)
```
