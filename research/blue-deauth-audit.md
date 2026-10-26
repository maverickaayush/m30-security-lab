# `blue-deauth` audit and bounded experiment design

Checked 2026-10-26. Repository: `https://github.com/its0x08/blue-deauth`,
revision `34ec2c0b2628796d42740887ae5809d6b0979fb3` (GPL-3.0). The scripts
were read from that revision and were not run against a Bluetooth target.

## What the scripts actually do

| Mechanism | Source command | Protocol/requirements | What the source proves | What it does not prove |
| --- | --- | --- | --- | --- |
| `l2ping` mode | `l2ping -i hci0 -s $packet_size -f $target_addr` | Linux HCI adapter, BlueZ `l2ping`, a reachable Bluetooth Classic address that answers L2CAP echo | Repeated local L2CAP echo requests are attempted | It does not address an arbitrary BLE link, identify an existing target-to-target connection, or prove a disconnect |
| `rfcomm` mode | `rfcomm connect $target_addr 1` | Linux HCI adapter, BlueZ `rfcomm`, a target exposing a compatible RFCOMM service at channel 1 | Repeated RFCOMM connection attempts are attempted | It does not establish that an earbud exposes RFCOMM, nor that an existing link is affected |

`blue_dos.sh` accepts three positional strings, interpolates them into a shell
command, launches a new background process every 200 ms forever, and prints
“Packet sent” before checking any result. It has no address, size, adapter,
deadline, concurrency, or cleanup validation. `blue_dos_v2.sh` adds root,
dependency, adapter, MAC, and size checks and caps tracked background jobs at
10, but still uses `eval`, an infinite loop, the hard-coded `hci0`, a fixed
RFCOMM channel, and status messages that mean only that a process was started.
Its `EXIT` trap also calls `exit`, which makes cleanup behavior harder to reason
about during signal handling.

The repository's README calls this “BLE deauthentication”, but both commands
are Classic Bluetooth utilities. The label is not evidence of a BLE link-layer
primitive. BlueZ documents `l2ping` as L2CAP ping and `rfcomm` as an RFCOMM
device utility. A Bluetooth HCI controller can disconnect a connection handle
owned by that controller; neither script obtains a handle for a link owned by a
different controller.

## Project backend

`host/blue_stress.py` is a separate implementation with these controls:

- one validated `hciX` controller and one validated target address;
- a read-only adapter check before any request;
- an explicit target identity check, plus SDP service discovery before
  RFCOMM mode;
- one baseline request before the bounded sequence;
- one worker, 1–20 requests, 0.1–10 second pacing, a 30 second hard limit,
  and a 5 second per-request ceiling;
- structured `subprocess` argument lists with no shell interpolation;
- process groups, SIGINT/SIGTERM cancellation, and RFCOMM local-device release;
- JSON results with baseline, each operation, return code, timeout, and the
  explicit `target_effect: not_measured` field;
- a dry-run mode that never sends a target request.

The backend returns `controller_unavailable` if the selected adapter cannot be
read and `baseline_failed` if the first request fails. It does not turn a
timeout, refusal, or local adapter error into a claim that another device was
disconnected. The Android screen reports the phone backend as unsupported
because the installed image has no Linux `hci0`; it does not silently redirect
the operation to the laptop.

## Comparison

| Question | Original script | Bounded backend | Acceptance evidence |
| --- | --- | --- | --- |
| Does a controller exist? | Hard-coded `hci0` | Explicit adapter check | `hciconfig -a hciX` JSON result |
| Is the target a required protocol peer? | Assumed | Baseline request and target metadata are recorded | Named target, Classic capability, and baseline response |
| Is work bounded? | No | Count, pace, duration, and per-call deadlines | JSON run reaches `completed`, `deadline`, or `stopped` |
| Did the target's existing link disconnect? | Never measured | Explicitly `not_measured` until independent observation | Target-side connection log before/during/after |
| Can it run on this phone? | No: no Linux HCI | No: disabled phone status; laptop only | Live `/proc/config.gz`, no internal `hci0` |

No active run is claimed in this audit because the user has not supplied the
target model/firmware and the device's current connection. A named target is
required for an ethical, interpretable baseline → bounded test → recovery
measurement.
