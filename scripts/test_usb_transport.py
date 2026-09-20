"""Fault-injection tests of production UsbIo, isolated from protocol-only USB fakes."""
import argparse, shutil, subprocess
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('--build-dir', required=True)
p.add_argument('--java-home')
a = p.parse_args()
root = Path(__file__).resolve().parents[1]
out = Path(a.build_dir).resolve()
out.mkdir(parents=True, exist_ok=True)
generated = out / 'usb-transport-fixtures'
generated.mkdir(exist_ok=True)
sources = []
for fixture in sorted((root / 'tests/usb-transport').glob('*.fixture')):
    source = generated / fixture.name.removesuffix('.fixture')
    shutil.copyfile(fixture, source)
    sources.append(source)
production = root / 'app/src/main/java/io/oniimai/kanade'
sources += [production / (name + '.java') for name in ('UsbIo', 'Protocol', 'PortSelection', 'Io4Output')]
def jdk(name):
    return str(Path(a.java_home) / 'bin' / (name + '.exe')) if a.java_home else name
subprocess.run([jdk('javac'), '--release', '8', '-encoding', 'UTF-8', '-d', str(out), *map(str, sources)], check=True)
subprocess.run([jdk('java'), '-cp', str(out), 'io.oniimai.kanade.UsbTransportTest'], check=True)
