#!/usr/bin/env python3
"""Stage the versioned server source and sanitized audit payloads for Docker."""
import argparse
import pathlib
import shutil

source = pathlib.Path(__file__).resolve().parent
repo = source.parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--destination', type=pathlib.Path, required=True)
parser.add_argument('--audit', type=pathlib.Path, default=repo / 'docs/package-audit/2026-10-01')
args = parser.parse_args()
inputs = {name: source / name for name in ('Dockerfile', 'uad_lists.php', 'apply_browser_lists.php',
                                          'nginx.conf', 'compose.yaml', 'user_apps.json', 'README.md')}
inputs.update({name: args.audit / name for name in ('motorola_packages.json', 'motorola_review.json', 'tracker_findings.json')})
inputs['exodus-trackers.json'] = repo / 'app/src/main/assets/exodus-trackers.json'
for path in inputs.values():
    if not path.is_file():
        raise SystemExit('Missing source input: ' + str(path))
args.destination.mkdir(parents=True, exist_ok=True)
for name, path in inputs.items():
    shutil.copy2(path, args.destination / name)
print('Prepared Docker context:', args.destination.resolve())
