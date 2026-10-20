#!/usr/bin/env python3
"""Dependency-free CLI build using JDK 17 and Android SDK 35 build tools."""
from pathlib import Path
import argparse,os,subprocess,zipfile,shutil,xml.etree.ElementTree as ET

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--android-jar',type=Path,required=True)
    parser.add_argument('--build-tools',type=Path,required=True)
    parser.add_argument('--work',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    project=Path(__file__).resolve().parent.parent
    work=args.work.resolve();work.mkdir(parents=True,exist_ok=True)
    gen=work/'generated';classes=work/'classes';dex=work/'dex'
    for folder in (gen,classes,dex):
        if folder.exists():shutil.rmtree(folder)
        folder.mkdir(parents=True,exist_ok=True)
    tools=args.build_tools.resolve();android_jar=args.android_jar.resolve()
    java=Path(os.environ['JAVA_HOME'])/'bin/java' if os.environ.get('JAVA_HOME') else Path('java')
    ns='http://schemas.android.com/apk/res/android';ET.register_namespace('android',ns)
    tree=ET.parse(project/'app/src/main/AndroidManifest.xml');root=tree.getroot()
    root.set('package','com.simon.offlinebms');root.set('{'+ns+'}versionCode','13');root.set('{'+ns+'}versionName','1.2.8')
    manifest=work/'AndroidManifest.xml';tree.write(manifest,encoding='utf-8',xml_declaration=True)
    resources=work/'resources.zip';unsigned=work/'unsigned.apk'
    for path in (resources,unsigned,work/'aligned.apk'):
        if path.exists():path.unlink()
    subprocess.run([str(tools/'aapt2'),'compile','--dir',str(project/'app/src/main/res'),'-o',str(resources)],check=True)
    subprocess.run([str(tools/'aapt2'),'link','-I',str(android_jar),'--manifest',str(manifest),'--auto-add-overlay','-R',str(resources),'--java',str(gen),'--min-sdk-version','31','--target-sdk-version','35','--version-code','12','--version-name','1.2.8','-o',str(unsigned)],check=True)
    sources=list((project/'app/src/main/java').rglob('*.java'))+list(gen.rglob('*.java'))
    subprocess.run([str(java),'-m','jdk.compiler/com.sun.tools.javac.Main','--release','11','-classpath',str(android_jar),'-d',str(classes)]+[str(p) for p in sources],check=True)
    subprocess.run([str(java),'-cp',str(tools/'lib/d8.jar'),'com.android.tools.r8.D8','--release','--lib',str(android_jar),'--min-api','31','--output',str(dex)]+[str(p) for p in classes.rglob('*.class')],check=True)
    with zipfile.ZipFile(unsigned,'a',zipfile.ZIP_DEFLATED) as z:
        for path in dex.glob('*.dex'):z.write(path,path.name)
    aligned=work/'aligned.apk'
    subprocess.run([str(tools/'zipalign'),'-f','-p','4',str(unsigned),str(aligned)],check=True)
    private=project/'private-signing';key=private/'offline-bms.jks';password=private/'password.txt'
    env=dict(os.environ,OFFLINE_BMS_SIGNING_PASSWORD=password.read_text().strip())
    output=args.output.resolve()
    # A normal downloaded APK uses embedded signatures, not a separate incremental-install sidecar.
    sidecar=output.with_suffix(output.suffix+'.idsig')
    if sidecar.exists():sidecar.unlink()
    subprocess.run([str(java),'-jar',str(tools/'lib/apksigner.jar'),'sign','--ks',str(key),'--ks-key-alias','offline-bms','--ks-pass','env:OFFLINE_BMS_SIGNING_PASSWORD','--key-pass','env:OFFLINE_BMS_SIGNING_PASSWORD','--v1-signing-enabled','true','--v2-signing-enabled','true','--v3-signing-enabled','true','--v4-signing-enabled','false','--out',str(output),str(aligned)],env=env,check=True)
    result=subprocess.run([str(java),'-jar',str(tools/'lib/apksigner.jar'),'verify','--verbose','--print-certs',str(output)],check=True,capture_output=True,text=True)
    (work/'signature-check.txt').write_text(result.stdout)
    subprocess.run([str(tools/'zipalign'),'-c','4',str(output)],check=True)
    with zipfile.ZipFile(output) as z:
        assert len(z.namelist())==len(set(z.namelist())),'Duplicate APK entries'
        assert z.testzip() is None,'APK CRC verification failed'
        assert z.getinfo('resources.arsc').compress_type==zipfile.ZIP_STORED
        assert {'AndroidManifest.xml','resources.arsc','classes.dex'}.issubset(z.namelist())
    assert not sidecar.exists(),'Standalone APK must not need a v4 companion'
    print('Built and signed:',output.name,output.stat().st_size,'bytes')

if __name__=='__main__':main()
