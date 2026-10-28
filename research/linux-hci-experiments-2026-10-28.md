# Phone-side Linux HCI experiments

Date: 2026-10-28 (Asia/Kolkata)

## Outcome

The installed corrected research kernel now provides a real, supervised Linux
`hci0` path through the M30 SCSC controller. A bounded phone-side LE scan
received the project LAB advertisement, three Android-to-Linux-to-Android
cycles returned the controller to Android ownership, and a phone-side raw
L2CAP echo request received a valid response from the cooperating laptop.

RFCOMM did not work on the installed image. The phone returned `EPROTONOSUPPORT`
before the laptop fixture saw a connection. This is explained by a source and
runtime mismatch: `CONFIG_BT_RFCOMM=y` is present in the effective config, but
the family tree's `net/bluetooth/Makefile` comments out the RFCOMM subdirectory,
so the protocol registration object is absent. A separate offline candidate
with the one-line Makefile correction compiled completely; it has not been
packaged, flashed, or boot-tested.

No third-party link interruption, Bluetooth deauthentication, RF interference,
or arbitrary nearby message delivery was observed.

## Device and ownership baseline

The handset was verified as a Samsung SM-M305F (`m30lte`) running Android 10,
build `M305FDDS8CVB2`, with the corrected research kernel, Magisk root and
SELinux enforcing. The Android Bluetooth HAL normally holds the SCSC H4 node
and exposes the internal controller at the SCSC-backed `/sys/class/bluetooth/hci0`.
The private evidence records the serial and addresses; they are intentionally
not reproduced here.

The supervisor requires all of these before Linux acquisition:

* Android Bluetooth service is running and reports ON.
* SELinux is Enforcing.
* The SCSC H4 node is not open by a former HAL process.
* The HCI device is not already UP.
* A single on-device lock prevents concurrent helper runs.

On cleanup it disables Linux scan activity, brings the HCI device down,
restarts the vendor HAL and enables Android Bluetooth. It then verifies the
service state and that the HAL has reopened the H4 node. A failed verification
is reported as recovery required; the supervisor does not start a second owner.

## Experiment records

All phone records below are private files under
`/home/aayush-yadav/m30-rollback-20261027/METADATA/`. They contain run IDs,
monotonic/UTC timestamps, command status and cleanup events. The laptop LAB
fixture was the only intended RF peer.

| Run | Operation | Phone observation | Cleanup |
|---|---|---|---|
| `m30-le-20261028-first` | 10-second raw LE scan | HCI Read Local Version (`0x1001`) completed; LE scan commands completed; 4 LAB advertisements matched at about -53 to -58 dBm | Scan disabled, Linux released, Android owner restored |
| `m30-le-20261028-cycle1` | 5-second supervised scan | 2 LAB reports; helper exit 0 | Android owner restored |
| `m30-le-20261028-cycle2` | 20-second deadline scan; cancellation request used the old child-discovery code | 13 LAB reports; scan deadline and cleanup completed. The cancellation request itself logged `No such file` and was not counted as a cancellation pass | Android owner restored |
| `m30-le-20261028-cycle3` | 20-second scan with corrected child discovery; stopped after about 3 seconds | SIGTERM reached the helper; exit 3; scan disabled; no leaked HCI owner | Android owner restored |
| `m30-echo-20261028-first` | One raw Classic L2CAP echo request | Socket connected in about 3053 ms; 20-byte request sent; 20-byte response matched the expected echo format | Socket closed, Linux released, Android owner restored |
| `m30-rfcomm-linux-20261028` | One RFCOMM request to the registered laptop service | Local version command succeeded, then socket creation returned `EPROTONOSUPPORT` (93); no connection attempt reached the fixture | Linux released, Android owner restored |

After each successful recovery the Android framework remained ON and the HAL
reopened the SCSC node. A later Android scan also observed the laptop LAB
advertisement. The Android application itself was not changed or reinstalled
for these helper experiments.

## Linux LE receive evidence

The helper sends HCI Read Local Version (`0x1001`), LE Set Scan Parameters
(`0x200b`) and LE Set Scan Enable (`0x200c`), installs a Command Complete/
LE Meta event filter, and accepts only reports containing the project LAB
128-bit service UUID. It records RSSI and a count, not unrelated payloads.

The first run produced four matching reports before the hard deadline. The
scan was then disabled with a successful Command Complete, and the controller
was brought down before Android was restarted. This establishes:

1. the command path reaches the internal SCSC controller;
2. valid controller events are received by a phone-side raw HCI socket;
3. the controller can receive the cooperating LAB advertisement; and
4. the Android owner can be restored afterward.

It does not establish packet rate, packet loss, promiscuous capture, or
reception of unrelated Bluetooth links.

## L2CAP result

The phone-side helper opened a raw Bluetooth L2CAP socket after the HCI
controller was acquired, connected to the explicitly selected laptop fixture,
sent a bounded 20-byte project echo frame, and validated the 20-byte response.
The response was observed by the phone and required the remote laptop endpoint
to process and return the request. A laptop `btmon` capture was attempted but
could not bind its monitor channel (`Operation not permitted`), so there is no
independent packet-level trace. The result is therefore a successful local
protocol exchange with a cooperating peer, not a disruption measurement.

The operation was not run as a loop and did not target any unrelated device.

## RFCOMM blocker and offline candidate

The RFCOMM run reached Linux HCI successfully and failed at socket creation:

```text
protocol_socket_failed value=93
```

The laptop fixture had already registered an explicit SDP ProfileManager
service and logged no connection, received bytes, response, or disconnection.
This rules out an RFCOMM service-level failure for that run.

The family source revision used for comparison is
`095206579f69121278ed1dd5fd0d7975c0705c6a` (not an exact installed-build
Samsung source release). Its effective build configuration contains:

```text
CONFIG_BT=y
CONFIG_BT_BREDR=y
CONFIG_BT_RFCOMM=y
CONFIG_BT_RFCOMM_TTY=y
```

However, `net/bluetooth/Makefile` has the RFCOMM subdirectory commented out:

```make
#obj-$(CONFIG_BT_RFCOMM)    += rfcomm/
```

`net/bluetooth/af_bluetooth.c` returns `-EPROTONOSUPPORT` when the protocol
family table has no registered implementation. `net/bluetooth/rfcomm/sock.c`
contains the registration path, but that object is not linked when the
Makefile line remains commented. Runtime `/proc/net/protocols` on the phone
showed L2CAP and no RFCOMM, and the built vmlinux without the correction had no
RFCOMM socket symbols.

The project now carries
`research/kernel/scsc_rfccomm-build.patch`, a one-line source correction, and
`scripts/build_m30_kernel.sh` accepts `SCSC_BT_RFCOMM=1`. An offline
`bluez-minimal` build using the family source, ABOX I/O fix, auto-registration
patch and this RFCOMM correction completed with these selected options:

```text
CONFIG_BT=y
CONFIG_BT_BREDR=y
CONFIG_BT_RFCOMM=y
CONFIG_BT_RFCOMM_TTY=y
CONFIG_BT_LE=y
CONFIG_SCSC_BT=y
CONFIG_SCSC_BT_BLUEZ=y
```

Private output directory:
`/home/aayush-yadav/m30-rollback-20261027/BUILD/rfcomm-bluez-20261028/`

| Artifact | SHA-256 |
|---|---|
| `Image` | `7e49a023a071b7ef4f02a33de5d8d24ead63dc01ae0119c4adf2f843a56e53b3` |
| `Image.gz` | `3bf60397df66ed292f98d200cd9821dbc007ccfc1beaed6f67ba544d7a268759` |
| `dtb.img` | `acb171e3ba68dbfbdad7ef51cb52aa8b0da85ead138e1c0f68b42c0a159f2487` |
| `vmlinux` | `68f3ef4ae380608efd9afdee246e93d3b02088e10319769f1b2df8bf269ca084` |
| `.config` | `0566aa7ae2ab32065378ff27c88554a7c09a3e08558b59d5e02130e48a336e11` |

The build completed with one existing section-mismatch warning, RWX linker
segment warnings and a missing optional `firmware/tsp_zinitix` directory
warning. It produced the expected kernel artifacts and RFCOMM symbols, but it
is still a family-source candidate, not a bootable or installed M30 image.
The exact installed-build source, boot packaging and recovery gates remain
unresolved for any future installation.

## Source and tool changes

* `host/phone_linux_lab.c` provides bounded `inspect`, `scan`, `echo`,
  `rfcomm` and `down` operations with an explicit target and RFCOMM channel.
* `scripts/phone_handoff.sh` enforces Android/H4 release, one-owner locking,
  deadlines, cancellation and Android restoration.
* `scripts/build_m30_kernel.sh` can include the RFCOMM Makefile correction
  without leaving the source tree patched after the build.
* `host/gatt_fixture.py` now emits run IDs and monotonic/UTC lifecycle events
  and exits cleanly on SIGINT/SIGTERM.
* `tests/test_phone_linux_lab.c` passes the parser/bounds suite; the current
  host run reports AD UUID matching, 33 truncation cases, malformed lengths,
  unrelated AD data and run-ID validation. Shell syntax and invalid-argument
  checks pass for the handoff supervisor.

The static helper binary built from the current source has SHA-256
`e6801d4ab66034a9797cc403afed35546e9dfcec3821e09252377f44c6ebac7f` and is
kept outside Git with the private evidence. The Android APK was unchanged.
The private artifact manifest is
`/home/aayush-yadav/m30-rollback-20261027/METADATA/rfcomm-bluez-artifact-manifest-20261028.txt`
(SHA-256 `839c634b35a84d3cfe946f84d38ab5c0ff82b16ce8cc45973ea20ef5b9649ce7`).

## Security interpretation

The live results establish a usable, reversible phone-side HCI access path for
ordinary local controller operations. They do not establish a universal
Bluetooth deauthentication primitive. The audited `blue-deauth` techniques
remain Classic L2CAP echo and RFCOMM connection attempts. L2CAP responsiveness
to a cooperating fixture is not a link drop; RFCOMM could not start until the
kernel actually links the RFCOMM subsystem. An HCI Disconnect command would
still apply only to a connection handle owned by the phone's controller.

No third-party link was selected or interrupted. No RF jamming, unlimited
flooding, popup impersonation, or unrelated-device probing was performed.

## Reproduction outline

1. Build `host/phone_linux_lab.c` with the pinned AArch64 cross toolchain and
   BlueZ headers using `scripts/build_phone_linux_lab.sh`.
2. Push the helper to the rooted phone with mode 0700.
3. Start the bounded LAB advertiser on the laptop and record its run ID.
4. Run `scripts/phone_handoff.sh RUN_ID SECONDS normal` from a root ADB shell.
5. Correlate the JSONL phone output with the laptop fixture lifecycle log.
6. For the RFCOMM candidate, build with `SCSC_BT_RFCOMM=1` into a persistent
   output directory. Do not flash without exact-source, packaging and
   recovery gates.

## Next experiment

The next smallest useful experiment is an **offline-to-live RFCOMM candidate
validation**: inspect the completed candidate package against the exact stock
boot layout, then—only after the existing installation gates are revalidated—
boot it in a controlled baseline and repeat one phone-side RFCOMM PING/PONG
with the cooperating laptop. Do not add stress or disruption traffic until
that ordinary exchange and Android restoration are independently measured.
