# Kernel installation and recovery readiness

Checked 2026-10-26. This phase performed read-only inspection only. No reboot,
flash, partition write, controller takeover or SELinux change occurred.

## Current layout observed on the M30

The live device reports no slot suffix, bootloader state `orange`, and
`ro.boot.flash.locked=0`. Read-only `/dev/block/by-name` inspection exposed
the Samsung eMMC layout:

| Partition | Device size | Current raw SHA-256 |
| --- | ---: | --- |
| BOOT | 33,554,432 bytes | `a63676cd0ed5d90cfca66aaa744c173886b72606aec64ea174575f44250064d4` |
| DTB | 8,388,608 bytes | `2daeb1f36095b44b318410b3f4e8b5d989dcc7bb023d1426c492dab0a3053e74` |
| DTBO | 8,388,608 bytes | `2daeb1f36095b44b318410b3f4e8b5d989dcc7bb023d1426c492dab0a3053e74` |
| RECOVERY | 39,845,888 bytes | `c0ebb7d6700c6f7ed218d933ab380e5082c5a29a49c05a33b483858c3c6cd311` |
| VENDOR | 520,093,696 bytes | not read into a backup |
| SYSTEM | 3,992,977,408 bytes | not read into a backup |

The user-reported TWRP installation was not independently verified by booting
recovery. The hashes are current-state planning evidence, not stock restore
artifacts and not a guarantee that a future backup would recover the phone.
The exact Samsung source/firmware package for `M305FDDS8CVB2` is still not in
the project. Do not publish raw partitions or EFS/pairing material.

## Required gates before any future installation

1. Acquire the exact stock AP/BL/CP/CSC package and record vendor-provided
   hashes; do not use a different M30/M30s/M31 package.
2. Independently verify the recovery image and a download-mode restore path on
   this exact model. Check Samsung binary revision compatibility and do not
   downgrade it.
3. Make bounded, readable copies of only the device-specific partitions needed
   for restore (BOOT, DTB/DTBO, RECOVERY and relevant metadata), hash them, and
   test that the copies can be read back. This was completed on 2026-10-27 for
   BOOT, DTB, DTBO and RECOVERY; live-source, phone-copy and host-copy hashes
   match. The host copy is volatile and no durable second-storage copy exists.
4. Reproduce the family baseline from source, compare kernel release,
   configuration, DTBs, vendor modules, boot packaging and firmware paths, and
   resolve every mismatch that could prevent boot.
5. Package only according to the actual Samsung boot layout. Do not assume
   fastboot boot, A/B slots, or a generic Android boot image format.
6. Prepare a physical recovery procedure with a second verified cable/host and
   enough battery. The first boot experiment must verify system health and
   Android Bluetooth before enabling Linux ownership.

## Proposed reversible live experiment (not executed)

On a boot-tested candidate, boot or install the candidate through the verified
Samsung procedure, confirm Android health and Bluetooth, record controller
registration, then perform an ordinary feature query. Stop the Android HAL,
verify descriptor release, register one Linux HCI owner, run one cooperating
fixture exchange, stop it, restore Android and repeat the same Bluetooth check.
Any ownership or service timeout is a stop condition and requires recovery;
there is no safe basis for a live handoff on the current phone image.

## Continuation: package and backup validation

The live BOOT image was parsed with the family tree's Android Image Kitchen
tools and an unchanged `--original --origsize` round-trip reproduced the
exact live BOOT hash. A candidate package using the Bluetooth `Image` and
family `dtb.img` was also parsed successfully and fits the BOOT partition, but
its DTB and configuration differ from the handset. Details and hashes are in
[`research/boot-package-validation-2026-10-27.md`](boot-package-validation-2026-10-27.md)
and [`research/installation-manifest-2026-10-27.md`](installation-manifest-2026-10-27.md).

The TWRP ramdisk is now confirmed as static content in RECOVERY, but no
recovery boot or Download Mode USB session was performed. The installation
decision remains blocked by the exact source/ABI, dm-verity/configuration,
durable backup, and restore-procedure gates documented in
[`research/readiness-gates-2026-10-27.md`](readiness-gates-2026-10-27.md).
