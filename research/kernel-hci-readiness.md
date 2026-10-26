# Internal HCI build readiness

Checked 2026-10-26 on Samsung SM-M305F, firmware `M305FDDS8CVB2`, kernel
`4.4.177-22392479` (2022-02-21). No kernel or partition was changed.

The family source at revision
[`0952065`](https://github.com/akhil1999/android_kernel_samsung_m30lte/tree/095206579f69121278ed1dd5fd0d7975c0705c6a)
contains a genuine SCSC BlueZ bridge. Its
[`Kconfig`](https://github.com/akhil1999/android_kernel_samsung_m30lte/blob/095206579f69121278ed1dd5fd0d7975c0705c6a/drivers/misc/samsung/scsc_bt/Kconfig)
defines `SCSC_BT_BLUEZ`, and its
[`Makefile`](https://github.com/akhil1999/android_kernel_samsung_m30lte/blob/095206579f69121278ed1dd5fd0d7975c0705c6a/drivers/misc/samsung/scsc_bt/Makefile)
adds `scsc_bluez.o` to the existing `scsc_bt` object. In
[`scsc_bluez.c`](https://github.com/akhil1999/android_kernel_samsung_m30lte/blob/095206579f69121278ed1dd5fd0d7975c0705c6a/drivers/misc/samsung/scsc_bt/scsc_bluez.c),
the bridge registers an `hci_dev` and exchanges H4 frames through the SCSC
driver file operations. The source's
[`net/bluetooth/Kconfig`](https://github.com/akhil1999/android_kernel_samsung_m30lte/blob/095206579f69121278ed1dd5fd0d7975c0705c6a/net/bluetooth/Kconfig)
shows that Linux Bluetooth core support is a separate `CONFIG_BT` dependency.

The project now has a minimal proposed config fragment in
`research/kernel/m30lte-hci.fragment`. Its purpose is to make the required
configuration change reviewable. It is not a tested build. It does not include
USB `btusb`, since the proposed path uses the internal SCSC controller.

## Build gates found

1. The running kernel has `# CONFIG_BT is not set` and
   `# CONFIG_SCSC_BT_BLUEZ is not set`; both require a new kernel build.
2. An exact Samsung kernel source package for `M305FDDS8CVB2` has not been
   obtained. The family source uses `CONFIG_SCSC_DEBUG=y` in its M30 defconfig,
   while the running kernel has it disabled, proving at least one configuration
   difference. Build compatibility is therefore unverified.
3. At the initial audit the host had no ARM64 cross compiler. On 2026-10-26,
   GCC 14 and binutils were extracted into volatile `/tmp`; the public
   **family** bridge compiled offline after two source patches. See
   `research/kernel-bridge-build.md`. No exact installed-build source package
   or validated installable image is available.
4. The active Android Bluetooth HAL owns `/dev/scsc_h4_0`. The SCSC BlueZ bridge
   calls the same driver's open/read/write operations, so a future runtime
   must assign the controller to one stack at a time and provide compatible
   service startup and SELinux policy. Merely enabling config symbols would
   not make Android and BlueZ concurrent owners.
5. The family `scsc_bluez.c` appears to access `skb->len` and packet type after
   `kfree_skb(skb)` in its send callback (lines 139-155). That source needs
   review and correction before it is suitable for a research build. A minimal
   source patch is staged at `research/kernel/scsc_bluez-send-lifetime.patch`;
   it moves the free after the last read of `skb`. `git apply --check` passed
   against the family source at revision `0952065`. This patch and a 4.4-header
   compatibility patch later compiled in the family tree, but have not run on
   the installed handset.
   This is a source observation, not a proven defect in the installed kernel,
   because the bridge is disabled on the phone. The family-tree bridge and a
   Bluetooth-enabled candidate kernel now compile offline after the documented
   compatibility and lifetime patches; see
   [`research/kernel-build-2026-10-26.md`](kernel-build-2026-10-26.md). This
   still does not establish compatibility with the installed firmware or a
   safe flash.

The current phone's USB manager reports a dual-role port and the kernel has
`CONFIG_USB_XHCI_HCD=y`; `CONFIG_USB_OTG` being unset alone does **not** prove
USB host mode is unavailable. No adapter or powered OTG fixture was attached,
so external-controller operation on the phone remains untested. The laptop's
Realtek `btusb` adapter remains the working Linux HCI controller.

## What an internal HCI build would add

It would expose the SCSC controller to Linux HCI tools for raw local controller
commands, local connection measurement, and HCI monitoring when BlueZ is the
owner. It would not give that controller handles for links between two other
devices, an area-wide disconnect command, or a way to force UI on nearby
receivers. Those outcomes require a specific affected target/protocol or a
cooperating receiver.
