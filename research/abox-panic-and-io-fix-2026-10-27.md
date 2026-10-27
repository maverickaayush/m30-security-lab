# ABOX boot panic and I/O-copy fix

## Finding

The second baseline BOOT trial did not fail silently. The private
`/proc/last_kmsg` record contains two copies of the same early-boot failure
(the log is SRAM-backed and has byte corruption in places). The useful record
is stored outside Git at:

`/home/aayush-yadav/m30-rollback-20261027/METADATA/last_kmsg-after-second-failure.txt`

Private artifact SHA-256: `0b20451ac7f4a402423a7447cfc9af7be4cbf8b31c305a699de4755a5b84e080`.

The stable fields are:

| Field | Observation |
|---|---|
| Kernel | `4.4.177-g09520657 #1` |
| Workqueue | `events` / `abox_boot_done_work_func` |
| Fault | ARM64 alignment fault `0x96000061` at ABOX SRAM address ending in `0x2004` |
| PC | `__memcpy+0x110/0x180` |
| LR | `abox_start_ipc_transaction_atomic.constprop.0+0xe0/0x2c8` |
| Call path | `abox_boot_done_work_func` → `abox_cpu_pm_ipc` → `abox_request_ipc` → `abox_start_ipc_transaction_atomic` → `__memcpy` |

The family source maps the ABOX `sram` resource with `devm_ioremap()` in
`sound/soc/samsung/abox/abox.c`. Its IPC TX path nevertheless used ordinary
`memcpy()` to the `void __iomem *` TX address. The ARM64 generic memcpy in the
built `vmlinux` uses source alignment to select pair stores. The crash-site
instruction is `stp x7, x8, [x6], #16`; the IPC source pointer is offset by
four bytes, so the first pair store is issued at TX SRAM + 4. Device-mapped
SRAM rejects that access and the kernel panics.

This explains the repeated panic in both the raw and Magisk-prepared baseline
trials. The earlier `skip_initramfs` → `want_initramfs` mismatch was real and
was corrected, but it was not the remaining cause of the boot loop.

## Source evidence

Source tree: `akhil1999/android_kernel_samsung_m30lte`, commit
`095206579f69121278ed1dd5fd0d7975c0705c6a`.

Relevant source locations:

* `abox.c:339` and `abox.c:406`: TX IPC copies to mapped SRAM.
* `abox.c:4391` and `abox.c:4762`: reads from mapped SRAM.
* `abox.c:4997` and `abox.c:5099`: extra firmware copies; area 0 is SRAM,
  areas 1 and 2 are ordinary memory.
* `abox.c:5119`: main SRAM firmware download.
* `abox_synchronized_ipc.c:131,176,186`: shared-memory IPC access through
  `abox_addr_to_kernel_addr()`.
* `abox_util.c:66-95`: `devm_request_and_map_byname()` calls
  `devm_ioremap()`.
* `arch/arm64/kernel/io.c:26-77`: `memcpy_fromio()` and `memcpy_toio()` use
  byte writes until the I/O destination is aligned, then I/O-width accesses.

The upstream Linux SRAM driver uses the same `memcpy_toio()`/
`memcpy_fromio()` contract for mapped SRAM; see the primary source at
https://android.googlesource.com/kernel/common/+/88d92fb1c034922572bab93482ac9cc61d4ba43c/drivers/misc/sram.c.

## Fix

`research/kernel/abox-io-copy.patch` changes only copies whose source or
destination is confirmed ABOX I/O memory:

* TX IPC and SRAM firmware writes use `memcpy_toio()`.
* SRAM IPC reads use `memcpy_fromio()`.
* Extra firmware uses `memcpy_toio()` only for area 0 (SRAM); coherent DRAM
  and shared-memory areas retain ordinary `memcpy()`.
* Synchronized IPC accesses use the matching I/O helpers.

The patch does not change the DT, firmware files, controller commands,
bootloader, partitions or SELinux policy.

## Offline build and package evidence

The reproducible build command was:

```sh
SRC=/tmp/m30-family-kernel \
OUT=/dev/shm/m30-minimal \
CROSS_COMPILE=/tmp/m30-cross/usr/bin/aarch64-linux-gnu- \
JOBS=4 ABOX_IO_PATCH=1 \
timeout 900s scripts/build_m30_kernel.sh bluez-minimal
```

The source tree was clean before the command; the script applies the patch
only for the build and restores the source on exit. Exit status was 0.

| Artifact | SHA-256 |
|---|---|
| Candidate `Image` | `cff2048a457a9d35bfd4947731ca535bcdcd3ade256eb1953147ab81c047b3c2` |
| Magisk-prepared kernel copy | `0294494b636aa862da01328b8750dd1493d30a1e919e9ed5709f32e8430f2c38` |
| Candidate 32 MiB BOOT package | `3bd30a1df1f30162cf316c31a6ca88997770fa79844d1b192b6fc20ff68455ec` |
| Candidate DTBH retained from live BOOT | `80bcef6b1c78693944a832d77a1674c791e277dbe154d5dcec22929346215711` |
| Candidate compressed Magisk ramdisk retained from live BOOT | `f5e345b1e350fd445050118e30c3aab1b83c669e79f6630bb3281fe3bd6b9dcf` |

The package script performed an unchanged live-BOOT round trip first, then
replaced only the kernel and re-extracted the candidate components. The
round-trip and component comparisons passed. The fixed ABOX function in the
linked image calls `__memcpy_toio` at the prior crash site. Compilation still
emits the known missing `firmware/tsp_zinitix` directory warning, one
modpost section-mismatch warning and RWX linker warnings; none were hidden.

## Live trial, 2026-10-27

The fresh Download Mode session accepted the candidate BOOT at 23:49:40
Asia/Kolkata. Heimdall 2.1.0 reported BOOT upload successful, exit 0, and
requested reboot. Only BOOT was selected. Two preceding attempts failed
before transfer during protocol/session initialization; a fresh mode entry
resolved that transport failure. The omitted `--resume` after the earlier
no-reboot PIT read was a procedural error, not a kernel result.

Android subsequently reported `sys.boot_completed=1`, boot animation stopped,
and kernel `4.4.177-g09520657-dirty`. SELinux was Enforcing, Magisk root was
available and /data was mounted with about 43 GiB available. This is one
successful boot, not long-term stability validation. The prior ABOX panic
was not reproduced in the collected boot log. The patch addresses the
observed fault; this does not establish that every compatibility issue is fixed.

The filename `BASELINE_UNTESTED/baseline-abox-io-fixed-v2-boot.img` is a legacy
mislabel: the actual build is **bluez-minimal**, with CONFIG_BT=y,
CONFIG_SCSC_BT_BLUEZ=y, CONFIG_SCSC_BT=y and CONFIG_DM_VERITY=y confirmed
from the running /proc/config.gz. This trial is not an independent corrected
Bluetooth-disabled baseline. The `-dirty` release suffix accurately reflects
applied source patches and has not been hidden.

Android Bluetooth reports enabled/ON. No Linux HCI controller appears under
/sys/class/bluetooth. The boot log records the SCSC BlueZ probe. Source
scsc_bluez.c:228-281 registers the HCI device only after its power_control
parameter or rfkill callback schedules the open worker; probe alone does not
register it. No control parameter was changed and no alternate owner started.
The candidate remains active; restoration was not needed for this boot.

The Wi-Fi driver logs repeated missing MIB PSID 2324 (0x914) messages.
Wi-Fi being enabled is not proof of data exchange or full firmware compatibility.
A later shell health query still showed boot_completed=1 at uptime 161 seconds.
A privileged combined ownership/partition-hash query timed out after 30 seconds;
no owner or installed partition readback result is claimed from it.
Audio functionality, ordinary Bluetooth exchanges on this build, sustained
stability, and exclusive Android/Linux ownership remain untested.

Private logs, live configuration and health observations are stored under the
rollback directory's METADATA folder. Original BOOT rollback remains SHA-256
`a63676cd0ed5d90cfca66aaa744c173886b72606aec64ea174575f44250064d4`.

Next: prepare and validate exclusive controller release/restoration before
activating SCSC Linux registration; never race the active Android HAL.
