# Approved storage cleanup — 2026-10-28

The user selected items 2, 5, 6 and 7, then approved item 1 if unnecessary.
Only the listed paths and 22 exact Docker image identities were removed.
Docker containers and volumes were not removed, and no force/prune operation was used.
All selected images were rechecked for container references immediately before removal.

- Item 2: removed stopped failed-build output `/dev/shm/m30-minimal`; preserved small configuration/build metadata privately first.
- Items 5–7: removed the 22 selected unused images. Full identities, tags, exit codes and Docker output are in ignored `evidence/cleanup-20261028-approved-2-5-6-7/actions.json`.
- Item 1: removed `/tmp/m30-baseline` only after preserving and independently hashing 17 final artifacts (251,558,793 bytes) under the private rollback directory's `BASELINE_UNTESTED/offline-build-20261027-preserved/`. The old Image and configuration matched the published offline baseline manifest. These artifacts remain untested; they are not the corrected booted kernel.
- Items 3 and 4 were retained: temporary source Git metadata and the Strix sandbox image.

## Measured space

Docker cleanup freed 9,983,049,728 bytes (9.98 GB) on the persistent filesystem.
Preserving the old baseline's final artifacts used about 252 MB, leaving about
10.71 GB available on the persistent filesystem at completion.
Removing the failed build freed 2,372,886,528 bytes in `/dev/shm`.
Removing the old baseline tree freed 3,580,588,032 bytes in `/tmp`.
Both temporary locations are RAM-backed tmpfs mounts; their released space
must not be counted as persistent disk space. Concurrent system activity can
slightly affect free-space deltas. The earlier cleanup table mixed these
storage types and overstated the persistent-space estimate.

The selected items did not reclaim 20 GB of persistent disk space. No additional
items were removed to meet that target. The original BOOT and installed
registration-candidate package hashes were verified after cleanup. Stock
firmware, recovery images, project source and signing material were outside
the deletion scope. No phone operation or kernel installation occurred during
this cleanup.
