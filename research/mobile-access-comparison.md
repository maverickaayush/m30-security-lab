# Phone Bluetooth access comparison

Checked 2026-10-26. The live Android HAL process holds fd 6 to
`/dev/scsc_h4_0`. `/sys/class/bluetooth` is absent, and the running config has
`CONFIG_BT` and `CONFIG_SCSC_BT_BLUEZ` disabled.

| Approach | Required kernel features | Current M30 compatibility | Ownership model | Available operations | Blocker/evidence |
|---|---|---|---|---|---|
| Android framework APIs | Existing vendor stack | **Live** BLE scan/advertising/GATT; APK now includes RFCOMM test code but it has no named Classic fixture result | Android HAL owns SCSC fd | Advertise, scan, selected GATT, selected RFCOMM service socket | RFCOMM target service not selected or independently tested; public `BluetoothDevice` API does not expose L2CAP echo |
| Android HCI diagnostics/snoop | Existing Android stack and logging setting | Prior app and receiver evidence; capture availability documented separately | Android remains sole owner | Own host/controller HCI diagnostics | Does not expose a Linux `hci_dev` or promiscuous third-party link capture |
| Direct H4 character node | SCSC char driver (present) | Node exists; no exclusive handoff performed | Would require HAL stop and fd release | H4 byte stream if compatible, not AF_BLUETOOTH sockets | HAL fd 6 currently owns node; protocol/reset/restore and SELinux access unverified |
| Userspace H4-to-Linux bridge | Kernel `CONFIG_BT` plus a working HCI registration path, or a fully specified separate HCI bridge | **Blocked** on current kernel | One owner; bridge must prevent simultaneous HAL access | Potential Linux HCI after registration, not from copying bytes alone | `CONFIG_BT=n`; no verified bridge compatible with Samsung driver/Android10 image |
| SCSC BlueZ kernel bridge | `CONFIG_BT`, `CONFIG_SCSC_BT_BLUEZ`, BREDR/LE, optional RFCOMM + TTY | Family object **compiled offline**; installed image lacks options | Explicit Android-to-Linux handoff | Local HCI controller and Linux protocol sockets if runtime validated | Exact source/build/DT/ABI, SELinux, handoff and recovery unverified |
| External USB controller | Phone USB host power plus `CONFIG_BT`, compatible USB driver and firmware | Untested, no adapter attached to phone | Separate hardware can avoid SCSC ownership conflict | Depends on adapter/driver | No actual VID/PID, power fixture or phone driver match established |

The host laptop's Realtek `btusb` controller is separate hardware. Its BlueZ
`hci0` does not establish native phone execution. BlueZ userspace or a chroot
cannot create `AF_BLUETOOTH` when kernel `CONFIG_BT` is disabled. Even a future
HCI port would control local links and commands; it would not supply a generic
disconnect operation for links between two other controllers.

Sources: [Android RFCOMM socket API](https://developer.android.com/reference/android/bluetooth/BluetoothDevice#createRfcommSocketToServiceRecord(java.util.UUID)),
[Android Classic discovery API](https://developer.android.com/reference/android/bluetooth/BluetoothAdapter#startDiscovery()),
[BlueZ management protocol](https://github.com/bluez/bluez/blob/master/doc/mgmt-protocol.rst),
[M30-family SCSC bridge](https://github.com/akhil1999/android_kernel_samsung_m30lte/blob/095206579f69121278ed1dd5fd0d7975c0705c6a/drivers/misc/samsung/scsc_bt/scsc_bluez.c).
