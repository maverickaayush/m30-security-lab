# Exclusive Android-to-Linux controller handoff design

Status: **offline model only** (2026-10-26). The installed kernel has no
Linux HCI device, so the research-owner state cannot be entered on the phone.
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
