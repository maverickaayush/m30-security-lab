# Native SCSC bridge versus Bluebinder/VHCI

Checked 2026-10-26. Bluebinder source was cloned at
[`ebab7c3c3fb15f0eea4db0ff14837066a716f973`](https://github.com/mer-hybris/bluebinder/tree/ebab7c3c3fb15f0eea4db0ff14837066a716f973). The repository describes a
proxy that carries Android Binder Bluetooth HIDL/AIDL traffic through Linux
VHCI; its Makefile depends on `libgbinder`, GLib and optionally systemd.

## Source-backed route comparison

| Approach | Kernel/features | Userspace and ownership | Current M30 compatibility | Operations if validated | Main blocker |
| --- | --- | --- | --- | --- | --- |
| Native SCSC bridge | `CONFIG_BT`, `CONFIG_BT_BREDR`/`LE`, `CONFIG_SCSC_BT_BLUEZ`, SCSC core/driver; optional RFCOMM | Linux `hci_dev` calls SCSC file operations. Android HAL must be stopped and its H4 fd released before Linux opens the path. | Family candidate built completely; installed kernel has `CONFIG_BT=n`, bridge off and no `hci0`. | Local Linux HCI/BlueZ operations through the SCSC controller. | Exact source/ABI, service startup, SELinux, exclusive handoff and recovery are untested. |
| Bluebinder/VHCI | `CONFIG_BT`, `CONFIG_BT_HCIVHCI=y`, `/dev/vhci`, likely rfkill and Linux HCI core | Bluebinder opens `/dev/vhci`, creates a virtual HCI device, sends HCI command/ACL/SCO/ISO packets through Android `IBluetoothHci`, and consumes callbacks. Android HAL remains the controller owner. | Not available: phone has no `CONFIG_BT`, no VHCI node and no proven `libgbinder`/SELinux integration. The phone exposes a HIDL 1.0 HAL, which the source supports in principle, but compatibility is not tested. | BlueZ sees a VHCI `hci*` and can use normal local HCI operations if Binder and VHCI work. | Kernel VHCI and BlueZ userspace, Binder device policy, Android service lifecycle and a safe service ownership model. |
| External USB HCI | `CONFIG_BT`, `CONFIG_BT_HCIBTUSB`/matching driver, USB host power and adapter firmware | Android or Linux must own the external adapter; internal SCSC HAL can remain untouched. | No phone-attached VID/PID or powered OTG fixture; current kernel has no Bluetooth core/USB Bluetooth driver. | Independent adapter's HCI capabilities, subject to chipset firmware. | Hardware, driver and power fixture; still no generic third-party disconnect primitive. |

Bluebinder's official service unit explicitly allows `/dev/binder`,
`/dev/hwbinder`, `/dev/vhci` and `/dev/rfkill`, restricts the service to
`AF_BLUETOOTH`, and starts before `bluetooth.service`. Those dependencies are
evidence that it is more than a userspace script. They also show why copying a
binary into an Android chroot is insufficient. The M30 build lacks the kernel
objects and policy assumptions the service requires.

## Ownership state machine

The native route has the clearest device-specific boundary because family
source shows `scsc_bt_h4_open`/`release` and a single-user check. The preferred
future sequence is:

1. Android owns the controller and is healthy.
2. Stop Android Bluetooth through a controlled service contract; wait for
   `VendorInterface::Close` to stop its async watcher, close vendor operation
   4/0 and release the H4 descriptor.
3. Verify the node is no longer open and acquire the Linux bridge.
4. Register/query the local HCI device and run only an ordinary command first.
5. Stop Linux userspace, unregister the HCI device, and restore Android.
6. Verify Android Bluetooth, scan, and a cooperating fixture exchange.

Any timeout is `recovery_required`; no second owner may race the transport.
Bluebinder changes step 3 to VHCI/Binder setup but still needs explicit service
shutdown/restart semantics and Android policy. Neither route supplies a handle
for a connection between two other controllers or a universal area-wide
disconnect operation.

## Preferred route

Native SCSC is the preferred research route for this handset because the
vendor transport, SCSC file-operations boundary and optional bridge are
source-visible and the complete family candidate now links offline. Bluebinder
is a useful comparison and may preserve vendor initialization better, but its
Binder/VHCI environment is designed for hybris/Sailfish deployments and has no
M30 Android 10 validation here. The next live milestone is not a flash: obtain
exact source and recovery evidence, then build a disposable handoff supervisor
and test only service restart/ordinary HCI registration on a boot-tested image.

Sources: [bluebinder repository](https://github.com/mer-hybris/bluebinder),
[bluebinder source](https://github.com/mer-hybris/bluebinder/blob/ebab7c3c3fb15f0eea4db0ff14837066a716f973/bluebinder.c),
[bluebinder service](https://github.com/mer-hybris/bluebinder/blob/ebab7c3c3fb15f0eea4db0ff14837066a716f973/bluebinder.service),
[M30 SCSC bridge](https://github.com/akhil1999/android_kernel_samsung_m30lte/blob/095206579f69121278ed1dd5fd0d7975c0705c6a/drivers/misc/samsung/scsc_bt/scsc_bluez.c).
