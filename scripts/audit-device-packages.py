#!/usr/bin/env python3
"""Read-only Android package inventory and APK evidence. Never changes phone settings."""
import argparse
import concurrent.futures
import datetime
import hashlib
import json
import os
import pathlib
import re
import shlex
import struct
import subprocess
import tempfile
import zipfile


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + '.tmp')
    temporary.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n')
    temporary.replace(path)


def dex_classes(data, dexdump=None):
    if len(data) < 112 or data[:4] != b'dex\n' or data[7] != 0:
        raise ValueError('Not a standard DEX file')
    if data[4:7] == b'041':
        # Android's authoritative tool handles all logical DEX headers and shared
        # data in the 041 container format. Do not treat the first header as a file.
        executable = dexdump or pathlib.Path(os.environ.get('ANDROID_HOME', str(pathlib.Path.home() / 'Android/Sdk'))) / 'build-tools/37.0.0/dexdump'
        with tempfile.TemporaryDirectory(prefix='cana-dex41-', dir='/tmp') as temporary:
            dex_path = pathlib.Path(temporary) / 'classes.dex'
            dex_path.write_bytes(data)
            result = subprocess.run([str(executable), str(dex_path)], capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=120)
            if result.returncode:
                raise ValueError('Android dexdump rejected DEX 041: ' + result.stderr.strip())
            descriptors = re.findall(r"Class descriptor\s*:\s*'([^']+)'", result.stdout)
            return [x[1:-1].replace('/', '.') for x in descriptors if x.startswith('L') and x.endswith(';')]
    if data[4:7] not in (b'035', b'037', b'038', b'039', b'040'):
        raise ValueError('Unsupported DEX version: ' + repr(data[4:7]))
    if struct.unpack_from('<I', data, 40)[0] != 0x12345678:
        raise ValueError('Unsupported DEX endianness')
    def integer(off):
        return struct.unpack_from('<I', data, off)[0]
    strings_n, strings_off = struct.unpack_from('<II', data, 56)
    types_n, types_off = struct.unpack_from('<II', data, 64)
    classes_n, classes_off = struct.unpack_from('<II', data, 96)
    for count, offset, width in ((strings_n, strings_off, 4), (types_n, types_off, 4), (classes_n, classes_off, 32)):
        if offset + count * width > len(data):
            raise ValueError('Truncated DEX table')
    names = []
    for index in range(classes_n):
        type_index = integer(classes_off + index * 32)
        if type_index >= types_n:
            raise ValueError('DEX type index out of range')
        string_index = integer(types_off + type_index * 4)
        if string_index >= strings_n:
            raise ValueError('DEX string index out of range')
        offset = integer(strings_off + string_index * 4)
        for _ in range(5):
            if offset >= len(data):
                raise ValueError('DEX string offset out of range')
            byte = data[offset]
            offset += 1
            if not byte & 128:
                break
        else:
            raise ValueError('Invalid DEX string length')
        end = data.find(b'\x00', offset)
        if end < 0:
            raise ValueError('Unterminated DEX string')
        descriptor = data[offset:end].decode('utf-8', errors='replace')
        if not descriptor.startswith('L') or not descriptor.endswith(';'):
            raise ValueError('Invalid class descriptor')
        names.append(descriptor[1:-1].replace('/', '.'))
    return names


def inspect_apk(path, tools_dir, signatures):
    result = {'sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'bytes': path.stat().st_size}
    commands = {
        'badging': [str(tools_dir / 'aapt2'), 'dump', 'badging', str(path)],
        'manifest': [str(tools_dir / 'aapt2'), 'dump', 'xmltree', str(path), '--file', 'AndroidManifest.xml'],
    }
    for key, command in commands.items():
        process = subprocess.run(command, capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=90)
        result[key] = process.stdout
        if process.returncode:
            result[key + '_error'] = process.stderr
    if re.search(r'E: overlay\b', result.get('manifest', '')):
        process = subprocess.run([str(tools_dir / 'aapt2'), 'dump', 'resources', str(path)], capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=90)
        result['resources'] = process.stdout
        if process.returncode:
            result['resources_error'] = process.stderr
    classes = set()
    errors = []
    with zipfile.ZipFile(path) as apk:
        dex_files = [x for x in apk.infolist() if re.fullmatch(r'classes(?:\d+)?\.dex', x.filename)]
        result['dex_files'] = [x.filename for x in dex_files]
        result['native_libraries'] = [x.filename for x in apk.infolist() if x.filename.startswith('lib/') and x.filename.endswith('.so')]
        for entry in dex_files:
            if entry.file_size > 512 * 1024 * 1024:
                errors.append(entry.filename + ': exceeds audit memory limit')
                continue
            try:
                classes.update(dex_classes(apk.read(entry), tools_dir / 'dexdump'))
            except Exception as exc:
                errors.append(entry.filename + ': ' + str(exc))
    result['class_count'] = len(classes)
    result['tracker_matches'] = {}
    buckets, unbucketed, infix, found = {}, [], [], {}
    for tracker in signatures:
        for part in tracker.get('code_signature', '').split('|'):
            prefix = part.strip().replace('/', '.')
            if not prefix:
                continue
            candidate = (prefix, tracker['name'], tracker['code_signature'])
            if prefix.startswith('.'):
                infix.append(candidate)
            elif prefix.count('.') >= 2 and prefix.split('.')[0] and prefix.split('.')[1]:
                key = '.'.join(prefix.split('.')[:2])
                buckets.setdefault(key, []).append(candidate)
            else:
                unbucketed.append(candidate)
    for name in classes:
        key = '.'.join(name.split('.')[:2]) if name.count('.') >= 2 else None
        for prefix, tracker_name, signature in buckets.get(key, []) + unbucketed + infix:
            if (prefix in name if prefix.startswith('.') else name.startswith(prefix)):
                value = found.setdefault(tracker_name, {'classes': set(), 'signature': signature})
                value['classes'].add(name)
    for tracker_name, value in sorted(found.items()):
        matches = sorted(value['classes'])
        result['tracker_matches'][tracker_name] = {'classes': matches[:8], 'class_count': len(matches), 'signature': value['signature']}
    result['dex_complete'] = not errors
    result['dex_errors'] = errors
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--serial', required=True)
    parser.add_argument('--out', type=pathlib.Path, required=True)
    parser.add_argument('--tools', type=pathlib.Path, default=pathlib.Path(os.environ.get('ANDROID_HOME', str(pathlib.Path.home() / 'Android/Sdk'))) / 'build-tools/37.0.0')
    parser.add_argument('--trackers', type=pathlib.Path, default=pathlib.Path(__file__).resolve().parents[1] / 'app/src/main/assets/exodus-trackers.json')
    parser.add_argument('--workers', type=int, default=2)
    parser.add_argument('--inventory-only', action='store_true')
    parser.add_argument('--retry-errors', action='store_true')
    parser.add_argument('--retry-resources', action='store_true')
    parser.add_argument('--existing-only', action='store_true')
    parser.add_argument('--retry-incomplete', action='store_true')
    parser.add_argument('--retry-factory', action='store_true', help='Reinspect updated system packages with factory variants')
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    raw = args.out / 'raw'
    raw.mkdir(exist_ok=True)
    def adb(*arguments, timeout=90, allow_not_found=False):
        process = subprocess.run(['adb', '-s', args.serial, *arguments], capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=timeout)
        if allow_not_found and process.returncode == 1 and not process.stdout.strip() and not process.stderr.strip():
            return ''
        if process.returncode or 'error: device' in process.stderr:
            raise RuntimeError(process.stderr.strip() or process.stdout.strip())
        return process.stdout
    def capture(name, command):
        text = adb('shell', command)
        (raw / (name + '.txt')).write_text(text)
        return text
    keys = ('ro.product.manufacturer', 'ro.product.model', 'ro.product.device', 'ro.build.version.release', 'ro.build.version.sdk', 'ro.build.version.security_patch', 'ro.build.fingerprint')
    device = {key: adb('shell', 'getprop ' + key).strip() for key in keys}
    if device['ro.product.manufacturer'].lower() != 'motorola':
        raise RuntimeError('Refusing to audit an unexpected manufacturer: ' + repr(device))
    users_text = capture('users', 'pm list users')
    users = sorted(set(int(x) for x in re.findall(r'UserInfo\{(\d+):', users_text)))
    if not users:
        raise RuntimeError('No users returned')
    inventory = {'captured_at': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'device': device, 'users': users, 'packages': {}}
    def package_ids(text):
        return set(re.findall(r'^package:([^\s=]+)$', text, re.MULTILINE))
    for user in users:
        sets = {}
        for name, flags in (('known', '-u'), ('installed', ''), ('system', '-s -u'), ('third_party', '-3 -u'), ('disabled', '-d'), ('enabled', '-e')):
            text = capture(f'user-{user}-{name}', f'pm list packages {flags} --match-libraries --user {user}')
            if 'Exception' in text or 'Error:' in text:
                raise RuntimeError(f'Cannot enumerate user {user}: {text}')
            sets[name] = package_ids(text)
        for package in sorted(set.union(*sets.values())):
            entry = inventory['packages'].setdefault(package, {'profiles': {}, 'apex': False})
            entry['profiles'][str(user)] = {name: package in values for name, values in sets.items()}
    apex_text = capture('apex-packages', 'pm list packages --apex-only -u')
    for package in package_ids(apex_text):
        inventory['packages'].setdefault(package, {'profiles': {}})['apex'] = True
    for name, command in (('overlays', 'cmd overlay list --user 0'), ('shared-libraries', 'pm list libraries'), ('features', 'pm list features')):
        capture(name, command)
    write_json(args.out / 'inventory.json', inventory)
    print('Inventory:', len(inventory['packages']), 'packages; users', users, flush=True)
    if args.inventory_only:
        return
    library_dump = capture('library-paths', 'dumpsys package libraries')
    library_aliases = {}
    for logical, internal in re.findall(r'^\s*(\S+) version=\d+ ->\s+\(apk\) (\S+)', library_dump, re.MULTILINE):
        library_aliases.setdefault(logical, []).append(internal)
    library_paths = {}
    for user in users:
        paths_text = capture(f'user-{user}-library-paths', f'pm list packages -f -u --match-libraries --user {user}')
        for path, package in re.findall(r'^package:(/[^\n]+\.apk)=([^\s]+)$', paths_text, re.MULTILINE):
            library_paths.setdefault(package, set()).add(path)
    signatures = list(json.loads(args.trackers.read_text())['trackers'].values())
    def investigate(item):
        package, state = item
        output = args.out / 'packages' / (package + '.json')
        if args.existing_only and not output.exists():
            return package, 'pending initial inspection'
        if output.exists():
            previous = json.loads(output.read_text())
            needs_retry = args.retry_errors and bool(previous.get('errors'))
            needs_retry |= args.retry_incomplete and not previous.get('apex') and not previous.get('apk_scan_complete')
            needs_retry |= args.retry_factory and 'UPDATED_SYSTEM_APP' in previous.get('package_dump', '')
            needs_retry |= args.retry_resources and any(re.search(r'E: overlay\b', apk.get('manifest', '')) and 'resources' not in apk for apk in previous.get('apks', []))
            if not needs_retry:
                return package, 'cached'
        record = {'package': package, 'profiles': state['profiles'], 'apex': state['apex'], 'apks': [], 'errors': []}
        try:
            dump = '\n'.join(adb('shell', 'dumpsys package ' + shlex.quote(alias)) for alias in library_aliases.get(package, [package]))
            record['package_dump'] = dump
            path_sets = []
            for user in users:
                paths_text = adb('shell', f'pm path --user {user} ' + shlex.quote(package), allow_not_found=True)
                path_sets.extend(re.findall(r'^package:(.+)$', paths_text, re.MULTILINE))
            paths = sorted(set(x.strip() for x in path_sets))
            if not paths:
                paths = sorted(library_paths.get(package, set()))
            record['active_apk_paths'] = paths.copy()
            # Updated system packages expose a second, older factory codePath.
            # Read all exposed variants even when pm path already returns an update.
            roots = re.findall(r'^\s*codePath=(.+)$', dump, re.MULTILINE)
            for root in sorted(set(x.strip() for x in roots)):
                if root.startswith(('/system/', '/system_ext/', '/product/', '/vendor/', '/odm/', '/data/app/', '/apex/')):
                    if root.endswith('.apk'):
                        paths.append(root)
                    else:
                        # Package codePath contains APKs directly. Avoid descending into
                        # unrelated protected native-library directories under /data/app.
                        files = adb('shell', 'find ' + shlex.quote(root) + " -maxdepth 1 -name '*.apk' -type f 2>/dev/null", allow_not_found=True)
                        paths.extend(x.strip() for x in files.splitlines() if x.startswith(root + '/') and x.endswith('.apk'))
            if not paths:
                paths.extend(re.findall(r'^\s*path:\s*(/\S+\.apk)\s*$', dump, re.MULTILINE))
            record['apk_paths'] = sorted(set(paths))
            if state['apex']:
                record['inspection_status'] = 'APEX module; Java/native contents not covered by APK-only tracker scan'
            for remote in record['apk_paths']:
                if not remote.endswith('.apk'):
                    continue
                with tempfile.TemporaryDirectory(prefix='cana-audit-apk-', dir='/tmp') as temp:
                    local = pathlib.Path(temp) / 'app.apk'
                    pull = subprocess.run(['adb', '-s', args.serial, 'pull', remote, str(local)], capture_output=True, text=True, timeout=300)
                    read_method = 'adb pull'
                    if pull.returncode:
                        # Android applies different SELinux permissions to adbd's sync service
                        # and the ordinary shell. This uses the already-authorized shell UID.
                        with local.open('wb') as destination:
                            fallback = subprocess.run(['adb', '-s', args.serial, 'exec-out', 'cat ' + shlex.quote(remote)], stdout=destination, stderr=subprocess.PIPE, timeout=300)
                        if fallback.returncode or not zipfile.is_zipfile(local):
                            record['errors'].append(remote + ': ' + (fallback.stderr.decode(errors='replace').strip() or pull.stderr.strip() or pull.stdout.strip() or f'adb pull exited {pull.returncode}'))
                            continue
                        read_method = 'adb exec-out cat (ordinary shell; sync service could not read path)'
                    facts = inspect_apk(local, args.tools, signatures)
                    facts['device_path'] = remote
                    facts['read_method'] = read_method
                    record['apks'].append(facts)
            record['apk_scan_complete'] = bool(record['apks']) and len(record['apks']) == len([p for p in record['apk_paths'] if p.endswith('.apk')]) and all(x['dex_complete'] for x in record['apks']) and not record['errors']
            record['tracker_names'] = sorted({name for apk in record['apks'] for name in apk['tracker_matches']})
            record['class_count'] = sum(apk['class_count'] for apk in record['apks'])
        except Exception as exc:
            record['errors'].append(str(exc))
            record['apk_scan_complete'] = False
        write_json(output, record)
        return package, f"{len(record['apks'])} APKs, {len(record.get('tracker_names', []))} matches, {len(record['errors'])} errors"
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        for index, result in enumerate(pool.map(investigate, sorted(inventory['packages'].items())), 1):
            print(f'{index}/{len(inventory["packages"])} {result[0]}: {result[1]}', flush=True)


if __name__ == '__main__':
    main()
