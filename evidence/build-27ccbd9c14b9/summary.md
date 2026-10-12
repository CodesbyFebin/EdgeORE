# Build gate for 27ccbd9c14b9

JVM/build results only. No phone, wallet, owned AI host or on-device node session is part of this gate.

- Command: `./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:processReleaseMainManifest`
- Gradle exit: EXIT=0
- Started: 2026-10-12T13:18:02Z · Finished: 2026-10-12T13:20:02Z
- Toolchain: openjdk version "17.0.20.1" 2026-08-18; gradle-8.9

## Unit tests (JUnit XML in `junit/`, parsed by attribute name)
```
suites=52 tests=378 passed=371 failures=0 errors=0 skipped=7
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
sha256 38471b507551f80130b76799afeb045f8a47adf20f3538816ac0decb4c7844a0
size   59457715 bytes
BuildConfig.GIT_COMMIT = 27ccbd9c14b9 
```

## Release manifest
```
MANIFEST CHECK: PASS (app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml)
```
check-manifest exit: EXIT=0
