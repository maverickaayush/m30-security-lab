#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
CC=${CC:-/tmp/m30-cross/usr/bin/aarch64-linux-gnu-gcc}
SYSROOT=${SYSROOT:-"$ROOT/host/toolchain/phone-libc/root"}
OUT=${OUT:?choose a new private output file}
test ! -e "$OUT"
test -r "$SYSROOT/usr/aarch64-linux-gnu/include/stdio.h" || {
  echo "missing AArch64 sysroot; set SYSROOT to an extracted libc6-dev-arm64-cross tree" >&2
  exit 2
}
export LD_LIBRARY_PATH=/tmp/m30-cross/usr/lib/x86_64-linux-gnu
"$CC" --sysroot="$SYSROOT" -isystem "$SYSROOT/usr/aarch64-linux-gnu/include" \
  -B"$SYSROOT/usr/aarch64-linux-gnu/lib/" -L"$SYSROOT/usr/aarch64-linux-gnu/lib" \
  -I"$ROOT/host/toolchain/bluez-headers" -O2 -Wall -Wextra -Werror -static \
  "$ROOT/host/phone_linux_lab.c" -o "$OUT"
file "$OUT"
sha256sum "$OUT"
