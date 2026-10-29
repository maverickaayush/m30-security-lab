#!/usr/bin/env python3
"""Summarize one owned Classic link from a private Android BTSnoop/H4 capture.

Prints no addresses, ACL payloads, pairing keys, or unrelated-device details.
It needs a Connection Complete event for the target to map its controller handle.
"""

import argparse
import datetime as dt
import json
import math
import re
import struct

EPOCH_DELTA = 0x00DC_DDB3_0F2F_8000
HEADER = struct.Struct(">8sII")
RECORD = struct.Struct(">IIIIQ")


def parse_time(value):
    parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        raise argparse.ArgumentTypeError("timestamps need a UTC offset")
    return parsed.timestamp()


def parse_address(value):
    if not re.fullmatch(r"[0-9a-fA-F]{2}(?::[0-9a-fA-F]{2}){5}", value):
        raise argparse.ArgumentTypeError("expected six hex address octets")
    return bytes(int(part, 16) for part in reversed(value.split(":")))


def summarize(fileobj, target, start, end):
    header = fileobj.read(HEADER.size)
    if len(header) != HEADER.size:
        raise ValueError("truncated BTSnoop header")
    magic, version, datalink = HEADER.unpack(header)
    if (magic, version, datalink) != (b"btsnoop\0", 1, 1002):
        raise ValueError("expected BTSnoop v1 with HCI UART/H4 datalink 1002")

    handle = None
    events = []
    acl_packets = 0
    acl_bins = [0] * math.ceil((end - start) / 5)
    packets = 0
    dropped = 0
    truncated = 0
    capture_start = capture_end = None
    state = state_at_start = state_at_end = "unknown"
    while True:
        raw = fileobj.read(RECORD.size)
        if not raw:
            break
        if len(raw) != RECORD.size:
            raise ValueError("truncated BTSnoop record header")
        original, included, _flags, drops, timestamp = RECORD.unpack(raw)
        if included > original or included > 1_048_576:
            raise ValueError("invalid BTSnoop record length")
        packet = fileobj.read(included)
        if len(packet) != included:
            raise ValueError("truncated BTSnoop packet")
        if included != original:
            truncated += 1
        when = (timestamp - EPOCH_DELTA) / 1_000_000
        if capture_start is None:
            capture_start = when
        capture_end = when
        packets += 1
        dropped = max(dropped, drops)

        if len(packet) == 14 and packet[:3] == b"\x04\x03\x0b" and packet[6:12] == target and packet[12] == 1:
            if packet[3] == 0:
                handle = int.from_bytes(packet[4:6], "little") & 0x0FFF
                state = "connected"
                events.append({"event": "connected", "utc": dt.datetime.fromtimestamp(when, dt.timezone.utc).isoformat(), "in_window": start <= when <= end})
            else:
                events.append({"event": "connection_failed", "status": packet[3], "utc": dt.datetime.fromtimestamp(when, dt.timezone.utc).isoformat(), "in_window": start <= when <= end})
        elif len(packet) == 7 and packet[:3] == b"\x04\x05\x04":
            event_handle = int.from_bytes(packet[4:6], "little") & 0x0FFF
            if handle is not None and event_handle == handle:
                events.append({"event": "disconnected", "reason": packet[6], "utc": dt.datetime.fromtimestamp(when, dt.timezone.utc).isoformat(), "in_window": start <= when <= end})
                handle = None
                state = "disconnected"
        elif len(packet) >= 5 and packet[0] == 2 and handle is not None and start <= when <= end:
            acl_handle = int.from_bytes(packet[1:3], "little") & 0x0FFF
            if acl_handle == handle:
                acl_packets += 1
                acl_bins[min(int((when - start) // 5), len(acl_bins) - 1)] += 1

        if when <= start:
            state_at_start = state
        if when <= end:
            state_at_end = state

    if capture_start is None:
        raise ValueError("empty BTSnoop capture")
    return {
        "capture_start_utc": dt.datetime.fromtimestamp(capture_start, dt.timezone.utc).isoformat(),
        "capture_end_utc": dt.datetime.fromtimestamp(capture_end, dt.timezone.utc).isoformat(),
        "window_covered": capture_start <= start and capture_end >= end,
        "target_connection_seen": any(event["event"] == "connected" for event in events),
        "target_state_at_window_start": state_at_start,
        "target_state_at_window_end": state_at_end,
        "target_events": events,
        "target_acl_packets_in_window": acl_packets,
        "target_acl_packets_per_5s": acl_bins,
        "total_capture_packets": packets,
        "cumulative_drop_count": dropped,
        "truncated_records": truncated,
        "conclusion_ready": capture_start <= start and capture_end >= end and
                            state_at_start == "connected" and dropped == 0 and
                            truncated == 0,
        "note": "HCI evidence from the receiver phone; ACL count is not decoded audio continuity.",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("capture", help="private BTSnoop H4 file")
    parser.add_argument("--target", type=parse_address, required=True, help="owned accessory Classic address")
    parser.add_argument("--from-utc", dest="start", type=parse_time, required=True)
    parser.add_argument("--to-utc", dest="end", type=parse_time, required=True)
    args = parser.parse_args()
    if args.end <= args.start:
        parser.error("end must be after start")
    if args.end - args.start > 600:
        parser.error("analysis window must be at most 600 seconds")
    with open(args.capture, "rb") as source:
        result = summarize(source, args.target, args.start, args.end)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
