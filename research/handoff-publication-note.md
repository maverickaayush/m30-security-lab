# Publication value of the SCSC handoff work

Checked 2026-10-26. The optional SCSC-to-BlueZ bridge already exists in
[`scsc_bluez.c` at family revision 0952065](https://github.com/akhil1999/android_kernel_samsung_m30lte/blob/095206579f69121278ed1dd5fd0d7975c0705c6a/drivers/misc/samsung/scsc_bt/scsc_bluez.c).
The [Linux Bluetooth core](https://github.com/torvalds/linux/tree/master/net/bluetooth)
and [BlueZ management API](https://github.com/bluez/bluez/blob/master/doc/mgmt-protocol.rst)
are prior art. Therefore simply enabling `CONFIG_BT` or displaying a phone
button is not novel.

This continuation adds two evidence-backed engineering findings: REA traces
the installed HAL/vendor open, H4 send and close paths to concrete offsets;
and a patched family bridge compiles and links with its own 4.4 headers after
two minimal fixes. Neither establishes a deployed handoff or security flaw.
The source-only skb lifetime issue is disabled on the installed phone; no
installed-device vulnerability or CVE claim follows from it.

A credible open-source contribution would include a **source-matched** patch
series, a reproducible stock-kernel build, an exclusive-owner supervisor,
measured fd release/HCI registration, and a verified Android restore cycle.
That would be a driver/tool engineering result. A technical article can publish
the current static map and compile failures if it clearly labels the family
source gap and private binary provenance. A stronger workshop paper would need
multiple runs, failure injection, compatibility data and comparison against
existing ports. A responsible vulnerability disclosure would require an
actually reachable security impact on an affected build; none is shown here.

The key falsifier is inability to reproduce the installed kernel or to restore
Android Bluetooth after Linux ownership. If the bridge only works in a family
tree and never on the stock M30 build, the current result remains a useful
negative/compatibility note rather than a completed port.
