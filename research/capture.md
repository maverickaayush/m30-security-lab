# HCI capture workflow

On this Android 10 build, `/data/misc/bluetooth/logs` exists but was empty at inventory. HCI snoop logging was not enabled by this project.

1. On the phone, open **Settings → Developer options → Enable Bluetooth HCI snoop log** and turn it on. Restart Bluetooth for logging to take effect. Reproduce only a bounded lab run.
2. Inspect filenames with `adb -s SERIAL shell 'su -c "ls -l /data/misc/bluetooth/logs"'`. Do not dump the whole Bluetooth service history into a report.
3. If a `btsnoop_hci.log` file appears, copy it privately with `adb -s SERIAL exec-out su -c 'cat /data/misc/bluetooth/logs/btsnoop_hci.log' > evidence/btsnoop_hci.log`. Check its size and use `wireshark evidence/btsnoop_hci.log`, filtering for LE advertising commands/events and the LAB UUID or marker.
4. If the build writes only in-memory snoop events, collect a targeted bug report and use AOSP's `btsnooz.py` extraction workflow. Keep that report private; it can contain personal data.
5. Turn the Developer options toggle off and restart Bluetooth afterward.

HCI snoop records the phone host/controller interface. It is not a promiscuous over-the-air capture of unrelated links. A separate owned BLE receiver or purpose-built sniffer is needed for independent RF confirmation. The laptop's `btusb` receiver can confirm received advertisements; it is not a raw link-layer sniffer for other connections. See [AOSP Bluetooth verification and debugging](https://source.android.com/docs/core/connect/bluetooth/verifying_debugging).

## Unrooted receiver phone with one USB cable

The 2026-10-29 Nothing Phone (2a), Android 16, had no readable
`/sdcard/btsnoop_hci.log`, but its private `adb bugreport` ZIP contained
`FS/data/misc/bluetooth/logs/btsnoop_hci.log`. To observe an existing
receiver-to-accessory link, enable HCI snoop in Developer options and restart
Bluetooth, reconnect the accessory, then take a **preflight** bug report.
Confirm the target's Classic Connection Complete event is present before
starting an experiment. With one cable, unplug the receiver without restarting
its Bluetooth, connect the M30 for the bounded test, then reconnect the
receiver and take the post-test bug report. Use distinct serial-pinned ADB
commands and a persistent private destination outside Git. Check ZIP integrity
and extract only the BTSnoop member needed for analysis. The bug report itself
contains much more personal data than the HCI summary and must remain private.

`scripts/summarize_bt_link.py` summarizes an owned Classic address from a
BTSnoop v1 H4 file over specified UTC windows. It maps Connection Complete
to a handle, checks Disconnection Complete events, reports whether the capture
covers the window and flags dropped/truncated records. Its output contains no
addresses or payloads. A missing connection event or incomplete capture must
be reported as inconclusive. HCI records establish the receiver controller's
link state, not every audio sample or unrelated over-the-air traffic. The
instrumented example and evidence hashes are in
[`reports/noise4-l2cap-instrumented-2026-10-29.md`](../reports/noise4-l2cap-instrumented-2026-10-29.md).
