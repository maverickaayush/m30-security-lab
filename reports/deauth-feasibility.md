# Bluetooth disconnect feasibility

The term “Bluetooth deauth” is too broad for a single command or capability.

| Scenario | Finding for this lab |
| --- | --- |
| End an app-owned GATT connection | Feasible through `BluetoothGatt.disconnect()`. This ends only the link established by this app. The app logs connection state and status. |
| Measure reconnection of an owned peripheral | Implemented as three bounded connect/disconnect cycles after explicit fixture selection. Results are recorded in the test report. |
| End a link between two other devices | No generic method is supported by the Android app or observed internal interface. The Bluetooth SIG HCI Disconnect command refers to an existing connection handle on the local controller, not an arbitrary third-party link. No third-party disconnect was tested. |
| Vulnerability-based disruption | Unknown until a specific owned target, firmware/version, advisory and prerequisites are identified. No exploit was selected or run. |
| RF interference or jamming | In principle, sufficient interference can degrade a radio link, including links between other devices. It is not a universal Bluetooth disconnect command, cannot guarantee every link in an area will drop, and cannot be delivered as a controlled feature of the observed M30 Android Bluetooth API. No interference generator was implemented or tested. A separate, instrumented RF setup would be required to establish an effect on the owned earbud link. |

The handset's `CONFIG_BT` is unset and `CONFIG_SCSC_BT_BLUEZ` is off. Its observed route is Android framework → vendor HAL → `/dev/scsc_h4_0` → SCSC controller. Root alone does not add an AF_BLUETOOTH socket or a Linux HCI controller. The project therefore does not supply a fabricated “deauth” command. The [Bluetooth SIG HCI specification](https://www.bluetooth.com/wp-content/uploads/Files/Specification/HTML/Core_v6.3/out/en/host-controller-interface/host-controller-interface-functional-specification.html) defines HCI Disconnect with a local connection handle; [Android BluetoothGatt.disconnect()](https://developer.android.com/reference/android/bluetooth/BluetoothGatt#disconnect()) applies to the app's GATT link. The [M30-family source Kconfig](https://github.com/akhil1999/android_kernel_samsung_m30lte/blob/095206579f69121278ed1dd5fd0d7975c0705c6a/drivers/misc/samsung/scsc_bt/Kconfig) describes the optional BlueZ path. The running config, rather than that repository alone, establishes this handset's disabled path.

Ownership of all nearby devices permits controlled testing but does not change the controller's command semantics. The earbud/host models and firmware were not supplied, so no vulnerability-specific claim can be made. Even with an external raw link-layer radio or sniffer, disrupting an unrelated connection requires a demonstrated target-specific mechanism and verification. A generic “works on every Bluetooth connection” result is unsupported.

## Linux controller check

The laptop's USB Realtek controller is a separate BlueZ `hci0` (HCI 5.3,
`btusb`, central/peripheral roles). `hciconfig hci0 version` succeeded and
`hcitool con` reported no connections at the time of the check. The adapter is
useful for receiving the project's synthetic advertisements and for a bounded
owned GATT fixture. It does not expose the M30's internal SCSC connection
handles, and a host-side disconnect operation would only apply to a connection
owned by that adapter. No command was sent to disconnect a third-party link.

## Nearby popup or custom message request

The phone can broadcast a synthetic LAB marker and can exchange a message with
an explicitly selected, cooperating GATT fixture. It cannot make arbitrary
nearby Android, iOS, earbud, or computer devices display a custom popup. A
Bluetooth advertisement is data for a scanner; the receiving operating system
decides whether to show anything. Triggering a popup on an uninstalled or
non-consenting receiver would require a product-specific protocol, pairing
workflow, or vulnerability. The app therefore has no popup-spam or pairing
impersonation mode. A safe extension is an opt-in receiver app or fixture that
recognizes the project UUID and renders a clearly labeled LAB message.
