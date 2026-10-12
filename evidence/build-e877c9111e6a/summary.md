# Build gate for e877c9111e6a

JVM/build results only. No phone, wallet, owned AI host or on-device node session is part of this gate.

- Command: `./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:processReleaseMainManifest`
- Gradle exit: EXIT=0
- Started: 2026-10-12T09:01:51Z · Finished: 2026-10-12T09:03:30Z
- Toolchain: openjdk version "17.0.20.1" 2026-08-18; gradle-8.9

## Unit tests (JUnit XML in `junit/`, parsed by attribute name)
```
suites=45 tests=355 passed=348 failures=0 errors=0 skipped=7
SKIPPED com.edgeore.app.ContributionSessionE2ETest.realSessionOnOwnedNodeAndGatewayThenCancelledSession (no reason given)
SKIPPED com.edgeore.app.GatewayProxyIntegrationTest.uploadObserveDownloadQuotaAuthThroughHttpsProxy (no reason given)
SKIPPED com.edgeore.app.MineDeployCardTest.minePreview (no reason given)
SKIPPED com.edgeore.app.MiningAnimationComposeTest.frames (no reason given)
SKIPPED com.edgeore.app.NodeAgentIntegrationTest.pairObserveRevokeAgainstRealAgent (no reason given)
SKIPPED com.edgeore.app.OreHomeStripTest.homePreview (no reason given)
SKIPPED com.edgeore.app.OrePreviewFrames.frames (no reason given)
```

## Lint (debug)
```
No issues found.
```

## Debug APK
```
package: name='com.edgeore.app' versionCode='11' versionName='0.2.9-review' platformBuildVersionName='15' platformBuildVersionCode='35' compileSdkVersion='35' compileSdkVersionCodename='15'
sha256 02ec0969266df4d995bde7c1938319109c52baaf7a40009faf529fc7a99a2bf3
size   58125790 bytes
BuildConfig.GIT_COMMIT = e877c9111e6a 
signer c9b4666505b1de4c34b18bb4a0c2a5795931a20e77035af2cc9e9c37b7ae4b93 (Android debug key unless you configured release signing)
```

## Release manifest
```
MANIFEST CHECK: PASS (app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml)
```
check-manifest exit: EXIT=0
