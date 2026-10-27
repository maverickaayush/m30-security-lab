# Storage cleanup and durable backup preparation

Checked 2026-10-27. The cleanup was performed only after the user selected
items 1–6 from the numbered audit. No rollback image, firmware archive, source
tree, signing material, capture, or personal file was removed.

## Approved cleanup

| Selection | Exact item | Action | Measured result |
|---|---|---|---:|
| 1 | Docker image `dbf650906dae` (untagged, zero containers) | Removed by full image ID | 1.84 GB unique layers |
| 2 | Docker image `8577de747e7c` (untagged, zero containers) | Removed by full image ID | 928 MB unique layers |
| 3 | Docker image `ed59c313612c` (untagged, zero containers) | Removed by full image ID | 597 MB unique layers |
| 4 | Docker image `fca23768757e` (untagged, zero containers) | Removed by full image ID | 298 MB unique layers |
| 5 | Docker image `14600a0aa8b4` (untagged, zero containers) | Removed by full image ID | 231 MB unique layers |
| 6 | `/home/aayush-yadav/zerodha-bounty/privy-auth-lab/node_modules` | Removed exact directory; `package-lock.json` retained | 1.3 GB |

The five Docker images were rechecked immediately before removal: each had no
tag and no referencing container. The node_modules path was rechecked as a
directory, not a symlink, and its lockfile remained present. No wildcard,
Docker prune, or Trash operation was used.

Persistent free space increased from 74,792,960 bytes to 5,295,558,656 bytes:
5,220,765,696 bytes recovered (about 4.86 GiB). Copying the stock archive and
rollback set subsequently used about 3.4 GB. The Linux root filesystem now has
about 1.6 GB free. It is the ext4 filesystem `/dev/nvme0n1p5`; the rollback
directory and the project are on the same physical disk. `/tmp` and `/dev/shm`
remain volatile and are not used for the durable backup.

## Reproduction evidence

The pre-removal image digests and Docker layer report are retained in the
terminal action log for this session. The project remained on `main` and its
source and final artifacts were present after cleanup. The approved dependency
directory can be recreated from its retained `package-lock.json`.

