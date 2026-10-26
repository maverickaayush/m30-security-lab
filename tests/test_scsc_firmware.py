import importlib.util
from pathlib import Path
import unittest


SOURCE = Path(__file__).resolve().parents[1] / "scripts" / "inspect_scsc_firmware.py"
spec = importlib.util.spec_from_file_location("inspect_scsc_firmware", SOURCE)
inspector = importlib.util.module_from_spec(spec)
spec.loader.exec_module(inspector)


class SmxfInspectorTest(unittest.TestCase):
    def test_corruption_and_bounds_are_detected(self):
        image = bytearray(256)
        image[8:12] = b"smxf"
        image[16:20] = (192).to_bytes(4, "little")
        image[28:32] = (224).to_bytes(4, "little")
        for offset, value in ((188, inspector.ether_crc(image[:188])),
                              (32, inspector.ether_crc(image[192:224])),
                              (24, inspector.ether_crc(image[192:]))):
            image[offset:offset + 4] = value.to_bytes(4, "little")
        # Header CRC includes the other CRC fields, so calculate it last.
        image[188:192] = inspector.ether_crc(image[:188]).to_bytes(4, "little")
        result = inspector.inspect(bytes(image))
        self.assertTrue(all(x["matches"] for x in result["crc"].values()))
        image[240] ^= 1
        result = inspector.inspect(bytes(image))
        self.assertFalse(result["crc"]["full_image"]["matches"])
        self.assertTrue(result["crc"]["header"]["matches"])
        image[28:32] = (257).to_bytes(4, "little")
        with self.assertRaises(ValueError):
            inspector.inspect(bytes(image))


if __name__ == "__main__":
    unittest.main()
