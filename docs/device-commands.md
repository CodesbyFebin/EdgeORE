# Device commands

> **Historical (0.2.6-review).** This page describes the frozen `0.2.6-review` candidate and is kept for the record. The current candidate is `0.2.7-review`; see [qualification-status.md](qualification-status.md).

Run these on a machine that has the frozen APK and a phone. Do not substitute a debug build.

Confirm the installed certificate matches `5baab0630958af7a3a5faabc3f3525f8fd5e2bb6a1b98e19bd6e16b1f6e834a1` before uninstalling. A different certificate cannot upgrade in place.

```bash
apksigner verify --print-certs com.edgeore.app-0.2.6-review.apk
adb install -r com.edgeore.app-0.2.6-review.apk
adb shell am start -n com.edgeore.app/.MainActivity
adb exec-out screencap -p > cold-launch.png
adb shell am force-stop com.edgeore.app
adb shell am start -n com.edgeore.app/.MainActivity
```

The APK is not in git. The local rebuild that produced `d64d5b92f28ac8856a9904528f464125a3b6333a9b52e1f7b0e622e46196dd0b` was built from commit `3929edb`.

These commands have not been run here. No device was attached.
