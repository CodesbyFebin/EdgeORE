# Build gate for 84a788cbd926

JVM/build results only. No phone, wallet, owned AI host or on-device node session is part of this gate.

- Command: `./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:processReleaseMainManifest`
- Gradle exit: EXIT=0
- Started: 2026-10-12T05:16:22Z · Finished: 2026-10-12T05:18:03Z
- Toolchain: openjdk version "17.0.20.1" 2026-08-18; gradle-8.9

## Unit tests (JUnit XML in `junit/`, parsed by attribute name)
```
suites=42 tests=330 passed=325 failures=0 errors=0 skipped=5
SKIPPED com.edgeore.app.ContributionSessionE2ETest.realSessionOnOwnedNodeAndGatewayThenCancelledSession (no reason given)
SKIPPED com.edgeore.app.GatewayProxyIntegrationTest.uploadObserveDownloadQuotaAuthThroughHttpsProxy (no reason given)
SKIPPED com.edgeore.app.MiningAnimationComposeTest.frames (no reason given)
SKIPPED com.edgeore.app.NodeAgentIntegrationTest.pairObserveRevokeAgainstRealAgent (no reason given)
SKIPPED com.edgeore.app.OrePreviewFrames.frames (no reason given)
```

## Lint (debug)
```
No issues found.
```

## Debug APK
```
package: name='com.edgeore.app' versionCode='11' versionName='0.2.9-review' platformBuildVersionName='15' platformBuildVersionCode='35' compileSdkVersion='35' compileSdkVersionCodename='15'
sha256 9a101821892e5946c9aac32f68988ebd2ecb050fe6c5eb500db5feb1156f5222
size   57927923 bytes
BuildConfig.GIT_COMMIT = 84a788cbd926 
signer c9b4666505b1de4c34b18bb4a0c2a5795931a20e77035af2cc9e9c37b7ae4b93 (Android debug key unless you configured release signing)
```

## Release manifest
```
MANIFEST CHECK: PASS (app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml)
```
check-manifest exit: EXIT=0

## Screenshot suite
verifyRoborazziDebug exit: EXIT=0
