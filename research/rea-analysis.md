# REA-backed vendor analysis

Checked 2026-10-26. REA was installed in `host/toolchain/rea` with the
official npm package `rea-agents@6.8.0` using project-local Node `v24.22.1`.
The package's engine requirement is satisfied. The official REA revision used
for repository documentation and source inspection is
`e54bf1006eadd0dfeea5c6c259cc3dc84bb414ec`.

The Ghidra provider is the official `ghidra_12.1.4_PUBLIC` release, downloaded
from the National Security Agency release page and checked against SHA-256
`ddac49f903da9d5bac833e5cc79395098b9c33cfd3279be5f31bd00387d2d4db`. REA's
`doctor --json` reported Node, Ghidra installation, Ghidra headless launcher,
native decompiler, and Java 21 as healthy. It also reported unrelated client
configuration drift; no `rea setup` was run, so Codex configuration was not
changed.

## Operations performed

All targets were copied vendor artifacts; REA/Ghidra imports are ephemeral and
do not modify the originals.

| Operation | Input/hash | Result |
| --- | --- | --- |
| `rea analyze --provider ghidra` | `evidence/libbt-vendor-arm64.so`, SHA-256 `57d60dd2f2ee3bdfe5e13b8dc3c1b56dce0bc60feeb5b7f55a756d7698561cdb` | ELF AArch64; image base `0x100000`; 26 observed sections/segments, 47 procedures, 63 defined strings |
| `rea search --kind strings --mode regex` | same input; `scsc\|h4\|firmware\|uart\|bt_` | Exact strings at `0x1007af` (`/sys/module/scsc_bt/parameters/bluetooth_address`), `0x1007ea` (`Open h4 device failed: %s (%d)`), and `0x102028` (`/dev/scsc_h4_0`) |
| `rea search --kind procedures --mode regex` | same input; `open\|init\|close\|send\|read\|write\|firmware` | Imported libc open/read/write/close entries and an `init` function at `0x101014` |
| `rea function 0x101014 --detail summary` | same input | 31 assembly units, three basic blocks, one callee (`__android_log_print` thunk at `0x101530`), two incoming references, eight outgoing references |

The matching copied HAL was analyzed with the same provider. Its SHA-256 is
`0e68668c2367f18a6eb150e606402425c1c323584b5ae8629f218870f35dbe27`. REA
reported an AArch64 ELF with image base `0x100000`, 30 sections/segments, 615
procedures and 363 defined strings. The procedure inventory identifies
`BluetoothHci::initialize` at `0x10a254`, `BluetoothHci::close` at `0x10a78c`,
`sendHciCommand` at `0x10a7f0`, `sendDataToController` at `0x10a830`,
`VendorInterface::Open` at `0x10c108` (with a PLT/thunk entry at `0x113fc0`),
`VendorInterface::Send` at `0x113e20`, and `VendorInterface::Close` at
`0x114080`. The `initialize` evidence contains 334 assembly units, 77 basic
blocks, 10 resolved callees and two unresolved calls.

The REA decompilation of `VendorInterface::Open` at `0x10c108` provides the
most useful boundary evidence so far. It calls `dlopen("libbt-vendor.so", 2)`,
resolves `BLUETOOTH_VENDOR_LIB_INTERFACE` with `dlsym`, calls
`BluetoothAddress::get_local_address`, invokes vendor-interface operations to
load and configure the library, requests a set of file descriptors, rejects
invalid descriptors, and constructs the H4/MCT protocol and asynchronous file
watcher callbacks. The analyzed strings include
`android.hardware.bluetooth-hci-mct`, `android.hardware.bluetooth-hci-h4`,
`vendor.bluetooth_fw_ver`, `/mnt/vendor/efs/bluetooth/bt_addr`, and the error
messages for invalid fd counts and vendor-library loading. This statically
connects the Android HIDL/HAL boundary to a vendor-provided fd transport; it
does not by itself prove the runtime fd pathname, boot-time firmware load, or
that another process can safely claim the fd.

The decompilation of `BluetoothHci::initialize` at `0x10a254` shows the HIDL
callback null check, callback wrapper construction, and a call to
`VendorInterface::Initialize` with completion, ACL, SCO and event callbacks.
Failure calls `initializationComplete(ERR)` and logs the failure. The
`VendorInterface::Open` body then shows vendor-library initialization and
fd-count validation before selecting an H4 protocol for one fd or an MCT
protocol for multiple fds. These are resolved static call paths; actual fd
values and firmware timing remain unobserved.

The full HAL evidence bundles are retained under ignored `evidence/` with
`rea-hal-*` names. The copied HAL and vendor ELFs remain private.

The `init` disassembly records a null-context failure branch that calls a log
helper with the `Open h4 device failed` string and returns `-1`; the success
branch stores the input pointer and reconstructs a six-byte value from the
second argument. This is evidence about this small vendor library, not proof
that every framework initialization path or boot-time firmware load has been
recovered. REA's own evidence notes that decompiler pseudocode is not source,
indirect calls may remain unresolved, and the Ghidra database is temporary.

The complete JSON evidence bundles are retained under ignored `evidence/`:
`rea-libbt-overview.json`, `rea-libbt-strings.json`,
`rea-libbt-procedures.json`, `rea-libbt-init-summary.json`, and
`rea-libbt-init.json`. Public reports record hashes and addresses without
publishing the vendor binaries.

## Remaining reverse-engineering questions

1. Recover the vendor-library caller/entry path that opens the H4 node. The
   HAL-side `Open` path is now mapped through the vendor interface, but the
   small vendor library's imported libc operations do not expose a resolved
   caller to `open`; framework/HAL tracing or a matching larger vendor artifact
   is needed.
2. Compare the exact installed HAL and vendor library build IDs with the
   source-visible SCSC transport. Static strings alone cannot establish
   firmware loading or ownership transitions.
3. Map the `smxf` firmware sections with the existing read-only inspector and
   source `fwhdr.c`/`fwimage.c`. The three CRC matches establish integrity of
   the copy, not a signature scheme or patchability.
