# HCI capture workflow

On this Android 10 build, `/data/misc/bluetooth/logs` exists but was empty at inventory. HCI snoop logging was not enabled by this project.

1. On the phone, open **Settings → Developer options → Enable Bluetooth HCI snoop log** and turn it on. Restart Bluetooth for logging to take effect. Reproduce only a bounded lab run.
2. Inspect filenames with `adb -s SERIAL shell 'su -c "ls -l /data/misc/bluetooth/logs"'`. Do not dump the whole Bluetooth service history into a report.
3. If a `btsnoop_hci.log` file appears, copy it privately with `adb -s SERIAL exec-out su -c 'cat /data/misc/bluetooth/logs/btsnoop_hci.log' > evidence/btsnoop_hci.log`. Check its size and use `wireshark evidence/btsnoop_hci.log`, filtering for LE advertising commands/events and the LAB UUID or marker.
4. If the build writes only in-memory snoop events, collect a targeted bug report and use AOSP's `btsnooz.py` extraction workflow. Keep that report private; it can contain personal data.
5. Turn the Developer options toggle off and restart Bluetooth afterward.

HCI snoop records the phone host/controller interface. It is not a promiscuous over-the-air capture of unrelated links. A separate owned BLE receiver or purpose-built sniffer is needed for independent RF confirmation. The laptop's `btusb` receiver can confirm received advertisements; it is not a raw link-layer sniffer for other connections. See [AOSP Bluetooth verification and debugging](https://source.android.com/docs/core/connect/bluetooth/verifying_debugging).
