import io
import struct
import unittest

from scripts.summarize_bt_link import EPOCH_DELTA, parse_address, summarize


TARGET = parse_address("01:02:03:04:05:06")


def record(seconds, packet, drops=0):
    timestamp = EPOCH_DELTA + int(seconds * 1_000_000)
    return struct.pack(">IIIIQ", len(packet), len(packet), 0, drops, timestamp) + packet


def capture(disconnect_at=None, drops=0):
    data = struct.pack(">8sII", b"btsnoop\0", 1, 1002)
    data += record(1000, bytes([4, 3, 11, 0, 0x40, 0]) + TARGET + bytes([1, 0]))
    data += record(1001, bytes([2, 0x40, 0, 1, 0, 0xAA]), drops)
    if disconnect_at is not None:
        data += record(disconnect_at, bytes([4, 5, 4, 0, 0x40, 0, 0x13]))
    data += record(1003, bytes([4, 0x0E, 0]))
    return io.BytesIO(data)


class LinkSummaryTests(unittest.TestCase):
    def test_connected_through_window(self):
        result = summarize(capture(), TARGET, 1000.5, 1002.5)
        self.assertTrue(result["conclusion_ready"])
        self.assertEqual(result["target_acl_packets_in_window"], 1)
        self.assertEqual(result["target_state_at_window_end"], "connected")
        self.assertFalse(any(event["event"] == "disconnected" for event in result["target_events"]))

    def test_disconnect_detected(self):
        result = summarize(capture(disconnect_at=1002), TARGET, 1000.5, 1002.5)
        self.assertTrue(result["conclusion_ready"])
        self.assertEqual(result["target_state_at_window_end"], "disconnected")
        self.assertEqual(result["target_events"][-1]["reason"], 0x13)
        self.assertTrue(result["target_events"][-1]["in_window"])

    def test_missing_handle_or_dropped_records_is_not_conclusive(self):
        result = summarize(capture(), parse_address("10:20:30:40:50:60"), 1000.5, 1002.5)
        self.assertFalse(result["conclusion_ready"])
        self.assertEqual(result["target_state_at_window_start"], "unknown")
        result = summarize(capture(drops=1), TARGET, 1000.5, 1002.5)
        self.assertFalse(result["conclusion_ready"])

    def test_bad_header_rejected(self):
        with self.assertRaises(ValueError):
            summarize(io.BytesIO(b"not-a-capture"), TARGET, 1000, 1001)


if __name__ == "__main__":
    unittest.main()
