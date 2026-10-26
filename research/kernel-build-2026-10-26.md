# Complete offline kernel builds

Checked 2026-10-26. No phone partition was written and no kernel was booted.

## Source and toolchain

The build uses the public family tree
[`akhil1999/android_kernel_samsung_m30lte@095206579f69121278ed1dd5fd0d7975c0705c6a`](https://github.com/akhil1999/android_kernel_samsung_m30lte/tree/095206579f69121278ed1dd5fd0d7975c0705c6a),
whose README and defconfig identify Linux 4.4.177 and M30 support. It is not
proven to be the exact Samsung source package for the installed
`M305FDDS8CVB2`; Samsung OSRC was checked but its direct M305F search returned
HTTP 403 in this environment. The running kernel has the same base version but
a different release string (`4.4.177-22392479`) and configuration.

The host used GCC 15.2.0 and GNU Make 4.4.1 for host tools. The AArch64
compiler and binutils were extracted project-locally into volatile
`/tmp/m30-cross`: GCC 14.3.0 and binutils 2.46. Java/Android builds use the
existing project-local JDK 21.0.13, SDK and Gradle 8.10.2. No system compiler
or Codex configuration was replaced.

The checked-in reproduction wrapper is [`scripts/build_m30_kernel.sh`](../scripts/build_m30_kernel.sh).
It requires a clean source tree, applies only the optional bridge patches for
the `bluez` mode, uses `HOSTCFLAGS` ending in `-fcommon` for this old host `dtc`,
and passes explicit `-Wno-error=<diagnostic>` compatibility flags required by
GCC 14/15. Those flags turn old-tree warnings into warnings; they do not make
the source ABI-compatible or bootable.

## Source-match comparison

| Tree | Revision observed | What it establishes | Match to installed M305FDDS8CVB2 |
| --- | --- | --- | --- |
| Samsung OSRC | Official portal checked; exact package not acquired (HTTP 403 on direct search) | Required provenance for an exact source release | Unknown; this is the unresolved gate |
| `akhil1999/android_kernel_samsung_m30lte` | `095206579f69121278ed1dd5fd0d7975c0705c6a` | M30-specific 4.4.177 tree, `m30lte_00_defconfig`, SCSC driver and optional BlueZ bridge; complete offline builds above | Closest public functional tree, but not exact: running release/configuration differ |
| `SamarV-121/android_kernel_samsung_universal7904` | remote `HEAD` `554e05f367dfa36c2eefcf1dfaea64946ee6a76f` at audit; current lineage branch Makefile is 4.4.302 | Universal7904 family lineage kernel and newer Android build context | Not a source match for the running 4.4.177 Samsung kernel |
| `SamarV-121/android_device_samsung_m30lte` | `lineage-18.1` remote `HEAD` `7cb4bf31ee5780e078200ccbbf6f08eb298ce7c5` | Community device packaging: separated DT, custom Exynos dtb tool and recovery partition size | Device-tree/packaging reference only; archived and not stock source |

The archived device tree's `BoardConfig.mk` uses a different
`m30lte_defconfig`, separated DT and custom boot image tooling. It is useful
for packaging research, not a reason to transplant its configuration into the
stock phone. The universal7904 kernel's current Makefile is 4.4.302, so its
matching Bluetooth behavior cannot be inferred from the M30's 4.4.177 build.

## Build gates and corrections

The initial baseline reached the full compile but stopped because the old tree
uses `-Werror` and modern GCC diagnostics (`array-compare`, packed members,
`restrict`, and enum/int mismatch). A second pass exposed that the tree's
Android USB ioctl definitions require `-D__ANDROID__`. The final invocation
included both the Android macro and explicit warning overrides. The family
tree's generated `dtb.img` also emitted a warning that
`firmware/tsp_zinitix` is absent.

The unmodified family bridge did not compile against this tree's 4.4 headers:
its bundled `h4_recv.h` referenced missing `hci_skb_pkt_type`,
`hci_skb_expect`, and `KERNEL_VERSION` definitions. The staged local
compatibility patch maps the packet fields to the existing `bt_cb(skb)` and
includes `linux/version.h`. The staged lifetime patch moves `kfree_skb` until
after successful-send statistics are read. Both patches are source review
artifacts, not claims about the disabled installed driver.

## Results

### Baseline family defconfig

`m30lte_00_defconfig` built completely with `CONFIG_BT` disabled and
`CONFIG_SCSC_BT=y`. The output contained `Image`, `Image.gz`, `vmlinux`,
`System.map`, `dtb.img`, four M30 DTBs, and one test module (`kutf.ko`).

| Artifact | SHA-256 |
| --- | --- |
| Image | `415d42710fe18d4fb2f07598a68be16dd964a663a8087fcb2254dfd854965a54` |
| Image.gz | `b3323bcea7a300e651a33c85d0f9d2c7e2950b656f154b73a6ef0d52ac49018d` |
| vmlinux | `0165e7be4943efcaca0ed88008f6ee3a198c760e99d35f99cdd58cfdd0f460a0` |
| System.map | `611bd1b4b389247ae1acdecce0e2dd4d6e1c78c831ca6cf067568870b03a3b59` |
| dtb.img | `acb171e3ba68dbfbdad7ef51cb52aa8b0da85ead138e1c0f68b42c0a159f2487` |

### Native SCSC BlueZ candidate

The same source and defconfig, merged with
[`research/kernel/m30lte-hci.fragment`](kernel/m30lte-hci.fragment), built
completely after applying both staged patches. The final configuration has
`CONFIG_BT=y`, `CONFIG_BT_BREDR=y`, `CONFIG_BT_LE=y`, `CONFIG_BT_RFCOMM=y`,
`CONFIG_BT_RFCOMM_TTY=y`, `CONFIG_SCSC_BT=y`, and
`CONFIG_SCSC_BT_BLUEZ=y`. `CONFIG_BT_HCIVHCI` was not enabled in this native
candidate.

| Artifact | SHA-256 |
| --- | --- |
| Image | `9768a67f1704d133fa4a802b7ed65afdb0b97583a2419132e20b72a56e806e30` |
| Image.gz | `497e9c75e4022fedd1edf230efd816a731de8ed5b20997a6a47ebf7d5962a2eb` |
| vmlinux | `cd40251f7714f463c2bb63c9a07d0c40e348ae368c2ccfafe49d323c3051c683` |
| System.map | `beb1b6f69867e96264804fe3560eb7be388642a1348cbb37650f0496131789a8` |
| dtb.img | `acb171e3ba68dbfbdad7ef51cb52aa8b0da85ead138e1c0f68b42c0a159f2487` |

The output also contained the four M30 DTBs and `kutf.ko`. The complete output
trees and compiler packages remain outside Git; the artifact hashes above were
recorded before cleanup of redundant intermediates.

## What this proves

Both source configurations compile and link offline for this public family
tree. The bridge object was compiled as part of a complete candidate kernel.
The result is not an exact-build match, not an Android boot image, not a
verified Samsung package, and not a boot or HCI runtime test. The family
defconfig differs from the phone (for example, live `CONFIG_USB_OTG` is unset
while the family output enables it). Module vermagic, DT/boot packaging,
vendor firmware ABI, SELinux policy and Android service startup remain
unvalidated. A full source/config match and a tested restore procedure are
required before any future installation consideration.
