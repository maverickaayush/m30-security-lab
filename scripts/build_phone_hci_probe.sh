#!/usr/bin/env bash
set -euo pipefail

# Build the narrow phone-side HCI probe with the existing private AArch64
# cross toolchain. The result is kept outside Git by default.
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
CC=${CC:?set CC to an AArch64 compiler, e.g. /tmp/m30-cross/usr/bin/aarch64-linux-gnu-gcc}
OUT=${OUT:?set OUT to a private output path}
SRC="$ROOT/host/phone_hci_probe.c"
test -r "$SRC"
test ! -e "$OUT" || { echo "OUT already exists" >&2; exit 2; }
mkdir -p -m 700 "$(dirname "$OUT")"

# The cross-binutils package is isolated with its shared libraries beside the
# toolchain. Make the standalone helper reproducible without requiring callers
# to remember an environment-specific loader path.
if [ -z "${LD_LIBRARY_PATH:-}" ]; then
  export LD_LIBRARY_PATH="$(dirname "$(dirname "$CC")")/lib/x86_64-linux-gnu"
fi

"$CC" -O2 -Wall -Wextra -Werror -ffreestanding -fno-stack-protector \
  -fno-pie -nostdinc -nostdlib -nodefaultlibs -static -Wl,-e,_start -Wl,--build-id=none \
  "$SRC" -o "$OUT"
file "$OUT"
sha256sum "$OUT"
