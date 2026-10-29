# M30 Bluetooth Security Lab

Project for an owned Samsung SM-M305F (`m30lte`) running Android 10. The app uses Android Bluetooth APIs and never transmits on install or boot. Keep all RF tests inside the isolated lab; advertisements are broadcasts and cannot be contained by an address allowlist.

## Scope and current status

This repository covers the Android lab app, cooperating Linux fixtures,
Samsung SCSC transport and kernel research, supervised Android/Linux controller
handoff, and capture-based evaluation of bounded Bluetooth experiments on
owned devices. Source, reproducible checks and redacted research reports are
published here; toolchains, firmware, device backups and raw evidence stay local.

As of 2026-10-29:

- Android RFCOMM fixture exchange and LAB advertisement reception have been
  validated. The rooted Linux helper separately supports LE scanning and
  Classic L2CAP echo through the installed research kernel.
- The helper now includes a bounded `echo-seq` mode: up to 100 requests over
  at most 60 seconds, a minimum scheduled interval of 600 ms, and an abort
  after three consecutive failures. Android ownership is restored by the
  phone supervisor after Linux releases the controller.
- An instrumented Noise Air Buds Pro 4 run returned 100/100 echoes. Independent
  Nothing Phone (2a) HCI evidence showed no disconnection during the run or
  two minutes afterward; the user noticed no audio interruption. See the
  [instrumented result](reports/noise4-l2cap-instrumented-2026-10-29.md) and
  [earlier inconclusive observation](reports/noise4-l2cap-observation-2026-10-29.md).
- `scripts/summarize_bt_link.py` checks a selected Classic link in a private
  BTSnoop capture without emitting device addresses or payloads. See the
  [capture workflow](research/capture.md).
- Linux RFCOMM remains unavailable in the installed image. A corrected kernel
  candidate has compiled offline but has not been installed or live-tested.

These results establish protocol responsiveness and observed link state for
specific lab runs. They do not establish a general Bluetooth disconnect
mechanism or an audio continuity guarantee.

## Layout

- `android/`: Kotlin app source and Gradle project
- `host/`: tracked Linux fixtures and phone helper sources; local toolchains
  under `host/toolchain/` are ignored
- `host/blue_stress.py`: bounded laptop-side Bluetooth Classic responsiveness harness
- `scripts/`: reproducible setup, build, install, cleanup
- `research/`: source-backed hardware and API notes
- `evidence/`: private captures/logs (ignored from Git)
- `reports/`: redacted observations and feasibility assessment
- `backups/`: ignored private partition backups, stock package and offline candidate images
- `tests/`: host unit tests, helper checks and manual device test procedures
- `diff/`: local review diffs (generated files ignored)

## Rebuild

The phone-to-laptop Bluetooth Classic RFCOMM fixture was validated end to end on 2026-10-26; see [the measured report](reports/rfcomm-phone-fixture-2026-10-26.md).

```bash
cd ~/m30-security-lab
./scripts/bootstrap-toolchain.sh
./scripts/build.sh
./scripts/install.sh YOUR_ADB_SERIAL
```

`bootstrap-toolchain.sh` fetches Gradle 8.10.2 from Gradle, JDK 21 from Adoptium, and Android platform/build tools 35 from Google. The JDK endpoint is rolling; the build records the installed version. `build.sh` creates a project-specific signing key on first use in ignored `host/toolchain/`. Preserve that key to upgrade the installed app. Losing it requires uninstalling this package before installing a differently signed build. The host's system `adb` is used; the local Android SDK lacks platform-tools.

Run tests with:

```bash
cd ~/m30-security-lab/android
JAVA_HOME=../host/toolchain/jdk21 ANDROID_HOME=../host/toolchain/sdk \
  ../host/toolchain/gradle-8.10.2/bin/gradle --no-daemon testDebugUnitTest
```

The app uses `targetSdk=29` for Android 10 compatibility and `compileSdk=35` with runtime guards. On Android 10, scanning needs fine location permission and the system Location switch. On Android 12+, Nearby Devices permissions are requested at runtime. The app keeps scan data in memory until the user explicitly exports it through Android's document picker.

## Safe operation

Open the app, grant its requested permission, refresh capabilities, and select a lab test. Scanning stops at 30 seconds and stores at most 500 records. The advertising controls default to one legacy set, low-power mode, ultra-low transmit power, 30 seconds, and a one-second payload-change interval. Durations clamp to 5–120 seconds, intervals to 1–10 seconds. The emergency stop ends scanning, advertising, the phone connection test and the app-owned GATT link. Leaving the app also stops these tests.

**Bluetooth connection tests** now contains a phone-side Android RFCOMM backend.
Select one owned Classic device, request its SDP UUIDs, choose a returned
service UUID, and set a bounded request count and duration. The screen also
shows why Linux L2CAP echo cannot run while Android owns the controller. A
separate rooted supervisor can now stop the Android HAL, acquire the installed
research kernel's SCSC-backed `hci0`, run one bounded operation, and restore
Android ownership. The APK does not call that privileged helper or claim that
Android and Linux can own the controller concurrently. Linux LE and L2CAP
fixture results are recorded in [the phone-side HCI report](research/linux-hci-experiments-2026-10-28.md);
RFCOMM remains blocked in the installed image because its protocol object was
omitted from the family Makefile.
See [mobile integration](reports/mobile-integration.md),
[REA handoff trace](research/rea-handoff-followup.md),
[offline bridge compile](research/kernel-bridge-build.md), and
[handoff state model](research/handoff-design.md).

The LAB advertisement uses service UUID `7f5f42d6-4659-4c15-9fe7-7de7a7033b01` and manufacturer test marker `LAB` with a profile byte, one-byte sequence counter and optional four-byte ASCII tag under company ID `0xffff` (reserved test use). Single mode uses the baseline profile; multi mode rotates the named LAB-A/B/C profiles. It is synthetic and non-connectable. API callback success shows stack acceptance, not a measured packet rate. Use a separate receiver for RF observations.

The LAB marker is not a request to show a popup. Nearby devices display a
message only when an explicitly installed, opt-in receiver understands this
project UUID. The app does not impersonate pairing prompts or send arbitrary
messages to uninstalled devices.

The laptop can act as one opted-in popup receiver:

```bash
cd ~/m30-security-lab
python3 host/lab_popup_receiver.py --seconds 35
```

Run it while the phone sends a bounded LAB advertisement. The phone UI's
four-character LAB popup text appears on this opted-in desktop. Use
`--message 'Longer local text'` to override that text at the receiver. The
receiver shows at most three notifications per run by default (maximum ten),
does not connect or pair, and stops after at most 120 seconds. Other Linux
desktops can run the same receiver. An Android or iOS device needs its own
cooperating receiver app to display a custom message. Legacy advertisements
on this phone cannot carry an arbitrary-length message alongside the LAB UUID.

See [device capabilities](reports/device-capabilities.md), [test results](reports/test-results.md), [capture workflow](research/capture.md), [transport reverse engineering](research/hardware-reverse-engineering.md), [Noise package inspection](research/noise-firmware.md), and [disconnect feasibility](reports/deauth-feasibility.md).

The follow-on research audit is in [claim ledger](research/research-audit.md),
[capability comparison](research/capability-comparison.md),
[reverse-engineering opportunities](research/reverse-engineering-opportunities.md),
[Linux Bluetooth paths](research/linux-bluetooth-paths.md),
[prior art](research/prior-art.md), [publication plan](research/publication-plan.md)
and [proof-of-concept results](research/experiment-results.md). The read-only
`scripts/inspect_scsc_firmware.py` inspects a **local copy** of a Samsung SCSC
`smxf` image; copied firmware remains ignored under `evidence/`.

The proposed internal HCI kernel config and source readiness checks are in
[kernel HCI readiness](research/kernel-hci-readiness.md). The corrected
auto-registration/ABOX research kernel is installed and has passed the
bounded HCI command, LE scan, L2CAP echo and ownership-restoration checks
described in [the phone-side HCI report](research/linux-hci-experiments-2026-10-28.md).
The newer RFCOMM-enabled candidate is offline-only and has not been installed.

The 2026-10-27 minimal-kernel continuation classified [all 46 earlier config
differences](research/config-difference-classification-2026-10-27.md),
[compared all embedded device trees](research/minimal-device-tree-review-2026-10-27.md),
[rebuilt and validated an offline candidate](research/minimal-candidate-build-2026-10-27.md),
and recorded [source/recovery gaps](research/minimal-source-and-recovery-2026-10-27.md)
and the [installation decision](research/minimal-readiness-decision-2026-10-27.md).
The candidate retains the live embedded device tree and Magisk ramdisk.
That 2026-10-27 candidate review did not flash the phone. Later work installed
the corrected research kernel described in the 2026-10-28 HCI report above.

The source audit of the required repository and the maintained replacement
harness are in [blue-deauth audit](research/blue-deauth-audit.md). A dry run
checks the laptop adapter without contacting a target:

```bash
python3 host/blue_stress.py --controller hci0 \
  --target AA:BB:CC:DD:EE:FF --mechanism l2cap-echo
```

An active run requires a named owned Bluetooth Classic target and an explicit
`--execute`; it is capped and reports `target_effect: not_measured` until an
independent target-side observation exists. BLE-only devices and the M30's
Android-only controller are unsupported by this backend.

REA and Ghidra records are summarized in [REA analysis](research/rea-analysis.md).
The complete evidence JSON is ignored under `evidence/`; no vendor binary or
pairing material belongs in Git.

## Cleanup

```bash
cd ~/m30-security-lab
./scripts/cleanup.sh YOUR_ADB_SERIAL
```

Disable HCI snoop in Developer options if enabled, restart Bluetooth, and remove any exported files or private captures you no longer need. `cleanup.sh` does not touch partitions, recovery, or SELinux.
