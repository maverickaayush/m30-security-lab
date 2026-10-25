#!/usr/bin/env bash
set -euo pipefail
base="$(cd "$(dirname "$0")/.." && pwd)"
dir="$base/host/toolchain"
mkdir -p "$dir"
props="$dir/signing.properties"
store="$dir/m30-lab-debug.jks"
if [[ -e "$props" || -e "$store" ]]; then
  [[ -f "$props" && -f "$store" ]] || { echo "Incomplete signing state; inspect $dir" >&2; exit 1; }
  echo "Existing project signing key retained"
  exit 0
fi
password="$(openssl rand -hex 24)"
umask 077
"$dir/jdk21/bin/keytool" -genkeypair -noprompt -keystore "$store" -storetype PKCS12 \
  -alias m30-lab-debug -keyalg RSA -keysize 3072 -validity 3650 \
  -dname 'CN=M30 Bluetooth Lab,O=Local Research' -storepass "$password" -keypass "$password"
printf 'storePassword=%s\nkeyPassword=%s\n' "$password" "$password" > "$props"
echo "Created project-only signing key"
