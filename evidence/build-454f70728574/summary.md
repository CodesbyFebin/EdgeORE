# Build gate for 454f70728574

JVM/build results only. No phone, wallet, owned AI host or on-device node session is part of this gate.

- Command: `./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:processReleaseMainManifest`
- Gradle exit: EXIT=0
- Started: 2026-10-12T01:46:52Z · Finished: 2026-10-12T01:48:17Z
- Toolchain: openjdk version "17.0.20.1" 2026-08-18; gradle-8.9

## Unit tests (JUnit XML in `junit/`, parsed by attribute name)
```
suites=31 tests=275 passed=273 failures=0 errors=0 skipped=2
SKIPPED com.edgeore.app.MiningAnimationComposeTest.frames (no reason given)
SKIPPED com.edgeore.app.NodeAgentIntegrationTest.pairObserveRevokeAgainstRealAgent (no reason given)
```

## Lint (debug)
```
No issues found.
```

## Debug APK
```
package: name='com.edgeore.app' versionCode='11' versionName='0.2.9-review' platformBuildVersionName='15' platformBuildVersionCode='35' compileSdkVersion='35' compileSdkVersionCodename='15'
sha256 2eeeff76167cac28b717c2a64bc437f3ccaa6ce5579ae26c9841932e904ee083
size   57780467 bytes
BuildConfig.GIT_COMMIT = 454f70728574 
signer c9b4666505b1de4c34b18bb4a0c2a5795931a20e77035af2cc9e9c37b7ae4b93 (Android debug key unless you configured release signing)
```

## Release manifest
```
MANIFEST CHECK: PASS (app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml)
```
check-manifest exit: EXIT=0

## Screenshot suite
verifyRoborazziDebug exit: EXIT=0
