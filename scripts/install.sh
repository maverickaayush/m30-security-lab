#!/usr/bin/env bash
set -euo pipefail
base="$(cd "$(dirname "$0")/.." && pwd)"
serial="${1:?Usage: install.sh ADB_SERIAL}"
apk="$base/android/app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$apk" ]] || { echo "Build the APK first" >&2; exit 1; }
count="$(timeout 8 adb devices | awk '$2=="device" {n++} END {print n+0}')"
[[ "$count" == 1 ]] || { echo "Expected exactly one authorized device; found $count" >&2; exit 1; }
timeout 10 adb -s "$serial" shell getprop ro.product.device
existing="$(timeout 10 adb -s "$serial" shell pm path org.cattail.m30lab || true)"
if [[ -n "$existing" ]]; then echo "Project package already present: $existing"; fi
timeout 45 adb -s "$serial" install -r "$apk"
