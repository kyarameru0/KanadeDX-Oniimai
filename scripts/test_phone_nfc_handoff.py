"""Run the production PhoneNfcReader callback with isolated Android/Binder fakes."""
import argparse, os, shutil, subprocess
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('--build-dir', required=True)
p.add_argument('--java-home')
p.add_argument('--reader-source', help='Optional old revision for regression comparison')
a = p.parse_args()
root = Path(__file__).resolve().parents[1]
out = Path(a.build_dir).resolve()
generated = out / 'phone-nfc-fixtures'
generated.mkdir(parents=True, exist_ok=True)
sources = []
for fixture in sorted((root / 'tests/phone-nfc-handoff').glob('*.fixture')):
    source = generated / fixture.name.removesuffix('.fixture')
    shutil.copyfile(fixture, source)
    sources.append(source)
production = root / 'app/src/main/java/io/oniimai/kanade'
sources += [Path(a.reader_source) if a.reader_source else production / 'PhoneNfcReader.java', production / 'PhoneScan.java']

def jdk(name):
    return str(Path(a.java_home) / 'bin' / (name + ('.exe' if os.name == 'nt' else ''))) if a.java_home else name

subprocess.run([jdk('javac'), '--release', '8', '-encoding', 'UTF-8', '-d', str(out), *map(str, sources)], check=True)
subprocess.run([jdk('java'), '-cp', str(out), 'io.oniimai.kanade.PhoneNfcHandoffTest'], check=True)
