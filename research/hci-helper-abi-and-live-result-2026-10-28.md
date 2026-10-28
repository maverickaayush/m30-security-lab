# Real phone-side Linux HCI exchange after correcting the helper

## Outcome

One bounded Android → Linux → Android transition succeeded on the **already
installed** auto-registration/ABOX kernel. No new kernel was flashed. SELinux
remained enforcing. This establishes a real HCI command/event exchange, not
BLE RF delivery, Linux RFCOMM, sustained stability or third-party disruption.

The service-retention candidate is on hold: the failed first helper did not
establish a missing probe context, and the corrected helper needed no retention
patch. The earlier ENODEV diagnosis was wrong.

## ABI corrections and offline verification

Matching source revision: `095206579f69121278ed1dd5fd0d7975c0705c6a`.

| Defect | Source contract | Correction |
|---|---|---|
| Pointer passed to HCIDEVUP/DOWN | `net/bluetooth/hci_sock.c`, `hci_sock_ioctl`: `hci_dev_open(arg)` / `hci_dev_close(arg)` take the scalar index | Pass 0, not the address of 0 |
| Integer timeout passed to syscall 73 | ARM64 asm-generic syscall ABI: `ppoll` takes a timespec pointer | Supply 3-second timespec |
| Missing raw event filter | `hci_sock.c`, HCI_FILTER uses `hci_ufilter` | Select Command Complete / opcode 0x1001 |
| Success inferred from prior write length | Old poll timeout retained positive write result | Require exactly valid 15-byte successful response |
| EALREADY treated as ownership | An already-up controller may belong to another process | Refuse and do not close that controller |

Nonblocking socket read prevents a spurious poll event from causing an indefinite
read. Any timeout, syscall failure, wrong opcode, truncated response or controller
error produces failure. Down is attempted after successful acquisition. A device
`timeout 25` and host 35-second timeout bounded the live invocation; the helper
alone does not bound a blocked kernel ioctl or handle forced termination recovery.
It is not yet a production ownership supervisor.

`tests/test_phone_hci_probe.c` passed 12 mocked cases: success, already-up,
open failure, write failure, poll timeout/error, read failure, controller error,
down failure, filter failure, wrong opcode and truncated event. Mocks validate
arguments/results/cleanup; they do not simulate SCSC hardware.

Build: isolated AArch64 GCC 15.2.0 (Ubuntu 15.2.0-16ubuntu1), existing `scripts/build_phone_hci_probe.sh`, static
freestanding executable. Corrected binary SHA-256:
`2a44c4f6b8c301394fa4c15de960c259a1584a7cf985bfab9b4ed7a1e576bb95`.

Reproduce offline (choose a new private OUT, script refuses overwrite):

```sh
cc -Wall -Wextra -Werror -O2 tests/test_phone_hci_probe.c -o "$PRIVATE_TEST"
"$PRIVATE_TEST"
CC=/tmp/m30-cross/usr/bin/aarch64-linux-gnu-gcc OUT="$PRIVATE_PROBE" \
  bash scripts/build_phone_hci_probe.sh
```

## Live evidence

Before testing: SM-M305F, kernel `4.4.177-g09520657-dirty`, Android HAL running,
root available and SELinux enforcing. HAL held the SCSC H4 descriptor.

1. Disable Android Bluetooth. Framework reported disabled but BLE_ON remained,
   so this alone was not sufficient release evidence.
2. Stop `vendor.bluetooth-1-0`; verify state `stopped` and no process fd resolving
   to `/dev/scsc_h4_0` before invocation. No device permissions or policy changed.
3. Run the corrected helper with a device-side 25-second deadline.
4. Helper returned exit 0, including:

```text
owner_probe=controller_up
hci_command=read_local_version opcode=0x1001 bytes=4
hci_event_hex=04 0e 0c 01 01 10 00 09 00 00 09 75 00 00 00
result=validated_version_response
owner_probe=controller_down
```

The event is Command Complete, opcode 0x1001, status 0; returned HCI/LMP version
values 0x09, manufacturer 0x0075, revision/subversion 0. The kernel performs
ordinary controller initialization during HCIDEVUP in addition to the helper's
one explicit command. Do not claim that only one controller command occurred.

5. Kernel log at uptime 21418.355905 records `slsi_bt_open`, H4 users=0;
   21418.621730 begins close; reader exits before service release;
   21418.736006 records close completion.
6. Restart HAL and enable Android Bluetooth. Kernel at 21428.522563 records a
   new H4 open with users=0. Framework reports ON, HAL owns H4 again and SELinux
   remains enforcing. No reboot or image restoration was needed.

Private evidence in rollback `METADATA/`:
`hci-probe-abi-v2-2026-10-28.txt` and
`hci-probe-abi-v2-dmesg-2026-10-28.txt`. Keep full logs private.

Evidence SHA-256 respectively:
`90fc045f16dd41e633cd8689c3042236e173c9846d9a6c80970ac8c8808e79ab`;
`28202ab4073f71bb84eff63417c9cd15545fc3c7d747f85d5d0ba18b13ffb1a9`.

Android post-restoration RF/fixture validation is pending phone unlock. ON and
descriptor ownership alone do not establish a working Android exchange.

## Registration caveat and remaining work

`hci_register_dev()` queues `hci_power_on`, which invokes `hci_dev_do_open`.
The previous claim that the registration patch cannot open the transport was
incorrect. This successful deliberately sequenced transition does not prove
boot-time arbitration, race freedom, repeated transitions or process-death
cleanup. Audit and gate automatic initialization before distributing this kernel
or exposing ownership controls in the APK. The experimental registration patch's
comment has been corrected; no kernel binary was rebuilt for that comment.

No Linux L2CAP/RFCOMM fixture exchange, third-party disconnect, or APK Linux
backend integration was attempted here. The APK and its signing identity remain
unchanged. Next validation: Android bounded BLE scan against the owned fixture
after restoration, followed by a supervised repeatability plan.
