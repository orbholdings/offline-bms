#!/usr/bin/env python3
"""Validate the final standalone APK, not only unsigned/intermediate build files."""
from pathlib import Path
import argparse,hashlib,struct,subprocess,zipfile,zlib

p=argparse.ArgumentParser();p.add_argument('--apk',type=Path,required=True);p.add_argument('--build-tools',type=Path,required=True);args=p.parse_args()
apk=args.apk.resolve();tools=args.build_tools.resolve();raw=apk.read_bytes()
with zipfile.ZipFile(apk) as archive:
    assert len(archive.namelist())==len(set(archive.namelist())), 'Duplicate archive entries'
    assert archive.testzip() is None, 'ZIP CRC check failed'
    assert {'AndroidManifest.xml','resources.arsc','classes.dex'}.issubset(archive.namelist())
    entry=archive.getinfo('resources.arsc');name_len,extra_len=struct.unpack_from('<HH',raw,entry.header_offset+26)
    assert entry.compress_type==zipfile.ZIP_STORED
    assert (entry.header_offset+30+name_len+extra_len)%4==0
    for name in archive.namelist():
        if not name.endswith('.dex'):continue
        dex=archive.read(name)
        assert dex[:4]==b'dex\n' and dex[7]==0
        assert struct.unpack_from('<I',dex,32)[0]==len(dex)
        assert hashlib.sha1(dex[32:]).digest()==dex[12:32]
        assert zlib.adler32(dex[12:])&0xffffffff==struct.unpack_from('<I',dex,8)[0]
assert not apk.with_suffix(apk.suffix+'.idsig').exists(), 'Standalone output has an incremental-install companion'
subprocess.run([str(tools/'zipalign'),'-c','4',str(apk)],check=True)
for sdk in [23,24,28,31,35,36]:
    subprocess.run(['java','-jar',str(tools/'lib/apksigner.jar'),'verify','--min-sdk-version',str(sdk),'--max-sdk-version',str(sdk),str(apk)],check=True,capture_output=True)
print('Final APK archive, resource alignment, DEX checksums and SDK 23/24/28/31/35/36 signatures passed.')
print('APK SHA-256:',hashlib.sha256(raw).hexdigest())
