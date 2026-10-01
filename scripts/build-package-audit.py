#!/usr/bin/env python3
"""Publish reviewed package facts without publishing raw phone state or identifiers.

Research is deliberately a separate human-reviewed input. This script never guesses
package purpose or safe removal from package names, permissions or tracker matches.
"""
import argparse
import collections
import copy
import hashlib
import json
import pathlib
import re


def read(path):
    return json.loads(path.read_text())


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')


def overlay_text(original, packages):
    """Keep unrelated database records byte-for-byte instead of reformatting them."""
    decoder = json.JSONDecoder()
    edits, known = [], set()
    position = original.index('{') + 1
    while True:
        while original[position].isspace():
            position += 1
        if original[position] == '}':
            break
        key, position = decoder.raw_decode(original, position)
        known.add(key)
        while original[position].isspace():
            position += 1
        assert original[position] == ':'
        position += 1
        while original[position].isspace():
            position += 1
        start = position
        _, position = decoder.raw_decode(original, position)
        if key in packages:
            encoded = json.dumps(packages[key], ensure_ascii=False, indent=2).replace('\n', '\n  ')
            edits.append((start, position, encoded))
        while original[position].isspace():
            position += 1
        if original[position] == ',':
            position += 1
        else:
            assert original[position] == '}'
            break
    new = {key: value for key, value in packages.items() if key not in known}
    if new:
        encoded = json.dumps(new, ensure_ascii=False, indent=2)[1:-1].strip('\n')
        edits.append((position, position, (',' if known else '') + '\n' + encoded + '\n'))
    result = original
    for start, end, replacement in reversed(edits):
        result = result[:start] + replacement + result[end:]
    assert json.loads(result) == {**json.loads(original), **packages}
    return result


def manifest_nodes(text):
    """Parse only aapt2's structural XML dump; never copy arbitrary raw metadata."""
    nodes, stack = [], []
    for line in text.splitlines():
        element = re.match(r'^(\s*)E: ([\w-]+)\b', line)
        if element:
            indent, tag = len(element[1]), element[2]
            while stack and stack[-1]['indent'] >= indent:
                stack.pop()
            node = {'tag': tag, 'attrs': {}, 'indent': indent,
                    'parent': stack[-1]['tag'] if stack else None}
            nodes.append(node)
            stack.append(node)
        elif stack:
            attribute = re.match(r'\s*A: (?:http://schemas.android.com/apk/res/android:)?([\w-]+)(?:\([^)]*\))?=(.*)', line)
            if attribute:
                value = attribute[2]
                string = re.match(r'"([^"]*)"', value)
                stack[-1]['attrs'][attribute[1]] = string[1] if string else value.split(' (Raw:')[0]
    return nodes


def manifest_evidence(apks):
    nodes = [node for apk in apks for node in manifest_nodes(apk.get('manifest', ''))]
    def selected(tags, attributes):
        records = []
        for node in nodes:
            if node['tag'] in tags:
                record = {'type': node['tag'], **{k: v for k, v in node['attrs'].items() if k in attributes}}
                if record not in records:
                    records.append(record)
        return records
    queries = sorted({node['attrs']['name'] for node in nodes
                      if node['tag'] == 'package' and node['parent'] == 'queries' and 'name' in node['attrs']})
    targets = sorted({node['attrs']['targetPackage'] for node in nodes
                      if node['tag'] == 'overlay' and 'targetPackage' in node['attrs']})
    return {
        'components': selected({'activity', 'activity-alias', 'service', 'receiver', 'provider'},
                               {'name', 'targetActivity', 'exported', 'enabled', 'permission', 'authorities', 'process'}),
        'requested_permissions': selected({'uses-permission', 'uses-permission-sdk-23'}, {'name', 'maxSdkVersion'}),
        'defined_permissions': selected({'permission'}, {'name', 'protectionLevel'}),
        'declared_libraries': selected({'library', 'static-library', 'sdk-library', 'uses-library',
                                        'uses-static-library', 'uses-sdk-library', 'uses-native-library'},
                                       {'name', 'version', 'versionMajor', 'required', 'certDigest'}),
        'queried_package_names': queries,
        'overlay_targets': targets,
        'shared_user_ids': sorted({node['attrs']['sharedUserId'] for node in nodes
                                   if node['tag'] == 'manifest' and 'sharedUserId' in node['attrs']}),
        'interpretation': 'Overlay targets and package-visibility queries are associations, not hard dependencies. '
                          'Permission declarations and component names describe capabilities, not observed execution or collection.',
    }


def sanitize_sources(value, package):
    if isinstance(value, dict):
        result = {key: sanitize_sources(item, package) for key, item in value.items()}
        if result.get('kind') == 'device':
            result['url'] = 'device:' + package
        if isinstance(result.get('source'), str) and result['source'].startswith('../packages/'):
            result['source'] = 'device:' + package
        return result
    if isinstance(value, list):
        return [sanitize_sources(item, package) for item in value]
    return value


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence', type=pathlib.Path, required=True)
    parser.add_argument('--output', type=pathlib.Path, required=True)
    parser.add_argument('--bundled-packages', type=pathlib.Path, required=True)
    parser.add_argument('--baseline-packages', type=pathlib.Path, required=True,
                        help='Unmodified bundled snapshot, preserved privately before first publication')
    parser.add_argument('--tracker-catalog', type=pathlib.Path, required=True)
    parser.add_argument('--verdicts', type=pathlib.Path,
                        help="Cana's keep-or-uninstall overlay, published as each entry's `verdict`")
    args = parser.parse_args()
    inventory = read(args.evidence / 'inventory.json')
    research = {}
    for name in ('platform', 'oem', 'user', 'modules-overlays'):
        batch = read(args.evidence / 'research' / (name + '.json'))
        assert not set(batch) & set(research), 'Duplicate research ownership'
        research.update(batch)
    assert set(research) == set(inventory['packages']), 'Research/inventory coverage differs'
    verdicts = read(args.verdicts) if args.verdicts else {}
    assert set(verdicts) <= set(research), 'Verdict for a package outside the audit'
    catalog = read(args.tracker_catalog)
    tracker_ids = {tracker['name']: tracker['id'] for tracker in catalog['trackers'].values()}
    packages, reviews, findings, containers = {}, {}, {}, {}
    for package, record in sorted(research.items()):
        entry = copy.deepcopy(record['entry'])
        review = sanitize_sources(record['review'], package)
        assert review.get('reviewed') is True
        assert review['confidence'] in ('high', 'medium', 'low')
        assert entry['list'] in ('Aosp', 'Google', 'Oem', 'Carrier', 'Misc')
        assert entry['removal'] in ('Recommended', 'Advanced', 'Expert', 'Unsafe')
        assert entry['description'].strip() and entry['source'].strip()
        assert review['sources'] and review['features'] and review['rationale']
        assert review['confidence'] != 'low' or entry['removal'] in ('Expert', 'Unsafe')
        facts = read(args.evidence / 'packages' / (package + '.json'))
        apks = facts['apks']
        assert not facts.get('errors'), package + ': evidence collection failed'
        if not facts['apex']:
            assert facts.get('apk_scan_complete'), package + ': APK evidence incomplete'
        scan_apks = []
        for apk in apks:
            badging = re.search(r"^package: name='([^']+)' versionCode='([^']*)' versionName='([^']*)'", apk.get('badging', ''), re.M)
            assert badging and badging[1] == package, package + ': wrong APK identity'
            scan_apks.append({
                'apk_name': pathlib.PurePosixPath(apk['device_path']).name,
                'variant_origin': 'data_partition' if apk['device_path'].startswith('/data/') else
                                  'apex_apk' if apk['device_path'].startswith('/apex/') else 'factory_partition',
                'version_code': badging[2], 'version_name': badging[3],
                'sha256': apk['sha256'], 'bytes': apk['bytes'],
                'dex_files': apk['dex_files'], 'declared_class_count': apk['class_count'],
                'dex_scan_complete': apk['dex_complete'],
                'native_libraries': apk['native_libraries'],
                'matches': [{'tracker_id': tracker_ids[name], 'name': name, **match}
                            for name, match in sorted(apk['tracker_matches'].items())],
            })
        # The scan's limits are documented once in tracker_findings.json; descriptions only name matches.
        names = facts.get('tracker_names', [])
        if facts['apex']:
            status = 'apex_not_apk_scanned'
        elif not any(apk['dex_files'] for apk in apks):
            status = 'no_dex_in_readable_apks'
        elif names:
            status = 'signature_matches'
            entry['description'] += '\nTrackers (static scan): ' + ', '.join(names) + '.'
        else:
            status = 'no_signature_matches'
        if names:
            entry['labels'] = list(dict.fromkeys(entry['labels'] + ['exodus-signature-match']))
        if review['confidence'] == 'low' and 'Unverified' not in entry['description']:
            entry['description'] += '\nUnverified: exact behavior is unconfirmed.'
        if package in verdicts:
            verdict = verdicts[package]
            assert verdict['action'] in ('Keep', 'Uninstall') and verdict['reason'].strip()
            assert set(verdict['benefits']) <= {'privacy', 'performance'}
            assert verdict['action'] == 'Keep' or entry['removal'] in ('Recommended', 'Advanced'), package + ': uninstall verdict on a risky package'
            entry['verdict'] = verdict
        packages[package] = entry
        labels = sorted({match for apk in apks for match in re.findall(r"^application-label:'(.*)'$", apk.get('badging', ''), re.M)})
        display_apks = [apk for apk in apks if apk['device_path'] in facts.get('active_apk_paths', [])] or apks
        display_labels = [match for apk in display_apks for match in re.findall(r"^application-label:'(.*)'$", apk.get('badging', ''), re.M)]
        reviews[package] = {**review, 'apk_labels': labels,
                            'display_label': display_labels[0] if display_labels else package,
                            'manifest_relationships': manifest_evidence(apks), 'tracker_status': status}
        findings[package] = {'status': status, 'tracker_names': names, 'apks': scan_apks}
        owners = sorted({name for name in re.findall(r'^\s*apexModuleName=(\S+)', facts.get('package_dump', ''), re.M)
                         if name in inventory['packages'] and inventory['packages'][name]['apex']})
        containers[package] = owners
    library_path = args.evidence / 'raw/library-paths.txt'
    library_providers = {}
    if library_path.exists():
        for library, owner in re.findall(r'^\s*(\S+) ->\s+\(jar\) /apex/([^/]+)/', library_path.read_text(), re.M):
            if owner in packages:
                library_providers[library] = owner
    uid_members = collections.defaultdict(set)
    for package, review in reviews.items():
        for uid in review['manifest_relationships']['shared_user_ids']:
            uid_members[uid].add(package)
    for package, review in reviews.items():
        relationships = review['manifest_relationships']
        relationships['apex_containers'] = containers[package]
        relationships['contained_apk_packages'] = sorted(key for key, owners in containers.items() if package in owners)
        relationships['registered_framework_libraries'] = sorted(library for library, owner in library_providers.items() if owner == package)
        relationships['framework_library_provider_associations'] = [
            {'library': library['name'], 'provider_apex': library_providers[library['name']],
             'required': library.get('required', 'true')}
            for library in relationships['declared_libraries']
            if library['type'] == 'uses-library' and library.get('name') in library_providers]
        relationships['shared_uid_peers'] = sorted(set().union(*(uid_members[uid] for uid in relationships['shared_user_ids'])) - {package}) if relationships['shared_user_ids'] else []
        if containers[package]:
            packages[package]['description'] += '\nPart of the ' + ', '.join(containers[package]) + ' module.'
        if relationships['contained_apk_packages']:
            packages[package]['description'] += '\nContains: ' + ', '.join(relationships['contained_apk_packages']) + '.'
    for package, entry in packages.items():
        for dependency in entry['dependencies']:
            assert dependency in packages, package + ': unresolved dependency ' + dependency
            assert package in packages[dependency]['neededBy'], package + ': dependency has no reciprocal relationship'
        for consumer in entry['neededBy']:
            assert package in packages[consumer]['dependencies'], package + ': consumer has no reciprocal dependency'
    baseline = read(args.baseline_packages)
    new_ids = sorted(set(packages) - set(baseline))
    before_count = len(baseline)
    baseline.update(packages)
    all_apks = [apk for finding in findings.values() for apk in finding['apks']]
    metadata = {
        'audit_date': '2026-10-01', 'model': inventory['device']['ro.product.model'],
        'android_release': inventory['device']['ro.build.version.release'],
        'sdk': inventory['device']['ro.build.version.sdk'],
        'security_patch': inventory['device']['ro.build.version.security_patch'],
        'package_count': len(packages),
        'apk_package_count': sum(not state['apex'] for state in inventory['packages'].values()),
        'apex_count': sum(state['apex'] for state in inventory['packages'].values()),
        'inspected_apk_count': len(all_apks),
        'declared_class_count_across_apks': sum(apk['declared_class_count'] for apk in all_apks),
        'tracker_match_package_count': sum(finding['status'] == 'signature_matches' for finding in findings.values()),
        'no_dex_package_count': sum(finding['status'] == 'no_dex_in_readable_apks' for finding in findings.values()),
        'removal_counts': dict(collections.Counter(entry['removal'] for entry in packages.values())),
        'confidence_counts': dict(collections.Counter(review['confidence'] for review in reviews.values())),
        'verdict_counts': dict(collections.Counter(verdict['action'] for verdict in verdicts.values())),
        'low_confidence_packages': [package for package, review in reviews.items() if review['confidence'] == 'low'],
        'bundled_package_count_before': before_count, 'bundled_package_count_after': len(baseline),
        'new_bundled_package_ids': new_ids,
        'scope': 'All package identities exposed to authorized Package Manager queries across accessible profiles, '
                 'including installed, disabled, retained-uninstalled, shared-library and APEX records. '
                 'Factory/updated/base/split APKs were inspected where exposed. '
                 'Profile/installation states, device endpoint, owner names and raw dumps are private and excluded.',
        'limitations': [
            'Individual descriptions combine primary sources with device manifests/resources; confidence and unresolved details are recorded for every package.',
            'No package removal or feature-loss experiment was performed. Recommendations describe feature costs and conservative keep decisions.',
            'Retained-uninstalled records are discoverable retained packages, not every app ever deleted or an inaccessible user profile.',
            'Static APK scans do not analyze native/compiled APEX behavior, downloaded code, runtime traffic, obfuscated unknown SDKs or actual collection.',
            'Class counts sum APK inspections; code duplicated between variants or splits is not deduplicated globally.',
            'Manifest queries, shared UIDs and overlay targets alone do not establish hard dependencies.',
            'Manifest relationship tables combine all inspected APK variants; factory/updated implementations can differ. Per-APK findings retain versions, origins and hashes.',
        ],
    }
    write(args.output / 'motorola_packages.json', packages)
    write(args.output / 'motorola_review.json', {'audit': metadata, 'packages': reviews})
    write(args.output / 'tracker_findings.json', {
        'audit_date': '2026-10-01', 'signature_source': 'https://reports.exodus-privacy.eu.org/api/trackers',
        'catalog_tracker_count': len(catalog['trackers']),
        'catalog_sha256': hashlib.sha256(args.tracker_catalog.read_bytes()).hexdigest(),
        'interpretation': 'Matches identify included class definitions for possible tracker SDK components. '
                          'They do not prove execution or collection. No match is not evidence of no tracking. '
                          'Network-domain signatures are not compared with DEX code.',
        'packages': findings,
    })
    args.bundled_packages.write_text(overlay_text(args.baseline_packages.read_text(), packages))
    print(json.dumps({key: value for key, value in metadata.items() if key.endswith('count') or key.endswith('counts') or key.startswith('bundled_')}))


if __name__ == '__main__':
    main()
