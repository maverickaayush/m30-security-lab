# SCSC Linux HCI registration investigation (2026-10-28)

## Correction after helper ABI audit and successful live query

The original ENODEV interpretation is withdrawn. HCIDEVUP was called with a
pointer instead of a scalar controller number. The corrected helper brought
hci0 up, validated Read Local Version Command Complete, and brought it down on
the installed auto-registration kernel. Android HAL then reclaimed H4 and
Bluetooth reported ON. See `hci-helper-abi-and-live-result-2026-10-28.md`.

Also, `hci_register_dev()` queues `hci_power_on`, which calls
`hci_dev_do_open()`: the earlier claim that registration cannot open the
transport is incorrect. Historical descriptions below record what was assumed
at the time, not an established passive-registration guarantee.

## Scope

This note records the first narrow attempt to make the optional SCSC BlueZ
bridge publish a Linux `hci_dev`. It does not open the SCSC H4 transport, stop
Android's Bluetooth HAL, or claim a controller handoff. The phone remained on
the previously boot-tested corrected ABOX kernel while this report was
prepared.

## Current Android owner

Serial-pinned, read-only observations on the handset identified the same
SM-M305F / `m30lte` Android 10 build (`M305FDDS8CVB2`). The Android Bluetooth
HAL service was running in its normal vendor service and held a descriptor for
`/dev/scsc_h4_0`. SELinux remained Enforcing. The effective running
configuration included:

```text
CONFIG_BT=y
CONFIG_BT_BREDR=y
CONFIG_BT_LE=y
CONFIG_BT_RFCOMM=y
CONFIG_SCSC_BT=y
CONFIG_SCSC_BT_BLUEZ=y
CONFIG_DM_VERITY=y
```

`/sys/class/bluetooth` was empty and no `hci0` was present. The registration
test did not open the transport. Attempts to write the existing
`scsc_bt/power_control` parameter were denied by the device policy, and no
device-node permissions or SELinux rules were changed.

## Source-backed cause of the missing device

The family source revision used for the offline build is
`095206579f69121278ed1dd5fd0d7975c0705c6a`. In
`drivers/misc/samsung/scsc_bt/scsc_bluez.c`:

* `slsi_bt_notify_probe()` stores the SCSC file operations, wait queue,
  error counter and device pointer, then only logs the probe.
* `slsi_bt_open_worker()` allocates an HCI device, assigns virtual/BR-EDR
  callbacks and calls `hci_register_dev()`.
* The worker is scheduled by the `power_control` module parameter or the
  rfkill callback. The running kernel has no usable rfkill class, and the
  parameter is not writable from the enforced root shell.
* The HCI open callback calls `bt_fs->open()`, starts the SCSC reader and thus
  would claim the same transport used by the Android HAL. It must not be
  invoked while the HAL descriptor remains open.

Therefore the observed absence of `hci0` is consistent with the bridge probe
running without a registration trigger. It is not evidence that the SCSC
controller is absent, and it is not fixed by installing BlueZ userspace.

## Narrow offline change

`research/kernel/scsc_bluez-auto-register.patch` schedules the existing
`slsi_bt_open_worker()` from `slsi_bt_notify_probe()`. This calls
`hci_register_dev()` after the SCSC service is available; it does **not** call
the HCI open callback. The intended property is publication of a Linux HCI
device while leaving transport ownership unchanged until an explicit,
separately controlled open.

The build script accepts `SCSC_BLUEZ_AUTO_REGISTER=1` for this isolated mode.
The family source compiled successfully with the existing compatibility and
SKB-lifetime patches. The resulting raw Image is:

```text
SHA-256 5427fa99c2edb8eb34986fea4268dbca36e4e45edd831ca72dd6e3bd0267d985
size    20879112 bytes
```

The Magisk-compatible prepared kernel used for packaging is SHA-256
`87fab786b8cf0e5fb2a90fc127a9a89811c3fec662311ae5695c89c79288f13b`.

## Package validation

An initial `/dev/shm` package check left an incomplete extracted kernel after
the tmpfs quota was exhausted; that result was rejected and was not flashed.
The same input was then packaged on persistent storage. The complete
round-trip passed:

* unchanged live BOOT round-trip accepted;
* output size 33,554,432 bytes;
* extracted kernel matched the 20,879,112-byte prepared input;
* embedded live DTB and Magisk ramdisk matched the round-trip components;
* package SHA-256 `f41bb7078526c33cf125ac0a3e5bd99ab4771464b19c3ed015a6bdfb070e1a52`.

The validated private copy is
`/home/aayush-yadav/m30-rollback-20261027/BASELINE_UNTESTED/hci-autoreg-abox-v1-boot-validated.img`.
It is outside Git and mode 0600. The original BOOT rollback hash remains
`a63676cd0ed5d90cfca66aaa744c173886b72606aec64ea174575f44250064d4`.

The AArch64 object was also checked with the isolated cross-binutils toolchain:
the `slsi_bt_notify_probe` relocation sequence stores the callback and queues
the work item on `system_wq`; `hci_register_dev` remains an unresolved kernel
symbol for the normal link. This confirms that the patch is a registration
trigger in the compiled object, not a fabricated sysfs node or a direct H4
open.

## Live test gate

The next live step is a BOOT-only trial using the verified Download Mode /
Heimdall procedure, after a current non-writing detection check. The original
BOOT image, exact stock archive and recovery metadata remain available. No
image has been written for this auto-registration candidate at the time of
this note.

Success criteria are: `hci0` appears with a driver-backed sysfs identity,
Android HAL ownership is still visible and no transport is opened; later HCI
commands require an explicit, reversible ownership plan. A visible `hci0`
alone will not justify running BlueZ or a stress test.

## Live auto-registration and handoff result

The auto-registration candidate was subsequently flashed to BOOT and booted
Android. It published `/sys/class/bluetooth/hci0`, while the Android HAL still
held `/dev/scsc_h4_0`; this confirmed registration without transport ownership.
After the HAL was stopped, the static phone HCI probe bound a raw socket but
`HCIDEVUP` returned `-ENODEV`. It printed no HCI command and no controller
event. The kernel log showed service shutdown and an ABOX remove callback,
not proof that the SCSC probe context was cleared. Android was restored by restarting the vendor HAL and enabling
Bluetooth.

The initial interpretation incorrectly narrowed the blocker to service lifetime. An opt-in patch,
`research/kernel/scsc_bt-keep-service.patch`, retains one SCSC service
reference across Android H4 close. The candidate is built and packaged
offline, with details in `research/hci-handoff-keep-service-2026-10-28.md`.
The retained-service behavior remains untested on the phone until this
candidate is booted. No undocumented HCI command has been issued.
