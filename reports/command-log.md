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
| `python3 host/lab_popup_receiver.py --seconds 35` with phone LAB text `PING` | Exit 0; matched one marker, called `notify-send`, popup exit 0, stopped at 35 seconds |
| `hciconfig hci0 version`; `bluetoothctl show` | Exit 0; external Realtek USB controller, HCI 5.3, BlueZ powered with zero active advertising instances |
| `dpkg-query` / `command -v` for BlueZ tools; Android `dumpsys usb` | Host BlueZ 5.85, `btmon`, `btmgmt`, and Wireshark present; phone USB host mode inactive with zero attached USB devices |
| Targeted kernel config, `/dev/scsc_h4_0` ownership, and HAL file descriptors | `CONFIG_BT`/`CONFIG_USB_OTG` unset; HAL PID owns `/dev/scsc_h4_0` at fd 6; SELinux enforcing; no second-process access attempted |
| Family `scsc_bt` Kconfig/Makefile/bridge and M30 defconfig read | Source-supported internal HCI path found; config fragment created; exact firmware source and build toolchain unavailable, so no kernel artifact was installed |
| `git apply --check research/kernel/scsc_bluez-send-lifetime.patch` against family `scsc_bluez.c` | Exit 0; patch applies to source revision `0952065`; compilation and runtime remain untested |
| Android SDK `dexdump` on Noise `classes2.dex` | Exit 0; extracted candidate BES TOTA UUIDs and OTA method/string call paths; no popup/deauth path found |
| Rebuilt `./scripts/build.sh` and `./scripts/install.sh <serial>` | Exit 0; APK SHA-256 `19bd828132a48c39695b2be1a948438b00ea87bd70f7374ba84dde7873028d06` |
| Audit `git status --short`, `git log --oneline`, `git ls-files` | Exit 0; clean at `463eb83`, 37 tracked files before this follow-on work |
| Serial-pinned `adb devices -l`, `getprop`, `uname -a`, `getenforce`, targeted `/proc/config.gz` and device nodes | Exit 0; Android 10 M30 baseline, enforcing, SCSC H4 and UDI nodes reconfirmed |
| `adb pull /vendor/etc/wifi/mx140.bin evidence/mx140.bin`; `sha256sum`; `file`; `xxd` | Exit 0; local ignored 1,360,017-byte `smxf` candidate, hash in `research/experiment-results.md` |
| `python3 scripts/inspect_scsc_firmware.py evidence/mx140.bin` | Exit 0; header, constant and full-image CRCs match copied image |
| `python3 -m unittest discover -s tests -p 'test_scsc_firmware.py' -v` | Exit 0; synthetic corruption and bounds test passed |
| `python3 host/gatt_fixture.py --seconds 120` plus explicit phone UI scan/connect/subscribe/write/unsubscribe/three-cycle sequence | Exit 0; fixture logged two confirmed writes, notification subscription and bounded shutdown; phone reported 3/3 cycles status 0 |
| `git ls-remote` official REA and M30-family source | Exit 0; revisions `1173c0a9dc1b352a60943a491462294db8f353ae` and `095206579f69121278ed1dd5fd0d7975c0705c6a` |
| Samsung OSRC direct M305F search | HTTP 403; no exact stock source was acquired |

The host fixture and receiver use bounded timeouts. ADB helper commands use
explicit `timeout` wrappers where a device or UI operation could hang.
Audit tool versions: Python 3.14.4, Node 22.23.2, npm 10.9.8, ADB 34.0.5,
BlueZ 5.85, Wireshark 4.6.4, Git 2.53.0. All primary URLs and their role are
listed in `research/sources.md` and `research/prior-art.md`.
