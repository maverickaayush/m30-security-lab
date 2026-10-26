# Kernel installation readiness gates

Checked 2026-10-27. This gate table records why no flash was attempted.

| Gate | Evidence | Status | Consequence |
|---|---|---|---|
| Correct handset | ADB properties: SM-M305F, `m30lte`, `m30ltedd`, Exynos 7904 | Pass | Device operations were serial-pinned and read-only |
| Candidate build | Complete family baseline and Bluetooth candidate artifacts with hashes | Pass for offline build | Does not establish device compatibility |
| Boot package structure | AOSP-style image parsed; unchanged round-trip byte-identical; candidate re-parsed | Pass for structure | Signature, ABI and bootloader acceptance remain unknown |
| Candidate fits BOOT | Padded candidate equals 33,554,432-byte BOOT partition | Pass for size | Size is not a safety or compatibility proof |
| Exact source match | Samsung OSRC package for `M305FDDS8CVB2` not acquired; public family tree differs | **Fail** | Do not install family candidate |
| Storage/verity compatibility | Live `CONFIG_DM_VERITY=y`; candidate disables it; candidate DTB differs | **Fail** | Rebuild from matched config/source before boot testing |
| BOOT/DTB/DTBO/RECOVERY backup | Live source, phone copy and host copy hashes match | Pass, limited | Host copy is volatile; no durable second storage |
| Backup redundancy | No separate mounted durable storage available | **Fail** | Keep phone copy; acquire independent storage before flash |
| Recovery image | TWRP contents found statically in RECOVERY image | Unknown | Not booted; return-to-Android path untested |
| Download mode/restore tool | Heimdall is not installed; no Download Mode USB test; no matching AP/BL/CP/CSC package | **Unknown** | No approved restore procedure |
| Vendor firmware/ABI | Candidate uses public family source and family DTB; controller firmware loading not traced for this image | **Unknown** | Android Bluetooth and Wi-Fi boot behavior unvalidated |
| Host conditions | Battery 100%, USB ADB stable; root filesystem at 100% usage | **Fail** | Free durable host space before more builds or copies |
| Installation scope | Candidate would alter BOOT and embedded DTB; exact Samsung packaging/signing path unresolved | **Unknown** | No partition write authorized by the gates |

## Decision

The installation gates do not pass. No reboot, recovery boot, download-mode
transition, partition write, or controller ownership takeover was performed.
The smallest safe next gate is an exact Samsung firmware/source and restore
package match, followed by a durable independent backup copy and a verified
recovery/download return path. The candidate package remains an offline
research artifact only.

Team Win documents an M30-specific recovery path and warns that the device
uses dm-verity ([M30 recovery notes](https://twrp.me/samsung/samsunggalaxym30.html)).
That page does not prove that this handset's installed recovery boots. The
official Heimdall project documents Samsung Download Mode communication over
USB and the need for a matching firmware package ([Heimdall](https://github.com/Benjamin-Dobell/Heimdall)).
No such package or Download Mode session was available in this run.
