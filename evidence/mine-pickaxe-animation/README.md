# Gate evidence: Mine rock-and-pickaxe art (454f707)

Build host: EdgeORE box, JDK 17.0.20.1, Gradle 8.9, Android SDK 35. JVM/build results only: no phone, wallet,
owned AI host or node session. Runs on 2026-10-12 (IST).

| Gate | Result |
|---|---|
| `SCREENS=1 scripts/qualify.sh` (clean unit tests, lint, debug APK, androidTest APK, release manifest, screenshot verify) | exit 0; see `evidence/build-454f70728574/summary.md` |
| Android unit tests | 31 suites, 275 tests, 273 passed, 0 failures, 0 errors, 2 skipped (`MiningAnimationComposeTest.frames`, preview-only, runs when `MINE_FRAMES_OUT` is set; `NodeAgentIntegrationTest`, needs a live agent). Base 421508a: 29 suites, 265 tests, 264 passed, 1 skipped. |
| Lint (debug) | No issues found |
| Release manifest (`check-manifest.py`) | `MANIFEST CHECK: PASS`, exit 0 |
| Screenshot suite (`verifyRoborazziDebug -Pscreens`) | exit 0 against the re-recorded `full/01-mine-full.png` |
| Receipt checker (`bash gradlew -p tools/receipt-checker --no-daemon test installDist`, counted separately) | exit 0; 1 suite, 14 tests, 14 passed |

The receipt checker still only *verifies the integrity of signed receipt contents*; this change does not touch it.

## Debug APK

| | Base `9c1a9bc` (feature/agentic-redesign) | This commit `454f707` |
|---|---|---|
| sha256 | `7cd28c23641fe4b9a38b306d9522db4831a5b7101133969e25d93745aaab1c1a` | `2eeeff76167cac28b717c2a64bc437f3ccaa6ce5579ae26c9841932e904ee083` |
| size | 57,681,080 bytes | 57,780,467 bytes |
| delta | | **+99,387 bytes** (the three WebP drawables are 98,442 bytes) |

Both are debug builds signed with the build host's Android debug key; the base was built from a detached worktree of
`9c1a9bc` with `./gradlew :app:assembleDebug`. Not a release artifact; nothing was published.

## Not done here

No phone or emulator run of the animation (frame rate, battery, TalkBack on a device). The preview frames are Robolectric
renders of the real composable at fixed phases, not a device recording.
