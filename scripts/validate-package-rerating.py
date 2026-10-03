#!/usr/bin/env python3
"""Validate all 766 v2 IDs and reproduce publication twice from the HEAD baseline."""
import argparse
import collections
import hashlib
import importlib.util
import json
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

sys.dont_write_bytecode = True
ROOT = pathlib.Path(__file__).resolve().parents[1]
REL = 'docs/package-audit/2026-10-01/'
AUDIT = ROOT / REL
spec = importlib.util.spec_from_file_location('rerating', ROOT / 'scripts/apply-package-rerating.py')
apply = importlib.util.module_from_spec(spec)
spec.loader.exec_module(apply)


def check_doc_counts(path, ratings, verdicts):
    text = path.read_text()
    for prefix, counts, categories in [('Current ratings', ratings, 'Recommended|Advanced|Expert|Unsafe'),
                                       ('Verdicts', verdicts, 'Uninstall|Keep')]:
        match = re.search(prefix + r':\s*\*\*(.*?)\*\*', text, re.S)
        assert match, 'Missing counts: ' + str(path)
        found = {name: int(count) for count, name in re.findall(r'(\d+)\s+(' + categories + ')', match[1])}
        assert found == counts, str(path) + ': ' + prefix


def reproduce(baseline, expected):
    """Exercise the real writer, with original HEAD inputs separate from output files."""
    with tempfile.TemporaryDirectory(prefix='cana-rerating-v2-') as name:
        root = pathlib.Path(name)
        baseline_dir = root / 'baseline'
        for relative, text in baseline.items():
            for target in (root / relative, baseline_dir / relative):
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(text)
        for relative in ('scripts/apply-package-rerating.py', 'scripts/build-package-audit.py',
                         REL + 'rerating-2026-10-02.json', REL + 'rerating-v2-system.json', REL + 'rerating-v2-user.json'):
            target = root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / relative, target)
        command = [sys.executable, str(root / 'scripts/apply-package-rerating.py'), '--baseline-dir', str(baseline_dir)]
        subprocess.check_output(command, cwd=root, stderr=subprocess.STDOUT)
        first = {path: (root / path).read_bytes() for path in apply.OUTPUTS}
        assert first == expected, 'HEAD fixture did not reproduce all five published outputs'
        subprocess.check_output(command, cwd=root, stderr=subprocess.STDOUT)
        assert first == {path: (root / path).read_bytes() for path in apply.OUTPUTS}, 'Second application changed bytes'
        subprocess.check_output(command + ['--check'], cwd=root, stderr=subprocess.STDOUT)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline-ref', default=apply.BASELINE_REF)
    parser.add_argument('--report', type=pathlib.Path)
    args = parser.parse_args()
    baseline = apply.load_baseline(args.baseline_ref)
    baseline_commit = subprocess.check_output(['git', 'rev-parse', args.baseline_ref], cwd=ROOT, text=True).strip()
    history = apply.index_decisions(apply.read(AUDIT / 'rerating-2026-10-02.json'), 740)
    systems = apply.index_decisions(apply.read(AUDIT / 'rerating-v2-system.json'), 545)
    users = apply.index_decisions(apply.read(AUDIT / 'rerating-v2-user.json'), 221)
    assert not (set(systems) & set(users))
    decisions = {**systems, **users}
    new_ids = {id for id, d in users.items() if d.get('new')}
    assert len(decisions) == 766 and len(new_ids) == 26
    assert set(history) | new_ids == set(decisions) and not (set(history) & new_ids)
    old = json.loads(baseline[apply.OUTPUTS[0]], object_pairs_hook=apply.unique_object)
    old_review = json.loads(baseline[apply.OUTPUTS[2]], object_pairs_hook=apply.unique_object)
    old_findings = json.loads(baseline[apply.OUTPUTS[3]], object_pairs_hook=apply.unique_object)
    packages = apply.read(AUDIT / 'motorola_packages.json')
    verdicts = apply.read(AUDIT / 'cana_verdicts.json')
    review = apply.read(AUDIT / 'motorola_review.json')
    findings = apply.read(AUDIT / 'tracker_findings.json')
    assert set(decisions) == set(packages) == set(verdicts) == set(review['packages']) == set(findings['packages'])
    bundle_text = (ROOT / apply.OUTPUTS[-1]).read_text()
    bundle = json.loads(bundle_text)
    members = apply.bundle_members(bundle_text)
    occurrences = collections.Counter(key for key, *_ in members)
    assert all(occurrences[id] == 1 for id in decisions), 'Duplicate/missing reviewed bundle ID'
    for id, d in decisions.items():
        apply.validate_decision(d)
        entry, record = packages[id], review['packages'][id]
        assert entry['removal'] == d['new_removal'], id
        assert entry['list'] in ('Aosp', 'Carrier', 'Google', 'Misc', 'Oem'), id
        assert isinstance(entry['source'], str) and entry['source'].strip(), id
        for field in ('dependencies', 'neededBy', 'labels'):
            assert isinstance(entry[field], list) and all(isinstance(x, str) and x.strip() for x in entry[field]), id
        assert entry['verdict'] == verdicts[id] == d['new_verdict'] and bundle[id] == entry, id
        assert record['rationale'] == d['reason'] and record['reviewed'] is True, id
        assert record['rating_basis'] == apply.DEFINITIONS[d['new_removal']], id
        assert record['tracker_status'] == findings['packages'][id]['status'], id
        assert entry['description'].splitlines()[1].startswith('Removing breaks: '), id
        assert entry['description'].count('\nRemoving breaks: ') == 1, id
        if id in new_ids:
            assert d['old_removal'] is None and entry['source'] == record['source'] == apply.SOURCE, id
            assert all(entry[field] == [] for field in ('dependencies', 'neededBy', 'labels')), id
            assert entry['list'] == d['list'] and entry['description'] == d['description'], id
            assert record['display_label'] == d['label'] and record['apk_labels'] == [d['label']], id
            assert record['features'] == d['features'] and record['sources'] == d['sources'], id
            assert record['confidence'] == d['confidence'], id
            assert record['tracker_status'] == 'not_scanned' and findings['packages'][id]['apks'] == [], id
            assert not findings['packages'][id]['tracker_names'], id
            assert any('No tracker scan or manifest-relationship capture' in u for u in record['uncertainties']), id
            assert all(not value for value in record['manifest_relationships'].values() if isinstance(value, list)), id
            assert not apply.tracker_lines(entry['description']), id
        else:
            assert d['old_removal'] == history[id]['new_removal'], id
            description = d['description'] if d['description'] is not None else history[id]['new_description']
            assert entry['description'] == description, id + ': description inheritance'
            assert apply.tracker_lines(entry['description']) == apply.tracker_lines(old[id]['description']), id
            assert record['manifest_relationships'] == old_review['packages'][id]['manifest_relationships'], id
            assert record['sources'] == old_review['packages'][id]['sources'], id
            assert findings['packages'][id] == old_findings['packages'][id], id
            for field in set(old[id]) - {'removal', 'description', 'verdict'}:
                assert entry[field] == old[id][field], id + ': unrelated UAD field changed'
            finding = old_findings['packages'][id]
            was_system = finding['status'] == 'apex_not_apk_scanned' or any(
                apk['variant_origin'] in ('factory_partition', 'apex_apk') for apk in finding['apks'])
            assert was_system == (id in systems), id
        if id in systems:
            e = d['evidence']
            assert record['confidence'] == e['decision_basis']['removal_confidence'], id
            assert e['decision_basis']['owner_feature_loss'] == entry['description'].splitlines()[1].removeprefix('Removing breaks: '), id
            if d['new_removal'] == 'Recommended':
                apply.guard_system(d, record)
                assert all(line.startswith(('Note: ', 'Trackers (static scan):')) for line in entry['description'].splitlines()[2:]), id
                if e.get('consolidation'):
                    replacement = e['consolidation']['replacement']
                    assert replacement in packages and packages[replacement]['verdict']['action'] == 'Keep', id
            if e.get('boot_observed_removed'):
                assert e['package_manager']['user0']['installed'] == 'false', id
                assert e['boot_observation']['removal_timestamp_known'] is False, id
        elif id not in new_ids:
            assert record['confidence'] == history[id]['new_confidence'], id
    assert {k: v for k, v in findings.items() if k != 'packages'} == {
        k: v for k, v in old_findings.items() if k != 'packages'}, 'Historical tracker metadata changed'
    # Inspect actual helper contracts for the two static-query exceptions traced in Pass 2.
    guard = systems['com.vzw.ecid']['evidence']['guarded_core_query']
    helper = next(h for h in guard['helpers'] if h['method'].endswith(';.packageInstalled'))
    assert 'Landroid/content/pm/PackageManager$NameNotFoundException;' in helper['catch_types']
    assert 'Landroid/content/pm/PackageManager;.getPackageInfo' in helper['all_called_methods']
    assert systems['com.motorola.help']['evidence']['guarded_core_query']['missing_package_handling']
    keyboard = systems['com.google.android.inputmethod.latin']['evidence']['keyboard_replacement']
    assert keyboard['service_direct_boot_aware'] and keyboard['default_and_current_ime']
    assert users[keyboard['id']]['new_verdict']['action'] == 'Keep'
    for id in ('com.android.storagemanager', 'com.motorola.audiorecorder'):
        assert systems[id]['new_removal'] == 'Advanced' and systems[id]['evidence']['review_override']
        assert 'Removal is recommended' not in packages[id]['description']
    assert systems['com.google.android.apps.safetyhub']['new_removal'] == 'Unsafe'
    assert systems['com.google.android.signature']['new_removal'] == 'Expert'
    # Compare raw members, including unrelated duplicate occurrences and their order.
    before = baseline[apply.OUTPUTS[-1]]
    untouched_before = [(key, before[start:end]) for key, start, end, _, _ in apply.bundle_members(before)
                        if key not in decisions]
    untouched_after = [(key, bundle_text[start:end]) for key, start, end, _, _ in members if key not in decisions]
    assert untouched_before == untouched_after, 'Unrelated bundle bytes/order/count changed'
    head_text = subprocess.check_output(['git', 'show', 'HEAD:' + apply.OUTPUTS[-1]], cwd=ROOT).decode('utf-8')
    untouched_head = [(key, head_text[start:end]) for key, start, end, _, _ in apply.bundle_members(head_text)
                      if key not in decisions]
    assert untouched_head == untouched_after, 'Unrelated bundle records differ from current HEAD'
    assert set(bundle) == set(json.loads(before)) | new_ids, 'Unexpected bundle additions/losses'
    audit = review['audit']
    ratings = apply.counted((p['removal'] for p in packages.values()), apply.RATINGS)
    actions = apply.counted((v['action'] for v in verdicts.values()), ('Uninstall', 'Keep'))
    confidences = apply.counted((r['confidence'] for r in review['packages'].values()), ('high', 'medium', 'low'))
    system_counts = apply.counted((d['new_removal'] for d in systems.values()), apply.RATINGS)
    user_counts = apply.counted((d['new_removal'] for d in users.values()), apply.RATINGS)
    assert audit['removal_counts'] == ratings and audit['verdict_counts'] == actions and audit['confidence_counts'] == confidences
    assert audit['system_removal_counts'] == system_counts and audit['user_removal_counts'] == user_counts
    assert audit['package_count'] == 766 and audit['apk_package_count'] == 724 and audit['apex_count'] == 42
    assert audit['system_package_count'] == 545 and audit['user_package_count'] == 221
    assert audit['apex_count'] == sum(d['evidence']['apex_module'] for d in systems.values())
    assert set(audit['re_review_added_package_ids']) == new_ids and audit['re_review_added_package_count'] == 26
    assert audit['tracker_not_scanned_package_count'] == sum(r['tracker_status'] == 'not_scanned' for r in review['packages'].values()) == 26
    assert audit['bundled_package_count_after'] == len(bundle) == 5742
    assert audit['low_confidence_packages'] == [id for id, r in review['packages'].items() if r['confidence'] == 'low']
    for path in (AUDIT / 'README.md', ROOT / 'tools/debloat-server/README.md', AUDIT / 'rerating-v2-report.md'):
        check_doc_counts(path, ratings, actions)
        assert '766' in path.read_text() and '26' in path.read_text(), str(path)
    report = (AUDIT / 'rerating-v2-report.md').read_text()
    section = report.split('<!-- recommended-start -->')[1].split('<!-- recommended-end -->')[0]
    listed = re.findall(r'^\| `([^`]+)` \| (system|user) \|', section, re.M)
    expected_recommended = {id for id, d in decisions.items() if d['new_removal'] == 'Recommended'}
    assert len(listed) == len(dict(listed)) == len(expected_recommended) == 115
    assert set(dict(listed)) == expected_recommended
    assert all((kind == 'system') == (id in systems) for id, kind in listed)
    new_section = report.split('<!-- additions-start -->')[1].split('<!-- additions-end -->')[0]
    addition_rows = re.findall(r'^\| `([^`]+)` \|', new_section, re.M)
    assert len(addition_rows) == len(set(addition_rows)) == 26 and set(addition_rows) == new_ids
    removed = {id for id, d in systems.items() if d['evidence']['boot_observed_removed']}
    assert len(removed) == 58
    private_list = ROOT / '.superpowers/package-audit/bootloop-2026-10-01/uninstalled_user0_now.txt'
    if private_list.exists():
        assert removed == {line.removeprefix('package:') for line in private_list.read_text().split()}, 'Private removed list differs'
    expected_restore = {id: systems[id]['new_removal'] for id in removed
                        if systems[id]['new_removal'] in ('Expert', 'Unsafe')}
    restore = (AUDIT / 'restore-2026-10-02.md').read_text()
    restore_rows = re.findall(r'^\| `([^`]+)` \| (Expert|Unsafe) \|', restore, re.M)
    assert len(restore_rows) == len(dict(restore_rows)) == len(expected_restore) == 24
    assert dict(restore_rows) == expected_restore and apply.counted(expected_restore.values()) == {'Unsafe': 1, 'Expert': 23}
    ET.parse(ROOT / 'app/src/main/res/values/strings.xml')
    changed = subprocess.check_output(['git', 'diff', '--name-only', 'HEAD'], cwd=ROOT, text=True).splitlines()
    assert not any(path.endswith('.kt') for path in changed), 'Kotlin was changed'
    expected = {relative: (ROOT / relative).read_bytes() for relative in apply.OUTPUTS}
    reproduce(baseline, expected)
    subprocess.check_output([sys.executable, str(ROOT / 'scripts/apply-package-rerating.py'),
                             '--baseline-ref', args.baseline_ref, '--check'], cwd=ROOT)
    issues = [{'id': 'com.touchtype.swiftkey',
               'issue': 'Supplied reason and inherited description name HeliBoard, also Recommended; FUTO is retained. Approved user decision preserved.'}]
    result = {
        'date': '2026-10-02', 'status': 'passed', 'baseline_commit': baseline_commit,
        'package_count': 766, 'system_package_count': 545, 'user_package_count': 221, 'added_package_count': 26,
        'system_removal_counts': system_counts, 'user_removal_counts': user_counts, 'removal_counts': ratings,
        'verdict_counts': actions, 'confidence_counts': confidences,
        'restore_count': len(expected_restore), 'restore_counts': apply.counted(expected_restore.values()),
        'unrelated_bundle_records_compared_as_bytes': len(untouched_after),
        'core_query_exceptions': sorted(id for id, d in systems.items() if d['new_removal'] == 'Recommended' and d['evidence']['queried_by_core']),
        'user_decision_issues': issues,
        'checks': [
            'All 766 IDs present exactly once in decisions, audit JSON outputs and reviewed bundle records.',
            'Valid JSON with unique audit keys; Uninstall if and only if Recommended, exact approved verdicts/descriptions.',
            'All 71 Recommended system packages pass persistent, shared UID, RRO, APEX, library/WebView, confidence and core-relationship guards.',
            'Four scoped core queries retain positive inert evidence; guarded helpers and retained Direct-Boot-aware FUTO checked.',
            'Original sources, manifest relationships, tracker lines/findings preserved; 26 additions explicitly not_scanned.',
            'Unrelated bundle record bytes, order and duplicate occurrences unchanged from HEAD.',
            'Both approved system overrides recorded; package/rating/verdict/confidence totals and docs match.',
            'Report covers every Recommended package and all additions; restore covers all 24 Expert/Unsafe current removals.',
            'All five outputs reproduced from HEAD in a separate fixture; second application byte-identical and --check passed.',
            'English strings XML valid; Kotlin unchanged.',
        ],
        'sha256': {relative: hashlib.sha256(data).hexdigest() for relative, data in expected.items()},
        'limitations': ['Approved user decision inconsistency is reported, not silently changed.',
                        'No new tracker scan/manifest-relationship capture for the 26 additions.',
                        'No JDK/Android SDK; Gradle unit tests, lint and APK assembly not run.',
                        'Failed-boot logs and removal timestamps unavailable; no phone mutations or feature/call tests.'],
    }
    for name in ('rerating-2026-10-02.json', 'rerating-v2-system.json', 'rerating-v2-user.json'):
        result['sha256'][REL + name] = hashlib.sha256((AUDIT / name).read_bytes()).hexdigest()
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(apply.encoded(result))
    print(apply.encoded(result).strip())


if __name__ == '__main__':
    main()
