#!/usr/bin/env python3
"""ADB UI helper for repeatable, explicitly named lab controls."""
import argparse
import re
import subprocess
import xml.etree.ElementTree as ET


def adb(serial, *args):
    return subprocess.run(["adb", "-s", serial, *args], check=True, capture_output=True, timeout=12).stdout


def nodes(serial):
    adb(serial, "shell", "uiautomator", "dump", "/data/local/tmp/m30-lab-ui.xml")
    xml = adb(serial, "exec-out", "cat", "/data/local/tmp/m30-lab-ui.xml")
    return ET.fromstring(xml).iter("node")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("serial")
    parser.add_argument("contains")
    parser.add_argument("--direction", choices=["down", "up"], default="down")
    parser.add_argument("--scrolls", type=int, default=8)
    args = parser.parse_args()
    for attempt in range(max(1, min(args.scrolls, 12))):
        for node in nodes(args.serial):
            value = node.attrib.get("text", "")
            if args.contains.lower() not in value.lower() or not node.attrib.get("clickable") == "true":
                continue
            bounds = [int(x) for x in re.findall(r"\d+", node.attrib["bounds"])]
            x, y = (bounds[0] + bounds[2]) // 2, (bounds[1] + bounds[3]) // 2
            adb(args.serial, "shell", "input", "tap", str(x), str(y))
            print(f"Tapped {args.contains!r} at ({x},{y}) after {attempt} scrolls")
            return
        if args.direction == "down":
            adb(args.serial, "shell", "input", "swipe", "540", "1900", "540", "500", "250")
        else:
            adb(args.serial, "shell", "input", "swipe", "540", "400", "540", "1850", "250")
    raise SystemExit(f"Control {args.contains!r} not found")


if __name__ == "__main__":
    main()
