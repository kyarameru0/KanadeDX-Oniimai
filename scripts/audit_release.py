"""Bounded source/APK distribution checks; not legal clearance or a security audit."""
import argparse
import json
from pathlib import Path
import re
import sys
import urllib.parse
import zipfile

root = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--apk', type=Path)
a = p.parse_args()
ignored = {'.git', '.gradle', '.kotlin', '.cxx', 'build', 'work', '__pycache__'}
forbidden = {'.apk', '.aab', '.aar', '.jar', '.so', '.dll', '.exe', '.bin', '.keystore', '.jks', '.bks', '.mct', '.pcap', '.dmp', '.ttf', '.otf'}
files = [f for f in root.rglob('*') if f.is_file() and not ignored.intersection(f.relative_to(root).parts)]
errors = []
for f in files:
    relative = f.relative_to(root).as_posix()
    if f.suffix.lower() in forbidden or f.name in {'local.properties', 'global-metadata.dat'}:
        errors.append('Forbidden source-distribution file: ' + relative)
    if f.name.startswith('.env'):
        errors.append('Environment file: ' + relative)
    try:
        text = f.read_text(encoding='utf-8')
    except UnicodeError:
        continue
    patterns = [r'gh[pousr]_[A-Za-z0-9]{30,}', r'github_pat_[A-Za-z0-9_]{35,}',
                r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----']
    if any(re.search(pattern, text) for pattern in patterns):
        errors.append('Credential-like material (value withheld): ' + relative)
    if f.suffix == '.md':
        if re.search(r'[\uac00-\ud7af]', text):
            errors.append('Non-English documentation: ' + relative)
        for link in re.findall(r'\]\(([^\s)]+)(?:\s+"[^"]*")?\)', text):
            if ':' in link or link.startswith('#'):
                continue
            target = urllib.parse.unquote(link.split('#')[0])
            if target and not (f.parent / target).exists():
                errors.append('Broken local link in ' + relative + ': ' + target)

for header_path in (root / 'app/src/main/cpp').glob('target_*.h'):
    if re.search(r'SIG_\w+\[\]\s*=\s*\{', header_path.read_text()):
        errors.append('Raw target instruction signatures remain: ' + header_path.name)
inventory = json.loads((root / 'dependency-inventory.json').read_text())
if not all(row.get('licenses') and row.get('source_sha256') for row in inventory):
    errors.append('Incomplete dependency license/source inventory')

if a.apk:
    with zipfile.ZipFile(a.apk) as z:
        names = z.namelist()
        banned = ('libunity.so', 'libil2cpp.so', 'global-metadata.dat', 'assets/npatch/', 'assets/lspatch/')
        if any(any(part in name for part in banned) for name in names):
            errors.append('Game/patch-loader content found in APK')
        for name in ['GPL-3.0-only.txt', 'PN532-Aime-Reader-MPL-2.0.txt',
                     'Miuix-Apache-2.0.txt', 'Mai2Touch-MIT.txt', 'NDK-NOTICE.txt',
                     'DEPENDENCY_NOTICES.txt', 'PROJECT-NOTICE.txt']:
            if 'META-INF/licenses/' + name not in names:
                errors.append('Missing packaged notice: ' + name)
        expected_notices = {f.name: f for f in (root / 'licenses').glob('*.txt')}
        expected_notices['PROJECT-NOTICE.txt'] = root / 'THIRD_PARTY_NOTICES.md'
        for name, source in expected_notices.items():
            entry = 'META-INF/licenses/' + name
            if entry not in names:
                errors.append('Missing current notice: ' + name)
            elif z.read(entry).decode('utf-8').replace('\r\n', '\n') != source.read_text(encoding='utf-8'):
                errors.append('Packaged notice does not match current source: ' + name)
        if z.testzip() is not None:
            errors.append('APK ZIP integrity error')

for error in errors:
    print('FAIL: ' + error)
if errors:
    sys.exit(1)
print(f'PASS: {len(files)} source files, local documentation links, credential patterns and distribution exclusions')
if a.apk:
    print('PASS: module-only APK markers and required packaged notices')
print('Scope limitation: pattern checks do not prove legal compliance, secret absence or secure behavior.')
