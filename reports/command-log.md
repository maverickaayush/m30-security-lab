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
| Earlier `git ls-remote` audit of REA and M30-family source | Exit 0; prior REA revision `1173c0a9dc1b352a60943a491462294db8f353ae`; M30 family `095206579f69121278ed1dd5fd0d7975c0705c6a` |
| Samsung OSRC direct M305F search | HTTP 403; no exact stock source was acquired |

The host fixture and receiver use bounded timeouts. ADB helper commands use
explicit `timeout` wrappers where a device or UI operation could hang.
Audit tool versions: Python 3.14.4, Node 22.23.2, npm 10.9.8, ADB 34.0.5,
BlueZ 5.85, Wireshark 4.6.4, Git 2.53.0. All primary URLs and their role are
listed in `research/sources.md` and `research/prior-art.md`.
## 2026-10-26 continuation

| Command/tool | Result |
| --- | --- |
| `node-v24.22.1/bin/node --version` | `v24.22.1` |
| `node-v24.22.1/bin/npm --version` | `11.19.1` |
| `rea --version` | `6.8.0` |
| REA official revision | `e54bf1006eadd0dfeea5c6c259cc3dc84bb414ec` |
| Ghidra release/hash | `12.1.4`, SHA-256 `ddac49f903da9d5bac833e5cc79395098b9c33cfd3279be5f31bd00387d2d4db` |
| `rea doctor --json` with local Ghidra/Java | Ghidra, headless, native decompiler, Java 21 healthy; client config drift reported and left untouched |
| `python3 -m unittest discover -v tests` | 7 passed |
| `./scripts/build.sh` | success; final APK SHA-256 `3c65c58b014d44bc17cb8c8cd1ae0daed2f0eba6aa2d054e193787a0e7fc2ab0` |
| serial-pinned streamed install | success on Android package `org.cattail.m30lab` |
| `host/blue_stress.py` dry-run | `ready_dry_run`; laptop adapter readable; no target request |
| REA/Ghidra HAL analysis | `vendor-bt-hal-arm64.so` AArch64; 615 procedures/363 strings; `VendorInterface::Open` body `0x10c108` statically resolves `dlopen`/`dlsym` vendor loading, fd validation and H4/MCT setup |

The REA command outputs and hashes are retained in ignored `evidence/` files;
the vendor ELFs and firmware image remain private.

## 2026-10-26 native-phone continuation

| Command/tool | Exit/result |
|---|---|
| Serial-pinned `getprop ro.build.display.id`, `/proc/config.gz`, HCI sysfs | Exit 0; M305FDDS8CVB2, `CONFIG_BT` and `CONFIG_SCSC_BT_BLUEZ` unset, no `/sys/class/bluetooth` |
| Serial-pinned `ps -AZ`, `/proc/3699/fd` targeted read | Exit 0; HAL in `hal_bluetooth_default` domain had fd 6 to `/dev/scsc_h4_0` |
| REA/Ghidra function analyses (HAL `close`, `Shutdown`, `Close`, `Send`, `H4Protocol::Send`; vendor `op`, `cleanup`) | Exit 0; offsets and hashes in `research/rea-handoff-followup.md` |
| `git clone --depth 1 --single-branch --filter=blob:none` family tree | Exit 0; revision `095206579f69121278ed1dd5fd0d7975c0705c6a` in volatile `/tmp` |
| `make ARCH=arm64 m30lte_00_defconfig`; fragment merge; `olddefconfig` | Exit 0; requested Bluetooth and SCSC symbols survived Kconfig normalization |
| First bridge compile with host GCC 15 | Exit 2; old host `dtc` duplicate `yylloc`; temporary `HOSTCFLAGS=-fcommon` advanced |
| Bridge compile before compatibility patch | Exit 2; unavailable `hci_skb_pkt_type`, `hci_skb_expect`, `KERNEL_VERSION` in family headers |
| Bridge compile after both staged patches | Exit 0; AArch64 `scsc_bluez.o` and linked composite `scsc_bt.o`; hashes in `research/kernel-bridge-build.md` |
| `python3 -m unittest discover -s tests -p 'test_*.py' -v` | Exit 0; 11 tests, including four handoff mocks |
| Android `testDebugUnitTest` | Exit 0; bounded phone scheduler tests pass |
| `scripts/build.sh`, `scripts/install.sh SERIAL`, `am start` | Exit 0; final APK SHA-256 `6b07fec128512b5a8f94d8d17fb23d8671f93e1a12afb4e588b8ab3ac28956c5`; installed and activity launched |
| `uiautomator dump` | Exit 0; lock-screen nodes only, so tap/export verification remains pending user unlock |
