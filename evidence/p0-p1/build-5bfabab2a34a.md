# Build evidence for 5bfabab2a34a (fix/audit-p0, 0.2.7-review)

All values below were produced on the build box from a clean tree at commit `5bfabab2a34a`.
They are JVM/build results only. No phone, wallet, owned AI host or on-device node session was run.

- Command: `bash gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
- Log: `build-5bfabab2a34a.log` · Gradle exit: EXIT=0
- Finished: 2026-10-09T15:41:17Z
- Toolchain: openjdk version "17.0.20.1" 2026-08-18; Gradle 8.9; AGP 8.7.3; Kotlin 2.0.21; build-tools 35.0.0

## Unit tests (JUnit XML in `junit-5bfabab2a34a/`, parsed by attribute name)
```
suites=16 tests=143 passed=142 failures=0 errors=0 skipped=1
SKIPPED com.edgeore.app.NodeAgentIntegrationTest.pairObserveRevokeAgainstRealAgent (no reason given)
```
The skipped test needs a live DeProof node agent (`EDGEORE_NODE_IT`); its capability stays unqualified here.

## Lint (debug)
```
No issues found.
```

## Debug APK
```
package: name='com.edgeore.app' versionCode='9' versionName='0.2.7-review' platformBuildVersionName='15' platformBuildVersionCode='35' compileSdkVersion='35' compileSdkVersionCodename='15'
sha256 adef056ed42563a9117966b9a160db3cf99a39b88c6fe37e25f46b32cb5559a7
size   15064522 bytes
BuildConfig.GIT_COMMIT = 5bfabab2a34a
signer c9b4666505b1de4c34b18bb4a0c2a5795931a20e77035af2cc9e9c37b7ae4b93 (Android debug key, not a release certificate)
```
