#!/usr/bin/env python3
from pathlib import Path
import argparse,hashlib,re,subprocess,zipfile

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--apk',type=Path,required=True)
    parser.add_argument('--previous-apk',type=Path,required=True)
    parser.add_argument('--build-tools',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();project=Path(__file__).resolve().parent.parent
    apk=args.apk.resolve();tools=args.build_tools.resolve()
    def certificate(path):
        result=subprocess.run(['java','-jar',str(tools/'lib/apksigner.jar'),'verify','--verbose','--print-certs',str(path)],check=True,capture_output=True,text=True).stdout
        return re.search(r'certificate SHA-256 digest: ([0-9a-f]+)',result).group(1),result
    old_cert,_=certificate(args.previous_apk);new_cert,signature=certificate(apk)
    assert old_cert==new_cert,'Signing certificate differs from the installed app'
    permissions=subprocess.run([str(tools/'aapt'),'dump','permissions',str(apk)],check=True,capture_output=True,text=True).stdout
    badging=subprocess.run([str(tools/'aapt'),'dump','badging',str(apk)],check=True,capture_output=True,text=True).stdout
    assert "package: name='com.simon.offlinebms'" in badging and "versionCode='14'" in badging and "versionName='1.2.9'" in badging
    expected={'android.permission.BLUETOOTH_SCAN','android.permission.BLUETOOTH_CONNECT','android.permission.POST_NOTIFICATIONS','android.permission.FOREGROUND_SERVICE','android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE','android.permission.WAKE_LOCK'}
    assert set(re.findall(r"uses-permission: name='([^']+)'",permissions))==expected
    subprocess.run(['python3',str(project/'tools/verify-apk.py'),'--apk',str(apk),'--build-tools',str(tools)],check=True)
    sha=hashlib.sha256(apk.read_bytes()).hexdigest()
    work=args.output.resolve().parent/'release-package';work.mkdir(parents=True,exist_ok=True)
    source=work/'OfflineBMS-source.zip'
    with zipfile.ZipFile(source,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for path in sorted(project.rglob('*')):
            if not path.is_file():continue
            relative=path.relative_to(project)
            if any(part in ('build','.gradle','private-signing','__pycache__','.git') for part in relative.parts) or path.name=='local.properties':continue
            archive.write(path,'OfflineBMS/'+relative.as_posix())
    instructions='''OFFLINE BMS 1.2.9 - AUTOMATIC RECONNECT

Install the APK over the existing app to retain profiles, keys and history.
Connect a battery and leave monitoring running. When out of range, the app
shows Waiting for battery and reconnects when that battery returns.
Tools > Monitoring & recording has the automatic-reconnect switch, enabled
by default. Keep screen-off monitoring enabled to reconnect in background.
Stop monitoring cancels reconnecting until you connect again. Opening the
app resumes the last requested battery unless you stopped it. No service
starts itself after a reboot. Saved readings keep their timestamp until
fresh readings arrive; missing intervals are excluded from measured energy.

Reconnection repeats telemetry setup only, never control/settings writes.
Existing quick switching, balancing controls and Victron support remain.
Physical return-to-range behaviour still needs the user's confirmation.
Keep maintenance/ private: it contains the original signing-key backup.
'''
    verification=f'''OFFLINE BMS 1.2.9 - VERIFICATION

Package: com.simon.offlinebms
Version code: 14
Minimum API: 31 (Android 12)
Target/compile API: 35
APK bytes: {apk.stat().st_size}
APK SHA-256: {sha}

Protocol/control checks: 519. Host SQLite/storage checks: 40.
Android runtime checks: see tests/evidence/runtime-1.2.9.log.
The new checks cover loss of range, availability-based reconnection, fresh
JK telemetry after reconnect, explicit stop, battery switching, Bluetooth
off/on, permissions, unsupported services, app resume and cached readings.
Hardware reconnection still needs confirmation on a phone and BMS.

Official SDK compilation, D8, archive/DEX integrity, aligned resources,
and embedded signatures are verified. The certificate matches 1.2.8 and
the version code increases to 14. Six existing permissions; no INTERNET.

PERMISSIONS
{permissions}
SIGNATURE VERIFICATION
{signature}
'''
    with zipfile.ZipFile(args.output,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        archive.write(apk,apk.name);archive.write(source,source.name)
        archive.writestr('START-HERE.txt',instructions);archive.writestr('VERIFICATION.txt',verification)
        archive.write(project/'README.md','USER-GUIDE.md')
        for path in sorted((project/'private-signing').iterdir()):
            if path.is_file():archive.write(path,'maintenance/private-signing/'+path.name)
        archive.writestr('maintenance/KEEP-PRIVATE.txt','Keep this signing backup private; do not publish it in public repositories.\n')
    with zipfile.ZipFile(args.output) as archive:
        assert archive.testzip() is None
        assert hashlib.sha256(archive.read(apk.name)).hexdigest()==sha
    (work/'VERIFICATION.txt').write_text(verification)
    print('Verified app and source pack:',args.output.name,args.output.stat().st_size,'bytes')

if __name__=='__main__':main()
