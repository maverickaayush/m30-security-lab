# Durable off-phone rollback set

Checked 2026-10-27. The user selected the laptop as the off-phone storage
location, so no external drive is required for this phase. The backup is at:

`/home/aayush-yadav/m30-rollback-20261027/`

The directory and its category directories are mode 0700. Files are mode 0600.
The location is persistent ext4, outside the Git working tree and outside
`/tmp` and `/dev/shm`.

## Contents and independent verification

| Category | Artifact | Size | SHA-256 |
|---|---|---:|---|
| ORIGINAL_WORKING | `BOOT.img` | 33,554,432 | `a63676cd0ed5d90cfca66aaa744c173886b72606aec64ea174575f44250064d4` |
| ORIGINAL_WORKING | `RECOVERY.img` | 39,845,888 | `c0ebb7d6700c6f7ed218d933ab380e5082c5a29a49c05a33b483858c3c6cd311` |
| ORIGINAL_WORKING | `DTB.img` | 8,388,608 | `2daeb1f36095b44b318410b3f4e8b5d989dcc7bb023d1426c492dab0a3053e74` |
| ORIGINAL_WORKING | `DTBO.img` | 8,388,608 | `2daeb1f36095b44b318410b3f4e8b5d989dcc7bb023d1426c492dab0a3053e74` |
| BASELINE_UNTESTED | `baseline-boot.img` | 33,554,432 | `83d500e9b77f6b02ef27118e176ea4ed025c868ceb83c0078ae62d66b8e9abbb` |
| BASELINE_UNTESTED | `Image` | 20,947,080 | `6cabe9ec5a449024fa2bc3dbd5edb5840cb6308c765b02343658bad7457b423e` |
| BLUETOOTH_CANDIDATE_UNTESTED | `bluetooth-candidate-boot.img` | 33,554,432 | `9cf8c2159324796f3fb5fa993c7abdc5438edc59f8245ea602c99a429aea81c9` |
| BLUETOOTH_CANDIDATE_UNTESTED | `Image` | 20,879,112 | `e6895c5f4795985e9cf827de5a6537ecb21916fa6eb6d9e791fdef04281244f6` |
| STOCK_RESTORE | Samsung FUS archive | 3,388,201,984 | `b8090882a4564b5836ffdb38ae936c5412fe8325977e70a299925ce546806d6b` |
| STOCK_RESTORE | extracted `AP-boot.img` | 21,015,072 | `2e056f5eb65ac3c2f3964ed3b5dbf0a5b44b274f43e32079d6eef87a4bdb4963` |
| STOCK_RESTORE | `device.pit` | 8,192 | `552be522ccbddf0f060b917b73ab1bdec008d5e0251ffbd5c2c5aa5967fdf519` |

The source and destination copies of BOOT, RECOVERY, DTB, DTBO, the stock ZIP,
AP boot image and PIT were compared byte-for-byte; all comparisons exited 0.
The destination stock ZIP passed `unzip -tqq`. Source and destination inodes
are different, so these are copies rather than hard links. All destination
hashes were calculated after copying. The Samsung ZIP's five internal CRC and
embedded TAR MD5 checks were already passed by the project inspector and were
not replaced by the local SHA-256.

The separate DTB and DTBO source partitions are distinct PIT entries and each
is 8 MiB, but both are byte-for-byte zero-filled. Their equal digest is a
content result, not a path alias or duplicate-copy error. The active board
data is the embedded DTBH in BOOT; neither zero-filled partition is included
in the proposed BOOT-only trial.

## Redundancy and limitations

There are now two usable copies of the rollback images: the laptop copy above
and the phone user-data copy recorded in the private source manifest. The
laptop copy and project source are on the same physical ext4 disk, so it does
not protect against laptop-disk failure. No independent external disk is
mounted. Hash equality verifies copy integrity, not that a future TWRP or
Download Mode restore will be accepted. The exact stock package is available
for a stock fallback, but no destructive restore test was performed.

