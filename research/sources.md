# Primary sources and claim status

Checked 2026-10-26.

| Source | Use | Status |
| --- | --- | --- |
| [Android BluetoothAdapter API](https://developer.android.com/reference/android/bluetooth/BluetoothAdapter) | scanner/advertiser availability, multi-advertising and extended advertising API guards | API documentation; measured values are on the handset |
| [Android BluetoothLeAdvertiser API](https://developer.android.com/reference/android/bluetooth/BluetoothLeAdvertiser) | callback/error semantics and legacy advertisement limits | API documentation; callback success is not an RF measurement |
| [Android Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions) | Android 10 location and Android 12 Nearby Devices permission rules | API guidance; app targets SDK 29 for this handset |
| [AOSP Bluetooth verification/debugging](https://source.android.com/docs/core/connect/bluetooth/verifying_debugging) | HCI snoop enablement, storage path, bug-report extraction | current AOSP documentation; device still requires user toggle |
| [Bluetooth SIG Core HCI specification](https://www.bluetooth.com/wp-content/uploads/Files/Specification/HTML/Core_v6.3/out/en/host-controller-interface/host-controller-interface-functional-specification.html) | HCI Disconnect takes a local connection handle | primary protocol specification; interpretation applied to this HAL boundary |
| [BlueZ LEAdvertisement documentation](https://github.com/bluez/bluez/blob/master/doc/org.bluez.LEAdvertisement.rst) | host fixture advertisement semantics | official BlueZ source documentation |
| [BlueZ example GATT server](https://github.com/bluez/bluez/blob/master/test/example-gatt-server) | bounded fixture structure | official BlueZ example; this project's fixture is a small lab-specific adaptation |
| [Samsung Open Source Release Center](https://opensource.samsung.com/) | exact kernel/source lookup | public portal checked; exact installed firmware source match remains unknown |
| [Samsung SM-M305F update history](https://doc.samsungmobile.com/SM-M305F/006517190527/wel.html) | build association for `M305FDDS8CVB2` | Samsung's device page lists Android 10 and the 2022-03-01 security patch for this build |
| [M30-family kernel tree](https://github.com/akhil1999/android_kernel_samsung_m30lte/tree/095206579f69121278ed1dd5fd0d7975c0705c6a) | SCSC path comparison | public family source, not confirmed exact build |
| [Kali NetHunter Bluetooth Arsenal](https://www.kali.org/docs/nethunter/nethunter-btarsenal/) | external/raw-controller requirements | official NetHunter documentation; no requirement demonstrated here |
| [REA repository](https://github.com/morluto/rea) and [REA security policy](https://github.com/morluto/rea/security) | reverse-engineering workflow and handling boundary | official repository read; not installed or invoked |

The attached research document's citation markers were not treated as evidence.
Claims in the reports are labeled as observed, source-supported, inferred, or
unknown where that distinction affects the conclusion.
