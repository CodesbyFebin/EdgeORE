# Build gate for d675002bd701

JVM/build results only. No phone, wallet, owned AI host or on-device node session is part of this gate.

- Command: `./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`
- Gradle exit: EXIT=0
- Started: 2026-10-09T18:09:12Z · Finished: 2026-10-09T18:10:45Z
- Toolchain: openjdk version "17.0.20.1" 2026-08-18; gradle-8.9

## Unit tests (JUnit XML in `junit/`, parsed by attribute name)
```
suites=18 tests=164 passed=163 failures=0 errors=0 skipped=1
SKIPPED com.edgeore.app.NodeAgentIntegrationTest.pairObserveRevokeAgainstRealAgent (no reason given)
```

## Lint (debug)
```
No issues found.
```

## Debug APK
```
package: name='com.edgeore.app' versionCode='10' versionName='0.2.8-review' platformBuildVersionName='15' platformBuildVersionCode='35' compileSdkVersion='35' compileSdkVersionCodename='15'
sha256 6ee17a3cac0c363b130caa2523118e068b3449f9f1d1ac49b449f3dae3f6a4e6
size   15080965 bytes
BuildConfig.GIT_COMMIT = d675002bd701 
signer c9b4666505b1de4c34b18bb4a0c2a5795931a20e77035af2cc9e9c37b7ae4b93 (Android debug key unless you configured release signing)
```

## Live node agent
scripts/node-agent-it.sh exit: EXIT=0
