# Answerable reverse-engineering questions

Priority is based on what the current hardware and evidence permit. Source
revision for Samsung-family paths is
[`095206579f69121278ed1dd5fd0d7975c0705c6a`](https://github.com/akhil1999/android_kernel_samsung_m30lte/tree/095206579f69121278ed1dd5fd0d7975c0705c6a).

| Question | Current evidence and exact boundary | Next method / falsifier |
| --- | --- | --- |
| Which firmware file did this boot load, and is its header intact? | `vendor.bluetooth_fw_ver` suffix matches copied `/vendor/etc/wifi/mx140.bin`; `smxf` CRCs all match. Family `mx140_file.c` tries vendor/system paths and `mx140.bin` first. | Boot-time path log or trusted runtime mapping. A different loaded filename falsifies path inference. |
| Which HCI commands and vendor events traverse the HAL for bounded advertising? | HAL ELF exports `IBluetoothHci` and H4 symbols; `/dev/scsc_h4_0` owned by HAL; prior `dmesg` includes `le_advertising_start/end` with interval 131250 µs. | Enable supported HCI snoop and correlate callbacks, H4 events and separate receiver timestamps. Callback alone is not an RF packet count. |
| What firmware ABI/version handshake is enforced? | Family `mxman.c` accepts header 1.x/API 0.2 and uses mailbox 0 entry point; `fwhdr.c` maps header fields. | Trace startup path in exact stock source and compare live safe read-only properties. A different exact-source API branch falsifies family generalization. |
| Can SCSC Wi-Fi UDI expose useful 802.11 metadata? | Live `/dev/s5n2560udi0` label `sec_wifi_device`; family `udi.c` logs FAPI `MA_UNITDATA_REQ/IND`, direction, timestamp and signal length. Descriptor can denote IEEE 802.3 or 802.11. Queue caps/drop counters exist. | Use a bounded, authorized own-traffic capture **only after** documenting ioctl configuration and privacy filters. Compare against external monitor sniffer. FAPI unitdata about the phone is not promiscuous monitor traffic. |
| Does this Wi-Fi driver advertise true monitor mode? | Family `cfg80211_ops.c` `interface_modes` includes station/AP/P2P/adhoc but omits monitor; `mgmt_tx` is P2P/unsync action-frame handling. Live `phy0` exists; phone lacks `iw`. | Query installed nl80211 wiphy modes with compatible client; then measure actual RX radiotap/channel metadata if monitor is advertised. Flag patches or `mgmt_tx` do not prove monitor capture/injection. |
| Can the family SCSC BlueZ bridge survive basic static and build checks? | `scsc_bluez.c` registers `hci_dev`, shares SCSC file ops, and has a visible post-free SKB read in the disabled code path. | Exact-source diff, build in matched toolchain, static analyzer and unit fixture. A passing `git apply --check` is not a compile/runtime result or installed vulnerability. |
| Which controller core/segments does `smxf` contain? | Trampoline begins with an ARM instruction; header has R4 and M4 panic offsets and an API version. Generic `file` says data. | Parse sections from source-defined offsets, validate bounds/checksums, then use a backend with matching raw architecture and load address. No binary modification or unsupported import assumptions. |
| Can this phone act as USB HID? | `CONFIG_USB_CONFIGFS=y` with ADB/MTP/ACM/RNDIS/MIDI, but `CONFIG_USB_CONFIGFS_F_HID=n`, `CONFIG_USB_G_HID=n`. | A matched kernel change and host enumeration test; Android app or root alone cannot instantiate absent HID gadget function. |
| Which external radio is useful? | Phone USB is dual-role; zero attached host devices; no `btusb`. | Attach a powered lab-owned VID/PID-identified adapter, measure role/power/driver/firmware, and test bound HCI controller. No purchase recommendation from brand alone. |
| Can a named owned earbud be disrupted by a vulnerability? | Earbud model, firmware and chipset are unknown; Noise app has candidate BES OTA GATT UUIDs but no target match. | Read exact product/version and official advisory, then design controlled reproduction with normal-use and no-attack controls. A mismatch falsifies applicability. No generic deauth claim. |
| Can Noise send nearby custom popups? | `com.noise.audio.firmwarelab` adds an HTTPS firmware metadata lookup and has INTERNET only; main app has a connected BES OTA path. Opaque 128,401-byte `IAP` asset is unclassified. | With a named, owned accessory, record owner-initiated GATT operation and map characteristic/status. Finding only local UI or OTA metadata falsifies popup hypothesis. No production API probing. |

## Firmware and REA choice

The official [`morluto/rea`](https://github.com/morluto/rea) HEAD was verified as
`1173c0a9dc1b352a60943a491462294db8f353ae`. Its
[README](https://github.com/morluto/rea/blob/1173c0a9dc1b352a60943a491462294db8f353ae/README.md),
[installation guide](https://github.com/morluto/rea/blob/1173c0a9dc1b352a60943a491462294db8f353ae/docs/installation.md)
and [security policy](https://github.com/morluto/rea/security) were read. REA
needs a supported Node runtime and a separately installed Ghidra/Hopper/IDA
backend for native binary analysis. Host Node was `v22.23.2`; current REA
README requires `>=22.23.4` in the 22.x line. No Ghidra binary was found, and
REA does not install it. The local persistent disk had ~701 MiB free. The
concrete `smxf` header question was answered more directly with matching
source and a validated parser, so no installer or Codex configuration change
was justified. For later disassembly, stage a supported backend and Node in
project-local or temporary space, inspect `rea setup`'s plan, and retain the
private firmware copy/hash. Do not present raw firmware as an ELF or assume
Ghidra autodetects its R4/M4 mapping. The same-user provider is not a sandbox.

## Hardware map and precise blockers

- **A application permission:** Android 10 BLE scan requires fine location and
  enabled location service; Android 12+ has Nearby Devices runtime permissions.
- **B userspace ownership:** Android's Bluetooth HAL owns H4. Linux BlueZ needs
  an explicit handoff; concurrent access would corrupt state.
- **C kernel config:** `CONFIG_BT=n`, `SCSC_BT_BLUEZ=n`, `btusb` absent,
  ConfigFS HID absent. Each requires a matched kernel build and runtime tests.
- **D driver mismatch:** An external radio needs its particular USB/transport
  driver and firmware. No attached phone USB radio has been identified.
- **E firmware restriction:** Legacy/extended advertising difference is
  measured at Android API; the exact controller-side cause is unresolved.
  `smxf` CRCs are present; signature verification is not established.
- **F physical radio:** no observed LF RFID/sub-GHz/IR front end on this phone;
  software cannot add one. NFC hardware has not been independently verified,
  and the running kernel has `CONFIG_NFC=n`.
- **G unknown:** exact Samsung source package, installed Wi-Fi nl80211 modes,
  USB host adapter power, HCI snoop availability, earbud firmware, and any
  target-specific vulnerability applicability.
