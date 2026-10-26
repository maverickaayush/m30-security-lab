#!/usr/bin/env python3
"""Read-only Maxwell/SCSC smxf header and integrity inspector.

Layout and CRC ranges follow the M30-family fwhdr.c and fwimage.c at
095206579f69121278ed1dd5fd0d7975c0705c6a. This does not authenticate
firmware: CRC detects accidental changes, not a trusted publisher.
"""
import argparse
import hashlib
import json
import struct
import sys
import zlib
from pathlib import Path


def u16(data: bytes, offset: int) -> int:
    return struct.unpack_from("<H", data, offset)[0]


def u32(data: bytes, offset: int) -> int:
    return struct.unpack_from("<I", data, offset)[0]


def ether_crc(data: bytes) -> int:
    # Linux ether_crc = bitrev32(crc32_le(~0, bytes)); zlib returns the
    # complemented result, so invert before reversing the 32 bits.
    value = zlib.crc32(data) ^ 0xFFFFFFFF
    return int(f"{value:032b}"[::-1], 2)


def inspect(data: bytes) -> dict:
    if len(data) < 192 or data[8:12] != b"smxf":
        raise ValueError("no complete smxf v0.2/v1 header")
    header_length = u32(data, 16)
    constant_length = u32(data, 28)
    if not 192 <= header_length <= constant_length <= len(data):
        raise ValueError("invalid header or constant-section bounds")
    build_id = data[48:176].split(b"\0", 1)[0].decode("ascii", "replace")
    checks = {
        "header": (u32(data, header_length - 4), ether_crc(data[:header_length - 4])),
        "constant": (u32(data, 32), ether_crc(data[header_length:constant_length])),
        "full_image": (u32(data, 24), ether_crc(data[header_length:])),
    }
    return {
        "format": "smxf", "size": len(data), "sha256": hashlib.sha256(data).hexdigest(),
        "header_version": f"{u16(data, 14)}.{u16(data, 12)}",
        "firmware_api_version": f"{u16(data, 22)}.{u16(data, 20)}",
        "header_length": header_length, "constant_section_end": constant_length,
        "runtime_length": u32(data, 36), "entry_point": u32(data, 40),
        "build_id": build_id,
        "r4_panic_record_offset": u32(data, 176),
        "m4_panic_record_offset": u32(data, 180),
        "crc": {name: {"stored": f"0x{stored:08x}", "calculated": f"0x{actual:08x}",
                       "matches": stored == actual}
                for name, (stored, actual) in checks.items()},
    }


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("firmware", type=Path, help="local copy; never writes to it")
    args = p.parse_args()
    try:
        result = inspect(args.firmware.read_bytes())
    except (OSError, ValueError) as exc:
        print(f"firmware inspection failed: {exc}", file=sys.stderr)
        return 2
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if all(check["matches"] for check in result["crc"].values()) else 1


if __name__ == "__main__":
    raise SystemExit(main())
