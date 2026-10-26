# Capability and research comparison

Checked 2026-10-26. `O` observed; `S` family/official source; `I` inference;
`U` unsupported or unmeasured. Blocking layer codes: A app permission, B
userspace ownership, C kernel config, D driver, E firmware, F physical radio,
G unknown. Effort ranges assume one researcher already familiar with Android,
Linux drivers and Python, access to this phone/laptop, and no vendor NDA;
they are work estimates, not delivery promises. Recovery risk concerns a
future device change, not this read-only phase. Evidence abbreviations:
**TR** `reports/test-results.md`; **ER** `research/experiment-results.md`;
**BL** `reports/device-capabilities.md`; **FS** family source revision `0952065`
linked in `research/prior-art.md`.

| Capability or research question | Current observed status | Desired behavior | Actual blocking layer | Evidence | RE target | Candidate implementation or experiment | Internal hardware sufficient? | External hardware required? | Kernel/firmware changes required? | Relative difficulty and uncertainty | Device recovery risk | Existing prior art | Potential original contribution | Acceptance criterion | Recommended priority |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Bounded BLE scan/inspector | O: works | Reproducible privacy-safe scan | A/location | TR, app source | Android ScanResult | Multi-trial fixture scan with explicit receiver timestamps | Yes | Separate receiver for validation | No | Low; 1–3 days, timing uncertainty | None | Android API, BlueZ | Device-specific measurement protocol | Parsed fixture, bounded stop, independent observations | 2 |
| Legacy LAB advertising | O: callback + laptop observation | Measure fixture resilience | A/E scheduling | TR, receiver logs | HAL/HCI scheduling | Correlate phone callback, HCI snoop, sniffer | Yes for transmit | Sniffer for true RF rate/loss | No for baseline | Medium; 1–2 weeks | None | Android advertiser API | Measured callback-to-air gap on SCSC | Repeated clocked trials and loss bounds | 1 |
| Extended advertising | O: framework reports false | Multiple extended payloads | E/G controller/API | BL, TR | HCI capability/version response | Capture supported-command response; compare firmware | Unknown | No to study cause | Firmware may be required; unproven | Medium/high; 1–3 weeks | High only if flashing | Android API | Firmware-version capability map | Controller evidence agrees or explains API false | 3 |
| GATT read/write/notification | O: all worked on LAB fixture; prior failed write preserved | Robust owned-device protocol tester | A/fixture lifetime | ER, fixture and phone logs | App callback/fixture | Repeat across trials/devices | Yes | Owned peripheral/host fixture | No | Low; 2–5 days | None | Android GATT/BlueZ examples | Reproducible Samsung callback study | Status 0 and independent fixture effect | 1 |
| Local connect/disconnect/reconnect | O: 3/3 status 0 one fixture run | Link resilience metrics | A/G target variance | ER | GATT status/callback | Repeat with randomized cycles/control | Yes | Fixture | No | Low/medium; 3–7 days | None | Android GATT | Device-specific latency distributions | Repeatable failure and latency estimates | 2 |
| HCI snoop | U: not enabled | Host/controller trace of local traffic | A/user toggle/G path | `research/capture.md` | Bluetooth service | Enable Developer Options, pull bug report | Yes | No | No | Low; <1 day | None | AOSP debug guide | Correlated HAL events | Parseable local HCI trace | 2 |
| Internal Linux HCI/BlueZ | O: no hci0; FS bridge exists | Exclusive Linux HCI controller | B+C, exact source G | BL; `kernel-hci-readiness.md` | `scsc_bluez.c`, service init | Reproduce exact stock build, then isolated bridge | Likely yes for local HCI; unproved | No | Matched kernel and ownership changes | High; 4–12 weeks+ | High: boot/recovery | Family bridge, BlueZ | Reproducible port + bug fixes if validated | Bootable matched build; hci0; Android handoff; restore drill | 3 |
| Android-to-Linux userspace bridge | U: none verified | Expose useful diagnostics | B/G | HAL fd/source audit | HIDL, H4 | Compare maintained bridge semantics; trace exact calls | Yes for API-level bridge | No | Perhaps SELinux/service integration | Medium/high; 2–6 weeks | Medium if service disrupted | AOSP/BlueZ | Safe ownership bridge if actually novel | Documented forwarded op set and bounded tests | 4 |
| SCSC firmware container | O: `smxf` and three CRCs verified | Public, read-only parser | E/G mapping | ER, FS `fwhdr.c` | Header/segment layout | Extend parser with bounded section map/test corpus | Yes | No | No | Medium; 1–3 weeks | None | FS header code | Portable parser plus build/ABI comparison | Matches multiple image versions; rejects corruption | 1 |
| Firmware signing/modification | U: signature and update trust unknown | Identify trust boundary | E/G | ER, `mxman.c` | loader/ROM/firmware | Source/static analysis; no live patch | Unknown | Analyzer maybe | Firmware changes **not** justified | High; 3–8 weeks | Very high if flashed | Samsung family source | Trust-chain map | Verified code path or measurement, no guess | 4 |
| Wi-Fi UDI/FAPI diagnostics | O: node exists; S: family logger | Diagnose own Wi-Fi traffic with metadata | B/G capture semantics | live node, FS `udi.c` | UDI header/filter/queue | Bounded own-frame capture, compare external sniffer | Yes for own logging | External sniffer for monitor comparison | No for initial read-only study | Medium; 1–3 weeks | Low | SCSC UDI source | Documented decoder and completeness limits | Decoded signals with direction/drop accounting | 2 |
| Wi-Fi monitor capture/injection | U on handset; FS omits monitor mode | Monitor arbitrary nearby 802.11 | C/E/F/G | FS `cfg80211_ops.c` | nl80211/firmware | Query installed wiphy modes and frame metadata | Unknown | External adapter likely for reliable monitor | Could require firmware/driver | High; 3–8 weeks | Medium/high | Linux cfg80211 | Negative capability measurement | Independent 802.11 RX with radiotap metadata | 4 |
| USB HID gadget | O: ConfigFS present, F_HID absent | Approved USB HID fixture | C | live config | gadget ConfigFS | Matched kernel build and host enumeration | USB hardware yes; software path blocked | No | Kernel driver/config required | Medium/high; 2–5 weeks after source | High if kernel flash | Linux USB gadget | Matched Samsung port | Host enumerates HID and restores Android USB | 4 |
| External USB Bluetooth on phone | U: no attached adapter | Independent Linux HCI | C+D/G power | BL, `dumpsys usb` | USB host/`btusb` | Powered OTG with known VID/PID, driver/firmware check | USB port possibly | Yes, exact adapter and power | `btusb` kernel build likely | Medium; 2–6 weeks | Medium/high if kernel | BlueZ `btusb` | Portable integration guide | HCI commands on adapter without Android conflict | 3 |
| Target-specific earbud disruption | U: model/firmware unknown | Demonstrated exact vulnerability effect | G/E/possibly F | deauth report; advisories | Accessory parser/OTA | Identify target/version then bounded advisory-matched study | Unknown | Raw LL radio may be required | Depends on specific bug | High uncertainty; 2–12 weeks after target ID | Low offline; unknown active | SweynTooth/BrakTooth/CERT cases | New validated product finding if real | Exact affected version, reproducible effect/control | 3 |
| Generic third-party “deauth” | U; no mechanism established | Drop every nearby link | F/protocol ownership | SIG HCI; local controller mapping | None generic | No honest universal test design | No demonstrated path | Different hardware alone insufficient | No software-only fix | Unsupported objective | N/A | SIG HCI semantics | None as stated | Exact mechanism per target, never universal assumption | 5 |
| Bounded Classic responsiveness backend | T: laptop dry-run and unit tests pass; no target run | Measure one owned target's protocol response with controls | G: target identity/service and independent observer still required | `host/blue_stress.py`, 7 tests, real adapter dry-run | BlueZ `l2ping`/`rfcomm` command path | Named-target baseline, bounded sequence, recovery and target-side logs | Laptop internal adapter sufficient for Classic experiment | Separate observer/fixture required | No M30 kernel change; phone backend remains disabled | Medium; 1–2 weeks for a target study | None for dry-run; active test bounded | `blue-deauth` scripts, BlueZ tools | Reproducible negative/robustness measurements instead of unverified “deauth” label | Target/version named, baseline and recovery independently logged | 2 |
| Arbitrary nearby custom popup | U; only cooperating Linux receiver | Message on every OS | Receiver protocol/G | Noise static, popup receiver | Target UI/protocol | Build opt-in receivers; inspect named target protocol | Yes for LAB beacon | Receiver app required for general custom text | No | Low for opt-in; unbounded for arbitrary | None | BLE advertisements | Cross-platform opt-in lab protocol | Receiver explicitly installed and displays marker | 3 |
| NFC / LF RFID / sub-GHz / IR | NFC kernel off; no observed LF/sub-GHz/IR front ends | Flipper-class physical interfaces | C+F/G | live config, Flipper specs | Hardware inventory | Probe NFC feature; external certified radios as separate projects | Not established; LF/sub-GHz/IR no evidence | Yes for LF/sub-GHz/IR | App/kernel cannot add absent RF front end | High/physical | Depends on hardware wiring | Flipper hardware docs | Integration article only if measured | Actual RF frontend and independent response | 5 |

## Flipper comparison, with stock hardware separated

Official [Flipper hardware specifications](https://docs.flipper.net/zero/development/hardware/tech-specs),
[firmware application list](https://github.com/flipperdevices/flipperzero-firmware/blob/dev/applications/ReadMe.md)
and [Bad USB documentation](https://docs.flipper.net/zero/bad-usb) establish
stock BLE, sub-GHz CC1101, NFC ST25R3916, LF 125 kHz RFID, infrared, GPIO and
USB/BLE HID. Wi-Fi appears through an **external** ESP32-S2 developer board,
not the Flipper's stock radio. Stock documentation does not claim a universal
Bluetooth deauthentication feature. Third-party firmware/apps and external
radios must be named separately from the stock product.

The M30 has a working Android BLE stack and Wi-Fi radio, a screen/battery and
USB port. It has no measured Linux internal HCI controller, USB HID gadget
function, Wi-Fi monitor mode, or RF front ends for LF RFID, sub-GHz or IR. Its
NFC state is unverified beyond `CONFIG_NFC=n`. Bluetooth advertising can carry
data, but receiving systems decide what UI to show. The installed Noise
firmware lookup is HTTPS metadata; it is not a broadcast-popup protocol.

## Continuation status table

| Capability | Works now | Evidence | Built offline | Tested on device | Required change | Remaining blocker |
| --- | --- | --- | --- | --- | --- | --- |
| Android RFCOMM fixture exchange | Yes, cooperating fixture | Phone sent `M30LAB/1 PING` and verified `PONG`; independent BlueZ logs matched | N/A | Yes, prior bounded runs | Isolate two earlier baseline failures with phase logs | Current matrix run awaits unlocked phone actions |
| RFCOMM cancellation/cleanup | Yes for prior run | Baseline plus nine matching fixture connections and disconnects | N/A | Yes | Repeat with new run correlation IDs | No isolated root cause for early setup failures |
| Native SCSC BlueZ bridge | No live access | Current phone has no `CONFIG_BT`/bridge and no `hci0` | Yes, complete family candidate | No | Exact source, packaging, ownership and recovery | Current image unchanged; no boot test |
| Baseline family kernel | No installed change | `Image`, `vmlinux`, DTB set and hashes in `research/kernel-build-2026-10-26.md` | Yes, complete | No | Match Samsung source/ABI/boot packaging | Exact OSRC source and restore procedure |
| Bluebinder/VHCI route | No | Official source requires `/dev/vhci`, Binder/HIDL/AIDL and Linux HCI | No M30 build attempted | No | Enable VHCI/BT, port Binder policy and service lifecycle | Kernel and Android policy unavailable |
| Raw HCI commands on M30 | No | No internal `hci0`; HAL H4 only | Candidate would support path | No | Boot-tested exclusive owner | Controller handoff unvalidated |
| Third-party link disruption | No mechanism established | HCI disconnect uses a local controller handle; no target advisory matched | N/A | No | Named affected target/version and independent control | Generic capability unsupported |

## Installation-readiness continuation

| Capability | Works now | Evidence | Built offline | Tested on device | Required change | Remaining blocker |
|---|---|---|---|---|---|---|
| Rollback partition copies | Hash-equal copies created | Live-source, phone-copy and host-copy hashes match for BOOT/DTB/DTBO/RECOVERY | N/A | Read-only source and copy checks | Durable independent storage and restore procedure | Host copy is volatile; recovery/download path untested |
| Stock boot package parsing | Yes, static | AIK recognized AOSP image; unchanged round-trip was byte-identical | N/A | Read-only partition copy | Device-specific signing/acceptance validation | No Samsung package or boot test |
| Bluetooth candidate boot package | Structurally valid only | Candidate unpacked with matching kernel/DTB/ramdisk hashes and fits BOOT size | Yes | No | Exact source/config/ABI and verity match | Candidate disables live DM-verity and changes DTB |
| Android storage/decryption after candidate | Not tested | Live config has DM-verity; candidate differs | Candidate includes family storage config only | No | Preserve matched storage/verity config | No exact source or boot validation |
| Recovery return | Unknown | TWRP ramdisk statically present in RECOVERY | N/A | No recovery boot | Verify physical recovery and Android return | User interaction and restore package required |
