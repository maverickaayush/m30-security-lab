#!/usr/bin/env bash
set -euo pipefail

# Offline preparation for this M30's observed Magisk legacy-SAR patch.
# Creates a new private copy; never alters the build Image or a device.
SOURCE_IMAGE=${1:?usage: prepare_magisk_kernel.sh SOURCE_IMAGE OUTPUT_IMAGE}
OUTPUT_IMAGE=${2:?usage: prepare_magisk_kernel.sh SOURCE_IMAGE OUTPUT_IMAGE}

test -r "$SOURCE_IMAGE"
test ! -e "$OUTPUT_IMAGE" || { echo 'OUTPUT_IMAGE already exists' >&2; exit 2; }
test -d "$(dirname "$OUTPUT_IMAGE")"

mapfile -t matches < <(rg -aob --no-filename 'skip_initramfs' "$SOURCE_IMAGE")
test "${#matches[@]}" -eq 1 || {
  echo "Expected one skip_initramfs marker; found ${#matches[@]}" >&2
  exit 2
}
if rg -aq 'want_initramfs' "$SOURCE_IMAGE"; then
  echo 'Source already contains want_initramfs; refusing mixed markers' >&2
  exit 2
fi

offset=${matches[0]%%:*}
umask 077
cp --reflink=never "$SOURCE_IMAGE" "$OUTPUT_IMAGE"
printf want | dd of="$OUTPUT_IMAGE" bs=1 seek="$offset" conv=notrunc status=none

test "$(stat -c %s "$SOURCE_IMAGE")" -eq "$(stat -c %s "$OUTPUT_IMAGE")"
test "$(rg -aoc --no-filename 'want_initramfs' "$OUTPUT_IMAGE")" -eq 1
! rg -aq 'skip_initramfs' "$OUTPUT_IMAGE"
test "$(cmp -l "$SOURCE_IMAGE" "$OUTPUT_IMAGE" | wc -l)" -eq 4

sha256sum "$SOURCE_IMAGE" "$OUTPUT_IMAGE"
printf 'Patched offset 0x%x; exactly four bytes changed. Offline only.\n' "$offset"
