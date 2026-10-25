# Test results

Date: 2026-10-26 (Asia/Kolkata). Device identifiers and unrelated discovered
device data are omitted. Raw screenshots, receiver logs, and copied libraries
are in ignored `evidence/` for local review.

## Build and install

| Command | Exit/result |
| --- | --- |
| `./scripts/bootstrap-toolchain.sh` | Completed; project-local Gradle 8.10.2, JDK 21.0.13, Android platform/build tools 35 |
| `.../gradle --no-daemon testDebugUnitTest` | `BUILD SUCCESSFUL`; parser truncation and JSON/CSV quoting tests passed |
| `./scripts/build.sh` | `BUILD SUCCESSFUL` |
| `./scripts/install.sh <pinned serial>` | `Success`; package `org.cattail.m30lab` installed without replacing an unrelated package |
| latest APK SHA-256 | `dad854a9290f21cebd204bca8fe9c8f9dcf6f8c48c74a80840dea6e1f9fbae0d` |

The app was launched from ADB. It does not contain a boot receiver and did not
start RF transmission on install or launch.

## Android capability and scanning

The dashboard reported Android 10 / SDK 29, the required location permission
granted, Bluetooth enabled, BLE scanner available, advertiser available,
multiple advertising supported, extended advertising unsupported, and maximum
advertising data length 31 bytes. Earlier inventory also observed the expected
permission prompt and the Bluetooth-disabled dashboard state.

The app scan ran for its 30-second hard deadline and stopped automatically after
4 records in the live lab. A synthetic host fixture advertisement was parsed
with its 128-bit LAB service UUID and raw bytes. Scan data is bounded at 500
records and remains in memory until an explicit JSON or CSV document export.

## Advertising and independent receiver

The single advertisement run logged `AdvertiseCallback.onStartSuccess` with the
selected low-power/ultra-low settings and stopped at its 30-second deadline.
The separate laptop BlueZ receiver saw the LAB service and marker during the
run. The bounded sequence run produced stack callbacks for synthetic counters
0 through 27 and stopped at its 30-second deadline. The receiver's BlueZ
Device1 cache observed counters `[0, 2, 4, 17]` with RSSIs in the mid -60 dBm
range. BlueZ cache observations are deduplicated; they do not establish packet
rate, packet loss, or exact over-the-air timing. The app reports this limitation
in its event log.

The current UI also accepts an optional four-byte printable LAB tag. It is
encoded in the synthetic manufacturer field for an opt-in receiver to inspect;
it is labeled as a broadcast marker and cannot cause a popup on a device that
has no cooperating receiver.

After this tag change, a fresh 35-second host receiver run observed counter 0
with tag `LAB` at approximately -62 dBm. The phone callback accepted the
advertisement, and leaving the activity triggered the app's cleanup path.

## GATT fixture

The host fixture is a bounded BlueZ D-Bus peripheral using the project LAB UUID
and a read/write/notify characteristic. The phone scan found four fixture
records. One measured app-owned connection returned status 0 / connected state
2 with approximately 1875 ms connection latency. Service discovery returned
status 0 and five services. The selected LAB characteristic read returned
status 0 and value `4C4142`; the fixture log recorded `READ 4c4142`.

A deliberate write preview of `414243` reached the phone API but returned status
1 after the corresponding fixture process had expired, so it is recorded as a
failed/fixture-timing case rather than a successful write. No characteristic is
written automatically. Notification and the three-cycle resilience control are
implemented, but a complete independent notification/resilience measurement was
not completed before the fixture deadline; they remain explicitly untested.

## Negative and cleanup checks

The app has hard deadlines, explicit stop buttons, callback failure handling,
and cleanup in `onStop`, `onDestroy`, timeout, and error paths. No HCI snoop log
was collected because the device-side Developer Options toggle requires a
physical user action. The exact workflow is in `research/capture.md`.

After revoking `ACCESS_FINE_LOCATION`, pressing Start Scan logged a permission
missing block; the permission was granted again afterward. The phone was
initially inventoried with Bluetooth off and the dashboard displayed the off
state. A later shell `svc bluetooth disable` attempt on this Samsung build did
not change the service state, so the button-level Bluetooth-off block was not
independently exercised; Bluetooth was left enabled.

The project did not flash partitions, wipe data, change the bootloader, replace
recovery, install a kernel, disable SELinux, or attempt RF interference.
