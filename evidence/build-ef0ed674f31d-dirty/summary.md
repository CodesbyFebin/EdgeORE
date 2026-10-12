# Build gate for ef0ed674f31d-dirty

JVM/build results only. No phone, wallet, owned AI host or on-device node session is part of this gate.

- Command: `./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:processReleaseMainManifest`
- Gradle exit: EXIT=0
- Started: 2026-10-12T11:10:35Z · Finished: 2026-10-12T11:12:17Z
- Toolchain: openjdk version "17.0.20.1" 2026-08-18; gradle-8.9

## Unit tests (JUnit XML in `junit/`, parsed by attribute name)
```
suites=46 tests=361 passed=354 failures=0 errors=0 skipped=7
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
/workspace/EdgeORE/app/src/main/java/com/edgeore/app/bot/OreBot.kt:58: Warning: Unnecessary; SDK_INT is always >= 26 [ObsoleteSdkInt]
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "ORE Bot reminders", NotificationManager.IMPORTANCE_DEFAULT))
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~

   Explanation for issues of type "ObsoleteSdkInt":
```

## Debug APK
```
package: name='com.edgeore.app' versionCode='12' versionName='0.1.0-clockin' platformBuildVersionName='15' platformBuildVersionCode='35' compileSdkVersion='35' compileSdkVersionCodename='15'
sha256 be3a365910b502be5213f5c83add1578e0feb01a113f2a8d1b27cbaa7b9c2cfb
size   58207742 bytes
BuildConfig.GIT_COMMIT = ef0ed674f31d-dirty 
signer c9b4666505b1de4c34b18bb4a0c2a5795931a20e77035af2cc9e9c37b7ae4b93 (Android debug key unless you configured release signing)
```

## Release manifest
```
MANIFEST CHECK: PASS (app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml)
```
check-manifest exit: EXIT=0
