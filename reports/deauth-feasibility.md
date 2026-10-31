# Bluetooth disconnect feasibility

The term “Bluetooth deauth” is too broad for a single command or capability.

| Scenario | Finding for this lab |
| --- | --- |
| End an app-owned GATT connection | Feasible through `BluetoothGatt.disconnect()`. This ends only the link established by this app. The app logs connection state and status. |
| Measure reconnection of an owned peripheral | Implemented as three bounded connect/disconnect cycles after explicit fixture selection. Results are recorded in the test report. |
| End a link between two other devices | No generic method is supported by the Android app or observed internal interface. The Bluetooth SIG HCI Disconnect command refers to an existing connection handle on the local controller, not an arbitrary third-party link. No third-party disconnect was tested. |
| Vulnerability-based disruption | Unknown until a specific owned target, firmware/version, advisory and prerequisites are identified. No exploit was selected or run. |
| RF interference or jamming | In principle, sufficient interference can degrade a radio link, including links between other devices. It is not a universal Bluetooth disconnect command, cannot guarantee every link in an area will drop, and cannot be delivered as a controlled feature of the observed M30 Android Bluetooth API. No interference generator was implemented or tested. A separate, instrumented RF setup would be required to establish an effect on the owned earbud link. |

The original Android-only image had `CONFIG_BT` unset and
`CONFIG_SCSC_BT_BLUEZ` off. The corrected research kernel now enables the
Linux Bluetooth core and SCSC bridge, but access is deliberately supervised:
the Android HAL must release `/dev/scsc_h4_0` before the phone-side HCI helper
acquires `hci0`. Root alone did not create that path. The [Bluetooth SIG HCI
specification](https://www.bluetooth.com/wp-content/uploads/Files/Specification/HTML/Core_v6.3/out/en/host-controller-interface/host-controller-interface-functional-specification.html)
defines HCI Disconnect with a local connection handle;
[Android BluetoothGatt.disconnect()](https://developer.android.com/reference/android/bluetooth/BluetoothGatt#disconnect())
applies to the app's GATT link. The [M30-family source
Kconfig](https://github.com/akhil1999/android_kernel_samsung_m30lte/blob/095206579f69121278ed1dd5fd0d7975c0705c6a/drivers/misc/samsung/scsc_bt/Kconfig)
describes the optional BlueZ path. The live result, not that repository alone,
establishes the supervised access boundary.

Ownership of all nearby devices permits controlled testing but does not change the controller's command semantics. The earbud/host models and firmware were not supplied, so no vulnerability-specific claim can be made. Even with an external raw link-layer radio or sniffer, disrupting an unrelated connection requires a demonstrated target-specific mechanism and verification. A generic “works on every Bluetooth connection” result is unsupported.

## Linux controller check

The laptop's USB Realtek controller is a separate BlueZ `hci0` (HCI 5.3,
`btusb`, central/peripheral roles). `hciconfig hci0 version` succeeded and
`hcitool con` reported no connections at the time of the check. The adapter is
useful for receiving the project's synthetic advertisements and for a bounded
owned GATT fixture. It does not expose the M30's internal SCSC connection
handles, and a host-side disconnect operation would only apply to a connection
owned by that adapter. No command was sent to disconnect a third-party link.

## Target-specific advisory examples

Public advisories demonstrate that a real third-party disconnect can be a
product firmware vulnerability, but they are not generic Bluetooth features:

* CERT/CC [VU#472136](https://kb.cert.org/vuls/id/472136) describes
  CVE-2025-13328, a repeatable RFCOMM denial of service in Redmi Buds 3 Pro
  through 6 Pro, and CVE-2025-13834, an information leak. The note names the
  affected product family, an unauthenticated in-range prerequisite, and
  firmware-side RFCOMM processing as the mechanism. It does not establish
  relevance to a Noise earbud or the M30.
* CERT/CC [VU#859658](https://kb.cert.org/vuls/id/859658) describes
  CVE-2025-20701 in Skullcandy Dime 3 model S2DCW firmware 1.0.0.28, where an
  unpaired device could pair without owner interaction. The vendor identified
  1.0.0.30 as the effective patch, and the affected product does not match the
  unknown lab fixture. This is an unauthorized pairing and audio-hijack case,
  not a universal disconnect command.

The phone's current read-only Bluetooth dump lists a bonded device named
`Damn's Noise Air Buds Pro 6`, but reports `STATE_DISCONNECTED`; its firmware,
the other endpoint and an active connection were not established. No Bluetooth
address is recorded here. Those identifiers and an active baseline are
required before a vulnerability claim or fixture-specific reproduction can be
responsibly selected. No advisory exploit or crafted RFCOMM traffic was run.

Static inspection of the installed Noise application did find a Bestechnic-like
(`BesOtaService`/`BES_TOTA_*`) OTA/control path with candidate GATT UUIDs
`0000ae00`, `0000ae01`, and `0000ae02`. That path is reached after selecting and
connecting to a compatible accessory and prepares vendor protocol writes. It is
not a radio-wide control plane, and no code path from it to a popup or a
third-party disconnect was found.

## Nearby popup or custom message request

The phone can broadcast a synthetic LAB marker and, in the separate audit run,
intentionally write and receive a notification from an explicitly selected,
cooperating GATT fixture. The earlier fixture-timed-out write remains a failure.
It cannot make arbitrary
nearby Android, iOS, earbud, or computer devices display a custom popup. A
Bluetooth advertisement is data for a scanner; the receiving operating system
decides whether to show anything. Triggering a popup on an uninstalled or
non-consenting receiver would require a product-specific protocol, pairing
workflow, or vulnerability. The app therefore has no popup-spam or pairing
impersonation mode. A safe extension is an opt-in receiver app or fixture that
recognizes the project UUID and renders a clearly labeled LAB message.

## Bounded blue-deauth adaptation

The pinned `its0x08/blue-deauth` scripts were audited in
`research/blue-deauth-audit.md`. Their L2CAP echo and RFCOMM loops are
Bluetooth Classic traffic generators with no independent target-effect
measurement. `host/blue_stress.py` replaces the unbounded shell loops with a
single baseline request, explicit controller and target, RFCOMM service check,
deadlines, structured arguments, process-group cleanup and JSON results. A
real laptop `hci0` dry-run completed without sending a target request. No
target-specific run was performed because the target model/firmware and its
existing connection were not identified. The backend therefore establishes a
repeatable stress-test harness, not a general deauthentication capability.

## 2026-10-28 phone-side update

The corrected installed research kernel now exposes a supervised SCSC-backed
Linux `hci0` after the Android HAL releases the H4 node. A bounded phone-side
LE scan received the controlled LAB advertisement and one raw L2CAP echo
request received a valid response from the cooperating laptop. Android
ownership was restored after each run. This expands the ordinary controller
access evidence; it does not change the disruption conclusion.

The first phone-side RFCOMM attempt returned `EPROTONOSUPPORT` before the
fixture saw a connection. The family source had `CONFIG_BT_RFCOMM=y` but
omitted `net/bluetooth/rfcomm/` from its Makefile. An offline one-line fix
compiled the RFCOMM objects. The image was installed on 2026-10-31 and
RFCOMM registration was verified; a phone-side fixture exchange remains
untested. See [the installation report](rfcomm-kernel-flash-2026-10-31.md). Full run IDs and private evidence locations are in
[`research/linux-hci-experiments-2026-10-28.md`](../research/linux-hci-experiments-2026-10-28.md).

No existing connection owned by another controller was interrupted. No
third-party disconnect, RF interference, or universal deauthentication
mechanism has been established.
