# Decision for the minimal kernel candidate

This report supersedes the compatibility conclusions in `readiness-gates-2026-10-27.md` for the new offline candidate. It does not supersede the original observations or claim a boot test. **No kernel was installed in this phase.**

| Gate | Evidence | Decision | Remaining consequence |
|---|---|---|---|
| Correct handset/build | Serial-pinned ADB identified SM-M305F / M305FDDS8CVB2; earlier stock FUS tuple and PIT matched | Pass | Recheck immediately before any future write |
| Original artifacts | BOOT, DTB, DTBO, RECOVERY persistent host and phone copies rehashed against manifest | Pass for copies | Actual image restoration remains untested |
| Independent off-phone backup | Host disk contains originals; phone user-data copy also exists; no mounted external drive | **Gap** | Make a second verified copy on separate external media before a boot trial |
| Exact stock package | INS M305FDDS8CVB2 archive retained and rehashed; prior ZIP CRC/TAR MD5 tests passed | Pass for acquisition/integrity | Signing/flash acceptance untested |
| Recovery access | TWRP UI and Download Mode/PIT read were observed in previous phase | Pass for access | Neither restore path has performed a write |
| Host rollback utility | Heimdall 2.1.0 extracted locally; it parsed the device-read PIT and confirmed BOOT ID 12/32 MiB | Pass for offline syntax/PIT parse | Live Heimdall USB detection and BOOT write remain untested |
| Source/security config | All 46 earlier differences classified; 29 normalized differences remain, only two enabled live options lack family source | Improved, **conditional** | FMM/debug hooks and Samsung secure-world ABI need live checks or exact source |
| Live board data | Four matching board variants; live DTBH retains proper clock limits and selected camera regulator names | Pass for static binding comparison | Full runtime DT/kernel compatibility untested |
| Minimal package | Unchanged live BOOT round-trip byte-identical; final package 32 MiB, re-extracted kernel/live DTBH/Magisk ramdisk match inputs | Pass for offline structure | Bootloader acceptance and runtime boot untested |
| Host capacity | Candidate and rollback images persistent; ~88 MiB free on root | Marginal | Do not begin another large persistent build without freeing space or adding storage |

The candidate is **technically prepared for a controlled boot experiment but not operationally ready to install today**. Most prior configuration and device-tree mismatches are eliminated, so “not stock-matched” alone is no longer a useful stop reason. The remaining enabled source gaps have identifiable roles and can be treated as explicit trial risks with rollback. The concrete operational blockers are the lack of an independent external backup and no live test of the host's proposed restore utility. A controlled trial would require those safeguards, a current device/hash recheck, and a prewritten stop rule. This investigation explicitly excludes flashing.

For a future authorized single-partition trial, the proposed write set is **BOOT only**. The candidate package SHA-256 is `9cf8c2159324796f3fb5fa993c7abdc5438edc59f8245ea602c99a429aea81c9`; the original BOOT rollback SHA-256 is `a63676cd0ed5d90cfca66aaa744c173886b72606aec64ea174575f44250064d4`. Verify both on durable media first. Preserve all other partitions. On first boot check ADB, kernel identity, SELinux, decryption/storage, USB, Wi-Fi, Android Bluetooth and crash logs **before** trying Linux HCI. If boot fails, restore the original BOOT via the verified TWRP/Download plan and check the original Android baseline; do not stack more patches.

**Next action:** attach independent external storage and copy/hash the original BOOT, RECOVERY, PIT/manifest, exact stock archive and minimal candidate there. This makes the prepared controlled trial recoverable without relying on the laptop's nearly full root disk or the phone's own user data.
