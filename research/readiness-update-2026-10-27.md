# Updated installation readiness decision

Checked 2026-10-27 after the approved storage cleanup and durable-copy pass.
No partition was written.

| Gate | Evidence | Status | Consequence |
|---|---|---|---|
| Correct handset and build | Serial-pinned ADB: SM-M305F, M305FDDS8CVB2, kernel 4.4.177-22392479; battery 100%; SELinux enforcing; Bluetooth on | Pass | Recheck immediately before any write |
| Durable original rollback images | BOOT, RECOVERY, DTB and DTBO copied to `/home/aayush-yadav/m30-rollback-20261027/`; destination hashes and byte comparisons pass | Pass for integrity | Restore acceptance still untested |
| Backup redundancy | Laptop copy plus phone copy; no external filesystem | Conditional | Sufficient for a bounded trial only with the phone copy retained; not independent physical host redundancy |
| Exact stock restore source | Direct Samsung FUS archive for SM-M305F/INS/M305FDDS8CVB2; ZIP CRC and five TAR MD5 checks pass | Pass for acquisition/integrity | Samsung flashing acceptance and full restore remain untested |
| Recovery access | TWRP screen entry/return observed; Download Mode USB detection and PIT read were previously completed | Pass for access | TWRP image restore and Heimdall write remain untested |
| Candidate packaging | Unchanged live BOOT round-trip byte-identical; baseline and Bluetooth packages fit the 32 MiB BOOT partition; candidate keeps live DTBH and ramdisk | Pass offline | Bootloader acceptance and runtime boot remain unknown |
| Source/ABI compatibility | Public family source is buildable and materially closer; FMM_LOCK_OFFSET and SEC_DEBUG_SOFTDOG_PWDT are absent from that source; secure-world ABI is unproven | Conditional risk | Must be tested as a baseline-first controlled boot, with stop rules |
| Device-tree compatibility | Live and stock DTBH board/resources match; family DTBH differs in clocks and camera regulator names, so it is excluded from the package | Pass for selected DTBH | Driver/source ABI remains untested |
| Recovery utility | Offline Heimdall PIT parsing passed; a live non-writing Heimdall session still needs one Download Mode detection check | Pending | Complete live detection before a BOOT write |
| Host conditions | Battery 100%, USB ADB stable, persistent root has about 1.6 GB free | Pass with caution | Avoid another large build; keep rollback media untouched |

## Decision

The candidate is ready for a **prepared, baseline-first boot experiment**, but
the write gate remains closed until a live, non-writing Heimdall `detect` and
PIT check are repeated on the current handset. This is a narrow operational
check, not a request to flash. The unresolved FMM/debug source gap is a real
runtime risk, but it has been reduced to a named, observable uncertainty and
the unchanged live DTBH/ramdisk remove the larger board-data and Magisk risks.

If the live detection/PIT check passes, the next experiment should use the
minimal baseline package first, writing BOOT only. Verify ADB, storage and
decryption, USB, Wi-Fi, SELinux, Magisk and Android Bluetooth. If that baseline
boots, the Bluetooth candidate can be considered separately. If it fails to
boot, stop and restore the original BOOT using the prepared TWRP or Download
Mode procedure; do not stack candidate images. No DTB, DTBO, RECOVERY, userdata,
bootloader or vbmeta write is in scope.

## Remaining exact uncertainties

1. Live Heimdall USB detection on this host/version has not yet been repeated
   after the durable-copy pass.
2. A BOOT-only restore has never been performed, so restore acceptance remains
   a plan supported by hashes and PIT metadata, not a tested guarantee.
3. The family source lacks the two enabled Samsung FMM/debug symbols and has
   no demonstrated secure-world ABI match; only a controlled boot can resolve
   the remaining runtime question.

