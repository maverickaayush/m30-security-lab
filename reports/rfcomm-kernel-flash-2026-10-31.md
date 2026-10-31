# RFCOMM kernel BOOT installation — 2026-10-31

The Samsung SM-M305F (`m30lte`, Android 10 build `M305FDDS8CVB2`) now runs
the RFCOMM-enabled research kernel. The user authorized a BOOT-only flash after
the battery reached 35%. The phone entered Download Mode through ADB; Heimdall
2.1.0 reported `BOOT upload successful`, rebooted it, and Android completed
boot. No other partition was written.

## Package and rollback

The offline RFCOMM build was based on the M30-family source revision
`095206579f69121278ed1dd5fd0d7975c0705c6a` with the SCSC, ABOX and
RFCOMM corrections already described in the 2026-10-28 HCI report. Its
20 MiB `Image` hash is
`7e49a023a071b7ef4f02a33de5d8d24ead63dc01ae0119c4adf2f843a56e53b3`.
A private copy received the four-byte `want_initramfs` patch required by this
phone's Magisk ramdisk; its hash is
`5e5d0b6af4fceeff4a8f89e84ea3cfb0742d0395142e130c95436aee06846c2c`.
The package script reproduced the original working BOOT byte for byte before
substituting that kernel. The resulting 32 MiB package hash is
`498999fea57b10f6f95bcaef3ec0c57fe52a4088864233734764d59bcfce6549`.
Its extracted device tree and ramdisk match the currently installed BOOT
readback byte for byte. The running and candidate kernel configurations match.

A fresh, read-only copy of the pre-flash BOOT partition was saved under
`/home/aayush-yadav/m30-rollback-20261027/BUILD/rfcomm-package-20261031/installed-before-rfcomm-BOOT.img`.
Its SHA-256 is `f41bb7078526c33cf125ac0a3e5bd99ab4771464b19c3ed015a6bdfb070e1a52`.
The original working BOOT backup and exact stock firmware archive also passed
hash checks; the stock ZIP integrity check passed. The older all-artifact
rollback manifest cannot pass in full because seven temporary baseline files
were removed during the documented 2026-10-28 storage cleanup. The required
original BOOT, stock package and PIT artifacts passed individual checks.

The live PIT read before flashing matched the saved PIT SHA-256
`552be522ccbddf0f060b917b73ab1bdec008d5e0251ffbd5c2c5aa5967fdf519`.
BOOT is identifier 12, 65,536 blocks (32 MiB). The package and Heimdall log
remain in the private rollback directory. The post-flash BOOT partition
readback matched the package hash exactly.

## Post-boot checks

- `sys.boot_completed=1`; kernel `4.4.177-g09520657-dirty`, build `#1` dated
  2026-10-28 23:44:13 IST.
- SELinux Enforcing; Magisk root works; `/data` mounted with about 43 GiB free.
- Vendor Bluetooth HAL running, Android Bluetooth manager ON, and Wi-Fi enabled.
- Linux `hci0` exists; `/proc/net/protocols` lists `RFCOMM` and `L2CAP`.

These checks establish successful installation and RFCOMM kernel registration.
A supervised Linux RFCOMM socket exchange with the cooperating laptop fixture
has not yet been run on this image.

The source patches, effective configuration and build instructions are in the
[private kernel diff repository](https://github.com/maverickaayush/m30-kernel-diff).
