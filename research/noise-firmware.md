# Noise package inspection

Date: 2026-10-26 (Asia/Kolkata). This is a static inspection only. The Noise
packages were not modified, repackaged, or invoked by this project.

## Artifact found on the phone

The handset contains two packages matching the requested name:

| Package | Observation |
| --- | --- |
| `com.noise.audio.firmwarelab` | Version 1.0, target SDK 28, one exported `ProbeActivity`, `INTERNET` permission only |
| `com.noise.audio` | Version 2.2.8 (version code 228), target SDK 36; the installed Noise audio application; requests Bluetooth scan/advertise/connect, location, notifications, and other app features |

The firmware-lab base APK was copied from its installed path to ignored local
evidence and hashed:

```
evidence/noise-static/firmwarelab.apk
SHA-256 279fba9466633158c2c82b5f8dd3f663f6c4c2f5c07b6e66c6c2ca3b79bd7e0a
```

The installed Noise audio base APK was also copied for local static comparison
and has SHA-256
`58ca78ed3460f8772b933699c1462facaec0c7e21c7a7b7722edeaf7fd741fed`.
Its `classes.dex`, `classes2.dex`, and `classes3.dex` are byte-identical to the
first three DEX files in the firmware-lab APK. The firmware-lab package adds a
small fourth DEX with the lookup activity. This makes the two package names
part of the same app-code family, but does not make the lookup activity a
Bluetooth transport.

The APK manifest identifies its label as `Noise Firmware Lab` and its activity
as `com.noise.audio.firmwarelab.ProbeActivity`. It has no Bluetooth, location,
notification, or foreground-service permission. It cannot advertise, scan, or
send a Bluetooth popup on this handset.

## What the firmware-lab code does

The decoded `ProbeActivity.lookup(int, String, String)` method constructs a
JSON request and makes a TLS URL connection to:

```
https://app.gonoise.com/core/audio/firmware-versions
```

It uses HTTP POST, 20-second connect/read timeouts, JSON content type, and
headers for a model/version/platform/firmware lookup. The embedded screen text
describes this as a “simulated device” and says it sends the firmware API
request “without Bluetooth”. The response is written to the app's external
files directory as `firmware-response.json` and summarized in the local UI.

This is a firmware-version lookup probe, not earbud firmware and not a popup
transport. Its use of `PopupWindow` or Android `Message` classes in unrelated
library code is ordinary local UI/worker behavior; it is not a mechanism for
making another device display a message.

## Noise audio application

The installed `com.noise.audio` package requests Bluetooth permissions and has a
`NotificationListenerService` protected by Android's
`BIND_NOTIFICATION_LISTENER_SERVICE`. Targeted DEX inspection of that class
shows `MediaSessionManager`, `MediaController`, `PlaybackState`, and music-mode
state updates. In the inspected class there was no `onNotificationPosted`
override or demonstrated path for sending an arbitrary popup to nearby
devices. Bluetooth permissions let the app manage compatible accessories;
they do not grant another device's user interface to this phone. A target
device would have to run a cooperating receiver or expose a specific vulnerable
protocol.

## Additional OTA protocol evidence

The main APK does contain a real accessory firmware-update path. Android SDK
`dexdump` on `classes2.dex` shows an obfuscated class whose static initializer
constructs these UUIDs:

```
0000ae00-0000-1000-8000-00805F9B34FB  (service candidate)
0000ae01-0000-1000-8000-00805F9B34FB  (characteristic candidate)
0000ae02-0000-1000-8000-00805F9B34FB  (characteristic candidate)
00002902-0000-1000-8000-00805f9b34fb  (CCCD)
```

Nearby bytecode strings and call paths include `BesBaseService`,
`BesOtaService`, `startOta`, `sendCommandSetTOTAData`, `getStartOTAPacketCMD`,
and `BES_TOTA_ENCRYPTION_KEY`. The path prepares byte arrays, optionally applies
the app's TOTA encryption/transform helpers, and sends them through a selected
`BluetoothDevice` and a `DeviceProtocol` object. This is evidence of a
vendor-specific connected accessory OTA/control transport, not a broadcast
message channel. The class and protocol names are partly obfuscated, so the
exact command grammar and firmware format were not reconstructed from strings
alone.

The static result is useful for a future matched Noise accessory capture: an
HCI snoop trace during an owner-initiated OTA operation could map these UUIDs,
commands, and response statuses. It still cannot make an unrelated phone,
computer, or earbud display a popup, and it cannot disconnect a link owned by a
different controller.

No firmware binary or controller patch was identified by the archive filename
and DEX inspection. One 128,401-byte opaque asset,
`assets/aUnJzipEZbqMgIgx`, begins with `00 49 41 50 02 00 00 00` (an `IAP`
prefix), but `file` recognizes it only as data. Its SHA-256 is
`4c7c7ed73db102420bab56c004069e6e21678fe397aef477a35325140460cb9c`.
It has not been established as earbud firmware, and its purpose remains
unknown. The firmware-lab lookup
activity cannot supply a universal popup capability, and the inspected Noise
code does not change the M30 deauth conclusion. No earbuds firmware image was
available for an offline firmware audit.
