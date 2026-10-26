# Offline SCSC BlueZ bridge build gate

Checked 2026-10-26. Source is the public **family** tree
[`akhil1999/android_kernel_samsung_m30lte@095206579f69121278ed1dd5fd0d7975c0705c6a`](https://github.com/akhil1999/android_kernel_samsung_m30lte/tree/095206579f69121278ed1dd5fd0d7975c0705c6a),
cloned into volatile `/tmp/m30-family-kernel`. It is **not established as the
exact Samsung source for installed build M305FDDS8CVB2**. No kernel was
installed or flashed.

The stock family `m30lte_00_defconfig` accepted the research fragment after
Kconfig normalization. It yielded `CONFIG_BT=y`, `CONFIG_BT_BREDR=y`,
`CONFIG_BT_LE=y`, `CONFIG_BT_RFCOMM=y`, `CONFIG_BT_RFCOMM_TTY=y`,
`CONFIG_SCSC_BT=y`, and `CONFIG_SCSC_BT_BLUEZ=y`. RFCOMM and its TTY layer
were added to the fragment after the first Kconfig check showed them disabled.
This validates configuration consistency for this family tree only.

The first compile failed in host `dtc` because GCC 15's `-fno-common` default
found duplicate `yylloc`. An isolated build invocation with `HOSTCFLAGS`
ending in `-fcommon` advanced to the bridge. The unmodified bridge then failed
because its bundled `h4_recv.h` uses `hci_skb_pkt_type`, `hci_skb_expect` and
`KERNEL_VERSION` without definitions in the included 4.4 headers. This is a
source mismatch in this exact public revision, not an installed-device bug.

Two reviewable patches are now staged:

- `research/kernel/scsc_bluez-send-lifetime.patch` moves `kfree_skb` after the
  successful send's stats reads. The caller in `net/bluetooth/hci_core.c`
  frees the skb after a negative `send` return. Short positive writes remain a
  separate behavior to investigate; compile success does not validate them.
- `research/kernel/scsc_bluez-family-compat.patch` includes `linux/version.h`
  and maps missing HCI skb accessors to the existing `bt_cb(skb)` `pkt_type`
  and `expect` fields, locally before `h4_recv.h`. Hash:
  `e6634c95c80579c3a0fd5ba62701185b1f0d54839e30411c421ba022c5e0beef`.

With both patches applied to the temporary source clone, `make ...
drivers/misc/samsung/scsc_bt/scsc_bluez.o` **compiled** an AArch64 ELF object
with SHA-256
`72324e9fbf91b1ce5c14146611de214dafd4603e2d93b2344e7deb7c8be2a65c`.
`make ... drivers/misc/samsung/scsc_bt/` also compiled sibling objects and
linked the composite `scsc_bt.o` (SHA-256
`6b529b1d4d819b849da582a9473cadf63a7d7c52ae4b632d69517c505133b5f6`).
The object contains `slsi_bt_open`, `slsi_bt_close` and `slsi_bt_send_frame`.
No full kernel, device tree, boot image, ABI or runtime test was produced.

Toolchain: host GCC 15.2.0 for Kbuild tools; Ubuntu `gcc-14-aarch64-linux-gnu`
14.3.0 and binutils 2.46 extracted into `/tmp/m30-cross`, without changing
system packages. The wrapper in that temporary tree supplies `-B` paths for
`cc1` and binutils. Reproduction (assuming the same temporary toolchain):

```sh
git clone --depth 1 --single-branch --filter=blob:none \
  https://github.com/akhil1999/android_kernel_samsung_m30lte.git /tmp/m30-family-kernel
git -C /tmp/m30-family-kernel apply research/kernel/scsc_bluez-send-lifetime.patch
git -C /tmp/m30-family-kernel apply research/kernel/scsc_bluez-family-compat.patch
make -C /tmp/m30-family-kernel O=/tmp/m30-kout ARCH=arm64 m30lte_00_defconfig
/tmp/m30-family-kernel/scripts/kconfig/merge_config.sh -m -O /tmp/m30-kout \
  /tmp/m30-kout/.config research/kernel/m30lte-hci.fragment
make -C /tmp/m30-family-kernel O=/tmp/m30-kout ARCH=arm64 ANDROID_MAJOR_VERSION=q olddefconfig
LD_LIBRARY_PATH=/tmp/m30-cross/usr/lib/x86_64-linux-gnu \
PATH=/tmp/m30-cross/usr/bin:$PATH timeout 120s make -C /tmp/m30-family-kernel \
  O=/tmp/m30-kout ARCH=arm64 ANDROID_MAJOR_VERSION=q \
  CROSS_COMPILE=/tmp/m30-cross/usr/bin/aarch64-linux-gnu- \
  HOSTCFLAGS='-Wall -Wmissing-prototypes -Wstrict-prototypes -O2 -fomit-frame-pointer -std=gnu89 -fcommon' \
  drivers/misc/samsung/scsc_bt/
```

The temporary compiler package extraction and wrapper are documented in
`reports/mobile-integration.md`. The installed phone still reports
`# CONFIG_BT is not set` and `# CONFIG_SCSC_BT_BLUEZ is not set`, so none of
these compiled changes execute there. A bootable matched build and tested
restore procedure are the next gates before any live kernel experiment.
