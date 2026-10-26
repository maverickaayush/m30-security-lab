# Kernel and disruption addendum

The family source revision `095206579f69121278ed1dd5fd0d7975c0705c6` contains
`drivers/misc/samsung/scsc_bt/scsc_bluez.c`. Its `slsi_bt_open()` calls the
SCSC file-operation `open`, queues a reader that calls `read`, and its send
callback prepends the H4 packet type before calling the SCSC `write`. The
reader accepts ACL and event H4 packets through `h4_recv_buf` and forwards them
to `hci_recv_frame`. The `power_control` module parameter and rfkill callback
schedule registration/unregistration of a virtual `HCI_BREDR` device.

The same file currently reads `skb->len` and the packet type after
`kfree_skb(skb)` on the successful send path. The staged
`research/kernel/scsc_bluez-send-lifetime.patch` moves the free after those
reads. In the matching family `net/bluetooth/hci_core.c`, `hci_send_frame()`
frees the skb when the driver's `send` callback returns a negative value, so
the driver should not also free the error path. This explains the proposed
success-path fix; it is not a vulnerability in the installed phone because
`CONFIG_SCSC_BT_BLUEZ` and `CONFIG_BT` are disabled there. No kernel was built
or flashed.

Enabling the bridge would add an HCI owner for the SCSC controller. It would
require a matching kernel configuration, toolchain, device tree/ABI, firmware
and service/SELinux ownership plan. Android's HAL must be stopped before BlueZ
opens the same transport, and the reverse handoff must close BlueZ first. A
working HCI path would expose local controller commands and events; it would
not expose handles for unrelated links or create a generic nearby disconnect.
