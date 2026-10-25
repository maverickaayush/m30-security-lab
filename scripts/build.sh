#!/usr/bin/env bash
set -euo pipefail
base="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME="$base/host/toolchain/jdk21"
export ANDROID_HOME="$base/host/toolchain/sdk"
cd "$base/android"
"$base/scripts/bootstrap-signing.sh"
timeout 300 "$base/host/toolchain/gradle-8.10.2/bin/gradle" --no-daemon --console=plain assembleDebug
sha256sum app/build/outputs/apk/debug/app-debug.apk
