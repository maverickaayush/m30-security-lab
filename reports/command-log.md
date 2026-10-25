# Command and tool log

Recorded 2026-10-26 (Asia/Kolkata). The ADB serial is intentionally omitted
from tracked documentation. Full raw logs and screenshots remain ignored under
`evidence/`.

| Command or check | Result / exit code |
| --- | --- |
| `adb devices -l` | One handset; initially `unauthorized`, then `device` after the user accepted the debugging prompt |
| `adb shell getprop` / targeted `uname`, `getenforce`, `df`, `dumpsys battery` | Baseline captured; see `reports/device-capabilities.md`; exit 0 |
| `adb shell su -c 'zcat /proc/config.gz'` targeted SCSC/BT read | Exit 0; `CONFIG_SCSC_BT=y`, `CONFIG_BT` unset, SCSC BlueZ path unset |
| Targeted `/vendor` library pulls, `sha256sum`, `readelf`, `strings` | Exit 0; hashes and transport strings recorded in `research/hardware-reverse-engineering.md` |
| `adb shell dumpsys bluetooth_manager` and package feature queries | Exit 0; Android framework Bluetooth service and BLE feature present |
| Host `adb version` | Android Debug Bridge 34.0.5 |
| Host `bluetoothctl --version` | BlueZ 5.85 |
| Host `wireshark --version` | Wireshark 4.6.4 |
| Host `git --version` | Git 2.53.0 |
| `./scripts/bootstrap-toolchain.sh` | Exit 0; Gradle 8.10.2, JDK 21.0.13, Android platform/build tools 35 installed under ignored project-local toolchain |
| Gradle `testDebugUnitTest` | Exit 0; parser malformed-input and JSON/CSV export tests passed |
| `./scripts/build.sh` | Exit 0; latest debug APK SHA-256 `dad854a9290f21cebd204bca8fe9c8f9dcf6f8c48c74a80840dea6e1f9fbae0d` |
| `./scripts/install.sh <serial>` | Exit 0; `Success`; package `org.cattail.m30lab` installed with project signing key |
| App scanner / advertiser / GATT fixture runs | See `reports/test-results.md`; all bounded deadlines and callback logs captured |
| Noise package paths, `dumpsys package`, APK pull, `aapt dump`, `dexdump` | Exit 0; static result in `research/noise-firmware.md` |

The host fixture and receiver use bounded timeouts. ADB helper commands use
explicit `timeout` wrappers where a device or UI operation could hang.
