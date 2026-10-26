# Phone Bluetooth connection-test integration

Checked 2026-10-26. The signed APK was rebuilt and upgraded on the pinned
SM-M305F running Android 10 / M305FDDS8CVB2. APK:
`android/app/build/outputs/apk/debug/app-debug.apk`; SHA-256
`6b07fec128512b5a8f94d8d17fb23d8671f93e1a12afb4e588b8ab3ac28956c5`.
Source and signing identity remain in the existing project; APK and key are
ignored by Git. The existing BLE scanner, synthetic LAB advertiser, GATT and
connection-resilience sections remain in the app.

## What the new screen does

**Bluetooth connection tests** shows the execution location as *this phone*,
the current owner estimate, whether a Linux `hci*` device is registered, and
the distinct Android RFCOMM and Linux L2CAP echo availability. It provides a
bounded Classic discovery list separate from BLE scanning (30-second stop),
explicit target selection, Android SDP UUID discovery, service UUID entry, a
mechanism selector, request/duration limits, Start, Stop and JSON export.
Selecting L2CAP echo reports the missing Linux HCI controller; it does not run
a dummy command. Android RFCOMM is a real phone-side API path using a selected
service UUID. It will only start when Android's SDP result includes that UUID.
It uses one worker, a baseline connection, at most 20 further attempts, a
30-second maximum duration and five-second per-operation socket-close timer.
Socket closure and thread interruption are used for cancellation. Results
separate operation success from any effect on an existing target link.
The APK copies no shell loop or protocol code from `blue-deauth`; it reuses
the bounded baseline/request/cleanup experiment design already expressed in
`host/blue_stress.py`. The phone mechanism is Android's UUID-based RFCOMM
socket, while the original Linux scripts use `l2ping` and `rfcomm` channel
attempts. These are not identical transport paths.

No Classic target or service was selected in this task, so the RFCOMM operation
path is **built but untested against a device**. It is not BlueZ `rfcomm`
channel testing and cannot run `l2ping`. The old laptop backend remains an
independent host tool and is not called by the APK.

## Verification and limits

| Check | Result |
|---|---|
| `scripts/build.sh` | Exit 0; APK hash above; Kotlin compile and packaging passed |
| `scripts/install.sh SERIAL` | Exit 0; existing project package upgraded using its signing key |
| `am start -n org.cattail.m30lab/.MainActivity` | Exit 0; Android logged activity display, no launch crash seen |
| Runtime permissions | `ACCESS_FINE_LOCATION` and legacy `BLUETOOTH` granted in package state |
| Controller readiness | `CONFIG_BT=n`, `CONFIG_SCSC_BT_BLUEZ=n`, no `/sys/class/bluetooth`, H4 node present; Linux echo unavailable |
| Handoff mock | Four tests pass; no live handoff attempted |
| Python suite | 11 tests pass, including existing host backend and firmware inspector fixtures |
| Android unit tests | `testDebugUnitTest` exit 0; phone scheduling gate covers request, deadline, cancellation and per-operation timeout boundaries |
| UI tap, Classic scan/SDP, cancellation, JSON export | **Not yet verified on screen**: phone remained at lock screen during ADB UI inspection; request to unlock is pending |
| Active RFCOMM or L2CAP exchange | **Not tested**: no explicitly selected Classic fixture/service; L2CAP path additionally blocked by kernel |
| Controller release/restoration | **Not tested**: HAL was never stopped, preserving working Android Bluetooth |

Build and install:

```sh
cd ~/m30-security-lab
timeout 180s bash scripts/build.sh
sha256sum android/app/build/outputs/apk/debug/app-debug.apk
timeout 60s bash scripts/install.sh YOUR_ADB_SERIAL
adb -s YOUR_ADB_SERIAL shell am start -n org.cattail.m30lab/.MainActivity
python3 -m unittest discover -s tests -p 'test_*.py' -v
```

Uninstall if desired: `adb -s YOUR_ADB_SERIAL uninstall org.cattail.m30lab`.
No radio test starts on install or boot. The new test does not change the
kernel, recovery, SELinux or vendor service.

## Continuation update — 2026-10-26

The RFCOMM path was subsequently exercised from the phone against the owned
laptop fixture. The phone discovered the explicit SDP UUID, sent
`M30LAB/1 PING`, verified `M30LAB/1 PONG`, and completed one baseline plus three
requested connections. A separate cancellation run completed one baseline
plus nine requested exchanges. Independent fixture logs recorded matching
connections, requests, responses and disconnections. The detailed evidence is
in [`reports/rfcomm-phone-fixture-2026-10-26.md`](rfcomm-phone-fixture-2026-10-26.md).

This does not erase the two earlier baseline failures: they occurred before the
fixture received a connection, and later SDP, timeout and trust conditions
changed together. The cause remains intermittent until the matrix in
[`research/rfcomm-reliability-2026-10-26.md`](../research/rfcomm-reliability-2026-10-26.md)
is completed.

The current logging revision adds run IDs, phase timestamps, redacted
exceptions and an explicit fixture-ready event. Its APK was built and
installed with SHA-256
`6075cba4395bddf99682fb6861a9dc03bed965356f03f98af46b44b6d71c5ce6`.

For the offline kernel compile, `apt-get download` fetched Ubuntu 14.3.0
`gcc-14-aarch64-linux-gnu`, `cpp-14-aarch64-linux-gnu`, base,
`libgcc-14-dev-arm64-cross`, and binutils 2.46 into `/tmp/m30-cross-debs`.
`dpkg-deb -x` extracted them under `/tmp/m30-cross`. A temporary
`aarch64-linux-gnu-gcc` wrapper invoked GCC 14 with `-B` paths for extracted
`cc1` and binutils; `LD_LIBRARY_PATH` supplied extracted binutils libraries.
These volatile paths are not bundled with the APK and no host system compiler
was replaced. See `research/kernel-bridge-build.md` for compile results.

```sh
mkdir -p /tmp/m30-cross-debs /tmp/m30-cross
cd /tmp/m30-cross-debs
apt-get download gcc-14-aarch64-linux-gnu cpp-14-aarch64-linux-gnu \
  gcc-14-aarch64-linux-gnu-base binutils-aarch64-linux-gnu \
  libgcc-14-dev-arm64-cross
for pkg in /tmp/m30-cross-debs/*.deb; do dpkg-deb -x "$pkg" /tmp/m30-cross; done
cat > /tmp/m30-cross/usr/bin/aarch64-linux-gnu-gcc <<'EOF'
#!/bin/sh
exec /tmp/m30-cross/usr/bin/aarch64-linux-gnu-gcc-14 \
  -B/tmp/m30-cross/usr/libexec/gcc-cross/aarch64-linux-gnu/14/ \
  -B/tmp/m30-cross/usr/bin/ "$@"
EOF
chmod +x /tmp/m30-cross/usr/bin/aarch64-linux-gnu-gcc
```
