#!/usr/bin/env python3
"""Check the public APK and source version before publishing a release."""
from pathlib import Path
import hashlib
import json
import re
import zipfile

root = Path(__file__).resolve().parent.parent
metadata = json.loads((root / 'downloads/release.json').read_text())
version = metadata['version']
assert re.fullmatch(r'\d+\.\d+\.\d+', version), 'Invalid version'
assert metadata['file'] == 'Offline-BMS.apk', 'Unexpected APK filename'
assert metadata['package'] == 'com.simon.offlinebms', 'Unexpected package'
apk = root / 'downloads' / metadata['file']
data = apk.read_bytes()
digest = hashlib.sha256(data).hexdigest()
assert len(data) == metadata['bytes'], 'APK size differs'
assert digest == metadata['sha256'], 'APK checksum differs'
assert (root / 'downloads/SHA256SUMS').read_text().strip() == digest + '  ' + apk.name
gradle = (root / 'app/build.gradle').read_text()
assert re.search(r"versionName\s+'" + re.escape(version) + r"'", gradle), 'Source version differs'
assert int(re.search(r'versionCode\s+(\d+)', gradle).group(1)) == metadata['version_code']
assert int(re.search(r'minSdk\s+(\d+)', gradle).group(1)) == metadata['min_sdk']
assert int(re.search(r'targetSdk\s+(\d+)', gradle).group(1)) == metadata['target_sdk']
assert (root / 'docs/releases' / ('v' + version + '.md')).is_file(), 'Missing release notes'
with zipfile.ZipFile(apk) as archive:
    assert archive.testzip() is None, 'Corrupt APK archive'
    assert {'AndroidManifest.xml', 'classes.dex', 'resources.arsc'} <= set(archive.namelist())
print('Verified Offline BMS', version, '/', len(data), 'bytes / SHA-256', digest)
