#!/usr/bin/env python3
"""Stage the Android Debloat List browser build without local audit overrides."""
import argparse
import pathlib
import shutil

source = pathlib.Path(__file__).resolve().parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--destination', type=pathlib.Path, required=True)
args = parser.parse_args()
inputs = {name: source / name for name in ('Dockerfile', 'uad_lists.php', 'browser_suggestions.php',
                                          'nginx.conf', 'compose.yaml', 'README.md')}
for path in inputs.values():
    if not path.is_file():
        raise SystemExit('Missing source input: ' + str(path))
args.destination.mkdir(parents=True, exist_ok=True)
for name, path in inputs.items():
    shutil.copy2(path, args.destination / name)
print('Prepared Docker context:', args.destination.resolve())
