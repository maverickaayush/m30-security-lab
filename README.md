# M30 Bluetooth Security Lab

Project for an owned Samsung SM-M305F (`m30lte`) running Android 10. The app uses Android Bluetooth APIs and never transmits on install or boot. Keep all RF tests inside the isolated lab; advertisements are broadcasts and cannot be contained by an address allowlist.

## Layout

- `android/`: Kotlin app source and Gradle project
- `host/`: local toolchain (ignored from Git)
- `scripts/`: reproducible setup, build, install, cleanup
- `research/`: source-backed hardware and API notes
- `evidence/`: private captures/logs (ignored from Git)
- `reports/`: redacted observations and feasibility assessment
- `backups/`: private backup staging; none taken during this task
- `tests/`: manual test procedures
- `diff/`: local review diffs (generated files ignored)

## Rebuild

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

Open the app, grant its requested permission, refresh capabilities, and select a lab test. Scanning stops at 30 seconds and stores at most 500 records. The advertising controls default to one legacy set, low-power mode, ultra-low transmit power, 30 seconds, and a one-second payload-change interval. Durations clamp to 5–120 seconds, intervals to 1–10 seconds. The emergency stop ends scanning, advertising and the app-owned GATT link. Leaving the app also stops scanning and advertising.

The LAB advertisement uses service UUID `7f5f42d6-4659-4c15-9fe7-7de7a7033b01` and manufacturer test marker `LAB` with a one-byte sequence counter and optional four-byte ASCII tag under company ID `0xffff` (reserved test use). It is synthetic and non-connectable. API callback success shows stack acceptance, not a measured packet rate. Use a separate receiver for RF observations.

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
[kernel HCI readiness](research/kernel-hci-readiness.md). They are offline
research artifacts; no kernel image has been installed on the phone.

## Cleanup

```bash
cd ~/m30-security-lab
./scripts/cleanup.sh YOUR_ADB_SERIAL
```

Disable HCI snoop in Developer options if enabled, restart Bluetooth, and remove any exported files or private captures you no longer need. The scripts do not touch partitions, recovery, or SELinux.
