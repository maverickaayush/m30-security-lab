#!/usr/bin/env python3
"""Read Samsung ZIP/TAR metadata without expanding system/vendor images.

All ZIP members are drained, checking ZIP CRCs. Optional small boot artifacts
are copied to a private output directory; no block devices are accessed.
"""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import tarfile
import zipfile


class DigestReader:
    def __init__(self, stream):
        self.stream = stream
        self.sha = hashlib.sha256()
        self.md5 = hashlib.md5()
        self.tail = b''
        self.size = 0

    def read(self, size=-1):
        data = self.stream.read(size)
        self.sha.update(data)
        self.size += len(data)
        buffered = self.tail + data
        if len(buffered) > 1024:
            self.md5.update(buffered[:-1024])
        self.tail = buffered[-1024:]
        return data

    def footer(self):
        # Samsung may append build metadata before its final md5sum line.
        for offset in range(len(self.tail)):
            suffix = self.tail[offset:]
            if not re.fullmatch(rb'[0-9a-fA-F]{32}  [^\r\n]+\r?\n?', suffix):
                continue
            expected = suffix[:32].decode('ascii', errors='replace')
            if any(c not in '0123456789abcdefABCDEF' for c in expected):
                continue
            digest = self.md5.copy()
            digest.update(self.tail[:offset])
            return {'supplied_md5': expected, 'calculated_md5': digest.hexdigest(),
                    'matches': expected.lower() == digest.hexdigest()}
        return {'matches': None, 'reason': 'No recognized aligned MD5 footer'}


def inspect(archive, output, extract=True):
    output.mkdir(mode=0o700, parents=True, exist_ok=True)
    results = []
    with zipfile.ZipFile(archive) as z:
        for entry in z.infolist():
            if entry.is_dir():
                continue
            result = {'name': entry.filename, 'size': entry.file_size, 'members': []}
            with z.open(entry) as stream:
                reader = DigestReader(stream)
                if entry.filename.endswith(('.tar', '.tar.md5')):
                    with tarfile.open(fileobj=reader, mode='r|') as tar:
                        for member in tar:
                            result['members'].append({'name': member.name, 'size': member.size})
                            if extract and member.name in ('boot.img', 'boot.img.lz4', 'dtb.img', 'dtb.img.lz4', 'dtbo.img', 'dtbo.img.lz4'):
                                if not member.isfile() or member.size > 64 * 1024 * 1024:
                                    raise ValueError('Unexpected boot artifact type/size')
                                name = Path(entry.filename).name.split('_', 1)[0] + '-' + member.name
                                with (output / name).open('xb') as target:
                                    source = tar.extractfile(member)
                                    while chunk := source.read(1024 * 1024):
                                        target.write(chunk)
                while reader.read(1024 * 1024):
                    pass
                result.update(sha256=reader.sha.hexdigest(), zip_crc_verified=True)
                if entry.filename.endswith('.tar.md5'):
                    result['tar_md5'] = reader.footer()
                results.append(result)
                print(json.dumps(result), flush=True)
    return results


if __name__ == '__main__':
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--no-extract', action='store_true')
    args = parser.parse_args()
    inspect(args.archive, args.output, not args.no_extract)
