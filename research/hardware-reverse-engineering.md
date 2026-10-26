# Hardware and transport reverse engineering

Date: 2026-10-26 (Asia/Kolkata)

This note records a bounded, read-only inspection of the installed Android
Bluetooth stack. The handset was not modified, its firmware was not patched,
and no raw HCI command was injected.

## Observed transport

The running phone exposes `/dev/scsc_h4_0` and `/dev/scsc_ant_0`. The first node
is owned by the `bluetooth` group. A vendor HIDL service named
`android.hardware.bluetooth@1.0-service` is running. The copied, hashed vendor
objects are in ignored local evidence:

| Object | Result |
| --- | --- |
| `/vendor/lib64/hw/android.hardware.bluetooth@1.0-impl.so` | AArch64 ELF; SHA-256 `0e68668c2367f18a6eb150e606402425c1c323584b5ae8629f218870f35dbe27` |
| `/vendor/lib64/libbt-vendor.so` | AArch64 ELF; SHA-256 `57d60dd2f2ee3bdfe5e13b8dc3c1b56dce0bc60feeb5b7f55a756d7698561cdb` |

The HIDL implementation exports the standard `IBluetoothHci` methods,
including `sendHciCommand`, `sendAclData`, `initialize`, and `close`. Its
symbols also show `H4Protocol`, `MctProtocol`, `VendorInterface::Open`, and
`VendorInterface::Send`. This establishes a standard framework-to-HAL HCI
boundary; it does not grant ordinary applications a privileged HCI socket.

The vendor library strings reference `/dev/scsc_h4_0`,
`/mnt/vendor/efs/bluetooth/bt_addr`, `vendor.bluetooth_fw_ver`, and
`/sys/module/scsc_bt/parameters/bluetooth_address`. It also contains the
diagnostic string `Open h4 device failed`. These strings, the live device node,
and the running service make the Android framework → vendor HIDL → SCSC H4
character device path an observation on this build.

The root shell reported:

```
# CONFIG_BT is not set
CONFIG_SCSC_BT=y
# CONFIG_SCSC_BT_BLUEZ is not set
```

No `/sys/module/scsc_bt/parameters` files were present in the read-only query,
and no `hci0` or other Linux Bluetooth controller was present. The absence of
AF_BLUETOOTH is therefore explained by the running kernel configuration and
does not imply that the internal radio is missing.

By comparison, the laptop's Realtek `0bda:b86a` adapter is bound to `btusb` and
appears as `hci0` in `hciconfig`. Its HCI version reports Bluetooth 5.3 and
BlueZ reports central/peripheral roles and no active advertising instances at
inspection. This is a working Linux Bluetooth interface for receiver and
fixture tests. It is a laptop controller, not a path into the phone's SCSC
controller. The `host/lab_popup_receiver.py` test used it to receive the M30
LAB marker and trigger one desktop notification.

The laptop already has BlueZ 5.85, `bluetoothctl`, `btmgmt`, `btmon`,
`python3-dbus`, and Wireshark 4.6.4. No host installation was needed. The
phone's USB state reported `host_connected=false`, `UsbDevices: 0`, and
`mtp,adb`; no external controller was attached to the handset. Installing a
BlueZ userspace binary on Android would not add an HCI kernel driver or replace
the vendor HAL, so it was not attempted.

## Workaround audit

The read-only kernel configuration shows `CONFIG_USB=y` and an XHCI platform
host controller, but `CONFIG_USB_OTG` is unset, `CONFIG_BT` is unset, and no
USB Bluetooth transport driver such as `btusb` is built. The running Android
USB manager also reported no attached host devices. An external adapter would
therefore require all of the following: a compatible OTG/power setup, a kernel
with USB host mode and a Bluetooth transport driver, and a controller owner
that does not conflict with Android's HAL. This build supplies none of those
Bluetooth pieces.

The HAL process currently owns the internal transport: its file descriptor 6
points to `/dev/scsc_h4_0`, and the node is labeled `sec_radio_device` under
enforcing SELinux. Reading or writing that node from a second process would
consume or inject H4 bytes into the live system transport. It was not touched.

A future research image could use a separately matched kernel and an external
controller, or a dedicated USB/radio board attached to the laptop. Those paths
would create another controller with its own connection handles; they would
still provide local controller operations and captures, not a universal
disconnect operation for links owned by other controllers. No partition,
recovery, bootloader, or kernel change was made here.

## Matching source comparison

The public M30-family kernel tree
[`akhil1999/android_kernel_samsung_m30lte`](https://github.com/akhil1999/android_kernel_samsung_m30lte/tree/095206579f69121278ed1dd5fd0d7975c0705c6a)
at revision `095206579f69121278ed1dd5fd0d7975c0705c6a` contains
`drivers/misc/samsung/scsc_bt/`, `drivers/misc/samsung/scsc/`, and
`drivers/net/wireless/scsc/`. Its SCSC Bluetooth Kconfig has an optional
`SCSC_BT_BLUEZ` path. The exact Samsung source package for the installed
`M305FDDS8CVB2` firmware was not identified in Samsung's public release search,
so this repository is a family comparison, not proof of an exact source match.

The source comparison explains why a future custom kernel could expose a
BlueZ/HCI path, but that would be a new kernel and controller ownership model.
This task deliberately did not install or build one. Directly opening the H4
character device while Android owns the HAL would race the system transport and
is not a safe capability addition.

## REA decision

The official [morluto/rea repository](https://github.com/morluto/rea) was read
at revision `1173c0a9dc1b352a60943a491462294db8f353ae`. Its README and security
instructions were reviewed. REA was not installed or invoked: the live node,
kernel configuration, ELF symbols, and strings resolved the transport question
without needing binary decompilation. No private binary was modified. The
copied objects remain traceable by path, hash, and build association in local
ignored evidence.

## Capability conclusion

Reverse engineering found a privileged standard HCI command boundary inside the
HAL, but no additional supported interface for an application, no Linux HCI
controller, and no controller feature that addresses another device's existing
connection. A direct HCI Disconnect command would require a connection handle
owned by this controller. It cannot be turned into an area-wide disconnect or a
popup sender by calling the HAL.

Potential future research that is technically meaningful is limited to a
separate, offline-matched kernel/HAL build or a second external controller with
documented raw-HCI support. Neither should be introduced by replacing the
phone's kernel or by sharing its internal controller with BlueZ during this
project.
