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
`BIND_NOTIFICATION_LISTENER_SERVICE`. Those permissions allow the app to manage
an owned audio accessory and observe notifications on the phone after the user
grants access. They do not provide a protocol for causing arbitrary nearby
devices to show a popup. A target device would have to run a cooperating
receiver or expose a product-specific vulnerable protocol.

No firmware binary, controller patch, or over-the-air popup implementation was
present in the package inspected. The result is therefore a negative finding:
Noise's installed firmware-lab logic cannot supply the requested universal
popup capability, and it does not change the M30 deauth conclusion.
