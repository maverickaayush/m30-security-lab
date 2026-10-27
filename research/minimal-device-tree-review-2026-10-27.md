# Embedded DTBH compatibility review

Offline analysis only. The live BOOT partition contains the active 655,360-byte Samsung DTBH; the separate DTB and DTBO partitions are distinct, zero-filled 8 MiB partitions. No device tree or partition was written. Compared the four live DTBH FDTs with the four family-built FDTs from source revision `095206579f69121278ed1dd5fd0d7975c0705c6a` using `dtc` 1.7.2. The exact INS stock AP and live DTBH have the same board values; their only decoded differences are the live Magisk/verity `fsmgr_flags` changes documented in `stock-package-results-20261027.md`.

| Variant | FDT offset | Live FDT size | Family FDT size | Board revision selection |
|---|---:|---:|---:|---|
| rev00 | 2,048 | 162,088 | 162,067 | 0 |
| rev01 | 165,888 | 162,152 | 162,130 | 1–2 |
| rev02 | 329,728 | 162,016 | 161,994 | 3 |
| rev03 | 493,568 | 160,172 | 160,149 | 4–255 |

Every paired variant has identical structural differences: **none** in node paths, reserved-memory locations, storage/USB nodes, SCSC connectivity resources or board-selection ranges. The decoded differences are the same in all four variants:

| Property | Live value | Family value | Source consumer and interpretation |
|---|---:|---:|---|
| `gpu_max_clock` | 845,000 | 1,300,000 | `drivers/gpu/arm/b_r19p0/platform/exynos/gpu_custom_interface.c` uses the property for DVFS ranges; family value raises the ceiling. |
| `gpu_max_clock_limit` | 845,000 | 1,300,000 | Same file caps requested GPU clocks against this value; keep the working limit. |
| CPU cluster `min-freq` 1 | 449,000 | 343,000 | `drivers/cpufreq/exynos-acme.c` takes the greater of CAL and DT minima; changing it can lower idle floor. |
| CPU cluster `min-freq` 2 | 936,000 | 520,000 | Same driver; changing it can lower second-cluster floor. |
| camera rail 1 | `VDDDF_1.05V_CAM` | `FCAM_DVDD_1P05` | Selected `fimc-legacy` 3p8sp module requests the live name. |
| camera rail 2 | `VDDDR1_1.05V_CAM` | `RCAM1_DVDD_1P05` | Selected 3l6 module requests the live name. |
| camera rail 3 | `VDDIOR1_1.8V_CAM` | `CAM_VDDIO_1P8` | Selected 3p8sp, 3l6, gc5035 and 5e9 modules request the live name. |
| camera rail 4 | `VDDDR2_1.2V_CAM` | `RCAM2_DVDD_1P2` | Selected 5e9 module requests the live name. |
| camera rail 5 | `VDDAF_2.8V_CAM` | `FCAM_AVDD_2P8` | Legacy autofocus and 3p8sp code request the live name. |
| camera rail 6 | `VDDAR1_2.8V_CAM` | `RCAM1_AVDD_2P8` | Selected 3l6 module requests the live name. |
| camera rail 7 | `VDDAR2_2.8V_CAM` | `RCAM2_AVDD_2P8` | Selected 5e9 module requests the live name. |
| SYSTEM/VENDOR/ODM `fsmgr_flags` | NUL-padded `wait` | `wait` string | Equivalent decoded flag, but retain exact live bytes to preserve current Magisk/verity behavior. |

The family source selects `CONFIG_VIDEO_EXYNOS_FIMC_LEGACY=y` and sensor objects 3p8sp, 3l6, gc5035 and 5e9. Their compiled `SET_PIN(..., PIN_REGULATOR, ...)` calls use the **live** regulator names. This is positive source evidence for retaining the live DTBH and negative evidence for replacing it with the family DTBH. The broader `fimc-is2` tree also contains newer names, but that driver is disabled in this config. `platform_mif.c` consumes `exynos,wifibt_if`, `exynos,btabox_rmem`, shared memory, mailboxes and IRQs; these properties and resources are identical in the paired FDTs.

**Decision:** package the candidate `Image` with the unmodified live embedded DTBH and live Magisk ramdisk. This removes the identified clock, regulator, and fstab changes. It does not prove every source/DT ABI or firmware interface is compatible at runtime. No assumption is made that the zero-filled DTB/DTBO partitions should be written.

Reproduction: extract live and family DTBHs, split at the four offsets above using each FDT's big-endian `totalsize`, decode with `dtc -I dtb -O dts`, then compare the corresponding `.dts` files. Keep all extracted blobs and decoded device trees private; the analysis above contains no device identifiers.
