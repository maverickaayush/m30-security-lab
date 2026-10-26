# Evidence audit and claim ledger

Checked 2026-10-26 (Asia/Kolkata). This audit covers the Git history through
`463eb83`, the then-clean worktree, 37 tracked files, Android and host source,
ignored raw evidence, and a live serial-pinned ADB session. Device serials,
Bluetooth addresses, pairing material and private captures are omitted. The
running transport was Android (`adb devices -l`: one `device`), not recovery.

Evidence labels: **O** observed on this handset or receiver; **S** supported by
the cited source; **I** inference; **H** hypothesis; **T** implemented but not
yet tested; **U** unsupported. Confidence concerns the *stated* claim, not a
broader capability.

| Claim | Supporting artifact | Verification method | Confidence | Missing evidence | Next experiment |
| --- | --- | --- | --- | --- | --- |
| SM-M305F/m30lte Android 10 build M305FDDS8CVB2, kernel 4.4.177-22392479, patch 2022-03-01 | `reports/device-capabilities.md`; live `getprop`, `uname` | Serial-pinned ADB recheck | O/high | Recovery image/restore validation | Verify recovery and stock package only before any future flash |
| Magisk root; SELinux enforcing | Baseline; live `su -c`, `getenforce` | Read-only shell | O/high | None for this claim | Recheck before privileged tests |
| Android BLE scan and legacy advertising work | `reports/test-results.md`, ignored `evidence/receiver-scan.log` and app screenshots | Phone callback plus laptop BlueZ receiver | O/high for feature; moderate for RF observation | Packet-level RF timing | Use independent sniffer; BlueZ Device1 cache is not a packet counter |
| Extended advertising unsupported; max data 31 bytes | App capability dashboard and `BluetoothAdapter` API | Runtime API result | O/high for framework report | Controller command response/HCI capture | Enable supported HCI snoop workflow if needed |
| LAB popup delivered to an opted-in laptop | `evidence/lab-popup-receiver.log`, host receiver source | Receiver matched `PING`, `notify-send` exit 0 | O/high for cooperating Linux receiver | Other OS receiver implementation | Test separately installed receiver on each OS |
| Generic custom popup on every nearby device | No matching receiver protocol; Noise lookup has no Bluetooth permission | Static manifest/DEX audit; protocol analysis | U/high that current evidence does **not** establish it | Exact receiver-recognized protocol/target | Identify and validate a named protocol, or use cooperating app |
| GATT read succeeded | Previous fixture log and phone UI result `4C4142` | Independent fixture `READ 4c4142` | O/high | None for selected fixture read | Repeatability study |
| Prior GATT write succeeded | Earlier callback status 1 after fixture expiry | Compare phone result and fixture lifetime | U/high: **failed write** | Live fixture result | Completed in new run below |
| New write and notifications succeeded | `evidence/gatt-audit-events.log`, `evidence/gatt-fixture-audit.log` | Phone status 0 plus fixture `WRITE 4c414232`; notification and CCCD status 0 | O/high for one session | Independent HCI trace; repeated devices | See `experiment-results.md` |
| Three app-owned reconnect cycles completed | New phone event log | Three connected/discovery/disconnected status 0, summary 3/3 failures 0 | O/high for fixture run | Long-run distribution; power-loss/timeout cases | Repeat with controlled fixture time and independent timestamps |
| Linux internal `hci0` is currently absent | Running `/proc/config.gz`: `CONFIG_BT` and `SCSC_BT_BLUEZ` off; no `hci0` | Root config and interface query | O/high | None for current image | Recheck only after a future matched kernel build |
| SCSC BlueZ bridge can register an HCI device | M30-family `scsc_bluez.c` at `0952065` | Source path `hci_alloc_dev`/`hci_register_dev` | S/high for family tree, **not** installed build | Exact Samsung package, compile/runtime proof | Reproduce current kernel before changing config |
| Android HAL owns SCSC H4 node | Ignored HAL ELF hashes, symbols/strings; prior `/proc/<pid>/fd/6`; live node | Triangulated static and runtime read-only evidence | O/high for ownership; I/medium for every internal call | HCI snoop or call trace | Trace a specific HAL call only when needed |
| `/vendor/etc/wifi/mx140.bin` is the running controller firmware | File build ID matches `vendor.bluetooth_fw_ver`; family `mx140_file.c` auto directory logic | Header parse and property match | I/high, **not direct load-path proof** | Boot-time load path or trusted runtime image hash | Capture boot log / exact source without changing firmware |
| Copied `mx140.bin` is an intact `smxf` image | `evidence/mx140.bin` SHA-256 `2cc929186c161f9ddd2ed52bfec6181e2a9a9cde85dada4b1625b60ed7d95474`; `scripts/inspect_scsc_firmware.py` | Header/constant/full CRC all match family `fwhdr.c`/`fwimage.c` | O+S/high; CRC is **not authentication** | Architecture map and signature scheme | Trace R4/M4 sections against source; do not modify image |
| SCSC Wi-Fi UDI is exposed | Live `/dev/s5n2560udi0`, SELinux `sec_wifi_device`; family `udi.c` | Node query and source read | O/high for node; S/medium for behavior | Exact installed driver, bounded authorized capture | Read-only status and controlled own-frame experiment |
| Wi-Fi monitor capture/injection works | No live test; family `cfg80211_ops.c` omits monitor in interface modes | Source comparison | U for this handset; S that family tree omits monitor | Installed nl80211 mode dump and 802.11 metadata capture | Query wiphy capabilities with a compatible local client |
| Generic third-party Bluetooth disconnect | No exact target/firmware mechanism; HCI Disconnect requires local handle | SIG HCI semantics, existing report | U/high for current platform | Named target/version and demonstrated vulnerability | Start with accessory inventory, not a universal command |
| Noise firmware-lab broadcasts popups | Manifest: INTERNET only; lookup POST and local response | `research/noise-firmware.md` static DEX inspection | U/high | Matched accessory protocol capture | Do not infer Bluetooth transmission from class names |
| TWRP provides tested rollback | User reports TWRP, never booted or tested; no backup | Audit of reports and backup directory | U/high | Recovery boot, partitions, restore drill | Required before any kernel flash |

## Corrections made in this audit

- The earlier failed `414243` write remains a failed attempt. The successful
  `4C414232` write is a distinct later run, with a separate fixture log.
- Prior reports that spoke of reconnect results before measurement or implied a
  GATT message exchange from read-only evidence were narrowed. New measurements
  are dated and linked rather than retroactively rewriting the old attempt.
- The phone UI labels its four-byte LAB field as opt-in popup text. That wording
  describes **the cooperating receiver's behavior**, not an OS-wide popup.
- `CONFIG_USB_OTG=n` alone does not establish that host mode is impossible; the
  phone reports a dual-role port. A suitable adapter, power and `btusb` driver
  still have not been tested on the phone.
- HCI snoop and BlueZ D-Bus cache records are different from promiscuous RF
  captures. No measured packet loss or exact advertising packet rate is claimed.

## Scope of code review

`MainActivity.kt` guards post-Android-10 API calls, bounds scanning to 30 s and
500 entries, bounds advertising to one legacy set and 5–120 s, rotates three
named profiles in multi mode, and stops it on activity teardown. Its stress
button reports the missing phone HCI backend instead of pretending to run a
test. `GattLab.kt` presents characteristics before intentional writes, requests
a confirmation, and bounds three reconnect cycles to 45 s.
`host/gatt_fixture.py` advertises only the LAB UUID for 10–120 s and records
read/write/notification events. `host/lab_popup_receiver.py` is opt-in and
bounds notification count and runtime. `scripts/inspect_scsc_firmware.py` is
read-only. These are source observations; test coverage is specified in the
ledger. An activity pause stops RF tests, so the app is not a persistent radio
service. No boot receiver or automatic RF transmission was found.

Raw evidence remains ignored under `evidence/`; copied vendor ELFs, the firmware
copy, Noise APKs, screenshots and capture contents must stay local. No partition
backup was created in the original audit phase. The public worktree contains
documentation, code, hashes, source revisions and redacted results only.

Continuation (2026-10-27): bounded read-only copies of BOOT, DTB, DTBO and
RECOVERY were made and the live-source, phone-copy and volatile-host hashes
matched. No durable second copy or recovery boot test exists; see the
installation manifest and readiness-gate report.

## Follow-on implementation audit (2026-10-26)

| Claim | Supporting artifact | Status | Missing verification |
| --- | --- | --- | --- |
| REA can analyze the copied vendor ELF with a supported backend | `research/rea-analysis.md`, ignored REA JSON bundles, SHA-256 of `libbt-vendor-arm64.so` | Observed/source-backed; high confidence for this artifact | Larger exact HAL caller path and runtime trace |
| `libbt-vendor.so` contains the SCSC H4 path identifiers | REA string evidence at `0x1007af`, `0x1007ea`, `0x102028` | Observed; high confidence for the copied ELF | Installed process call-path correlation |
| Android HAL loads the vendor library and constructs the H4/MCT boundary | REA HAL procedure/string evidence; `VendorInterface::Open` `0x10c108` | Observed static behavior; high confidence for the copied HAL | Runtime callback/fd trace and exact boot configuration |
| The staged SKB lifetime change is a source-level correction | Family `scsc_bluez.c`, matching `hci_core.c`, staged patch | Source-supported; not an installed-device vulnerability | Matched kernel compile and runtime handoff |
| The phone can run Linux BlueZ now | Live `/proc/config.gz` and interface inventory | Unsupported on current image; high confidence | A future matched kernel build could change this |
| The laptop backend can run a bounded protocol experiment | `host/blue_stress.py`, 7 unit tests, real adapter dry-run | Implemented and dry-run verified; target effect untested | Named target, service inventory, independent connection observation |
| New UI has single and multi-profile LAB controls | APK build/install, live UI hierarchy, callback log | Observed on handset; callback only | Independent receiver during this exact revision |

## Kernel/RFCOMM continuation corrections

| Claim | Supporting artifact | Level | Missing verification |
|---|---|---|---|
| Android RFCOMM exchanged a LAB request and response from the phone | `reports/rfcomm-phone-fixture-2026-10-26.md`, independent BlueZ fixture logs | Observed live for the cooperating fixture | A matrix run with the new phase logs to isolate earlier setup failures |
| Family baseline kernel built completely | `research/kernel-build-2026-10-26.md`, private artifact hashes | Compiled offline; source-supported only for family tree | Exact Samsung source/ABI, packaging and boot validation |
| Family Bluetooth candidate built completely | Same report; `CONFIG_BT=y`, `CONFIG_SCSC_BT_BLUEZ=y` in final config | Compiled offline; not installed | Exact build match, HCI registration and Android restoration |
| Bluebinder can provide a VHCI proxy in its supported environment | Bluebinder revision `ebab7c3...`, source and service unit | Source-supported | M30 kernel VHCI/Binder policy and Android 10 runtime compatibility |
| HAL close stops the async watcher and closes vendor resources | REA evidence `evidence/rea-hal-close-final.json`, HAL hash | Static binary evidence | Runtime callback timing and service restart |
| Current partition layout is known | Read-only by-name listing, sizes and hashes | Observed live | Verified recovery boot and stock restore artifact |

## Installation-readiness continuation ledger

| Claim | Supporting artifact | Level | Missing verification |
|---|---|---|---|
| Minimum rollback copies are hash-equal | `research/installation-manifest-2026-10-27.md`, private phone/host copies | Observed live | Durable independent storage and restoration acceptance |
| Live BOOT package is parseable and reproducible | `research/boot-package-validation-2026-10-27.md`, AIK output | Observed and offline reproduced | Samsung signing/bootloader acceptance |
| Bluetooth candidate package fits the BOOT partition | Candidate package hash and parsed size in boot-package report | Offline structural result | Exact source/DTB/verity compatibility and boot |
| Candidate is safe to install | No supporting evidence | Unsupported claim rejected | Exact Samsung source, config match, recovery path and boot validation |

# 2026-10-26 native-phone continuation ledger

| Claim | Supporting artifact | Level | Missing verification |
|---|---|---|---|
| Current HAL owns SCSC H4 fd | Read-only `/proc` fd 6 and service/SELinux query; `research/rea-handoff-followup.md` | Observed live | Verify transition behavior only after a safe supervisor exists |
| Vendor operation 3 opens `/dev/scsc_h4_0`; operation 4 closes it | REA/Ghidra of hashed installed-build vendor library at `0x101090` | Static binary evidence | Runtime tracing of callback/error timing |
| Family driver intends one H4 user | `scsc_bt_module.c:1072–1128` at `0952065`; returns `-EBUSY` when `h4_users` is set | Source-supported for family revision | Match exact installed source and test serialized handoff |
| Patched family SCSC bridge compiles | `research/kernel-bridge-build.md`; AArch64 bridge and composite object hashes | Compiled offline | Exact-build kernel, boot, HCI registration, restoration |
| New APK can run selected UUID-based RFCOMM operations | `PhoneProtocolBackend.kt`, Android build and install | Implemented and compiled; target operation untested | Unlock UI, choose named owned Classic fixture with SDP UUID, observe baseline |
| New APK runs Linux L2CAP echo on M30 | Live config has `CONFIG_BT=n`; no `/sys/class/bluetooth` | Unsupported on installed kernel | Matched kernel access path and safe handoff |
| Android-to-Linux handoff succeeds | Four mocked state-machine tests only | Mocked; no live handoff | Source-matched kernel, supervisor, restore drill |
