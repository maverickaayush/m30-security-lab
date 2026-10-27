# Classification of the 46 earlier configuration differences

Compared the running M305FDDS8CVB2 `/proc/config.gz` (SHA-256 `1c55f8d690acb88c3949b426379ac354c306c18c8a93655f1c5fc86d45532bc7`) with the **earlier** `bluez-verity` candidate. `absent` means the symbol was not emitted in that configuration; for a disabled symbol this does not imply a behavioral difference. Source is the public M30-family tree at `095206579f69121278ed1dd5fd0d7975c0705c6a`. This table is a diagnosis of that earlier build, not the effective configuration of the minimal build.

| Symbol | Running | Earlier candidate | Why different | Source implementation | Boot/runtime impact | Proposed resolution |
|---|---|---|---|---|---|---|
| `BT` | n | y | Intentional Linux stack | `net/bluetooth/Kconfig` | New host stack; ownership conflict possible | Keep for isolated BlueZ trial |
| `BT_BNEP` | absent | n | Menu exposed by BT | `net/bluetooth/bnep/Kconfig` | None, disabled | Leave n |
| `BT_BREDR` | absent | y | BT dependency/default | `net/bluetooth/Kconfig` | Classic protocol available | Keep for SCSC HCI testing |
| `BT_DEBUGFS` | absent | y | BT default | `net/bluetooth/Kconfig` | Diagnostic surface | Set n |
| `BT_HCIBCM203X` | absent | n | Driver menu exposed | `drivers/bluetooth/Kconfig` | None, disabled | Leave n |
| `BT_HCIBFUSB` | absent | n | Driver menu exposed | `drivers/bluetooth/Kconfig` | None, disabled | Leave n |
| `BT_HCIBTSDIO` | absent | n | Driver menu exposed | `drivers/bluetooth/Kconfig` | None, disabled | Leave n |
| `BT_HCIBTUSB` | absent | n | Driver menu exposed | `drivers/bluetooth/Kconfig` | No USB adapter driver | Leave n for internal-only trial |
| `BT_HCIUART` | absent | n | Driver menu exposed | `drivers/bluetooth/Kconfig` | None, disabled | Leave n |
| `BT_HCIVHCI` | absent | n | Driver menu exposed | `drivers/bluetooth/Kconfig` | No VHCI path | Leave n for native bridge |
| `BT_HIDP` | absent | n | Menu exposed | `net/bluetooth/hidp/Kconfig` | None, disabled | Leave n |
| `BT_HS` | absent | y | BT default | `net/bluetooth/Kconfig` | High-speed/AMP stack unnecessary | Set n |
| `BT_LE` | absent | y | BT dependency/default | `net/bluetooth/Kconfig` | BLE host protocol | Keep |
| `BT_MRVL` | absent | n | Driver menu exposed | `drivers/bluetooth/Kconfig` | None, disabled | Leave n |
| `BT_RFCOMM` | absent | y | Intentional Classic transport | `net/bluetooth/rfcomm/Kconfig` | New RFCOMM socket path | Keep |
| `BT_RFCOMM_TTY` | absent | y | RFCOMM default | `net/bluetooth/rfcomm/Kconfig` | New TTY binding | Keep provisionally; not essential to HCI registration |
| `BT_SELFTEST` | absent | n | Menu exposed | `net/bluetooth/Kconfig` | None, disabled | Leave n |
| `DEFEX_KERNEL_ONLY` | y | n | Family defconfig sets n | `security/samsung/defex_lsm/Kconfig`; active via `security/Kconfig` | Mode flag; `SECURITY_DEFEX=n` in live config, so do not equate flag with active DEFEX | Restore y; verify effective config |
| `EXYNOS_DTBH_PAGE_SIZE` | absent | 2048 | Family DTBH build metadata | `arch/arm64/mach-exynos/Kconfig` | Packaging, not runtime feature | Retain for family build; preserve live DTBH in package |
| `EXYNOS_DTBH_PLATFORM_CODE` | absent | `0x50a6` | Family DTBH metadata | same Kconfig | Board selector if family DTBH used | Retain build value; compare all blob headers |
| `EXYNOS_DTBH_SUBTYPE_CODE` | absent | `0x217584da` | Family DTBH metadata | same Kconfig | Board selector if family DTBH used | Retain build value; preserve live DTBH |
| `EXYNOS_DTBTOOL` | absent | y | Family packaging option | `arch/arm64/mach-exynos/Kconfig` | Creates DTBH artifact | Keep for build only |
| `EXYNOS_FMP_FIPS` | n | y | Family defconfig enables | `drivers/crypto/fmp/Kconfig`, FIPS self-test code | Can change inline-storage crypto initialization/failure behavior | Set n, retain `EXYNOS_FMP=y` |
| `EXYNOS_HDCP2` | n | y | Family defconfig | `drivers/soc/samsung/exynos-hdcp/Kconfig` | Display content path, unrelated | Set n |
| `EXYNOS_KERNEL_PROTECTION` | y | n | Family defconfig disables | `drivers/soc/samsung/exynos-el3_mon.c` | EL3 SMC protects kernel text; secure-world ABI unverified | Restore y; static implementation present, runtime ABI unknown |
| `FMM_LOCK_OFFSET` | `7340680` | absent | Declaration/implementation missing in family tree | no match in pinned source | Samsung find-my-mobile/security offset may affect policy or partition access; exact consumer unknown | Source-port or exact Samsung source required; do not invent symbol |
| `HDCP2_EMULATION_MODE` | absent | n | HDCP menu exposed | `drivers/soc/samsung/exynos-hdcp/Kconfig` | None, disabled | Leave n |
| `ION_TEST` | n | y | Family defconfig | `drivers/staging/android/ion/Kconfig` | Test device/interface, unrelated | Set n |
| `KNOX_KAP` | y | n | Family defconfig | `drivers/misc/Kconfig`, arm64 page-table and Samsung call sites | Alters Knox/page table paths; secure-world compatibility unverified | Restore y; compare emitted objects and boot behavior later |
| `MACH_EXYNOS7885_NONE` | absent | n | Board menu choice | `arch/arm64/mach-exynos/Kconfig` | None, disabled | Leave n |
| `MACH_EXYNOS7904_M30LTE_SWA_OPEN` | absent | y | Family board selector | `arch/arm64/mach-exynos/Kconfig` | Controls DTBH variant build | Keep as build metadata; verify board IDs |
| `NODE_FOR_SELFTEST_FAIL` | absent | y | Selected by FMP FIPS | `drivers/crypto/fmp/Kconfig` | FIPS failure policy | Disappears when FMP FIPS=n |
| `NTFS_DEBUG` | absent | n | NTFS menu exposed | `fs/ntfs/Kconfig` | None, disabled | Leave n |
| `NTFS_FS` | n | y | Family defconfig | `fs/ntfs/Kconfig` | Unused filesystem code | Set n |
| `NTFS_RW` | absent | y | NTFS suboption | `fs/ntfs/Kconfig` | Unneeded write path | Disappears with NTFS_FS=n |
| `PANIC_FOR_SELFTEST_FAIL` | absent | n | FMP FIPS choice exposed | `drivers/crypto/fmp/Kconfig` | None, disabled | Disappears with FMP FIPS=n |
| `PLATFORM_EXYNOS` | absent | y | Family platform selector | Exynos platform Kconfig | Build/platform wiring, not a new chipset | Retain if needed by source; compare DT contract |
| `QCOM_FALKOR_ERRATUM_1003` | n | y | Family ARM64 default | `arch/arm64/Kconfig` | CPU erratum workaround not relevant to Exynos | Set n |
| `SCSC_BT_BLUEZ` | n | y | Intended SCSC Linux HCI bridge | `drivers/misc/samsung/scsc_bt/Kconfig` | Adds HCI registration; Android ownership unresolved | Keep; never concurrently open H4 |
| `SCSC_WLAN_DUAL_STATION` | n | absent | Declaration absent in family tree | no match in pinned source | Disabled live; no lost enabled path shown | Record source delta; no synthetic symbol |
| `SCSC_WLAN_MAX_LINK_SPEED` | n | absent | Declaration absent in family tree | no match in pinned source | Disabled live; no lost enabled path shown | Record source delta; no synthetic symbol |
| `SEC_DEBUG_SOFTDOG_PWDT` | y | absent | Samsung implementation missing in family tree | no match in pinned source | Watchdog/debug integration may differ at boot or faults | Locate exact source/port before assuming equivalence |
| `SOFT_WATCHDOG` | y | n | Family defconfig disables | `drivers/watchdog/Kconfig`, `softdog.c` | Standard software watchdog missing | Restore y; does not replace missing Samsung PWDT hook |
| `TIMA` | y | n | Family defconfig has malformed `# CONFIG_TIMA isn ot set` | `arch/arm64/Kconfig` plus Samsung security sources | Enables TrustZone-related paths | Set y; inspect effective config and ABI |
| `TIMA_LOG` | y | absent | Hidden because TIMA=n | `drivers/misc/Kconfig` | Missing secure log interface | Restore TIMA then TIMA_LOG=y |
| `TZ_ICCC` | y | absent | Depends on TIMA; family defconfig request dropped | `security/tz_iccc/Kconfig`, `drivers/md/dm-verity-target.c` | Verity error can send secure-world status SMC | Restore TIMA and TZ_ICCC; verify effective config |

**Key distinction:** the previous report called several security options “missing implementations.” Source inspection shows their code exists but family defconfig disabled it or dependencies hid it. The true source gaps found here are the enabled `FMM_LOCK_OFFSET` and `SEC_DEBUG_SOFTDOG_PWDT`; their exact runtime roles still need matching-source comparison. The two absent SCSC WLAN declarations are disabled in the running kernel. No attempt was made to force undefined symbols into `.config`.
