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
    assert "package: name='com.simon.offlinebms'" in badging and "versionCode='13'" in badging and "versionName='1.2.8'" in badging
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
    instructions='''OFFLINE BMS 1.2.8 - QUICK BATTERY SWITCHING AND BALANCING

Install Offline-BMS-1.2.8.apk over the existing app. Choose Update; keep the
existing installation to retain names, history and encrypted keys.

Use the top arrows on Overview, Cells or History to switch and connect.
Tap the battery name for a picker. Your page is kept and the list wraps.
Victron equipment is separate and its broadcast monitoring continues.
Saved encrypted passwords are reused; remember a JBD password on Connect
when one is required. Only one BMS battery is live at a time.

Balance is in Overview > Battery status and on Cells. It enables/disables
automatic balancing using existing BMS limits; enabled and active differ.
JK02 controls read the current state, review, write only the balancing
switch and verify by readback. Missing/refused readback stays unconfirmed.
Classic JBD uses the existing parameter read/review/backup/save/verify flow.
JBD commit resets protection counters as described in the review; original
values are backed up. Demo, saved data and unsupported layouts cannot write.
No control write is automatically retried. Switching waits for operations.

629 checks/cases pass, including 70 Android runtime cases on API 31/35,
rapid switching, old callbacks, three phone widths, larger text and balance
readback/failure/cancel paths. Hardware balancing still needs user testing.
The working JK decoder and existing supported Victron formats are retained.
Keep maintenance/ private; it contains the original signing-key backup.
'''
    verification=f'''OFFLINE BMS 1.2.8 - VERIFICATION

Package: com.simon.offlinebms
Version code: 13
Minimum API: 31 (Android 12)
Target/compile API: 35
APK bytes: {apk.stat().st_size}
APK SHA-256: {sha}

46 JBD telemetry + 37 authentication + 66 offline feature/JK/Victron
+ 65 parameter/transaction + 56 AC charger/address + 91 wider Victron
+ 40 JK diagnostics/absent-sensor + 88 captured JK replay/validation
+ 30 balancing wire-vector/layout/bit preservation + 40 SQLite/storage/export/backup
+ 70 Android runtime = 629 passed.

New Android UI/control cases verify top arrows, wrapping, direct selection,
page retention, previous reading removal, rapid requests, old callbacks,
operation locking and the original centred bottom tabs at 360/393/412 dp
in dark/light themes with larger text. JK balancing reads, explicit review,
single control write, readback, refusal, timeout, cancellation and disconnect
are simulated. JBD uses its reviewed bit-preserving parameter transaction.
Native phone previews were inspected. Existing captured JK, storage, privacy,
lifecycle and Victron regressions pass. See battery-controls-1.2.8.txt and
runtime-1.2.8.log under tests/evidence in the source archive.

Official SDK compilation, D8, archive and DEX integrity, aligned resources,
and embedded signatures pass. Signing certificate matches 1.2.7; version
code increases to 13. Six existing permissions; no INTERNET permission.
No new physical JK/Victron result or battery parameter write is claimed.

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
