#!/usr/bin/env bash
set -euo pipefail
script=${1:?script path}
if sh -n "$script"; then :; else exit 1; fi
set +e
sh "$script" bad 1 rfcomm 00:11:22:33:44:55 0 >/dev/null 2>&1
rc=$?
set -e
test "$rc" -eq 2
for args in \
  'bad 60 echo-seq 00:11:22:33:44:55 101' \
  'bad 59 echo-seq 00:11:22:33:44:55 100' \
  'bad 60 echo-seq 00:11:22:33:44:55 0' \
  'bad 60 echo-seq invalid 100'; do
  set +e
  # Invalid arguments must be rejected before root checks or radio changes.
  sh "$script" $args >/dev/null 2>&1
  rc=$?
  set -e
  test "$rc" -eq 2
done
echo "PASS: handoff argument rejection and shell syntax"
