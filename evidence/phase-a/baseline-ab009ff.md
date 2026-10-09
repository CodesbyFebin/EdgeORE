# Phase A baseline: origin/main ab009ff (before any change on fix/audit-p0)

- Command: `bash gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
- Log: `baseline-main-ab009ff-20261009T152833.log` (exit captured directly from gradle, not through tee)
- Gradle exit: EXIT=0
- Finished: 2026-10-09T15:30:33Z
- JDK: openjdk version "17.0.20.1" 2026-08-18
- Gradle: 8.9 (wrapper distribution from services.gradle.org, cached in GRADLE_USER_HOME); AGP 8.7.3; Kotlin 2.0.21
- Host OS: Linux 6.12.94+

## JUnit XML (parsed by attribute name with scripts/junit-summary.py)
```
suites=8 tests=63 passed=62 failures=0 errors=0 skipped=1
SKIPPED com.edgeore.app.NodeAgentIntegrationTest.pairObserveRevokeAgainstRealAgent (no reason given)
```

## Lint (debug)
```
No issues found.
```

## Debug APK from ab009ff
```
package: name='com.edgeore.app' versionCode='8' versionName='0.2.6-review' platformBuildVersionName='15' platformBuildVersionCode='35' compileSdkVersion='35' compileSdkVersionCodename='15'
sha256 1a851895116a6eef61e575d8971df20ed8df06c25b5eb5106d73f67195a1e7ab  size 14966159
```

This replaces nothing: earlier counts in evidence/unit-tests.txt are historical (58 passed + 1 skipped at 7433eee).
