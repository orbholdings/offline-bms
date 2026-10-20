#!/usr/bin/env python3
"""Run the real Android host tests against a CLI-built APK (test-only dependencies)."""
from pathlib import Path
import argparse,os,subprocess,urllib.parse,xml.etree.ElementTree as ET,zipfile

p=argparse.ArgumentParser()
for name in ['android-jar','compiled-classes','apk','previous-apk','lib','work']:
    p.add_argument('--'+name,type=Path,required=True)
a=p.parse_args();project=Path(__file__).resolve().parent.parent
work=a.work.resolve();classes=work/'classes';classes.mkdir(parents=True,exist_ok=True)
for aar in a.lib.glob('*.aar'):
    with zipfile.ZipFile(aar) as z:
        if 'classes.jar' in z.namelist():aar.with_suffix('.jar').write_bytes(z.read('classes.jar'))
jars=[str(x.resolve()) for x in a.lib.glob('*.jar')]
cp=os.pathsep.join([str(a.android_jar.resolve()),str(a.compiled_classes.resolve())]+jars)
tests=['ConnectionLifecycleTest','LaunchTest','ArchiveParseTest']
sources=[project/'app/src/test/java/com/simon/offlinebms'/(name+'.java') for name in tests]
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--release','11','-cp',cp,'-d',str(classes)]+[str(s) for s in sources],check=True)
ns='http://schemas.android.com/apk/res/android';ET.register_namespace('android',ns)
tree=ET.parse(a.compiled_classes.parent/'AndroidManifest.xml')
sdk=ET.SubElement(tree.getroot(),'uses-sdk');sdk.set('{'+ns+'}minSdkVersion','31');sdk.set('{'+ns+'}targetSdkVersion','35')
tree.write(work/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
(work/'assets').mkdir(exist_ok=True)
props=classes/'com/android/tools/test_config.properties';props.parent.mkdir(parents=True,exist_ok=True)
props.write_text('android_merged_manifest='+str(work/'AndroidManifest.xml')+'\nandroid_merged_resources='+str(project/'app/src/main/res')+'\nandroid_merged_assets='+str(work/'assets')+'\nandroid_resource_apk='+str(a.apk.resolve())+'\nandroid_custom_package=com.simon.offlinebms\n')
cp=os.pathsep.join([str(classes),str(a.compiled_classes.resolve())]+jars)
cmd=['java','-Xmx1536m']
for module in ['java.base/java.lang','java.base/java.util','java.base/java.io','java.base/java.net','java.base/java.security','java.base/java.text','java.base/jdk.internal.access','java.desktop/java.awt.font','jdk.compiler/com.sun.tools.javac.api']:
    cmd.append('--add-opens='+module+'=ALL-UNNAMED')
proxy=urllib.parse.urlparse(os.environ.get('HTTPS_PROXY') or os.environ.get('HTTP_PROXY',''))
if proxy.hostname:cmd+=['-Dhttps.proxyHost='+proxy.hostname,'-Dhttps.proxyPort='+str(proxy.port or 80),'-Dhttp.proxyHost='+proxy.hostname,'-Dhttp.proxyPort='+str(proxy.port or 80)]
cmd+=['-Drobolectric.dependency.repo.url=https://repo.maven.apache.org/maven2','-Dbms.previewDir='+str(work/'previews'),'-Dbms.fixtureDir='+str(project/'tests/fixtures'),'-Dbms.currentApk='+str(a.apk.resolve()),'-Dbms.previousApk='+str(a.previous_apk.resolve()),'-cp',cp,'org.junit.runner.JUnitCore']+['com.simon.offlinebms.'+t for t in tests]
log=work/'runtime-tests.log'
with log.open('w') as out:result=subprocess.run(cmd,stdout=out,stderr=subprocess.STDOUT)
print(log.read_text())
print('Android test process exit:',result.returncode)
raise SystemExit(result.returncode)
