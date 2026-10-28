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

## 2026-10-26 kernel/RFCOMM continuation

| Command or check | Result / exit code |
|---|---|
| `timeout 1200s make ... m30lte_00_defconfig` with isolated AArch64 GCC 14 | Exit 0; family baseline configuration generated |
| Full baseline `make` with `__ANDROID__`, `HOSTCFLAGS=-fcommon`, explicit warning compatibility | Exit 0; Image, Image.gz, vmlinux, System.map, dtb.img and four M30 DTBs produced; hashes in `research/kernel-build-2026-10-26.md` |
| Full Bluetooth candidate with `m30lte-hci.fragment` and both SCSC patches | Exit 0; `CONFIG_BT=y` and `CONFIG_SCSC_BT_BLUEZ=y`; complete artifacts and hashes recorded |
| First candidate link in `/dev/shm` | Exit 2, no space; output moved to isolated tmpfs and rebuilt successfully; no source change or claim was based on the failed link |
| `scripts/build_m30_kernel.sh` shell syntax and source checks | Exit 0; reproduction script added; no flash or packaging performed |
| Bluebinder clone and source audit | Exit 0; revision `ebab7c3c3fb15f0eea4db0ff14837066a716f973`; `/dev/vhci`, Binder and HIDL/AIDL requirements recorded |
| REA `function ... 0x10ca2c --provider ghidra` | Exit 0; close-path evidence `evidence/rea-hal-close-final.json`; summary in `research/rea-follow-on-2026-10-26.md` |
| Android source build/install after RFCOMM logging changes | Exit 0; APK SHA-256 `6075cba4395bddf99682fb6861a9dc03bed965356f03f98af46b44b6d71c5ce6`; package upgrade succeeded |
| Android `testDebugUnitTest` | Exit 0; existing bounded scheduler/parser tests passed |
| Read-only boot layout and partition sizes/hashes | Exit 0; `research/recovery-readiness-2026-10-26.md`; no partition copied or written |
| New RFCOMM fixture run `rfcomm-reliability-20261026-a` | Fixture registered and emitted `fixture_ready`; handset remained locked, so no phone run occurred; SIGINT cleanup emitted `stopped_and_restored` and `bluetoothctl` confirmed Powered yes, Discoverable no, Pairable no |

## 2026-10-27 installation-readiness continuation

| Command or check | Result / exit code |
|---|---|
| Serial-pinned handset inventory | Exit 0; SM-M305F / `m30lte` / `m30ltedd`, Android 10, `M305FDDS8CVB2`, kernel `4.4.177-22392479`, Magisk 30.7, SELinux enforcing |
| Battery and storage check | Exit 0; battery 100% AC; `/data` had about 43 GiB free; host root filesystem was full |
| Read-only partition map | Exit 0; BOOT 33,554,432; DTB/DTBO 8,388,608 each; RECOVERY 39,845,888 bytes |
| Bounded rollback copies | Exit 0; BOOT, DTB, DTBO and RECOVERY copied to phone user-data storage and pulled to volatile host tmpfs |
| Source/copy/hash comparison | Exit 0; all four live-source, phone-copy and host-copy SHA-256 values matched |
| `file`/header inspection | Exit 0; BOOT and RECOVERY are AOSP-style arm64 Android images with 2048-byte pages; BOOT has gzip ramdisk, Samsung DTBH and `SEANDROIDENFORCE` |
| AIK unchanged `--original --origsize` round-trip | Exit 0; output hash exactly matched the live BOOT hash |
| Candidate package construction and parse | Exit 0; 32 MiB padded package, candidate kernel/DTB/ramdisk hashes verified after unpack |
| Live versus candidate config comparison | Exit 0; live `CONFIG_DM_VERITY=y`, candidate `CONFIG_DM_VERITY` unset; USB/debug/static-ring differences recorded |
| Recovery contents | Static TWRP ramdisk observed; recovery boot and Download Mode USB recognition not attempted |
| Host cache cleanup | Removed only disposable pip cache after root filesystem reached 100%; project evidence, firmware, toolchains and backup copies were retained |

## 2026-10-27 durability, firmware and compatibility continuation

| Command or check | Result / exit code |
|---|---|
| Persistent rollback copy | Exit 0; four images copied from `/dev/shm` to ignored `backups/private-20261027` with directory 0700 and image/manifest 0600 |
| Persistent digest verification | Exit 0; sorted SHA-256 digest sets for volatile and persistent image copies matched |
| DTB/DTBO path investigation | Exit 0; distinct `/dev/block/mmcblk0p14` and `mmcblk0p15`, 8 MiB each, both byte-for-byte zero-filled |
| Serial-pinned identity/CSC check | Exit 0; SM-M305F / m30lte / m30ltedd, INS, `M305FDDS8CVB2`, official CSC `M305FODM8CUI1`, bootloader property `M305FDDS8CVB2` |
| Samsung FUS metadata query | Exit 0; official `samloader` 2.2.0 returned `M305FDDS8CVB2/M305FODM8CUI1/M305FDDS8CVB2/M305FDDS8CVB2` |
| Stock package acquisition | Not completed; exact package identified, but AP is listed at 2.79 GB and no durable storage is available |
| DTBH/FDT structural comparison | Exit 0; four live/candidate FDT blobs decoded with `dtc`; board and reserved-memory layout aligned, clock/regulator/fstab value differences recorded |
| Corrected `bluez-verity` kernel build | Exit 0; complete family-tree build with `CONFIG_DM_VERITY=y`, `CONFIG_SCSC_BT_BLUEZ=y`, `CONFIG_USB_OTG=n` and SCSC debug/static-ring options unset; build log retained in ignored tmpfs |
| Corrected boot-package round-trip | Exit 0; 33,554,432-byte padded package SHA-256 `988749cf794048d3443475dc7398df3f63a53e2ee0b2a083616e1c7148c6cd41`; unpacked kernel/DTB/ramdisk hashes matched inputs |

## 2026-10-27 persistent package and mode checks

| Command or check | Result / exit code |
|---|---|
| User-selected A–D cleanup | Exit 0; Burp update cache, old executable backup, Trash and unrelated scratch directory removed; free root space rose from about 1.2 to 3.3 GiB; M30 backups retained |
| `samloader download` exact INS/FUS tuple to ignored persistent backup path | Exit 0; archive SHA-256 `b8090882a4564b5836ffdb38ae936c5412fe8325977e70a299925ce546806d6b` |
| `scripts/inspect_stock_zip.py` | Exit 0; five ZIP CRCs and five embedded TAR MD5 checks passed; AP boot artifact extracted only |
| Stock/live BOOT and four DTBH comparisons | Exit 0; four kernel bytes differ (`skip_initramfs`/`want_initramfs`); decoded DTBH property differences limited to fstab verify flags |
| `samloader detect --json` in user-entered Download Mode | Exit 0; recognized SM-M305F; private serial omitted |
| `samloader dump-pit --no-reboot` | Exit 0; 8,192-byte PIT parsed; no flash or restore |
| Android return check | Exit 0; original build/kernel, SELinux enforcing, Bluetooth on |
| TWRP screen and return | User observed TWRP after button sequence; Reboot → System returned ADB to original build/kernel, SELinux enforcing and Bluetooth on; recovery ADB/restore not tested |
| Post-recovery phone backup rehash | Exit 0; BOOT/DTB/DTBO/RECOVERY phone copies retained their manifest SHA-256 values |
| `python3 -m unittest discover -s tests -p 'test_stock_zip.py' -v` | Exit 0; synthetic metadata-footer and corruption cases passed |


## 2026-10-27 ABOX fix live trial

| Check | Result |
|---|---|
| Fresh-session Heimdall 2.1.0 BOOT upload | Exit 0, 23:49:40 Asia/Kolkata; candidate hash 3bd30a1df1f30162cf316c31a6ca88997770fa79844d1b192b6fc20ff68455ec; reboot requested |
| Serial-pinned Android health | boot_completed=1; kernel 4.4.177-g09520657-dirty; Enforcing; /data mounted; Magisk root available |
| Live configuration | CONFIG_BT=y, CONFIG_SCSC_BT_BLUEZ=y, CONFIG_DM_VERITY=y |
| Linux controller | No hci0 registered; no power-control/rfkill change performed |
| Android Bluetooth | Manager reports enabled/ON; exchange not retested on this kernel |
| Extended privileged ownership/readback query | Timed out at 30 seconds; no readback/ownership result claimed |
| Later shell health | boot_completed=1, uptime 161 seconds |

See research/abox-panic-and-io-fix-2026-10-27.md for source evidence,
remaining Wi-Fi warnings and the inaccurate legacy baseline filename.

## 2026-10-28 HCI registration candidate preparation

| Command or check | Result |
|---|---|
| Running serial-pinned inventory | Exit 0; SM-M305F / Android 10 / M305FDDS8CVB2; corrected ABOX kernel still active; SELinux Enforcing; Android Bluetooth service running |
| Targeted HAL ownership read | Exit 0; vendor Bluetooth service retained a descriptor for `/dev/scsc_h4_0`; no Linux `hci0` or rfkill class present |
| Running effective configuration | Exit 0; `CONFIG_BT`, BR/EDR, LE, RFCOMM, `CONFIG_SCSC_BT`, `CONFIG_SCSC_BT_BLUEZ` and `CONFIG_DM_VERITY` enabled |
| `scsc_bt/power_control` registration-only write | Denied by enforced device policy; no transport open, permission change or SELinux change attempted |
| Family source auto-registration patch check | Exit 0; `scsc_bluez-auto-register.patch` applies to revision `095206579f69121278ed1dd5fd0d7975c0705c6a`; schedules existing registration worker only |
| Incremental `bluez-minimal` build with `ABOX_IO_PATCH=1 SCSC_BLUEZ_AUTO_REGISTER=1` | Exit 0; raw Image SHA-256 `5427fa99c2edb8eb34986fea4268dbca36e4e45edd831ca72dd6e3bd0267d985`; compiler warnings retained |
| First candidate package check in `/dev/shm` | Rejected; tmpfs quota exhausted and extracted verification file was incomplete; no device write |
| Persistent candidate package round-trip | Exit 0; package SHA-256 `f41bb7078526c33cf125ac0a3e5bd99ab4771464b19c3ed015a6bdfb070e1a52`; extracted kernel/DTB/ramdisk matched; no device write |

## 2026-10-28 — explicitly approved cleanup

User selected items 2, 5, 6, 7 and conditionally item 1. Metadata inspection, Docker per-image removal (22 images, no force/prune), protected final-artifact copy/hash verification, and exact temporary-tree removal completed with exit code 0. See `reports/storage-cleanup-2026-10-28.md`; full private action logs are in ignored `evidence/cleanup-20261028-approved-2-5-6-7/`. Original BOOT and installed registration-candidate package hashes match their expected values.

## 2026-10-28 SCSC service-retention candidate

| Command or check | Result |
|---|---|
| Source reset and patch checks | Exit 0; temporary family tree returned to revision `095206579f69121278ed1dd5fd0d7975c0705c6a`; compatibility, SKB-lifetime, auto-register, keep-service and ABOX patches applied by the isolated build script |
| Full candidate compile/link | Kernel compilation and link reached `vmlinux`, `Image`, DTB output, `System.map`, module and effective `.config`; wrapper stopped only at `Image.gz` because `/dev/shm` per-user quota was exhausted |
| Completed output recovery | Exit 0; stopped-build link temporaries removed by explicit path and `Image.gz` created with `gzip -n -9`; raw Image SHA-256 `500225061643caefbca87432da62780aa72e8b3bad1cb257a3a7b9ae89e3a2c9` |
| Magisk kernel preparation | Exit 0; four-byte `skip_initramfs` → `want_initramfs` copy SHA-256 `81246707e8bdaa4ae991856d8cdd6e410e6abc3f7c614aaf5916f370903dffb5` |
| Candidate package round-trip | Exit 0; 33,554,432-byte BOOT package SHA-256 `f0951a16f3e243866ad111533f28ead262be2baff81bb5b409bbfa6d175f27a2`; live DTB, Magisk ramdisk and patched kernel re-extracted and matched |
| Phone HCI probe rebuild | Exit 0 with isolated loader path; helper SHA-256 `edccc39c409ccbcb7944d48fe981af3db67f2aeeb5767ea24e5354d3cc061779`; accepts `HCIDEVUP` `EALREADY` and still sends only Read Local Version |
| Device write | Not yet performed; waiting for user-entered Download Mode, then detection/PIT validation |

## 2026-10-28 13:13–13:24 IST — helper correction and actual HCI exchange

The service-retention installation was cancelled before any write. Detection
in Download Mode succeeded; user returned the phone to Android. The previous
ENODEV diagnosis and passive-registration claim are withdrawn as documented in
`research/hci-helper-abi-and-live-result-2026-10-28.md`.

| Check / bounded command | Exit/result |
|---|---|
| Existing rollback and proposed BOOT SHA-256 | Matched; neither image written this turn |
| Targeted ADB inventory | 0; original installed research kernel, enforcing, HAL running, hci0 present |
| Broad root fd walk | Host timeout 124; replaced with targeted HAL fd check, no device mutation |
| `cc -Wall -Wextra -Werror -O2 tests/test_phone_hci_probe.c ...` | 0; 12 mocked cases pass |
| `build_phone_hci_probe.sh` with isolated AArch64 GCC 15.2.0 | 0; static binary SHA-256 `2a44c4f6b8c301394fa4c15de960c259a1584a7cf985bfab9b4ed7a1e576bb95` |
| Push helper, chmod 700, `svc bluetooth disable` | 0; framework disabled but BLE_ON persisted |
| `stop vendor.bluetooth-1-0`; state/fd check | Service stopped; fd grep exit 1 (no SCSC H4 descriptor found) |
| Recheck HAL stopped/no H4 fd; device `timeout 25` corrected helper under host `timeout 35s` | 0; valid 0x1001 Command Complete, controller down |
| `start vendor.bluetooth-1-0`; `svc bluetooth enable` | 0; HAL running, H4 descriptor reacquired, framework ON, enforcing |
| Launch app and UI inspection | 0; lock screen prevented post-restoration scan, user asked to unlock |
| `gh repo view ... --json isPrivate,defaultBranchRef` | 0; private=true, main |

Some truncated `dumpsys | head` queries logged a broken-pipe notice after the
requested status lines; these were not interpreted as full dumpsys validation.
No additional cleanup, partition write or APK change occurred in this turn.

### 13:25–13:26 IST — Android restoration reception check

After the user unlocked the phone, `host/ui_tap.py` selected the existing app's
bounded scan. `host/gatt_fixture.py --seconds 25` registered the LAB advertisement
on the owned laptop. Phone callback at 13:25:45 reported the fixture at -56 dBm.
Phone scan stopped at the 30-second deadline with four records; host fixture
exited 0 after unregistering. Host discoverable/pairable/discovering states were
all off afterward. Private evidence hashes are in the HCI helper result report.
