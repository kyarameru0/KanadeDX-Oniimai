"""Download pinned upstream source artifacts without installing or executing them."""
import argparse
import concurrent.futures
import hashlib
import json
from pathlib import Path
import urllib.request

root = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--output', required=True, type=Path)
a = p.parse_args()
a.output.mkdir(parents=True, exist_ok=True)
items = {}
for row in json.loads((root / 'dependency-inventory.json').read_text()):
    name = f"{row['group']}--{row['name']}--{row['version']}-sources.jar"
    items[name] = (row['source_url'], row['source_sha256'])
for row in json.loads((root / 'source-extras.json').read_text()):
    items[row['name']] = (row['url'], row['sha256'])

def fetch(item):
    name, (url, expected) = item
    if Path(name).name != name or not url.startswith('https://'):
        raise ValueError('Invalid source entry')
    dest = a.output / name
    data = dest.read_bytes() if dest.exists() else urllib.request.urlopen(url, timeout=60).read()
    if hashlib.sha256(data).hexdigest() != expected:
        raise ValueError('Source checksum mismatch: ' + name)
    if not dest.exists():
        dest.write_bytes(data)
    return name

with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    names = list(pool.map(fetch, items.items()))
print(f'PASS: {len(names)} pinned source archives verified; none extracted or executed')
