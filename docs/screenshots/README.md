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
