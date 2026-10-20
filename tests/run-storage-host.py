#!/usr/bin/env python3
"""Real app data code, real SQLite; framework adapters do not emulate Android UI/service lifecycle."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,tempfile,urllib.request
parser=argparse.ArgumentParser()
parser.add_argument('--cache',type=Path,default=Path(tempfile.gettempdir())/'offline-bms-host-test-libs')
args=parser.parse_args();project=Path(__file__).resolve().parent.parent
args.cache.mkdir(parents=True,exist_ok=True)
for item in json.loads((project/'tests/host-libs.json').read_text()):
    path=args.cache/item['name']
    if not path.exists():urllib.request.urlretrieve(item['url'],path)
    if hashlib.sha256(path.read_bytes()).hexdigest()!=item['sha256']:raise SystemExit('Test dependency checksum mismatch: '+item['name'])
core=['JbdProtocol','JkProtocol','VictronProtocol','Reading','ProfileStore','HistoryStore','OfflineReport','EnergyStats','AlertEngine','EquipmentReading']
sources=[project/'app/src/main/java/com/simon/offlinebms'/f'{name}.java' for name in core]+list((project/'tests/host').rglob('*.java'))+[project/'tests/StorageHostTests.java']
classpath=os.pathsep.join(str(p.resolve()) for p in sorted(args.cache.glob('*.jar')))
java=str(Path(os.environ['JAVA_HOME'])/'bin/java') if os.environ.get('JAVA_HOME') else 'java'
with tempfile.TemporaryDirectory(prefix='offline-bms-host-classes-') as directory:
    subprocess.run([java,'-m','jdk.compiler/com.sun.tools.javac.Main','--release','11','-cp',classpath,'-d',directory]+[str(p) for p in sources],check=True)
    subprocess.run([java,'-cp',directory+os.pathsep+classpath,'com.simon.offlinebms.StorageHostTests'],check=True)
