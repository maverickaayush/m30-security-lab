# Bluetooth ownership and Linux access paths on this M30

Checked 2026-10-26; live-path addendum 2026-10-28. Source comparisons use M30-family tree
[`095206579f69121278ed1dd5fd0d7975c0705c6a`](https://github.com/akhil1999/android_kernel_samsung_m30lte/tree/095206579f69121278ed1dd5fd0d7975c0705c6a),
not an established exact match for `M305FDDS8CVB2`.

```text
Android app (permissions: location/Bluetooth) → Bluetooth service
 → HIDL IBluetoothHci 1.0 / vendor library → /dev/scsc_h4_0 (H4)
 → SCSC kernel/shared memory → WLBT firmware → 2.4 GHz radio

optional family-source path: Linux AF_BLUETOOTH / BlueZ → hci_dev
 → scsc_bluez.c → same SCSC driver file operations → same controller

laptop: BlueZ → btusb → Realtek 0bda:b86a controller (separate hardware)
```

**Observed:** vendor service is running; prior read-only fd inspection found
its descriptor 6 opened on `/dev/scsc_h4_0`. The node has SELinux label
`sec_radio_device`, with enforcing mode. Its vendor library names that node;
the HIDL ELF exports standard HCI methods. Current kernel config has
`CONFIG_SCSC_BT=y`, `CONFIG_BT=n`, `CONFIG_SCSC_BT_BLUEZ=n`. The phone exposes no
internal Linux `hci0`. Android can still operate Bluetooth through the vendor
path. BlueZ installed in a chroot cannot create AF_BLUETOOTH or `hci_dev`.

| Access path | Exact forward boundary | Current result | Required work | It does not provide |
| --- | --- | --- | --- | --- |
| Android framework and HCI snoop | Framework/controller traffic of the **local** controller | Scanner, advertiser and GATT tests work; snoop not enabled | User enables Developer Options snoop, bug report or build-specific pull; inspect with Wireshark | Promiscuous unrelated-link RF capture or third-party handles |
| Userspace bridge | Only semantics implemented by that bridge (e.g. D-Bus, RFCOMM, app API), subject to vendor ownership | No maintained bridge verified compatible with this SCSC H4 node | Source proof of forwarding, ownership and SELinux domain, plus bounded test | Raw link-layer control unless explicitly implemented and supported |
| Internal SCSC BlueZ kernel bridge | Family `scsc_bluez.c` registers `hci_dev` and calls SCSC read/write ops | Disabled in running kernel | Exact stock source/config, matched toolchain and DT/ABI, bridge correction, build, isolated ownership and tested recovery | Arbitrary disconnect of another controller's connection |
| External USB controller | USB host → `btusb` or matching transport driver → independent HCI | No USB controller attached to phone; laptop adapter works | Actual VID/PID/chipset, driver/firmware, OTG/power and test | Access to phone internal controller's handles or guaranteed raw LL features |

The [SIG HCI spec](https://www.bluetooth.com/wp-content/uploads/Files/Specification/HTML/Core_v6.3/out/en/host-controller-interface/host-controller-interface-functional-specification.html)
defines HCI Disconnect using a connection handle that belongs to the local
controller. [BlueZ's management protocol](https://github.com/bluez/bluez/blob/master/doc/mgmt-protocol.rst)
operates through an HCI control socket; it is not a universal nearby-link API.
Android's [BluetoothGatt.disconnect](https://developer.android.com/reference/android/bluetooth/BluetoothGatt#disconnect())
applies to the app's connection. A dual-owner design must explicitly hand off
the single internal controller: stop Android's Bluetooth service/HAL cleanly,
close its H4 descriptor, provide Linux stack and policy, then reverse the
handoff. Concurrent reads/writes would race H4 framing and state. Root or a
chroot alone does not solve any of that.

## Custom kernel gates, ordered by dependency

1. Obtain the Samsung Open Source Release Center package for the installed
   **exact** build. Its public portal was checked, but the direct M305F search
   returned HTTP 403 in this session. The family mirror's README calls for GCC
   4.9, `ANDROID_MAJOR_VERSION=q`, `ARCH=arm64` and
   `m30lte_00_defconfig`; these are family instructions, not a proven stock
   compiler invocation. Compare every relevant running config symbol, DTB,
   boot image format, module ABI and firmware/board pairing.
2. Rebuild the unmodified current kernel and compare `uname -a`, config,
   modules, boot/dtb integration and behavior. A same-version string alone is
   insufficient. At the initial audit no cross compiler or exact source was
   staged. A later volatile ARM64 toolchain compiled the public family bridge
   offline; exact installed-build source remains unavailable (see
   `research/kernel-bridge-build.md`).
3. Only then enable `CONFIG_BT=y` and `CONFIG_SCSC_BT_BLUEZ=y` as in
   `research/kernel/m30lte-hci.fragment`. The family
   [`Kconfig`](https://github.com/akhil1999/android_kernel_samsung_m30lte/blob/095206579f69121278ed1dd5fd0d7975c0705c6a/drivers/misc/samsung/scsc_bt/Kconfig),
   [`Makefile`](https://github.com/akhil1999/android_kernel_samsung_m30lte/blob/095206579f69121278ed1dd5fd0d7975c0705c6a/drivers/misc/samsung/scsc_bt/Makefile)
   and [`scsc_bluez.c`](https://github.com/akhil1999/android_kernel_samsung_m30lte/blob/095206579f69121278ed1dd5fd0d7975c0705c6a/drivers/misc/samsung/scsc_bt/scsc_bluez.c)
   show its dependency on Linux HCI core and `h4_recv.h`. The send callback
   dereferences `skb` after `kfree_skb`; the staged source patch addresses this
   in the family tree. It and a required 4.4-header compatibility patch now
   compile into the family `scsc_bt.o`; neither is installed or runtime tested.
4. Define a one-owner runtime handoff and test service startup, rfkill,
   SELinux policy and return to Android. Building a bridge does not prove it
   works with this firmware or that it can transmit arbitrary LL frames.
5. Before *any* future flash, independently verify recovery boots, acquire a
   matching stock package and partition layout, make a readable backup, and
   actually test restoration on the same model. User-reported TWRP and an
   untested backup would not establish recovery. No flash was attempted here.

The M30 reports a dual-role USB port and has XHCI host support, but the running
kernel has no `CONFIG_BT`, so it has no `btusb` either. `CONFIG_USB_OTG=n` alone
does not disprove host mode. The only measured VID/PID is the **laptop's**
`0bda:b86a`; it is not a phone-attached adapter recommendation. An external
adapter must be evaluated by actual VID/PID, driver binding, firmware, current
draw and desired role. A generic adapter, over-air sniffer and raw link-layer
research radio solve different tasks. NetHunter's
[Bluetooth Arsenal](https://www.kali.org/docs/nethunter/nethunter-btarsenal/)
adds packaged tools; it cannot supply missing phone kernel support or RF
hardware. There is no demonstrated need to install it for the working native
app and laptop fixture.

## 2026-10-28 live-path addendum

The installed corrected research kernel now has `CONFIG_BT=y` and
`CONFIG_SCSC_BT_BLUEZ=y`. After the Android HAL is stopped and its H4
descriptor is verified closed, the project supervisor acquires the SCSC-backed
`hci0`; a phone-side raw HCI command and a bounded LE scan both succeeded.
The supervisor then brings Linux down and restores the Android HAL, which
reopens the H4 node and returns Bluetooth to ON. This is a supervised,
exclusive path, not concurrent ownership or a userspace-only bridge.

The live LE scan found the controlled LAB advertisement and a raw L2CAP echo
request received a valid response from the cooperating laptop. RFCOMM returned
`EPROTONOSUPPORT`: the family build's effective config enables RFCOMM but its
`net/bluetooth/Makefile` omits the RFCOMM subdirectory. A one-line correction
and complete offline candidate build are recorded in
[`research/linux-hci-experiments-2026-10-28.md`](linux-hci-experiments-2026-10-28.md).
That candidate is not installed.
