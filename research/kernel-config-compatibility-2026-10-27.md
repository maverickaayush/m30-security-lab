# Kernel configuration compatibility continuation

Checked 2026-10-27 against the live `/proc/config.gz` snapshot
`1c55f8d690acb88c3949b426379ac354c306c18c8a93655f1c5fc86d45532bc7` and the
family-tree Bluetooth candidate at revision
`095206579f69121278ed1dd5fd0d7975c0705c6`.

## Material differences

| Area | Live handset | Earlier Bluetooth candidate | Compatibility action |
|---|---|---|---|
| Linux Bluetooth | `CONFIG_BT` unset; SCSC H4 only | `CONFIG_BT=y`, BR/EDR, LE, RFCOMM and `CONFIG_SCSC_BT_BLUEZ=y` | Keep as the isolated research delta |
| Device mapper | `CONFIG_DM_VERITY=y`; `CONFIG_DM_BUFIO=y`; Android at-most-once default enabled | `CONFIG_DM_VERITY` unset, so selected verity support was absent | Restore `CONFIG_DM_VERITY=y` and `CONFIG_DM_ANDROID_VERITY_AT_MOST_ONCE_DEFAULT_ENABLED=y` |
| Verity variants | FEC and AVB unset | Unset | Keep unset; no evidence requires either variant |
| USB role | `CONFIG_USB_OTG` unset; XHCI host/platform enabled | `CONFIG_USB_OTG=y` | Force OTG off to match the live configuration |
| SCSC logging | `CONFIG_SCSC_DEBUG` unset | Debug, compatibility logging and 4 MiB static ring enabled | Force all three off; this avoids a large unrelated memory/debug delta |
| Storage/encryption | ext4, F2FS, fs encryption and SHA-256 support enabled | Same family symbols were present | Retain family defaults and verify selected symbols after normalization |
| Release/ABI | `4.4.177-22392479` | `4.4.177-g09520657-dirty` | Preserve the real build identity; do not rewrite it |
| Device tree | Active DTB is embedded in BOOT | Candidate `dtb.img` from public family tree | Do not treat the family DTB as compatible; compare structurally before any boot test |

The source Kconfig shows that enabling `DM_VERITY` selects `CRYPTO`,
`CRYPTO_HASH` and `DM_BUFIO`; the new fragment therefore enables only the
observed verity symbols and lets Kconfig resolve dependencies. It does not
enable AVB-specific behavior or disable verification.

The new fragment remains a research build input, not evidence that the public
family tree is suitable for the installed phone. It narrows configuration
differences while the exact Samsung source, DTB and boot ABI remain unresolved.

## Rebuild definition

`research/kernel/m30lte-hci-verity.fragment` adds the Bluetooth bridge and
restores the observed verity/USB/debug choices. `scripts/build_m30_kernel.sh`
accepts a separate `bluez-verity` mode so the older candidate remains
reproducible and the corrected build can be compared independently.

Expected acceptance checks:

1. `olddefconfig` retains every requested symbol without an unmet dependency.
2. The resulting config has `CONFIG_DM_VERITY=y`, the Android at-most-once
   symbol enabled, USB OTG and SCSC debug/static-ring symbols disabled, and
   Bluetooth/SCSC BlueZ symbols enabled.
3. The build completes with the same public source/toolchain and produces
   hashes for Image, Image.gz, vmlinux, System.map, DTBs and `.config`.

These checks establish a better offline candidate only. They do not establish
bootability, Samsung acceptance, HCI registration or Android restoration.

## Completed build and remaining material differences

The `bluez-verity` build completed; kernel and package hashes are in the
installation manifest. The normalized comparison has 46 symbol differences,
including symbols newly visible when Bluetooth is enabled. The full symbol
comparison is [config-delta-20261027.tsv](kernel/config-delta-20261027.tsv).

Beyond the intended BT core/BR-EDR/LE/RFCOMM/SCSC changes, these need review:

- Family DTBH tooling and board-selection symbols are new packaging inputs;
  they do not prove installed board compatibility.
- DEFEX_KERNEL_ONLY, EXYNOS_KERNEL_PROTECTION, KNOX_KAP and TIMA are disabled
  in the family candidate. TIMA_LOG, TZ_ICCC and FMM_LOCK_OFFSET are absent.
  These security/vendor hooks cannot be restored by inventing symbols; matching
  implementations and TrustZone expectations must be established.
- EXYNOS_FMP_FIPS is enabled only in the candidate. This is an encryption-path
  compatibility concern despite matching filesystem/encryption base symbols;
  FIPS self-test behavior must be understood before a boot trial.
- EXYNOS_HDCP2, ION_TEST and NTFS write support are additional family features.
  They are unrelated to the Bluetooth objective and remain review items.
- SOFT_WATCHDOG is disabled and SEC_DEBUG_SOFTDOG_PWDT is missing. Watchdog
  behavior therefore differs from the installed configuration.
- Two SCSC WLAN option declarations are absent, and the Falkor erratum option
  is enabled. These reflect source/config differences, not measured failures.
- Both configurations enable module support, but matching config symbols do
  not establish exported-symbol ABI or module vermagic compatibility.

No release string was copied from the handset. No second rebuild was made
merely to hide the remaining differences; source contracts must be resolved
before choosing their values. Storage, USB and encryption are not declared
runtime-compatible from a configuration comparison alone.
