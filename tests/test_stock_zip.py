import importlib.util
import io
import hashlib
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location(
    'stock_zip', Path(__file__).resolve().parents[1] / 'scripts/inspect_stock_zip.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class SamsungFooterTests(unittest.TestCase):
    def check_footer(self, content, checksum):
        reader = module.DigestReader(io.BytesIO(content + checksum + b'  AP_TEST.tar\n'))
        while reader.read(137):
            pass
        return reader.footer()

    def test_metadata_footer_crosses_read_boundaries(self):
        content = bytes(4096) + b'Show the build information\nRBS BUILD_ID:123\n'
        checksum = hashlib.md5(content).hexdigest().encode()
        self.assertTrue(self.check_footer(content, checksum)['matches'])

    def test_corruption_is_not_accepted(self):
        content = bytes(4096)
        checksum = hashlib.md5(content).hexdigest().encode()
        self.assertFalse(self.check_footer(b'X' + content[1:], checksum)['matches'])
