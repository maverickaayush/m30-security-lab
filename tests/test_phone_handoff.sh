#!/usr/bin/env bash
set -euo pipefail
script=${1:?script path}
if sh -n "$script"; then :; else exit 1; fi
set +e
sh "$script" bad 1 rfcomm 00:11:22:33:44:55 0 >/dev/null 2>&1
rc=$?
set -e
test "$rc" -eq 2
echo "PASS: handoff argument rejection and shell syntax"
