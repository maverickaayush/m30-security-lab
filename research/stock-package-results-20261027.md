# Stock package inspection results

Checked 2026-10-27. Direct Samsung FUS download; private archive on persistent
host filesystem under `backups/stock-ins-cvb2/`. No flashing performed.

| TAR member | Bytes | Local SHA-256 | Embedded MD5 | ZIP CRC |
|---|---:|---|---|---|
| `BL_M305FDDS8CVB2_CL22392479_QB49439180_REV00_user_low_ship.tar.md5` | 2406577 | `25fefc44aa983a5b1ebac52b6ca48b44e23fcc4c4fbfe8f071d8720decb044c2` | Pass | Pass |
| `AP_M305FDDS8CVB2_CL22392479_QB49439180_REV00_user_low_ship_meta_RKEY_OS10.tar.md5` | 3567104195 | `0363f91617c5355211c064c86411a1e7474c9d3fb9286de4108e8d1404ab62df` | Pass | Pass |
| `CP_M305FDDS8CVB2_CP21729336_CL22392479_QB49439180_REV00_user_low_ship.tar.md5` | 26460349 | `caa3cf974eb5d88cfecdeec16c39ba13d23c73b7c7e108904eefed60067d500f` | Pass | Pass |
| `HOME_CSC_OMC_ODM_M305FODM8CUI1_CL22392479_QB43262064_REV00_user_low_ship.tar.md5` | 186398913 | `34ec66b654c9b96270bc50cda3277c8fb911c8307792b7e44f42473d9c2cd185` | Pass | Pass |
| `CSC_OMC_ODM_M305FODM8CUI1_CL22392479_QB43262064_REV00_user_low_ship.tar.md5` | 186409148 | `ee33f8215f5815626951f457da898c8b95b61714321f0fd21af8f65766b0b0fb` | Pass | Pass |

## Stock versus installed BOOT

The stock and live extracted kernel are both 19,473,008 bytes. Exactly four
bytes differ, at offsets 0x10da586–0x10da589: the stock `skip_initramfs`
string becomes `want_initramfs`. This is consistent with Magisk patching;
it does not prove which installer performed it.

- Stock kernel SHA-256: `7c8f81a19b34245300662f485dc63d4037e97211a131ac7986977aa91c539871`.
- Live kernel SHA-256: `81319f0a4f93c05ac3bfcdf2a8132ed28564f2f73bfa0d28b7a65fa6c81d3beb`.
- Stock DTBH SHA-256: `298d6f2a83f7689215e30f6a18cf33d82129e516b0783ef5b22ed9965c67b295`.
- Live DTBH SHA-256: `80bcef6b1c78693944a832d77a1674c791e277dbe154d5dcec22929346215711`.
- Stock gzip ramdisk SHA-256: `cbb74f3ae3e14e2fba7f203ee76e384e30b7467c667f47d27ccfdc99a80cd0d7`.
- Live gzip ramdisk SHA-256: `f5e345b1e350fd445050118e30c3aab1b83c669e79f6630bb3281fe3bd6b9dcf`.

The stock boot header's board, command line, offsets, page size, OS and patch
metadata match the live extraction. The ramdisk differs; the live copy has
Magisk files previously documented in the packaging report. No claim of
byte-identical stock and rooted BOOT is made.

All four DTBH variants decode successfully. Their only decoded stock/live
property differences are SYSTEM, VENDOR and ODM `fsmgr_flags`: stock uses
`wait,verify`, while live contains `wait` padded with NUL bytes. Board,
reserved memory, storage addresses, USB, connectivity and clock/regulator
values otherwise match in these decoded comparisons. The family candidate
still differs in GPU maximum clock, CPU minimum frequency and camera regulator
names. Preserving stock/live DTB avoids those board-data changes but does not
prove the family driver's ABI or security hooks are compatible.

## Reproduce without expanding large partitions

```sh
python3 scripts/inspect_stock_zip.py PRIVATE_ARCHIVE.zip PRIVATE_OUTPUT
# To recheck without creating another boot copy:
python3 scripts/inspect_stock_zip.py PRIVATE_ARCHIVE.zip PRIVATE_OUTPUT --no-extract
```

The tool reads all members, verifies ZIP CRCs, records TAR hashes and compares
embedded MD5. It never opens a block device. All outputs containing extracted
firmware or full decoded device trees stay in the ignored backup directory.
A synthetic TAR fixture validated the parser and extraction path. Official
package checks then validated the Samsung build-metadata footer variant.
