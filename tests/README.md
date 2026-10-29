# Reproducible lab checks

Run only with the phone and fixture devices owned by the lab. Keep the radio
tests inside the isolated lab; the phone's advertisements are broadcasts.

## Host checks without a connected device

From the repository root:

```bash
python3 -m unittest discover -s tests -p 'test_*.py' -v
sh tests/test_phone_handoff.sh scripts/phone_handoff.sh
```

These checks cover the host harness, handoff model, offline parsers and
supervisor argument rejection. They do not exercise the phone controller or
validate a live link outcome. The BTSnoop tests use synthetic captures,
including a known disconnect and missing/dropped evidence.

## Device checks

1. Build and unit-test:

   ```bash
   cd ~/m30-security-lab/android
   JAVA_HOME=../host/toolchain/jdk21 ANDROID_HOME=../host/toolchain/sdk \
     ../host/toolchain/gradle-8.10.2/bin/gradle --no-daemon testDebugUnitTest
   ```

2. Install and launch with the pinned ADB serial:

   ```bash
   cd ~/m30-security-lab
   ./scripts/install.sh SERIAL
   adb -s SERIAL shell am start -n org.cattail.m30lab/.MainActivity
   ```

3. In the app, grant the requested permission, refresh the dashboard, run scan,
   then run one single LAB advertisement and one bounded sequence. Confirm the
   event log reaches the hard deadline. Use `host/observe_lab.py` from a
   separate BlueZ receiver during the sequence; report observations as cache
   observations, not packet counts.

4. Start `host/gatt_fixture.py --seconds 120`, scan for the LAB service, select
   only that fixture, and use read/write/notify controls intentionally. A write
   requires the visible preview confirmation. Run the resilience button only
   after selecting the fixture and record all status/timeout messages.

5. Test the negative paths by turning Bluetooth off before pressing scan and by
   denying the requested permission in Settings. Restore Bluetooth and the
   permission afterward. Do not leave advertising active.

6. Check HCI logging only after following `research/capture.md`, retrieve the
   capture, inspect it with Wireshark, then disable the setting and restart
   Bluetooth.

The UI contains no popup-spam, pairing-impersonation, raw HCI injection, or
unlimited flooding mode. A generic third-party disconnect is not a test case
because the observed M30 transport has no such operation.
