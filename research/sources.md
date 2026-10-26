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
| [M30-family SCSC BlueZ bridge](https://github.com/akhil1999/android_kernel_samsung_m30lte/blob/095206579f69121278ed1dd5fd0d7975c0705c6a/drivers/misc/samsung/scsc_bt/scsc_bluez.c) and [Linux Bluetooth Kconfig](https://github.com/akhil1999/android_kernel_samsung_m30lte/blob/095206579f69121278ed1dd5fd0d7975c0705c6a/net/bluetooth/Kconfig) | internal HCI build readiness | source confirms HCI registration and `CONFIG_BT`; exact installed kernel source still unavailable |
| [Kali NetHunter Bluetooth Arsenal](https://www.kali.org/docs/nethunter/nethunter-btarsenal/) | external/raw-controller requirements | official NetHunter documentation; no requirement demonstrated here |
| [REA repository](https://github.com/morluto/rea) and [REA security policy](https://github.com/morluto/rea/security) | reverse-engineering workflow and handling boundary | official repository read; not installed or invoked |
| [CERT/CC VU#472136](https://kb.cert.org/vuls/id/472136) | current product-specific earbud DoS example | Redmi Buds 3 Pro through 6 Pro advisory; not matched to this lab fixture |
| [CERT/CC VU#859658](https://kb.cert.org/vuls/id/859658) | current product-specific unauthenticated pairing example | Skullcandy Dime 3 S2DCW firmware 1.0.0.28 advisory; not matched to this lab fixture |
| [SCSC firmware header](https://github.com/akhil1999/android_kernel_samsung_m30lte/blob/095206579f69121278ed1dd5fd0d7975c0705c6a/drivers/misc/samsung/scsc/fwhdr.c), [CRC ranges](https://github.com/akhil1999/android_kernel_samsung_m30lte/blob/095206579f69121278ed1dd5fd0d7975c0705c6a/drivers/misc/samsung/scsc/fwimage.c), [firmware path](https://github.com/akhil1999/android_kernel_samsung_m30lte/blob/095206579f69121278ed1dd5fd0d7975c0705c6a/drivers/misc/samsung/scsc/mx140_file.c) | read-only `smxf` inspector | source supports layout and CRC; exact stock load path still inferred |
| [SCSC Wi-Fi UDI](https://github.com/akhil1999/android_kernel_samsung_m30lte/blob/095206579f69121278ed1dd5fd0d7975c0705c6a/drivers/net/wireless/scsc/udi.c) and [cfg80211](https://github.com/akhil1999/android_kernel_samsung_m30lte/blob/095206579f69121278ed1dd5fd0d7975c0705c6a/drivers/net/wireless/scsc/cfg80211_ops.c) | diagnostic/monitor feasibility | live UDI node observed; exact installed monitor modes not measured |
| [Flipper Zero hardware](https://docs.flipper.net/zero/development/hardware/tech-specs), [firmware app list](https://github.com/flipperdevices/flipperzero-firmware/blob/dev/applications/ReadMe.md), [USB/BLE HID](https://docs.flipper.net/zero/bad-usb) | stock capability comparison | separate external Wi-Fi board/third-party apps from stock features |
| [SweynTooth original paper](https://www.usenix.org/conference/atc20/presentation/garbelini), [BrakTooth researchers](https://asset-group.github.io/disclosures/braktooth/disclosure.html) | target-specific Bluetooth prior art | no match to unnamed lab earbud established |
| [USENIX Security '27 CFP](https://www.usenix.org/conference/usenixsecurity27/call-for-papers), [WOOT '26 CFP](https://www.usenix.org/conference/woot26/call-for-papers), [Linux patch guide](https://docs.kernel.org/process/submitting-patches.html), [CVE CNA rules](https://www.cve.org/ResourcesSupport/AllResources/CNARules) | publication/disclosure thresholds | current audit is not a submission or CVE |

The attached research document's citation markers were not treated as evidence.
Claims in the reports are labeled as observed, source-supported, inferred, or
unknown where that distinction affects the conclusion.
