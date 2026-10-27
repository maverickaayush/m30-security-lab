#!/usr/bin/env bash
set -euo pipefail

# Reproducible offline build gate for the public M30-family source tree.
# This script produces build artifacts only; it never flashes or repacks a phone.
# Usage:
#   SRC=/tmp/m30-family-kernel OUT=/tmp/m30-kout \
#   CROSS_COMPILE=/tmp/m30-cross/usr/bin/aarch64-linux-gnu- \
#   timeout 1200s scripts/build_m30_kernel.sh baseline
#   ... scripts/build_m30_kernel.sh bluez
#   ... scripts/build_m30_kernel.sh bluez-verity
#   ... scripts/build_m30_kernel.sh bluez-minimal
#   ABOX_IO_PATCH=1 ... scripts/build_m30_kernel.sh bluez-minimal

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
SRC=${SRC:?set SRC to the checked-out kernel source}
OUT=${OUT:?set OUT to an isolated output directory}
CROSS_COMPILE=${CROSS_COMPILE:?set CROSS_COMPILE to an AArch64 prefix}
MODE=${1:-baseline}
EXPECTED_COMMIT=095206579f69121278ed1dd5fd0d7975c0705c6a
ABOX_IO_PATCH=${ABOX_IO_PATCH:-0}

case "$MODE" in
  baseline|bluez|bluez-verity|bluez-minimal) ;;
  *) echo "mode must be baseline, bluez, bluez-verity or bluez-minimal" >&2; exit 2 ;;
esac

if ! git -C "$SRC" diff --quiet || [ -n "$(git -C "$SRC" status --porcelain --untracked-files=all)" ]; then
  echo "source tree must be clean before this isolated build" >&2
  exit 2
fi
if [ "$(git -C "$SRC" rev-parse HEAD)" != "$EXPECTED_COMMIT" ]; then
  echo "warning: source commit is not the recorded family revision $EXPECTED_COMMIT" >&2
fi

export PATH="$(dirname "$CROSS_COMPILE"):$PATH"
export LD_LIBRARY_PATH="${LD_LIBRARY_PATH:-$(dirname "$(dirname "$CROSS_COMPILE")")/lib/x86_64-linux-gnu}"
HOSTCFLAGS=${HOSTCFLAGS:-'-Wall -Wmissing-prototypes -Wstrict-prototypes -O2 -fomit-frame-pointer -std=gnu89 -fcommon'}
KCFLAGS=${KCFLAGS:-'-Wno-error -Wno-error=enum-int-mismatch -Wno-error=restrict -Wno-error=stringop-overflow -Wno-error=array-bounds -Wno-error=array-compare -Wno-error=address-of-packed-member -Wno-error=misleading-indentation -Wno-error=zero-length-bounds -Wno-error=dangling-pointer -D__ANDROID__'}

cleanup_source() {
  if [ "$ABOX_IO_PATCH" = 1 ]; then
    git -C "$SRC" apply --reverse "$ROOT/research/kernel/abox-io-copy.patch" 2>/dev/null || true
  fi
  if [ "$MODE" = bluez ] || [ "$MODE" = bluez-verity ] || [ "$MODE" = bluez-minimal ]; then
    git -C "$SRC" checkout -- drivers/misc/samsung/scsc_bt/scsc_bluez.c
  fi
  find "$SRC/scripts/fmp" -type f -name '*.pyc' -delete 2>/dev/null || true
  find "$SRC/scripts/fmp" -type d -name __pycache__ -empty -delete 2>/dev/null || true
}
trap cleanup_source EXIT

make -C "$SRC" O="$OUT" ARCH=arm64 ANDROID_MAJOR_VERSION=q \
  CROSS_COMPILE="$CROSS_COMPILE" HOSTCFLAGS="$HOSTCFLAGS" m30lte_00_defconfig

if [ "$MODE" = bluez ] || [ "$MODE" = bluez-verity ] || [ "$MODE" = bluez-minimal ]; then
  git -C "$SRC" apply "$ROOT/research/kernel/scsc_bluez-family-compat.patch"
  git -C "$SRC" apply "$ROOT/research/kernel/scsc_bluez-send-lifetime.patch"
  FRAGMENT="$ROOT/research/kernel/m30lte-hci.fragment"
  if [ "$MODE" = bluez-verity ]; then
    FRAGMENT="$ROOT/research/kernel/m30lte-hci-verity.fragment"
  elif [ "$MODE" = bluez-minimal ]; then
    FRAGMENT="$ROOT/research/kernel/m30lte-hci-minimal.fragment"
  fi
  (cd "$OUT" && "$SRC/scripts/kconfig/merge_config.sh" -m -O "$OUT" \
    "$OUT/.config" "$FRAGMENT")
  make -C "$SRC" O="$OUT" ARCH=arm64 ANDROID_MAJOR_VERSION=q \
    CROSS_COMPILE="$CROSS_COMPILE" HOSTCFLAGS="$HOSTCFLAGS" olddefconfig
fi

if [ "$ABOX_IO_PATCH" = 1 ]; then
  git -C "$SRC" apply "$ROOT/research/kernel/abox-io-copy.patch"
fi

make -C "$SRC" O="$OUT" ARCH=arm64 ANDROID_MAJOR_VERSION=q \
  CROSS_COMPILE="$CROSS_COMPILE" HOSTCFLAGS="$HOSTCFLAGS" KCFLAGS="$KCFLAGS" \
  -j"${JOBS:-$(nproc)}"

printf '\nArtifacts:\n'
find "$OUT/arch/arm64/boot" -maxdepth 2 -type f \( -name Image -o -name Image.gz -o -name dtb.img -o -name '*.dtb' \) -print0 |
  xargs -0r sha256sum
sha256sum "$OUT/vmlinux" "$OUT/System.map" "$OUT/.config"
printf '\nSelected configuration:\n'
grep -E '^(CONFIG_BT|CONFIG_BT_BREDR|CONFIG_BT_LE|CONFIG_BT_RFCOMM|CONFIG_BT_RFCOMM_TTY|CONFIG_BT_HCIVHCI|CONFIG_SCSC_BT|CONFIG_SCSC_BT_BLUEZ|CONFIG_MODULES)=' "$OUT/.config" || true
