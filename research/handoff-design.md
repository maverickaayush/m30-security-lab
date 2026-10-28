# Exclusive Android-to-Linux controller handoff design

Status: **offline model plus supervised live path** (2026-10-28). The
corrected installed research kernel can enter the research-owner state only
after the Android HAL is stopped and its SCSC H4 descriptor is verified closed.
The first live HCI command, bounded LE scan, and Android restoration have
passed; RFCOMM remains unavailable in this image because its protocol object
was omitted from the family Makefile. The newer RFCOMM-enabled candidate is
offline-only.
The state machine is implemented and exercised by
`scripts/handoff_model.py` and `tests/test_handoff_model.py`; it never sends
ADB commands or touches Bluetooth.

| State | Required evidence before transition |
|---|---|
| Android owns controller | Bluetooth service running, fd to `/dev/scsc_h4_0` present, ordinary Android Bluetooth check passes. Read-only fd observation confirmed this state. |
| Preparing handoff | Privileged supervisor holds an exclusive process lock; user has initiated a bounded test; ADB and restoration channel work; Android test links are closed; deadline armed. |
| Controller released | HAL/service shutdown complete, reader thread joined, fd absent from **all** former HAL process fds, driver/service state quiescent. A timeout or ambiguous ownership aborts. |
| Research backend owns controller | A compatible kernel `hci_dev` is registered and opened by one Linux stack; `btmgmt` inspection passes. Android service remains stopped. |
| Stopping research backend | Stop requests, close protocol sockets, stop BlueZ, release Linux HCI, wait for fd/driver release. Deadline enforced by supervisor. |
| Restoring Android | Start original init HAL service, wait for fd reopen and initialized framework callbacks, then check scan/advertise or another ordinary operation. |
| Recovery required | Any ownership or restoration check fails. Keep ADB available; do not start a second owner or claim success. Restore with a separately tested plan. |

The model forces `stop_research` before `start_android`, including after a
failed acquire, and never calls `start_research` if release verification fails.
Four mocked tests cover normal cycle, unverified release, failed research start
and failed Android verification. The model does **not** provide a process-death
watchdog, actual lock, service control or recovery; these must be implemented
and independently tested before live handoff. A future supervisor must enforce
one owner using a lock outside the app process, a watchdog that survives app
death, explicit timeouts, and a restoration action that is idempotent. The
phone app has no button for this unverified transition.

The HAL decompilation in `research/rea-handoff-followup.md` defines a plausible
close order: watcher stop → protocol teardown → vendor `op(4)` fd close →
vendor cleanup. The family SCSC bridge uses `bt_fs->open/read/write/release`
and registers `hci_dev` in an `open_worker` triggered by a module parameter or
rfkill. This source path is not compiled in the installed kernel. The family
bridge source does not itself supply a complete Android/BlueZ ownership lock.
Its `scsc_bt_h4_open` path is intended to reject a second H4 open with
`-EBUSY` via `h4_users`; this driver-side guard does not serialize service
transitions or verify release/recovery completion.
Directly opening the H4 character node from userspace would yield a byte stream,
not Linux AF_BLUETOOTH; it would also conflict with the current HAL fd.

Do not infer a ready handoff from the passing mock tests or compiled family
object. Required live gates: exact installed-build source, reproducible stock
kernel, signed/bootable image workflow, validated backup/restore, SELinux/service
policy, driver exclusivity checks, then a reversible no-traffic handoff and
Android restoration drill. No partition changes are authorized in this phase.

## Source review of the optional BlueZ path

At family revision `095206579f69121278ed1dd5fd0d7975c0705c6a`,
`scsc_bluez.c` registers a separate Linux `hci_dev` only when its
`power_control` module parameter or rfkill callback schedules the open worker.
The worker sets `hdev->bus = HCI_VIRTUAL`, assigns the HCI open/close/send
callbacks, and calls `hci_register_dev`. Its open callback calls the same
`bt_fs->open` operation used by the SCSC H4 path; its close callback cancels
the reader work and calls `bt_fs->release`. The module does not stop the
Android Bluetooth service or coordinate Binder/HIDL ownership.

The sibling SCSC file operations guard the transport with `bt_service.h4_users`:
the first open starts the Bluetooth service, a second open returns `-EBUSY`,
and release stops the service before clearing the flag. This is a driver-level
exclusive-open guard, not a complete Android-to-BlueZ handoff. It means the
optional bridge cannot safely coexist with the Android HAL's live descriptor;
Android must be quiesced and its close path must complete before BlueZ opens
the bridge. Conversely, BlueZ must unregister/close before Android is started.

The source exposes a switching knob, but it is not proof of runtime switching:
there is no Android service stop/start contract, process-death watchdog, or
verification that all readers and vendor resources have drained. These source
findings are why the current candidate remains unboot-tested and why a live
handoff was not attempted.
