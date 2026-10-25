# Device and host baseline — 2026-10-26 (Asia/Kolkata)

## Observed on the attached handset

One ADB device was initially `unauthorized`; after the user accepted USB debugging it was the sole `device` transport. Subsequent ADB commands were pinned to that serial. The serial and Bluetooth addresses are excluded here.

| Item | Observation |
| --- | --- |
| Manufacturer/model/codename | Samsung SM-M305F / `m30lte` (`m30ltedd` product) |
| Board/platform | `exynos7904` / `universal7904` |
| Running OS | Android 10, SDK 29; regular Android shell, not recovery |
| Firmware fingerprint | `samsung/m30ltedd/m30lte:10/QP1A.190711.020/M305FDDS8CVB2:user/release-keys` |
| Security patch | 2022-03-01 |
| Bootloader reported | `M305FDDS8CVB2`, revision 4; verified boot state `orange` |
| Kernel | `4.4.177-22392479 #1 SMP PREEMPT Mon Feb 21 17:10:33 KST 2022 aarch64` |
| ABIs | `arm64-v8a, armeabi-v7a, armeabi` |
| Root/SELinux | Magisk `30.7:MAGISKSU`, root command succeeded; SELinux Enforcing |
| Storage/battery | `/data` ~43 GiB free of 53 GiB; battery 100%, charging at inventory |
| Bluetooth at discovery | Off; airplane mode on |
| Bluetooth after user action | On; airplane mode off |
| Package features | `android.hardware.bluetooth`, `android.hardware.bluetooth_le` |
| SCSC | `/dev/scsc_h4_0` owned by `bluetooth`; `/dev/scsc_ant_0` present; vendor Bluetooth HAL 1.0 service running |
| Kernel config | `CONFIG_SCSC_BT=y`, `# CONFIG_SCSC_BT_BLUEZ is not set`, `# CONFIG_BT is not set` (read from `/proc/config.gz` as root) |
| Firmware property | `vendor.bluetooth_fw_ver=UTC|lassen_s612|S612_E788X_C1_Y21W42A` |

The user reports TWRP is installed, but recovery was **not booted or verified** during this task. No partition backup was taken. The fingerprint, bootloader version, kernel build and firmware property above are rollback planning inputs, not a tested restore path.

## Host and interfaces

The laptop has ADB 34.0.5, BlueZ 5.85, Wireshark 4.6.4, Git 2.53.0, `lsusb`, `btmon`, `btmgmt`, and a built-in Realtek USB Bluetooth controller VID:PID `0bda:b86a` bound to `btusb`. BlueZ reports central/peripheral roles. This is a separate laptop receiver, not a USB adapter plugged into the phone. No external Bluetooth USB controller was found on the phone. At discovery, no usable Android SDK/JDK/Gradle was present on the host PATH; the project-local toolchain was fetched for the build. The host had ~2.7 GiB free initially, so captures and toolchain archives are excluded from Git.

## Access layers

| Layer | Current result |
| --- | --- |
| Android framework | BLE package feature and app API access present; actual advertiser/scanner callbacks recorded separately in test results |
| Android vendor HAL | `android.hardware.bluetooth@1.0-service` running; `libbt-vendor.so` contains `/dev/scsc_h4_0` and an H4 open-failure string |
| Linux AF_BLUETOOTH/HCI | `CONFIG_BT` is absent and SCSC BlueZ option is off; no `hci0` on the handset was observed |
| External USB controller | None attached to handset; support and power budget untested |

Android Bluetooth operating through the vendor HAL does not imply that BlueZ can open an internal `hci0`. No attempt was made to give Android and BlueZ concurrent ownership of the controller.
