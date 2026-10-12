# Build gate for 6a604b9a8f58

JVM/build results only. No phone, wallet, owned AI host or on-device node session is part of this gate.

- Command: `./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:processReleaseMainManifest`
- Gradle exit: EXIT=0
- Started: 2026-10-12T11:53:39Z · Finished: 2026-10-12T11:56:00Z
- Toolchain: openjdk version "17.0.20.1" 2026-08-18; gradle-8.9

## Unit tests (JUnit XML in `junit/`, parsed by attribute name)
```
suites=49 tests=368 passed=361 failures=0 errors=0 skipped=7
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
sha256 08f5de784ccc9b165b5806a9b5b371db4e2a426c93303831e28d543110b853ce
size   59407922 bytes
BuildConfig.GIT_COMMIT = 6a604b9a8f58 
```

## Release manifest
```
MANIFEST CHECK: PASS (app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml)
```
check-manifest exit: EXIT=0
