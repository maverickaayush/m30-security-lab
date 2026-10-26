# M30 installation manifest

Checked 2026-10-27. This manifest contains hashes and device/build facts only;
serial numbers, Bluetooth addresses, firmware binaries and backup contents are
kept outside Git.

## Handset

| Field | Observed value |
|---|---|
| Manufacturer/model | Samsung SM-M305F |
| Device/product | `m30lte` / `m30ltedd` |
| Board/platform | Exynos 7904 / `universal7904` |
| Android/API | Android 10 / SDK 29 |
| Build | `M305FDDS8CVB2` |
| Security patch | 2022-03-01 |
| Running kernel | `4.4.177-22392479`, arm64, 2022-02-21 |
| Root/SELinux | Magisk 30.7; SELinux enforcing |
| Verified boot state | `orange`; `ro.boot.flash.locked=0`; no slot suffix |
| Battery/USB | 100%, AC powered, authorized ADB over USB |
| Android Bluetooth | On; Android HAL owns `/dev/scsc_h4_0` |

## Source and candidate

The compiled source is the public family tree
[`akhil1999/android_kernel_samsung_m30lte@0952065`](https://github.com/akhil1999/android_kernel_samsung_m30lte/tree/095206579f69121278ed1dd5fd0d7975c0705c6).
It is not established as the exact Samsung source package for this firmware.

| Artifact | Size | SHA-256 | Location/status |
|---|---:|---|---|
| Baseline `Image` | 21,045,384 | `415d42710fe18d4fb2f07598a68be16dd964a663a8087fcb2254dfd854965a54` | volatile host tmpfs; not installed |
| Bluetooth candidate `Image` | 21,358,856 | `9768a67f1704d133fa4a802b7ed65afdb0b97583a2419132e20b72a56e806e30` | volatile host tmpfs; not installed |
| Candidate `dtb.img` | 655,360 | `acb171e3ba68dbfbdad7ef51cb52aa8b0da85ead138e1c0f68b42c0a159f2487` | volatile host tmpfs |
| Candidate padded boot package | 33,554,432 | `acfdba99619651904d11dfbd757509c3fa72a205dfc384e2bf36a64c22c3fc61` | offline package only |
| Candidate unpadded package | 23,025,680 | `3fab089f42bdcae56969ed25292c7a9e8867f9cc54e1f0bdf419e186b5877693` | offline inspection only |

The package retained the stock Magisk ramdisk and Samsung boot metadata. It
has not been copied to the phone or flashed.

## Rollback copies

The following raw partitions were copied read-only through root to the phone's
internal user-data storage, then pulled to `/dev/shm/m30-backups-20261027`.
The live-source hash, phone-copy hash and host-copy hash matched for every
entry.

| Partition | Size | SHA-256 |
|---|---:|---|
| BOOT | 33,554,432 | `a63676cd0ed5d90cfca66aaa744c173886b72606aec64ea174575f44250064d4` |
| DTB | 8,388,608 | `2daeb1f36095b44b318410b3f4e8b5d989dcc7bb023d1426c492dab0a3053e74` |
| DTBO | 8,388,608 | `2daeb1f36095b44b318410b3f4e8b5d989dcc7bb023d1426c492dab0a3053e74` |
| RECOVERY | 39,845,888 | `c0ebb7d6700c6f7ed218d933ab380e5082c5a29a49c05a33b483858c3c6cd311` |

The host copy is on volatile tmpfs and is not a durable second-storage copy.
No separate mounted disk was available, so recovery redundancy remains a
failed gate. The phone copy is still present under the user-data backup
directory. A hash proves copy equality; it does not prove that Samsung's
download-mode tooling would accept or restore the image.

## Installation status

Installation is blocked. The candidate changes the kernel and embedded DTB,
but the exact source/ABI, Samsung package/signing requirements, recovery
return path and durable rollback copy are unresolved. No partition write,
reboot-to-recovery or controller handoff was performed.
