# M30 recovery and Download Mode access

Checked 2026-10-27. Private USB/device identifiers and the raw PIT are stored
in the ignored `backups/stock-ins-cvb2/` directory. No flash, wipe or restore
was run.

The copied RECOVERY partition contains a TWRP ramdisk. This was initially a
static observation only. Team Win's [M30 page](https://twrp.me/samsung/samsunggalaxym30.html)
identifies `m30lte` and cautions that the device uses dm-verity. It does not
establish that this phone's installed recovery will boot.

The user entered Download Mode with physical buttons and USB. The laptop saw
Samsung USB ID `04e8:685d`. Samloader 2.2.0 `detect --json` identified the
model as SM-M305F. `dump-pit --no-reboot` completed with exit 0 and produced an
8,192-byte PIT (SHA-256
`552be522ccbddf0f060b917b73ab1bdec008d5e0251ffbd5c2c5aa5967fdf519`).
Its BOOT, RECOVERY, DTB and DTBO entries agree in names and sizes with the
Android-side block-device inventory: 32, 38, 8 and 8 MiB respectively.
The user exited Download Mode. Serial-pinned ADB then saw Android 10 build
M305FDDS8CVB2, original kernel 4.4.177-22392479, SELinux enforcing and
Bluetooth enabled. This verifies a detection/read/return cycle, not a tested
restore. Samloader's upstream README documents `detect`, `dump-pit` and
`flash` as distinct operations ([official source](https://github.com/topjohnwu/samloader-rs)).

A separate physical recovery boot reached the TWRP screen, as reported by the
user. The laptop did not see an ADB recovery session at that moment, so this
is a user-observed screen result rather than host-verified recovery ADB. The
user was instructed to keep TWRP read-only, avoid Wipe/Install/Restore, and
use Reboot → System. ADB subsequently reconnected in Android and reported
SM-M305F, build M305FDDS8CVB2, kernel 4.4.177-22392479, SELinux enforcing
and Bluetooth enabled. This verifies a TWRP-screen-to-Android return. No
recovery write was used merely to test backups.

For a future authorized restore, the exact FUS archive is present and its
five component integrity checks pass, but the precise component mapping and
bootloader acceptance of a stock/modified BOOT image have not been tested.
The stock AP's `boot.img.lz4` is known; the current phone's Magisk-patched BOOT
is separately preserved. An installation plan would have to specify which
artifact restores the *current* rooted setup and which returns to stock,
verify the package-to-PIT mapping, and account for dm-verity/fstab effects.
There is no justified flash command in this report.
