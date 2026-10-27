# Stock firmware acquisition and provenance

Checked 2026-10-27. Device properties were read again from the authorized
Android session before querying firmware metadata:

| Field | Observed value |
|---|---|
| Model/device/product | `SM-M305F` / `m30lte` / `m30ltedd` |
| Build/fingerprint | `M305FDDS8CVB2`, Android 10, 2022-03-01 patch |
| Active CSC/carrier | `INS` / India (`ro.csc.sales_code`, `ro.boot.carrierid`) |
| Official CSC version | `M305FODM8CUI1` (`ril.official_cscver`) |
| Bootloader property | `M305FDDS8CVB2` (`ro.bootloader`, `ro.boot.bootloader`) |
| Binary revision | 8 is corroborated by the FUS version tuple and distributor metadata; the older report's revision-4 note was not retained |

Samsung's official update history lists `M305FDDS8CVB2` for SM-M305F as
Android 10, released 2022-04-06 with the 2022-03-01 security patch
([Samsung update history](https://doc.samsungmobile.com/SM-M305F/006517190527/wel.html)).

The official `topjohnwu/samloader-rs` 2.2.0 binary was downloaded from its
GitHub release for a metadata-only FUS query. Local tool archive SHA-256:
`f6029dcce75b8a66acc1975529085c53903f7cdb35505e0ac0a973f2652480ff`.
The command was:

```sh
samloader check-update --model SM-M305F --region INS --all --verbose
```

Samsung FUS returned this historical version tuple:

```text
M305FDDS8CVB2/M305FODM8CUI1/M305FDDS8CVB2/M305FDDS8CVB2
```

That is direct server metadata obtained through the documented downloader;
it establishes that Samsung's backend knows the exact build, CSC and modem
components. It does not itself provide a local archive hash.

## Package identified

The SamFW listing for model `SM-M305F`, CSC `INS`, and PDA
`M305FDDS8CVB2` reports a Samsung-cloud-sourced full package and these child
names:

| Component | Filename | Listed size |
|---|---|---:|
| AP | `AP_M305FDDS8CVB2_CL22392479_QB49439180_REV00_user_low_ship_meta_RKEY_OS10.tar.md5.zip` | 2.79 GB |
| BL | `BL_M305FDDS8CVB2_CL22392479_QB49439180_REV00_user_low_ship.tar.md5.zip` | 2.07 MB |
| CP | `CP_M305FDDS8CVB2_CP21729336_CL22392479_QB49439180_REV00_user_low_ship.tar.md5.zip` | 20.59 MB |
| CSC | `CSC_OMC_ODM_M305FODM8CUI1_CL22392479_QB43262064_REV00_user_low_ship.tar.md5.zip` | 165.41 MB |
| HOME_CSC | `HOME_CSC_OMC_ODM_M305FODM8CUI1_CL22392479_QB43262064_REV00_user_low_ship.tar.md5.zip` | 165.41 MB |

The listing reports full-package MD5 `e2614fc2c238f49a8b0506c2ee87c1c7`.
This is distributor-provided metadata, not a locally calculated hash or an
independent Samsung signature. The listing and component names are recorded
for reproducibility ([SamFW build page](https://samfw.com/firmware/sm-m305f/INS/M305FDDS8CVB2)).

## Initial acquisition attempt (superseded below)

The exact package was identified through Samsung FUS and the matching INS
metadata, but the AP/BL/CP/CSC bytes were not downloaded into the project.
The host has a persistent root filesystem, but it is effectively full (about
63 MiB free after securing the rollback images) and no separate high-capacity
durable filesystem is available; the AP alone is listed at 2.79 GB. The
SamFW page was also protected by a Cloudflare challenge for direct CLI
downloads. No mirror package was substituted, and no firmware archive was
installed.

The required next acquisition is a durable copy of the exact FUS package (or
all five child files) followed by archive extraction, local SHA-256/MD5
verification, and comparison of its AP boot/DTB contents with the private
live BOOT copy. Until then, stock-package comparison and Samsung restore
acceptance remain unverified.

## Completed persistent acquisition

After user-authorized cache/installer cleanup, the interrupted partial was
copied to `backups/stock-ins-cvb2/` on the persistent host filesystem. SHA-256
matched before resuming. Samloader 2.2.0 completed the exact-version FUS
download there with exit 0. No new firmware download used tmpfs.

Archive: `SM-M305F_2_20220312160231_63hiiaa4v8_fac.zip`.
Local SHA-256: `b8090882a4564b5836ffdb38ae936c5412fe8325977e70a299925ce546806d6b`.
Local MD5: `b9cde880d11751cc37b1cb626973f6b0`.

All five ZIP entries passed CRC checks. All five embedded TAR MD5 footers
matched the streamed content, including the Samsung build metadata preceding
each checksum. The inspector initially did not recognize this metadata footer;
its corrected pass verified all five. These are integrity checks, not a
cryptographic proof of Samsung authorship. Provenance is the direct FUS
download via the official upstream samloader project. The distributor-listed
MD5 above differs from this archive; no claim of distributor-byte identity is
made, and the distributor checksum was not used to authenticate this download.

AP, BL, CP, CSC and HOME_CSC are retained inside the verified ZIP. Full
extraction would exceed free disk space, so the read-only streaming inspector
hashed each TAR and copied only the small AP boot artifact for analysis.
See [stock-package-results-20261027.md](stock-package-results-20261027.md)
for individual hashes and stock/live comparison. This acquires the restore
materials but does not test a restore operation.
