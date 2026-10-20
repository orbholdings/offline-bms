#!/usr/bin/env python3
"""Sign an existing unsigned build using the personal key from the download pack."""
from pathlib import Path
import argparse,os,subprocess

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--key-dir',type=Path,required=True,help='Directory containing offline-bms.jks and password.txt')
    parser.add_argument('--output',type=Path,default=Path('Offline-BMS.apk'))
    args=parser.parse_args()
    sdk_value=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if not sdk_value:parser.error('Set ANDROID_HOME to your Android SDK directory')
    sdk=Path(sdk_value)
    build_tools=sorted((sdk/'build-tools').iterdir(),key=lambda p:tuple(int(s) for s in p.name.split('.') if s.isdigit()))[-1]
    suffix='.bat' if os.name=='nt' else ''
    project=Path(__file__).resolve().parent.parent
    unsigned=project/'app/build/outputs/apk/release/app-release-unsigned.apk'
    if not unsigned.is_file():parser.error('Build :app:assembleRelease before signing')
    key=args.key_dir/'offline-bms.jks'
    password=args.key_dir/'password.txt'
    if not key.is_file() or not password.is_file():parser.error('The key directory must contain offline-bms.jks and password.txt')
    env=dict(os.environ,OFFLINE_BMS_SIGNING_PASSWORD=password.read_text().strip())
    output=args.output.resolve()
    aligned=output.with_suffix('.aligned.apk')
    subprocess.run([str(build_tools/('zipalign'+('.exe' if os.name=='nt' else ''))),'-f','-p','4',str(unsigned),str(aligned)],check=True)
    subprocess.run([str(build_tools/('apksigner'+suffix)),'sign','--ks',str(key.resolve()),'--ks-key-alias','offline-bms','--ks-pass','env:OFFLINE_BMS_SIGNING_PASSWORD','--key-pass','env:OFFLINE_BMS_SIGNING_PASSWORD','--out',str(output),str(aligned)],env=env,check=True)
    subprocess.run([str(build_tools/('apksigner'+suffix)),'verify',str(output)],env=env,check=True)
    aligned.unlink()
    print('Signed APK:',output)

if __name__=='__main__':main()
