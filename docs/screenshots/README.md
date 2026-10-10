# Screenshots (generated, not hand-made)

These images are rendered from this repository's source by `app/src/test/java/com/edgeore/app/screens/ScreenshotTest.kt`
(Robolectric 4.14.1 + Roborazzi 1.36.0, API 28, Pixel 7 qualifiers; `full/` uses a 411×4200 dp viewport so every control is visible).

```
source /path/to/android-env   # JDK 17 + Android SDK 35
./gradlew --no-daemon :app:recordRoborazziDebug -Pscreens   # rewrite these files
./gradlew --no-daemon :app:verifyRoborazziDebug -Pscreens   # fail if the UI no longer matches
```

The suite runs only with `-Pscreens`; the normal `testDebugUnitTest` excludes it.

What these images are and are not:
- They show the app in its **fresh-install state** on the JVM: no wallet, no node, no AI host, no receipts.
- On API 28 the Android thermal API does not exist, so thermal status renders as *not observed*; battery is *not observed*.
- The CPU percentage on the Storage page comes from the **build machine's** `/proc/stat` through Robolectric, not from a phone.
- They are **not device screenshots**. Phone qualification is still NOT_RUN (see `docs/known-limitations.md`).

## `storage/` — Storage page JVM renders (feature/storage-vault)

`StorageRenderTest` renders only the Storage vault section. Labelled `jvm-render-*` because they are **JVM renders, not device screenshots**:
the two listed files are real EOV2 objects encrypted into a temporary vault by a **software** AES key on the build machine
(Robolectric has no Android Keystore), remote backup is the shipped `NotConfiguredBackupProvider`, and device free space is not
observable in that harness, so it reads *Not observed*. In the full-app renders (`03-storage*.png`) Robolectric's own storage stub reports 0 B.

## Re-record log (integration/0.2.9-candidate)

Baselines are re-recorded only when the screen change is intentional and traced to a source commit:

| Baseline | Why it changed | Source change |
|---|---|---|
| `06-review.png`, `full/06-review-full.png` | Review copy for the expiry policy changed on `main` without a re-record, so `verifyRoborazziDebug` failed on `main` itself. Re-recorded in `8f9633d` (from `feature/storage-vault`). | `cc31714` `ReviewScreen.kt` (expiry-policy wording) |
| `02-ai.png`, `full/02-ai-full.png` | The AI page gained the on-device LiteRT-LM section (allowlist, download state); `feature/ai-edge-gallery` did not run the screenshot suite. | `4f665e5` `AiScreen.kt` |

All other baselines verified unchanged after the merges. A record run also rewrote `full/03-storage-full.png` with a sub-threshold difference; that file was restored because `verifyRoborazziDebug` already passes against the committed baseline.

### Re-record: build-machine-independent readings (integration/0.2.9-candidate, round 3)

The screenshot suite now installs `FixedDeviceReadings` (a test rule ordered before the Compose rule) through the test-only seam `DeviceResourcesReader.testSource`. Every device reading in a render is "not observed", so the CPU, disk I/O, storage and since-boot lines no longer carry the build machine's `/proc` counters. Nothing is invented: the screen shows exactly what it shows on a device that reports nothing. `03-storage.png` and `full/03-storage-full.png` were re-recorded for this; the per-shot 0.3% threshold on the tall Storage shot is gone and every shot compares at 0.1%. `storageRenderUsesNoHostReadings` asserts the fixture text is what is rendered. Two consecutive `verifyRoborazziDebug -Pscreens --rerun-tasks` runs passed against the new baselines.
