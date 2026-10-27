# Device-tree compatibility comparison

Checked 2026-10-27 using the live embedded DTBH extracted from BOOT and the
public family candidate `dtb.img` at revision
`095206579f69121278ed1dd5fd0d7975c0705c6`. No stock AP package was available,
so the first comparison below did not include stock. The later direct FUS
acquisition enabled the stock comparison recorded at the end.

## Container and blob inventory

Both DTBH containers have four FDT blobs at offsets 2048, 165888, 329728 and
493568. The blob totals are:

| Variant | Live total | Candidate total |
|---|---:|---:|
| rev00 | 162,088 | 162,067 |
| rev01 | 162,152 | 162,130 |
| rev02 | 162,016 | 161,994 |
| rev03 | 160,172 | 160,149 |

DTBH hashes:

- live embedded container: `80bcef6b1c78693944a832d77a1674c791e277dbe154d5dcec22929346215711`
- family candidate container: `acb171e3ba68dbfbdad7ef51cb52aa8b0da85ead138e1c0f68b42c0a159f2487`

The FDTs were decoded with the source-tree `dtc`. Every blob decoded
successfully, and each corresponding root node has the same board family and
hardware-revision range:

| Blob | Root compatible/model | Hardware range |
|---|---|---|
| rev00 | `samsung,Universal7904` / M30LTE SWA OPEN rev00 | 0–0 |
| rev01 | `samsung,Universal7904` / M30LTE SWA OPEN rev01 | 1–2 |
| rev02 | `samsung,Universal7904` / M30LTE SWA OPEN rev02 | 3–3 |
| rev03 | `samsung,Universal7904` / M30LTE SWA OPEN rev03 | 4–255 |

## Structural findings

The live and candidate trees retain the same node layout for reserved memory,
storage, USB, pinctrl and the `wifibt_if`/`btabox_rmem` connectivity nodes. No
path or reserved-memory region was added or removed in this comparison. That
is useful family-level evidence, but it does not prove compatible register
values or firmware assumptions.

The decoded value differences are material:

- GPU maximum clock and CPU minimum-frequency values differ.
- Camera regulator names differ throughout the S2MPB03 regulator block.
- The live fstab properties encode `wait` as a three-cell value while the
  candidate emits the equivalent string form.

The candidate therefore has a similar board structure but is not the exact
installed DTB. The active live DTB is embedded in BOOT; the separate DTB and
DTBO named partitions are distinct 8 MiB devices that are byte-for-byte zero,
as recorded in the private backup investigation. Preserving the live embedded
DTB during a future kernel experiment would avoid replacing board data, but
kernel/DTB ABI compatibility would still require a source-matched build and a
booted recovery path.

Acceptance for a future package is an exact stock AP DTBH whose blobs and
board values match the private live BOOT extraction, followed by offline
package parsing. This report does not authorize mixing the family kernel with
the live DTB or flashing either combination.

## Stock comparison completed after FUS acquisition

The direct FUS archive was acquired on persistent storage and all ZIP CRCs
and five TAR MD5 checks passed. Its embedded DTBH has the same four variant
sizes as live. Decoded stock/live diffs contain only the SYSTEM/VENDOR/ODM
fstab `verify` removal, retaining a NUL-padded `wait` string in live. The
stock/live board, memory, storage, USB, connectivity and clock/regulator
properties otherwise match. See [stock package results](stock-package-results-20261027.md).

This supports the installed boot artifact's association with the exact stock
package and accounts for its verity patch; it does not resolve candidate
kernel/DTB compatibility. Candidate GPU maximum is 1,300,000 versus stock/live
845,000 in the encoded clock units. CPU minimums also differ (343,000 versus
449,000 and 520,000 versus 936,000). These are substantive tuning changes,
not harmless hash differences. Camera supply-name changes can affect driver
lookups even where voltages agree. A future package should avoid these
unrelated deltas, after checking the driver's binding expectations against
the stock/live DTB. No DTB was patched or flashed in this phase.
