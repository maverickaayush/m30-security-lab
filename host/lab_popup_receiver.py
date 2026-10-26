#!/usr/bin/env python3
"""Opt-in Linux desktop popup for this project's synthetic LAB advertisement.

Requires BlueZ, python3-dbus, and notify-send. The receiver never pairs or
connects. Only the project UUID and manufacturer marker can trigger a popup.
"""

import argparse
import subprocess
import time

import dbus

UUID = "7f5f42d6-4659-4c15-9fe7-7de7a7033b01"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seconds", type=int, default=35)
    parser.add_argument("--message", help="Optional local message; otherwise show the phone's four-character LAB text")
    parser.add_argument("--max-popups", type=int, default=3, help="Maximum desktop notifications (1-10; default 3)")
    parser.add_argument("--dry-run", action="store_true", help="Print matching LAB events without a desktop popup")
    args = parser.parse_args()
    seconds = max(5, min(args.seconds, 120))
    message = args.message.strip()[:120] if args.message else None
    max_popups = max(1, min(args.max_popups, 10))
    if args.message is not None and not message:
        parser.error("--message must contain visible text")

    bus = dbus.SystemBus()
    manager = dbus.Interface(bus.get_object("org.bluez", "/"), "org.freedesktop.DBus.ObjectManager")
    scan = subprocess.Popen(
        ["bluetoothctl", "--timeout", str(seconds), "scan", "le"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    seen = set()
    popups = 0
    start = time.monotonic()
    try:
        while time.monotonic() - start < seconds:
            for _, interfaces in manager.GetManagedObjects().items():
                dev = interfaces.get("org.bluez.Device1")
                if not dev or UUID not in [str(v).lower() for v in dev.get("UUIDs", [])]:
                    continue
                for company, data in dev.get("ManufacturerData", {}).items():
                    raw = bytes(data)
                    if int(company) != 0xffff or not raw.startswith(b"LAB") or len(raw) < 4:
                        continue
                    tag = raw[4:].decode("ascii", errors="replace")
                    marker = (raw[3], tag)
                    if marker in seen:
                        continue
                    seen.add(marker)
                    popup_text = message or tag
                    print(f"LAB marker counter={raw[3]} tag={tag!r}; popup={popup_text!r}", flush=True)
                    if not args.dry_run and popups < max_popups:
                        result = subprocess.run(
                            ["notify-send", "--expire-time=5000", "M30 LAB message", popup_text],
                            timeout=5,
                            check=False,
                        )
                        print(f"notify-send exit={result.returncode}", flush=True)
                        popups += 1
            time.sleep(0.2)
    finally:
        scan.terminate()
        try:
            scan.wait(timeout=3)
        except subprocess.TimeoutExpired:
            scan.kill()
            scan.wait(timeout=3)
    print(f"Receiver stopped after {seconds}s; distinct LAB markers={len(seen)}; popups={popups}", flush=True)


if __name__ == "__main__":
    main()
