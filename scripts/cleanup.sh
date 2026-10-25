#!/usr/bin/env bash
set -euo pipefail
serial="${1:?Usage: cleanup.sh ADB_SERIAL}"
timeout 15 adb -s "$serial" shell am force-stop org.cattail.m30lab
timeout 30 adb -s "$serial" uninstall org.cattail.m30lab
echo "If HCI snoop was enabled, disable it in Developer options and restart Bluetooth."
