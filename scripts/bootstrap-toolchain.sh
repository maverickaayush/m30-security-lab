#!/usr/bin/env bash
set -euo pipefail
base="$(cd "$(dirname "$0")/.." && pwd)"
tools="$base/host/toolchain"
sdk="$tools/sdk"
mkdir -p "$tools" "$sdk/platforms" "$sdk/build-tools"
fetch() { curl --fail --location --silent --show-error --max-time 240 --output "$tools/$1" "$2"; }
if [[ ! -d "$tools/gradle-8.10.2" ]]; then
  fetch gradle.zip https://services.gradle.org/distributions/gradle-8.10.2-bin.zip
  unzip -q "$tools/gradle.zip" -d "$tools"
  rm "$tools/gradle.zip"
fi
if [[ ! -L "$tools/jdk21" ]]; then
  fetch jdk21.tar.gz https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse
  tar -xzf "$tools/jdk21.tar.gz" -C "$tools"
  rm "$tools/jdk21.tar.gz"
  found="$(find "$tools" -maxdepth 1 -type d -name 'jdk-21*' | sort | tail -1)"
  ln -s "$(basename "$found")" "$tools/jdk21"
fi
if [[ ! -f "$sdk/platforms/android-35/android.jar" ]]; then
  fetch platform35.zip https://dl.google.com/android/repository/platform-35_r02.zip
  unzip -q "$tools/platform35.zip" -d "$sdk/platforms"
  rm "$tools/platform35.zip"
fi
if [[ ! -x "$sdk/build-tools/35.0.0/aapt2" ]]; then
  fetch buildtools35.zip https://dl.google.com/android/repository/build-tools_r35_linux.zip
  unzip -q "$tools/buildtools35.zip" -d "$sdk/build-tools"
  mv "$sdk/build-tools/android-15" "$sdk/build-tools/35.0.0"
  rm "$tools/buildtools35.zip"
fi
printf 'Toolchain ready in %s\n' "$tools"
