# Minimal M30 Linux Bluetooth candidate: offline build and package

**No flashing or boot test occurred.** On 2026-10-27 the public M30-family tree at revision `095206579f69121278ed1dd5fd0d7975c0705c6a` was rebuilt in separate volatile output directories. Toolchain: Ubuntu AArch64 GCC 15.2.0 / binutils 2.46, `dtc` 1.7.2, host `make`; `JOBS=4`. The source and cross-compiler were restored into `/tmp` after a previous logout removed volatile build directories. Build logs were captured outside Git. The stock and live backup artifacts stayed on persistent storage.

| Artifact | Baseline SHA-256 | Minimal Bluetooth SHA-256 |
|---|---|---|
| `Image` | `6cabe9ec5a449024fa2bc3dbd5edb5840cb6308c765b02343658bad7457b423e` | `e6895c5f4795985e9cf827de5a6537ecb21916fa6eb6d9e791fdef04281244f6` |
| `Image.gz` | `ef25bd03536c0b3178cdd2ab56ee310455be196a42f01a12679b2dd94baba2b6` | `a479c0a5817f8ea513376ed9885c3f53ee2932197e93dd9b248b2d920d5784b6` |
| generated family `dtb.img` | `acb171e3ba68dbfbdad7ef51cb52aa8b0da85ead138e1c0f68b42c0a159f2487` | same; **excluded from boot package** |
| `vmlinux` | `52e729c8f36be2236bab9e39272a10b298dc13ce4533b53751fcfafb6af49a53` | `5eef304a45b656d84b121cf5319041f49af72b8a0ac7d78c4a3ccdf080edb04b` |
| `System.map` | `935f41981f48149f85df22b5a7060b0f8659ff21554840e041ff3750cdcf4d4c` | `97e41dedd5514d891c5ad374e7544b356e40b4738dab7211b9cd79087dc6fcb0` |
| effective `.config` | `f991a0b0e11a3a3780814e973ea43843fd056c8c258e40db69084ffa3e590334` | `0566aa7ae2ab32065378ff27c88554a7c09a3e08558b59d5e02130e48a336e11` |

The baseline and minimal builds exited 0 and produced `Image`, `Image.gz`, `vmlinux`, `System.map`, four FDTs/`dtb.img` and the single family test module. The build emitted GCC 15 warnings, an RWX `vmlinux` load-segment linker warning, and `firmware/tsp_zinitix` missing-directory warning; these were not hidden. Compilation does not establish the running Samsung ABI, firmware compatibility, boot success or HCI registration. The unmodified baseline itself has **40** normalized symbol differences from the live config, demonstrating that family defconfig alone is not a stock baseline.

## Effective configuration outcome

The previous `bluez-verity` candidate had 46 normalized differences from the live config. The new `bluez-minimal` candidate has **29**, of which 13 are newly visible but disabled Bluetooth suboptions and two are missing disabled SCSC WLAN options. Fourteen entries have a live/new enabled value or build metadata: six intended Bluetooth options (`BT`, BR/EDR, LE, RFCOMM, RFCOMM TTY, SCSC BlueZ); six family DTBH/platform build options; and the two genuinely unresolved enabled live options `FMM_LOCK_OFFSET` and `SEC_DEBUG_SOFTDOG_PWDT`. The fragment and effective `.config` agree on `DM_VERITY`, `DEFEX_KERNEL_ONLY`, `EXYNOS_KERNEL_PROTECTION`, `KNOX_KAP`, `TIMA`, `TIMA_LOG`, `TZ_ICCC`, `SOFT_WATCHDOG`, `EXYNOS_FMP_FIPS=n`, `USB_OTG=n`, no SCSC static debug ring, and `SCSC_BT_BLUEZ=y`. This is a normalized config comparison, not proof of equivalence in compiled code or secure-world behavior.

`Image` is 20,879,112 bytes, versus baseline 20,947,080 and the live embedded stock-derived kernel 19,473,008. The difference in size is another reminder that exact compiler/source/ABI matching is not established. Kernel release strings were not edited to resemble the running release.

## Boot container check

`scripts/package_m30_minimal.sh` uses the inspected family Android Image Kitchen. It first repacked the private live BOOT unchanged and required byte identity with the 32 MiB source image (SHA-256 `a63676cd0ed5d90cfca66aaa744c173886b72606aec64ea174575f44250064d4`). Magisk's mode-000 `.backup` prevents unprivileged expansion by `cpio`, so the script uses AIK `--original`: the compressed ramdisk is kept untouched, and **byte-identical unchanged repack** is the acceptance check. It then replaced only `zImage` with the minimal `Image`, retained the live embedded DTBH and original ramdisk, repacked to the 32 MiB partition size, and re-extracted all components for `cmp` verification.

| Final private artifact | SHA-256 |
|---|---|
| Candidate padded `boot.img` | `9cf8c2159324796f3fb5fa993c7abdc5438edc59f8245ea602c99a429aea81c9` |
| Re-extracted kernel | `e6895c5f4795985e9cf827de5a6537ecb21916fa6eb6d9e791fdef04281244f6` |
| Re-extracted **live** DTBH | `80bcef6b1c78693944a832d77a1674c791e277dbe154d5dcec22929346215711` |
| Re-extracted **live** compressed Magisk ramdisk | `f5e345b1e350fd445050118e30c3aab1b83c669e79f6630bb3281fe3bd6b9dcf` |

The baseline Image/config and final minimal Image/config/boot package were copied to the ignored, mode-0700 `backups/private-20261027/` directory with mode-0600 files and rehashed. The full build outputs remain volatile under `/tmp` and `/dev/shm`; these private images are excluded from Git. Persistent host space was ~58 MiB after preservation, so further large builds should use additional storage.

Reproduce the build with a clean checkout of the pinned source and a compatible AArch64 toolchain:

```sh
SRC=/tmp/m30-family-kernel OUT=/tmp/m30-baseline CROSS_COMPILE=/tmp/m30-cross/usr/bin/aarch64-linux-gnu- JOBS=4 timeout 1200s scripts/build_m30_kernel.sh baseline
SRC=/tmp/m30-family-kernel OUT=/dev/shm/m30-minimal CROSS_COMPILE=/tmp/m30-cross/usr/bin/aarch64-linux-gnu- JOBS=4 timeout 1200s scripts/build_m30_kernel.sh bluez-minimal
LIVE_BOOT="$PRIVATE_CURRENT_BOOT" KERNEL_IMAGE=/dev/shm/m30-minimal/arch/arm64/boot/Image AIK=/tmp/m30-family-kernel/AIK-Linux WORK=/dev/shm/new-private-work timeout 180s scripts/package_m30_minimal.sh
```

The packaging command deliberately requires the exact private live BOOT hash; a different handset/build must be reviewed and the script updated, not forced past the check. No command above writes a device.
