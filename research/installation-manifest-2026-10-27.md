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
| Earlier Bluetooth candidate `Image` | 21,358,856 | `9768a67f1704d133fa4a802b7ed65afdb0b97583a2419132e20b72a56e806e30` | historical family-config build; not installed |
| Earlier candidate padded package | 33,554,432 | `acfdba99619651904d11dfbd757509c3fa72a205dfc384e2bf36a64c22c3fc61` | historical offline package only |
| Earlier candidate unpadded package | 23,025,680 | `3fab089f42bdcae56969ed25292c7a9e8867f9cc54e1f0bdf419e186b5877693` | historical offline inspection only |
| Corrected `bluez-verity` `Image` | 21,243,016 | `a68e3eddfcfb90dc231a2eb8c97dda1a694d6b2e807bf0bdab417cd8063b377e` | volatile host tmpfs; not installed |
| Corrected `bluez-verity` `dtb.img` | 655,360 | `acb171e3ba68dbfbdad7ef51cb52aa8b0da85ead138e1c0f68b42c0a159f2487` | volatile host tmpfs |
| Corrected candidate `.config` | 144,193 | `0e39e99395ba31cdfa58ddc52fcfa676fb9ebc500624d4fdcacfeefc22e62c29` | offline build input/output |
| Corrected padded boot package | 33,554,432 | `988749cf794048d3443475dc7398df3f63a53e2ee0b2a083616e1c7148c6cd41` | offline package only |
| Corrected unpadded package | 22,908,944 | `f6b4e2efe0050ac7de825f01d1db73503ddc28ecf8be0308accba38f44c22d8e` | offline inspection only |

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

The initial host copy was on volatile tmpfs. A persistent project-local copy
was then created at the ignored path
`backups/private-20261027/` with directory mode 0700 and image/manifest mode
0600. Independent sorted digest comparison between the volatile source and
that persistent copy returned exit 0. The phone copy is still present under
the user-data backup directory. No separate physical storage location is
available, so backup redundancy remains a failed gate. A hash proves copy
equality; it does not prove that Samsung's download-mode tooling would accept
or restore the image.

DTB and DTBO are distinct 8 MiB partitions (`mmcblk0p14` and `mmcblk0p15`),
not aliases. Their identical hashes are genuine: bounded comparisons showed
both partitions are zero-filled. The active device-tree data is the DTBH
container embedded in BOOT. The detailed path and sampling evidence is kept
in the ignored `backups/private-20261027/dtb-investigation.txt` file.

## Installation status

Installation is blocked. The corrected candidate narrows the configuration
delta by restoring dm-verity and the observed USB/debug choices, but the exact
source/ABI, Samsung package/signing requirements, family DTB values, recovery
return path, exact stock package bytes and independent backup location remain
unresolved. No partition write, reboot-to-recovery or controller handoff was
performed.

## Later package and Download Mode results

The sentence above describes the earlier offline-only checkpoint. The exact
FUS package was subsequently acquired under the ignored persistent path
`backups/stock-ins-cvb2/` (0700 directory and 0600 files). Archive SHA-256:
`b8090882a4564b5836ffdb38ae936c5412fe8325977e70a299925ce546806d6b`.
All five ZIP members passed CRC checks and all five Samsung TAR MD5 footers
matched. AP/BL/CP/CSC/HOME_CSC remain inside that archive; no full extraction
or flash occurred. The archive and local hashes are detailed in the
[stock package inspection](stock-package-results-20261027.md).

The user entered Download Mode and the laptop identified USB ID 04e8:685d.
Samloader detected SM-M305F and read an 8,192-byte PIT. The user then returned
to Android; the original build/kernel, SELinux enforcing and Bluetooth-on state
were confirmed. This validates access and return, not a firmware restore.
The remaining installation blockers are the public family's security/FMP/
watchdog configuration differences, DTBH clock/regulator differences,
independent backup storage and a tested recovery boot/return. See the current
[gate table](readiness-gates-2026-10-27.md).
