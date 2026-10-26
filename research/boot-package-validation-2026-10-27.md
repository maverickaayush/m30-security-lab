# Offline boot-package validation

Checked 2026-10-27 using a read-only copy of the live BOOT partition and the
public family-tree candidate. No image was written to the handset.

## Stock package structure

Android Image Kitchen's pinned copy in the family source recognized the live
BOOT partition as an AOSP Android boot image. Its parsed fields were:

| Field | Value |
|---|---|
| Page size | 2048 bytes |
| Kernel | uncompressed arm64 `Image`, 19,473,008 bytes |
| Ramdisk | gzip, 1,006,312 bytes |
| Second stage | absent |
| Embedded DTB container | 655,360 bytes, Samsung `DTBH` format |
| Board | `SRPRK14G008KU` |
| Base/offsets | `0x10000000`, kernel `0x8000`, ramdisk `0x01000000`, tags `0x100` |
| OS metadata | Android 10.0.0, patch level 2022-03 |
| Command line | `androidboot.selinux=permissive` repeated twice |
| Footer | `SEANDROIDENFORCE` |

The ramdisk contains Magisk overlay files (`overlay.d/sbin/magisk.xz`,
`init-ld.xz`, `stub.xz`) and a private `.backup` directory. This explains why
the raw boot partition is not a stock unmodified boot image. The running
kernel nevertheless reports SELinux enforcing; the command line and Magisk
early-init behavior must be preserved and understood before repacking.

The RECOVERY partition has the same AOSP-style container and a TWRP recovery
ramdisk, including `teamwin`, TWRP resources and recovery fstab. This is static
content evidence only; recovery has not been boot-tested.

## Round-trip test

The family-source Android Image Kitchen scripts were copied into volatile
tmpfs and inspected before use. An unchanged `--original --origsize` repack
using the extracted kernel, original gzip ramdisk and original embedded DTB
produced a 33,554,432-byte image with the exact live BOOT hash:

`a63676cd0ed5d90cfca66aaa744c173886b72606aec64ea174575f44250064d4`

This establishes that the observed container can be parsed and reproduced by
that toolchain. It does not establish Samsung signature acceptance or a valid
restore path.

## Candidate package

The candidate `Image` and candidate `dtb.img` were substituted into the same
stock metadata scaffold while retaining the original Magisk ramdisk. The
result:

| Check | Result |
|---|---|
| Padded package size | 33,554,432 bytes, equal to BOOT partition size |
| Unpadded package | 23,025,680 bytes |
| Padded package SHA-256 | `acfdba99619651904d11dfbd757509c3fa72a205dfc384e2bf36a64c22c3fc61` |
| Kernel extracted from package | SHA-256 matches candidate `Image` |
| DTB extracted from package | SHA-256 matches candidate `dtb.img` |
| Ramdisk extracted from package | SHA-256 `f5e345b1e350fd445050118e30c3aab1b83c669e79f6630bb3281fe3bd6b9dcf`, identical to live package extraction |
| Footer and metadata | `SEANDROIDENFORCE`, stock board, offsets and command line preserved |

The candidate embedded DTB differs from the live embedded DTB:

- live embedded DTB hash: `80bcef6b1c78693944a832d77a1674c791e277dbe154d5dcec22929346215711`
- family candidate DTB hash: `acb171e3ba68dbfbdad7ef51cb52aa8b0da85ead138e1c0f68b42c0a159f2487`

That difference is expected from using a different source tree, but it is a
boot compatibility risk. The candidate also has a family-tree local version
(`4.4.177-g09520657-dirty`) rather than the running release
`4.4.177-22392479`.

## Unresolved package gates

The live configuration has `CONFIG_DM_VERITY=y` and
`CONFIG_DM_ANDROID_VERITY_AT_MOST_ONCE_DEFAULT_ENABLED=y`. The family
Bluetooth candidate has `CONFIG_DM_VERITY` disabled and enables `CONFIG_USB_OTG`
and several family debug/static-ring options that differ from the handset.
Team Win's M30 page also identifies dm-verity as a device boot concern
([Team Win device notes](https://twrp.me/samsung/samsunggalaxym30.html)).

The candidate has not been signed by Samsung tooling, checked against the
device's bootloader acceptance rules, or tested with the installed vendor
firmware and ramdisk. The package is therefore structurally validated but not
installation-ready. AOSP documents that boot headers, DTB placement and
verification rules are device and Android-version specific ([AOSP boot image
headers](https://source.android.com/docs/core/architecture/bootloader/boot-image-header),
[AOSP DTB images](https://source.android.com/docs/core/architecture/bootloader/dtb-images)).

## Reproduction commands

The validation used the Android Image Kitchen copy shipped in the pinned
family source. The following is the command shape; paths point to private
volatile copies and must be replaced with local paths after checking hashes:

```sh
AIK=/tmp/m30-family-kernel/AIK-Linux
WORK=/dev/shm/m30-aik-20261027
mkdir -p "$WORK"
cp -a "$AIK"/. "$WORK"/
cp /dev/shm/m30-backups-20261027/BOOT.img "$WORK/stock-boot.img"
cd "$WORK"
./unpackimg.sh --local --nosudo stock-boot.img
./repackimg.sh --local --original --origsize
sha256sum image-new.img stock-boot.img
cp /dev/shm/m30-bluez-artifacts/Image split_img/stock-boot.img-zImage
cp /dev/shm/m30-bluez-artifacts/dtb.img split_img/stock-boot.img-dt
./repackimg.sh --local --original --origsize
sha256sum image-new.img
./bin/linux/x86_64/unpackbootimg -i image-new.img -o candidate-unpack
```

The unchanged round-trip must match the source BOOT hash before the candidate
substitution is considered meaningful. The candidate output must then be
re-parsed and its extracted kernel, DTB and ramdisk hashes compared with the
inputs. Do not use these commands as a flash procedure.
