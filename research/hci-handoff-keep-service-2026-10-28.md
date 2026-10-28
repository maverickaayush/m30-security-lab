# SCSC service-retention handoff candidate

## Status: HOLD — original diagnosis withdrawn

The corrected helper completed a real HCI exchange on the already installed
kernel without this retention patch. See
`hci-helper-abi-and-live-result-2026-10-28.md`. Do not install this candidate
on the strength of the earlier ENODEV result. Reference-count ownership and
parameter-disable error paths need review before any future use.

## Earlier interpretation, corrected

The first phone-side ownership attempt registered `hci0`, but the HCI open
request returned `ENODEV` after Android's Bluetooth HAL was stopped. The
kernel log showed ordinary service shutdown and `samsung_abox_bt_remove`, not
proof that `common_service.maxwell_core` was cleared. The helper passed an
address to HCIDEVUP instead of the controller index. No HCI command reached the controller in that
trial. Android was restored by starting `vendor.bluetooth-1-0` and enabling the
framework service.

That trial did not establish a driver lifecycle failure.

## Workaround design

`research/kernel/scsc_bt-keep-service.patch` adds the opt-in module parameter
`bluez_keep_service`. When enabled, `scsc_bt_h4_release()` drops the Android
client's service reference without calling the SCSC service-stop routine. One
service reference therefore keeps the probed SCSC context alive while Android
has released `/dev/scsc_h4_0`. A later Linux H4 open can reuse that context.
The default is disabled, preserving normal Android close behavior.

`research/kernel/scsc_bluez-auto-register.patch` schedules the existing bridge
registration worker after SCSC probe. HCI registration queues `hci_power_on`,
which can invoke the open callback and controller initialization. Registration
must not be treated as passive publication. Exclusive ownership at boot needs
additional review even though the explicit live transition succeeded.

The service-retention change is a hypothesis until the candidate is booted and
the following sequence is observed: Android H4 close, retained service log,
Linux HCI open, command-complete event, HCI close, Android HAL restart and a
successful Android Bluetooth operation.

## Offline build and package

Source: public M30-family revision
`095206579f69121278ed1dd5fd0d7975c0705c6a`.

Applied patches: family BlueZ compatibility, SKB send lifetime, auto-register,
service retention and the previously boot-tested ABOX I/O copy fix. Effective
configuration includes `CONFIG_BT=y`, BR/EDR, LE, RFCOMM, `CONFIG_SCSC_BT=y`,
`CONFIG_SCSC_BT_BLUEZ=y` and `CONFIG_DM_VERITY=y`.

The complete compile and link reached `vmlinux`, `Image`, DTB output, the
configured module and `System.map`. The build wrapper exited at the final
`Image.gz` target because the `/dev/shm` per-user quota was exhausted while
writing the compressed copy. Link-stage temporary files were removed from the
stopped build, and `Image.gz` was created from the completed `Image` with
`gzip -n -9`. This is recorded as a storage-limited build completion rather
than a clean wrapper exit.

| Artifact | SHA-256 |
|---|---|
| Raw `Image` | `500225061643caefbca87432da62780aa72e8b3bad1cb257a3a7b9ae89e3a2c9` |
| Magisk-compatible kernel (`want_initramfs` marker) | `81246707e8bdaa4ae991856d8cdd6e410e6abc3f7c614aaf5916f370903dffb5` |
| 32 MiB BOOT package | `f0951a16f3e243866ad111533f28ead262be2baff81bb5b409bbfa6d175f27a2` |

Private package:
`/home/aayush-yadav/m30-rollback-20261027/BASELINE_UNTESTED/hci-keep-service-abox-v2-boot-validated.img`.
The package is mode 0600. `scripts/package_m30_minimal.sh` passed the unchanged
live BOOT round trip and candidate re-extraction checks: the patched kernel,
embedded live DTB and Magisk ramdisk match their inputs and the result is
exactly 33,554,432 bytes. No device write has occurred for this candidate.

## Historical proposed test — superseded; do not execute

The test must run with Android and Linux as exclusive owners. After the
candidate boots, set `bluez_keep_service=1`, verify the readback, disable the
framework Bluetooth service, stop `vendor.bluetooth-1-0`, and confirm that the
HAL descriptor is gone while the SCSC service remains present. Run the private
static HCI probe, which sends only standard HCI Read Local Version (`0x1001`),
then bring the controller down and restart the Android HAL. A valid result
requires an HCI event and a subsequent Android Bluetooth scan or fixture
exchange. A visible `hci0` alone is insufficient.

If the candidate fails to boot or the handoff leaves the service unusable, use
the verified original BOOT image and the already tested Download Mode / Heimdall
path. No DTB/DTBO, recovery, userdata, bootloader or firmware write is part of
this experiment.
