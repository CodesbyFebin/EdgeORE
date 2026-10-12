#!/usr/bin/env bash
# Records an operator-driven tour; never signs, submits, mines, or changes app data.
set -euo pipefail
apk=${1:?Usage: record-device.sh APK OUTPUT_DIR [ADB_SERIAL]}
out=${2:?Output directory required}
serial=${3:-}
[ -f "$apk" ] || { echo 'APK not found' >&2; exit 1; }
mkdir -p "$out"
adb_args=()
[ -z "$serial" ] || adb_args=(-s "$serial")
adb "${adb_args[@]}" get-state > "$out/adb-state.txt"
sha256sum "$apk" > "$out/apk-sha256.txt"
adb "${adb_args[@]}" shell getprop ro.build.fingerprint > "$out/device-fingerprint.txt"
adb "${adb_args[@]}" shell getprop ro.build.version.sdk > "$out/android-api.txt"
adb "${adb_args[@]}" install -r "$apk" > "$out/install.txt"
adb "${adb_args[@]}" shell am start -n com.edgeore.app/.MainActivity > "$out/launch.txt"
printf '%s\n' 'Recording 180 seconds. Walk the app on the device; no actions are automated.'
adb "${adb_args[@]}" shell screenrecord --bit-rate 4000000 --time-limit 180 /sdcard/edgeore-review.mp4
adb "${adb_args[@]}" pull /sdcard/edgeore-review.mp4 "$out/edgeore-review.mp4"
sha256sum "$out/edgeore-review.mp4" > "$out/video-sha256.txt"
