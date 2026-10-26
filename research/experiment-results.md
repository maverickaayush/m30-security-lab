# Non-destructive proof of concept: firmware header and GATT validation

Run 2026-10-26 (Asia/Kolkata) on the identified M30, stock running kernel,
SELinux enforcing. The laptop's `0bda:b86a`/`btusb` controller served the
LAB fixture. No partition, controller firmware, SELinux or pairing state was
changed; the fixture advertised for at most 120 s.

## Offline firmware experiment

Question: can the copied SCSC image be identified and integrity-checked using
the M30-family driver's actual header logic? Source revision
[`095206579f69121278ed1dd5fd0d7975c0705c6a`](https://github.com/akhil1999/android_kernel_samsung_m30lte/tree/095206579f69121278ed1dd5fd0d7975c0705c6a):
[`fwhdr.c`](https://github.com/akhil1999/android_kernel_samsung_m30lte/blob/095206579f69121278ed1dd5fd0d7975c0705c6a/drivers/misc/samsung/scsc/fwhdr.c)
defines `smxf` magic and offsets;
[`fwimage.c`](https://github.com/akhil1999/android_kernel_samsung_m30lte/blob/095206579f69121278ed1dd5fd0d7975c0705c6a/drivers/misc/samsung/scsc/fwimage.c)
defines the three `ether_crc` ranges;
[`mxman.c`](https://github.com/akhil1999/android_kernel_samsung_m30lte/blob/095206579f69121278ed1dd5fd0d7975c0705c6a/drivers/misc/samsung/scsc/mxman.c)
accepts header 1.x with firmware API 0.2.

Reproduce on a **local copy**, never the phone's live file:

```bash
adb -s "$LAB_SERIAL" pull /vendor/etc/wifi/mx140.bin evidence/mx140.bin
sha256sum evidence/mx140.bin
python3 scripts/inspect_scsc_firmware.py evidence/mx140.bin
python3 -m unittest discover -s tests -p 'test_scsc_firmware.py' -v
```

All commands exited 0. SHA-256 was
`2cc929186c161f9ddd2ed52bfec6181e2a9a9cde85dada4b1625b60ed7d95474`.
The 1,360,017-byte image has `smxf` at offset `0x08`, header version 1.0,
firmware API 0.2, header length 420, constant-section end 1,274,132, and
entry-point field 425. The build ID contains
`lassen_s612|S612_E788X_C1_Y21W42A`, matching the live
`vendor.bluetooth_fw_ver` suffix. Stored and calculated CRCs were identical:
header `0x662fb262`, constant `0xab427159`, full image `0xfe6abb1b`.
The synthetic unit test verifies that payload corruption fails the full-image
check and malformed length bounds are rejected. **The CRCs are integrity checks,
not a cryptographic signature.** A file/property match strongly links the file
to the running build but does not prove which pathname the boot loader opened.
The trampoline begins with an ARM instruction word; source names an R4 panic
record. Exact segment architecture, M4 content, compression and signing remain
unresolved; generic `file` reports only `data`.

## Live GATT experiment

Question: do the app's intentional write, CCCD notification and bounded
reconnection controls work against a separate owned fixture? The previous
`414243` write returned status 1 after the fixture expired and remains a
failure in `reports/test-results.md`.

```bash
python3 host/gatt_fixture.py --seconds 120 > evidence/gatt-fixture-audit.log 2>&1
adb -s "$LAB_SERIAL" shell am start -n org.cattail.m30lab/.MainActivity
# In the UI: scan, select the LAB fixture, connect, select the LAB characteristic,
# subscribe, enter 4c414232, preview and confirm, unsubscribe, run 3 cycles.
```

The phone received four LAB fixture scan records, connected (status 0), and
discovered five services (status 0). CCCD subscribe and unsubscribe descriptor
writes both returned status 0. The selected characteristic's write of
`4C414232` (`LAB2`) returned status 0 and the phone received a notification
with that value. The fixture independently logged `NOTIFY start`, two explicit
`WRITE 4c414232` events, `NOTIFY stop`, and bounded shutdown. The second write
resulted from a second visible confirmation during UI automation, not an
unlimited loop or automatic characteristic sweep.

Three app-owned connect/discover/disconnect cycles had status 0 throughout.
The phone summary was `cycles=3/3 failures=0 elapsed=6971ms`; connected callback
latencies were 64, 57 and 54 ms, service discovery succeeded each time, and
disconnect callbacks were status 0. These are Android callback timings for one
local fixture, not RF link-layer latency or a third-party disconnect test.

Private, address-redacted phone events:
`evidence/gatt-audit-events.log` SHA-256
`23b0c368aa2c7169647ffea86d0c659dbcf1a1316a592a61ffa42181ad108e43`.
Fixture log SHA-256
`640eebf50526397f6c384751b77cb0b59526d267603f80f9d8e6fee208e94133`.
Both are ignored by Git. No HCI snoop or promiscuous RF capture was made.

## Remaining measurement limits

The app's callback log shows controller/stack acceptance, while the separate
laptop receiver saw prior LAB advertisements. A BlueZ Device1 cache cannot
estimate packet loss. For publishable advertisement resilience measurements,
use a receiver with explicit scan-event timestamps or a suitable BLE sniffer,
multiple randomized trials, clock alignment, and a quiet-channel control.
The GATT fixture experiment should be repeated across firmware versions and
longer sessions before generalizing reliability.
