# Phone RFCOMM fixture validation — 2026-10-26

## Verdict

**Pass for a cooperating Bluetooth Classic service.** The M30 app connected to a laptop BlueZ RFCOMM profile, sent `M30LAB/1 PING`, verified `M30LAB/1 PONG`, and completed one baseline plus three requested connections. A later live run was stopped after one baseline plus nine requested exchanges. The laptop independently logged the same 4 and 10 connection/request/response/disconnection sequences. This validates Android framework RFCOMM through the phone's existing vendor Bluetooth stack; it does not establish Linux HCI access or any third-party disconnect capability.

**Remaining reliability issue:** two earlier phone attempts ended at baseline before BlueZ delivered a profile connection. One timed out at 5 seconds; another returned `IOException` after 6.4 seconds with Android's socket channel still `-1`. Refreshing SDP preceded a later successful run, but the cause of the intermittent failure has not been isolated. The successful retry changed the SDP record, deadline and laptop trust state together, so none of those changes can individually be credited as the fix.

## Fixture and protocol

- Source: [`host/rfcomm_fixture.py`](../host/rfcomm_fixture.py). It registers UUID `7f5f42d6-4659-4c15-9fe7-7de7a7033b02` and RFCOMM channel 22 with `org.bluez.ProfileManager1.RegisterProfile`, including an explicit SDP record. `org.bluez.Profile1.NewConnection` receives the connected descriptor. Pairing is required. The fixture accepts only the exact 14-byte ASCII request `M30LAB/1 PING\n`, sends `M30LAB/1 PONG\n`, logs UTC event times, and closes the socket. No shell or arbitrary payload endpoint exists.
- The phone uses `BluetoothDevice.createRfcommSocketToServiceRecord(UUID)` after explicit Classic discovery, target selection and `fetchUuidsWithSdp`. The fixture UUID is auto-filled only when returned for the selected device. Only that UUID triggers application data exchange; other selected RFCOMM UUIDs retain connect-only behavior.
- One worker, at most 20 requested operations, at most 30 seconds, and at most 12 seconds per operation. The app closes the active socket on stop, timeout and teardown. It does not transmit on launch or install.
- Laptop adapter: Realtek USB `hci0`, BlueZ 5.85. Phone: installed project app on the identified SM-M305F with Android Bluetooth on. Addresses and pairing keys are omitted here.

## Results and independent evidence

| Stage | Phone result | Laptop result | Interpretation |
| --- | --- | --- | --- |
| Pairing and SDP | Paired in Settings; Classic discovery selected laptop; refreshed SDP returned fixture UUID | BlueZ Adapter1 listed the fixture UUID | Discovery and service selection passed; the initial SDP cache alone was not treated as a live result |
| First baseline | `baseline_failed`, 5.062 s, `IOException`, timeout true | No `NewConnection` | Failed socket setup; no exchange |
| Second baseline | `baseline_failed`, 6.443 s, `IOException`, timeout false | No `NewConnection` | Another setup failure; Android log showed socket channel `-1` |
| Bounded exchange | `completed`, baseline + 3 operations; all `connected=true`, `exchange_ok=true`; 10.493 s total | Four connections, four recognized requests, four responses and four disconnections, 12:12:29–12:12:33 UTC | End-to-end phone-originated RFCOMM exchange passed |
| Cancellation | `stopped` after baseline + 9 operations; all recorded exchanges valid; 15.555 s total | Ten matching connections, requests, responses and disconnections; no socket left open | Live cancellation and cleanup passed |
| Cleanup | Android Bluetooth remained ON; no controller handoff occurred | Fixture unregistered; adapter discoverable/pairable returned to `no/no`; temporary trust and bond removed | Laptop configuration restored; phone may retain its Settings bond entry until forgotten there |

Private raw evidence, ignored by Git: `evidence/rfcomm/phone-baseline-1.json`, `phone-cancel-attempt-1.json`, `phone-success.json`, `phone-cancel-success.json`, and both fixture JSONL files. The successful phone JSON SHA-256 is `317e981930a037df2db5abf8acfd596c114a0c467ba3f96fe444f56e4d7a1154`; cancellation JSON is `64dee252f17a279395b711bb9c0f865bb89a20c2336d2fec74e79d046ae27936`. The successful fixture JSONL is `91fc75694d4332ddafc6860f28ace05190c0ad40572c2a975822d31608e11165`. Private addresses appear in those files; do not commit them. Phone Downloads exports and the temporary UI dump were removed after local capture.

## Build and repeat

Built APK: `android/app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `db1ec876da231500abb7b3dcd8b8f61c11d3a01c8533410ac5364c970c0f3efd`. Build and unit tests passed; the project signing identity was retained. Host Python 3.14.4, BlueZ 5.85, ADB 34.0.5-debian. Commands used from the project root:

```bash
python3 host/rfcomm_fixture.py --self-test
timeout 360 bash scripts/build.sh
timeout 180 bash -c 'cd android && JAVA_HOME=../host/toolchain/jdk21 ANDROID_HOME=../host/toolchain/sdk ../host/toolchain/gradle-8.10.2/bin/gradle --no-daemon --console=plain testDebugUnitTest'
timeout 75 bash scripts/install.sh YOUR_ADB_SERIAL
python3 host/rfcomm_fixture.py --log evidence/rfcomm/new-fixture.jsonl --max-seconds 900
```

Register a temporary BlueZ agent if pairing has no desktop agent (`bluetoothctl`, then `agent NoInputNoOutput` and `default-agent`; quit it afterward). Pair the phone with the laptop in Settings within the fixture's 180-second discoverable window. In the app's **Bluetooth connection tests** section, find Classic devices, select the laptop, check its Classic services, confirm the LAB UUID, then start the bounded test. Save the JSON using **Save connection-test JSON**. Press **Stop phone connection test** for a cancellation run. Stop the fixture with SIGINT; its `finally` block unregisters the profile and restores the adapter's initial discovery/pairing properties. Remove the temporary laptop bond if one was created. The app never opens `/dev/scsc_h4_0` directly.

## Source basis and next work

BlueZ documents [ProfileManager1 registration and options](https://github.com/bluez/bluez/blob/master/doc/org.bluez.ProfileManager.rst), [Profile1's NewConnection callback](https://github.com/bluez/bluez/blob/master/doc/org.bluez.Profile.rst), and an [official explicit RFCOMM SDP record example](https://github.com/bluez/bluez/blob/master/profiles/iap/main.c). Android documents [UUID-based RFCOMM client connection and SDP lookup](https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices). The live tests, rather than those documents alone, establish operation on this handset.

The next app reliability experiment is to log the exact `IOException` message and correlate a failed connection with a host HCI trace or Bluetooth daemon diagnostics, then vary SDP record, trust and timeout one at a time. The next **offline kernel** milestone is a complete build of a stock-matched kernel configuration, DTBs and modules with the SCSC BlueZ bridge integrated, followed by ABI/firmware checks and a tested restore procedure. Compiling only the bridge object is not a bootable kernel or a safe handoff.
