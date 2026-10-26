# REA follow-on: SCSC controller ownership and H4 path

Checked 2026-10-26. Inputs are private copies from the running M305FDDS8CVB2
phone. REA `rea-agents@6.8.0` from official `morluto/rea` revision
`e54bf1006eadd0dfeea5c6c259cc3dc84bb414ec` used Ghidra 12.1.4. The
HAL SHA-256 is `0e68668c2367f18a6eb150e606402425c1c323584b5ae8629f218870f35dbe27`;
the vendor library SHA-256 is
`57d60dd2f2ee3bdfe5e13b8dc3c1b56dce0bc60feeb5b7f55a756d7698561cdb`.
The binaries and full REA JSON remain ignored under `evidence/`.

| Boundary | REA evidence and interpretation | Remaining verification |
|---|---|---|
| Framework/HIDL close | `BluetoothHci::close` at HAL `0x10a78c` invokes `VendorInterface::Shutdown` at `0x10c964`; the latter calls `VendorInterface::Close` at `0x10ca2c`. Evidence: `rea-hal-close-followup.json`, `rea-hal-shutdown-followup.json`. | Confirm service restart restores Bluetooth in a safe future handoff rehearsal. Not performed now. |
| HAL close order | `VendorInterface::Close` calls vendor operation 6, `AsyncFdWatcher::StopWatchingFileDescriptors`, destroys the protocol object, calls vendor operation 4, then operation 0 and vendor cleanup, then `dlclose`. The operation numbers are decompiler observations, not documented public APIs. Evidence: `rea-hal-close-real-followup.json`. | Verify exact callback completion and timeouts with instrumented test build; no live service stop yet. |
| Vendor fd open | Exported `BLUETOOTH_VENDOR_LIB_INTERFACE` relocation table points to `init` (`0x101014`), `op` (`0x101090`), and `cleanup` (`0x101430`). `op(3)` opens `/dev/scsc_h4_0` read/write and returns one fd; `op(4)` closes stored fd; `cleanup` closes any still-open stored fd. Evidence: `rea-libbt-vendor-op-followup.json`, `rea-libbt-vendor-cleanup-followup.json`. | Test fd release only after a matched handoff implementation exists. |
| H4 write | `VendorInterface::Send` at HAL `0x10cd84` locks a mutex and delegates to the selected HCI protocol. `H4Protocol::Send` at `0x10f5a8` prepends a one-byte packet type using `writev(fd, two iovecs)`, retries EINTR/EAGAIN, and logs short writes. Evidence: `rea-hal-send-real-followup.json`, `rea-hal-h4-send-followup.json`. | Correlate with HCI snoop and driver read path; do not inject commands into a running HAL fd. |
| Firmware metadata | Vendor `op(3)` reads `/data/vendor/conn/.wifiver.info`, sets `vendor.bluetooth_fw_ver`, then opens H4. This is a version-metadata read, not demonstrated firmware loading. Family source `drivers/misc/samsung/scsc/mx140_file.c` names `mx140.bin`; exact installed-build boot load remains untraced. | Trace kernel request/load on a matching source/build, or capture safe boot-time diagnostics. |

Live read-only corroboration: Android's `android.hardware.bluetooth@1.0-service`
ran as `u:r:hal_bluetooth_default:s0` under user `bluetooth`; its fd 6 linked to
`/dev/scsc_h4_0`. The node was labeled `u:object_r:sec_radio_device:s0`.
`/vendor/etc/init/android.hardware.bluetooth@1.0-service.rc` declares a HAL
service with `BLOCK_SUSPEND NET_ADMIN SYS_NICE` capabilities. These observations
establish current ownership more strongly than the decompiled call path alone.
They do not establish safe concurrent access or a Linux HCI interface.

The family source adds a cross-check: `scsc_bt_module.c` registers
`scsc_bt_shm_fops` with `scsc_bt_h4_open`, `scsc_bt_h4_release`, shared-memory
H4 read and write. `scsc_bt_h4_open` checks `bt_service.h4_users` and returns
`-EBUSY` for an already occupied H4 path; release stops the service, resets
offsets and clears that flag on its normal path. The optional `scsc_bluez.c`
calls the same file operations. This is source evidence of an intended
single-open boundary in the family tree, not a measured concurrency guarantee
for the installed binary. `scsc_bt_h4_release` has a recovery-in-progress
branch and wait, so a handoff supervisor must treat release timeout as an
ambiguous state.

Reproduce a function analysis from the copied, hashed input:

```sh
cd ~/m30-security-lab
PATH="$PWD/host/toolchain/node-v24.22.1/bin:$PATH" \
GHIDRA_INSTALL_DIR=/tmp/m30-ghidra/ghidra_12.1.4_PUBLIC \
JAVA_HOME="$PWD/host/toolchain/jdk21" \
timeout 90s host/toolchain/rea/node_modules/.bin/rea function \
  "$PWD/evidence/libbt-vendor-arm64.so" 0x101090 --provider ghidra \
  --detail summary --evidence_output "$PWD/evidence/rea-libbt-vendor-op-followup.json" \
  --format json > /tmp/rea-vendor-op-summary.json
```

REA decompilation is static evidence. Indirect callback targets and error-path
timing need runtime confirmation. The source-visible optional bridge is at
[M30 family revision 0952065](https://github.com/akhil1999/android_kernel_samsung_m30lte/tree/095206579f69121278ed1dd5fd0d7975c0705c6a/drivers/misc/samsung/scsc_bt).
The REA repository and installation requirements are at
[morluto/rea](https://github.com/morluto/rea).
