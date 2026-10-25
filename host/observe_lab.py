#!/usr/bin/env python3
"""Bounded BlueZ receiver check for this project's LAB advertisements.

Reports distinct payload counter values observed in BlueZ's Device1 cache.
This is not an over-the-air packet counter or a link-layer sniffer.
"""
import argparse
import json
import subprocess
import time
from datetime import datetime, timezone

import dbus

UUID = "7f5f42d6-4659-4c15-9fe7-7de7a7033b01"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--seconds", type=int, default=35)
    parser.add_argument("--output", default="")
    args = parser.parse_args()
    seconds = max(5, min(args.seconds, 120))
    bus = dbus.SystemBus()
    manager = dbus.Interface(bus.get_object("org.bluez", "/"), "org.freedesktop.DBus.ObjectManager")
    scan = subprocess.Popen(
        ["bluetoothctl", "--timeout", str(seconds), "scan", "le"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    seen = {}
    first = None
    polls = 0
    start = time.monotonic()
    try:
        while time.monotonic() - start < seconds:
            polls += 1
            for _, interfaces in manager.GetManagedObjects().items():
                dev = interfaces.get("org.bluez.Device1")
                if not dev or UUID not in [str(v).lower() for v in dev.get("UUIDs", [])]:
                    continue
                for company, data in dev.get("ManufacturerData", {}).items():
                    raw = bytes(data)
                    if int(company) != 0xffff or not raw.startswith(b"LAB") or len(raw) < 4:
                        continue
                    counter = raw[3]
                    tag = raw[4:].decode("ascii", errors="replace")
                    if first is None:
                        first = datetime.now(timezone.utc).isoformat()
                    seen.setdefault(counter, {"firstSeenUtc": datetime.now(timezone.utc).isoformat(), "rssiDbm": int(dev.get("RSSI", 0)), "tag": tag})
            time.sleep(0.2)
    finally:
        scan.terminate()
        try:
            scan.wait(timeout=3)
        except subprocess.TimeoutExpired:
            scan.kill()
            scan.wait(timeout=3)
    report = {
        "receiver": "host BlueZ Device1 cache; not packet count",
        "labServiceUuid": UUID,
        "durationSeconds": seconds,
        "polls": polls,
        "firstMatchUtc": first,
        "uniquePayloadCounters": sorted(seen),
        "observations": seen,
    }
    output = json.dumps(report, indent=2)
    if args.output:
        with open(args.output, "w", encoding="utf-8") as handle:
            handle.write(output + "\n")
    print(output)


if __name__ == "__main__":
    main()
