# M30 kernel installation readiness

Checked 2026-10-27. This table supersedes the gate status from the earlier
offline-only phase. The user excluded kernel installation from this phase;
no installation was attempted.

| Gate | Current evidence | Status | Remaining consequence |
|---|---|---|---|
| Handset and firmware | Serial-pinned ADB: SM-M305F, M305FDDS8CVB2, INS, CSC M305FODM8CUI1; FUS tuple agrees | Pass | Recheck before any future write |
| Boot partition backup | 32 MiB BOOT copied read-only; source, phone and persistent-host SHA-256 agree; unchanged unpack/repack reproduced it byte-for-byte | Pass for copy/structure | Actual restore remains untested |
| DTB/DTBO/RECOVERY backup | Separate source/copy hashes agree; DTB/DTBO are distinct zero-filled partitions | Pass for copy | Recovery boot/restore still needs validation |
| Independent backup storage | Persistent host copy and phone user-data copy; no separate mounted external filesystem | Fail | Create a second verified copy on independent storage |
| Exact stock package | Direct FUS download of exact INS version to persistent ignored directory; ZIP CRC and five TAR MD5 footers pass | Pass for acquisition/integrity | Samsung signing/flash acceptance is untested |
| Download Mode | User entered it; USB ID 04e8:685d; samloader 2.2.0 identified SM-M305F and read 8 KiB PIT, SHA-256 `552be522ccbddf0f060b917b73ab1bdec008d5e0251ffbd5c2c5aa5967fdf519`; Android returned afterward | Pass for access and read | Restoration through this tool has not been exercised |
| Recovery | TWRP ramdisk verified statically; user physically reached TWRP screen; ADB returned to original Android build/kernel with SELinux enforcing and Bluetooth on | Pass for boot/return | Recovery ADB and actual restore remain untested |
| Stock/live boot association | Stock and live extracted kernels same size, four byte `skip_initramfs`/`want_initramfs` difference; DTBH differs only in fstab verify flags | Pass for strong offline association | Live Magisk/security behavior is still not a stock image |
| Candidate boot package | Corrected `bluez-verity` image packaged in 32 MiB container; extracted Image/DTBH/ramdisk hashes agree | Pass for offline structure | No bootloader signature/acceptance or runtime check |
| Kernel source and config | Public family source at `0952065`; 46 normalized config differences remain, including security hooks, FMP_FIPS and watchdog | Fail | Resolve substantive ABI/security differences before a justified boot trial |
| Candidate DTBH | Board variants and structural layout align; family clock and camera regulator values differ from exact stock/live | Fail | Match stock/live board data and check driver binding contracts |
| Host conditions | The archive and rollback images are persistent, but root filesystem is nearly full after acquisition | Fail for further package work | Add durable capacity before another complete build/backup |

The corrected offline build establishes compilation and boot-package parsing.
It does not establish that the handset would boot, expose Linux HCI, retain
Wi-Fi/Bluetooth, or return safely to Android. Android still runs the original
kernel and Bluetooth stack. No phone partitions were written.

The next compatibility work is to compare the exact stock kernel config and
Samsung security/FMP/watchdog interfaces against the family source, then
eliminate unrelated DTBH differences. A matching package filename by itself
would not resolve those source and ABI questions. The [stock package
results](stock-package-results-20261027.md), [config comparison](kernel-config-compatibility-2026-10-27.md),
and [device-tree comparison](device-tree-compatibility-2026-10-27.md) record
the checks and their limits. The private archive, PIT, images and identifiers
are excluded from Git.
