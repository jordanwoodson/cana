#!/usr/bin/env python3
"""Stage the Android Debloat List browser build, optionally with device-reviewed overlays.

The default list stays pure upstream. Each --device-list NAME=PATH stages a package-keyed review
(for example docs/package-audit/2026-10-01/motorola_packages.json) as overlays/NAME.json, which the
Dockerfile merges over upstream and serves at /devices/NAME/uad_lists.json.
"""
import argparse
import json
import pathlib
import re
import shutil

source = pathlib.Path(__file__).resolve().parent
parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
parser.add_argument('--destination', type=pathlib.Path, required=True)
parser.add_argument('--device-list', action='append', default=[], metavar='NAME=PATH',
                    help='stage a package-keyed review as overlays/NAME.json (repeatable)')
args = parser.parse_args()
inputs = {name: source / name for name in ('Dockerfile', 'uad_lists.php', 'browser_suggestions.php',
                                          'nginx.conf', 'compose.yaml', 'README.md')}
for path in inputs.values():
    if not path.is_file():
        raise SystemExit('Missing source input: ' + str(path))
overlays = {}
for spec in args.device_list:
    name, _, path = spec.partition('=')
    if not re.fullmatch(r'[a-z0-9][a-z0-9-]*', name) or not path:
        raise SystemExit('--device-list needs NAME=PATH with a lowercase slug: ' + spec)
    path = pathlib.Path(path)
    data = json.loads(path.read_text())
    if not isinstance(data, dict) or not data or not all(isinstance(v, dict) for v in data.values()):
        raise SystemExit('Not a package-keyed review: ' + str(path))
    if name in overlays:
        raise SystemExit('Duplicate device list: ' + name)
    overlays[name] = path
args.destination.mkdir(parents=True, exist_ok=True)
for name, path in inputs.items():
    shutil.copy2(path, args.destination / name)
staged = args.destination / 'overlays'
shutil.rmtree(staged, ignore_errors=True)
staged.mkdir()
(staged / '.keep').write_text('Device-reviewed overlays staged by prepare-context.py --device-list NAME=PATH\n')
for name, path in overlays.items():
    shutil.copy2(path, staged / (name + '.json'))
print('Prepared Docker context:', args.destination.resolve())
for name, path in overlays.items():
    print(f'  device list {name}: {path} -> /devices/{name}/uad_lists.json')
