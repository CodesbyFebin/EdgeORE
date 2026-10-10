# Build gate for 8f9633dadede

JVM/build results only. No phone, wallet, owned AI host or on-device node session is part of this gate.

- Command: `./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`
- Gradle exit: EXIT=1
- Started: 2026-10-10T00:48:17Z · Finished: 2026-10-10T00:49:39Z
- Toolchain: openjdk version "17.0.20.1" 2026-08-18; gradle-8.9

## Unit tests (JUnit XML in `junit/`, parsed by attribute name)
```
suites=20 tests=187 passed=184 failures=2 errors=0 skipped=1
SKIPPED com.edgeore.app.NodeAgentIntegrationTest.pairObserveRevokeAgainstRealAgent (no reason given)
FAILED com.edgeore.app.ControlEffectsTest.everyControlIsShownOnItsScreen
FAILED com.edgeore.app.ControlEffectsTest.everySwitchAndSliderHasAnEffectNote
```

## Lint (debug)
```
no lint report (lint did not run)
```
