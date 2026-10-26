# REA follow-on: verified HAL close path

Checked 2026-10-26. This is a focused follow-on to the existing REA/Ghidra
analysis, not a claim that the controller firmware was decompiled. The copied
HAL is `evidence/vendor-bt-hal-arm64.so`, SHA-256
`0e68668c2367f18a6eb150e606402425c1c323584b5ae8629f218870f35dbe27`. REA
`rea-agents@6.8.0` uses official revision
`e54bf1006eadd0dfeea5c6c259cc3dc84bb414ec` and Ghidra 12.1.4.

## Operation

```sh
cd ~/m30-security-lab
PATH="$PWD/host/toolchain/node-v24.22.1/bin:$PATH" \
GHIDRA_INSTALL_DIR=/tmp/m30-ghidra/ghidra_12.1.4_PUBLIC \
JAVA_HOME="$PWD/host/toolchain/jdk21" \
timeout 120s host/toolchain/rea/node_modules/.bin/rea function \
  "$PWD/evidence/vendor-bt-hal-arm64.so" 0x10ca2c --provider ghidra \
  --detail summary --evidence_output "$PWD/evidence/rea-hal-close-final.json" \
  --format json
```

REA completed with exit 0. The evidence bundle records the same artifact hash
and address and is ignored by Git. The decompiler recovered the following
static sequence for `VendorInterface::Close`:

* vendor operation 6 is called when a vendor interface exists;
* `AsyncFdWatcher::StopWatchingFileDescriptors()` runs and is logged as
  “Stop Thread begin/done”;
* the protocol object is destroyed;
* vendor operation 4, then operation 0, then vendor cleanup are called;
* the vendor handle is cleared and `dlclose` runs;
* the firmware timing object is freed.

This strengthens the handoff design: a Linux owner must wait for the async
reader to stop and the vendor fd to close, rather than merely stopping the
Android UI. It does not establish callback completion timing, the exact vendor
operation ABI, or successful service restart. Five call targets remain
indirect/unresolved in the REA relationship view.

## Limits and next verification

The result is decompiler-generated static evidence. It does not trace the
running process, prove that every error path closes the descriptor, or prove
that the controller reset/firmware sequence can be repeated by another owner.
The next useful REA target is only a matching vendor/driver build comparison;
no undocumented HCI command or firmware patch is justified by this function.
