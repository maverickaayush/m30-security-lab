#!/usr/bin/env bash
set -euo pipefail

# Offline only: retain the live Magisk ramdisk, boot header, and embedded DTBH.
# All inputs and output must be private, outside Git. Never writes a device.
LIVE_BOOT=${LIVE_BOOT:?set LIVE_BOOT to the verified BOOT.img backup}
KERNEL_IMAGE=${KERNEL_IMAGE:?set KERNEL_IMAGE to the offline Image}
AIK=${AIK:?set AIK to the inspected Android Image Kitchen directory}
WORK=${WORK:?set WORK to a new private output directory}
EXPECTED_LIVE_SHA256=a63676cd0ed5d90cfca66aaa744c173886b72606aec64ea174575f44250064d4

test ! -e "$WORK" || { echo 'WORK must not exist' >&2; exit 2; }
test -r "$LIVE_BOOT" && test -r "$KERNEL_IMAGE" && test -x "$AIK/unpackimg.sh"
test "$(stat -c %s "$LIVE_BOOT")" -eq 33554432
test "$(sha256sum "$LIVE_BOOT" | cut -d' ' -f1)" = "$EXPECTED_LIVE_SHA256"

# This exact live Magisk BOOT has the legacy-SAR kernel patch
# skip_initramfs -> want_initramfs. A raw family Image without that patch
# discards the preserved Magisk ramdisk. The first boot trial exposed this
# mismatch, so reject such a package before repacking.
if rg -aq 'want_initramfs' "$LIVE_BOOT" && ! rg -aq 'want_initramfs' "$KERNEL_IMAGE"; then
  echo 'Kernel lacks the want_initramfs patch present in the live Magisk BOOT.' >&2
  echo 'Prepare and verify a patched kernel copy before packaging.' >&2
  exit 2
fi
mkdir -m 700 "$WORK"
mkdir -m 700 "$WORK/roundtrip" "$WORK/candidate"

for phase in roundtrip candidate; do
  cp -a "$AIK"/. "$WORK/$phase"/
  cp "$LIVE_BOOT" "$WORK/$phase/live-boot.img"
  (
    cd "$WORK/$phase"
    unpack_status=0
    timeout 60s ./unpackimg.sh --local --nosudo live-boot.img > unpack.log 2>&1 || unpack_status=$?
    test -f split_img/live-boot.img-zImage
    test -f split_img/live-boot.img-dt
    test -f split_img/live-boot.img-ramdisk.cpio.gz
    if [ "$unpack_status" -ne 0 ]; then
      # Magisk's .backup is mode 000; unprivileged cpio cannot expand it.
      # --original repacks the untouched compressed ramdisk; the unchanged
      # round-trip cmp below is the acceptance check for the split operation.
      grep -q '^cpio: .backup/.*Cannot open: Permission denied$' unpack.log
      grep -q '^Unpacking failed, try without --nosudo\.' unpack.log
    fi
    if [ "$phase" = candidate ]; then
      cp "$KERNEL_IMAGE" split_img/live-boot.img-zImage
    fi
    timeout 60s ./repackimg.sh --local --original --origsize > repack.log 2>&1
    test "$(stat -c %s image-new.img)" -eq 33554432
  )
done

cmp "$LIVE_BOOT" "$WORK/roundtrip/image-new.img"
mkdir -m 700 "$WORK/candidate/verified-unpack"
"$AIK/bin/linux/x86_64/unpackbootimg" -i "$WORK/candidate/image-new.img" \
  -o "$WORK/candidate/verified-unpack" > "$WORK/candidate/verify.log" 2>&1
cmp "$KERNEL_IMAGE" "$WORK/candidate/verified-unpack/image-new.img-zImage"
cmp "$WORK/roundtrip/split_img/live-boot.img-dt" \
  "$WORK/candidate/verified-unpack/image-new.img-dt"
cmp "$WORK/roundtrip/split_img/live-boot.img-ramdisk.cpio.gz" \
  "$WORK/candidate/verified-unpack/image-new.img-ramdisk.gz"
sha256sum "$LIVE_BOOT" "$KERNEL_IMAGE" "$WORK/candidate/image-new.img" \
  "$WORK/candidate/verified-unpack/image-new.img-dt" \
  "$WORK/candidate/verified-unpack/image-new.img-ramdisk.gz"
echo 'Offline round-trip and candidate component checks passed. No device write performed.'
