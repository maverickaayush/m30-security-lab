#!/usr/bin/env bash
set -euo pipefail

# Read-only verification for the private rollback directory. This script does
# not access a device and never writes or deletes an artifact.
BASE=${1:?usage: verify_rollback_set.sh /path/to/m30-rollback-YYYYMMDD}
MANIFEST="$BASE/METADATA/sha256-manifest.txt"
ARCHIVE="$BASE/STOCK_RESTORE/SM-M305F_2_20220312160231_63hiiaa4v8_fac.zip"

test -d "$BASE"
test -r "$MANIFEST"
(
  cd "$BASE"
  sha256sum -c METADATA/sha256-manifest.txt
)
timeout 240s unzip -tqq "$ARCHIVE"
printf 'rollback hashes and stock ZIP integrity verified: %s\n' "$BASE"
