#!/usr/bin/env python3
"""Reproduce the v2 publication from HEAD, v1 history and both reviewed v2 inputs.

Only read-only git show queries are used. No device queries or research inference.
Null user descriptions inherit the v1 description, never a previously applied v2 output.
"""
import argparse
import collections
import copy
import importlib.util
import json
import pathlib
import subprocess
import sys
import tempfile


sys.dont_write_bytecode = True
ROOT = pathlib.Path(__file__).resolve().parents[1]
AUDIT = ROOT / 'docs/package-audit/2026-10-01'
# HEAD when Pass 3 began; pin it so publication still reproduces after a later review commit.
BASELINE_REF = 'dedbed0c2546f80140e296cf1ef7ab3710a54d7b'
REL = 'docs/package-audit/2026-10-01/'
OUTPUTS = [REL + name for name in ('motorola_packages.json', 'cana_verdicts.json',
                                  'motorola_review.json', 'tracker_findings.json')]
OUTPUTS.append('app/src/main/assets/uad_lists.json')
SOURCE = 'Cana Motorola Edge 2025 audit (2026-10-02 re-review)'
RATINGS = ('Recommended', 'Advanced', 'Expert', 'Unsafe')
DEFINITIONS = {
    'Recommended': 'Actually recommend removal to advance privacy/performance debloating or one-app-per-job consolidation; boot/core-phone safety is established and named feature/data loss is allowed.',
    'Advanced': 'Keep an app/feature the owner needs or likely uses; removal is boot-safe but loses the named feature and possibly local data.',
    'Expert': 'Broad, unpredictable or unknown removal effects, including setup, updates, sign-in and carrier integrations; keep.',
    'Unsafe': 'Do not remove: possible boot/crash loops, Rescue Party, or loss of core phone, UI, connectivity, installs, security or protection.',
}


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('Duplicate JSON key: ' + key)
        result[key] = value
    return result


def read(path):
    return json.loads(path.read_text(), object_pairs_hook=unique_object)


CORE_QUERY_EXCEPTIONS = {'com.att.iqi', 'com.glance.lockscreenM',
                         'com.motorola.help', 'com.vzw.ecid'}


def guard_system(d, record):
    """Require positive safety facts; allow only the four individually traced core queries."""
    id, e = d['id'], d['evidence']
    pm, relations = e.get('package_manager') or {}, record['manifest_relationships']
    basis = e['decision_basis']
    assert basis['removal_confidence'] != 'low' and record['confidence'] != 'low', id
    assert e['current_package_observed'] and pm and int(pm['app_id']) >= 10000, id
    assert basis['safety_assessment'] and basis['owner_feature_loss'], id
    assert not (pm.get('persistent') or 'PERSISTENT' in pm.get('flags', [])
                or e['persistent_running'] or e['privileged_shared_uid']), id
    assert not (e['overlay_targets'] or relations['overlay_targets'] or e.get('overlay_user0')), id
    assert not (e['apex_module'] or e.get('current_apex_containers')
                or pm.get('code_path', '').startswith('/apex/')
                or record['tracker_status'] == 'apex_not_apk_scanned'), id
    assert not (e['library_provider'] or e['webview_provider']
                or relations['registered_framework_libraries']
                or any(x['type'] in ('library', 'static-library', 'sdk-library')
                       for x in relations['declared_libraries'])), id
    # Historical manifest sharedUserId declarations may not be used by the updated APK.
    active_uid = e.get('active_shared_user')
    assert not (active_uid or pm.get('shared_user')), id + ': active shared UID'
    if relations['shared_user_ids']:
        assert e.get('historical_shared_uid_inactive'), id + ': unresolved historical shared UID'
    assert not (e['core_bindings'] or e.get('previous_core_bindings')
                or e['default_input_method'] or e['role_holders']), id
    if e['queried_by_core']:
        assert id in CORE_QUERY_EXCEPTIONS and e['positive_inert_exception']['positive_evidence'], id
        assert e['boot_observed_removed'] or e['guarded_core_query']['missing_package_handling'], id
    if e.get('consolidation'):
        assert e['consolidation']['replacement_installed_user0'], id
    # Provisioning/carrier optional UI is not exempt merely because another SIM is active.
    if id in {'com.motorola.setupwizard.devicesetup', 'com.motorola.vzw.cloudsetup', 'com.vzw.ecid'}:
        assert e['boot_observed_removed'] and e['package_manager']['user0']['installed'] == 'false', id


def encoded(data):
    return json.dumps(data, ensure_ascii=False, indent=2) + '\n'


def bundle_members(text):
    """Locate raw top-level records, including duplicate IDs in the upstream bundle."""
    decoder = json.JSONDecoder()
    position = text.index('{') + 1
    members = []
    while True:
        while text[position].isspace():
            position += 1
        if text[position] == '}':
            break
        start = position
        key, position = decoder.raw_decode(text, position)
        while text[position].isspace():
            position += 1
        assert text[position] == ':'
        position += 1
        while text[position].isspace():
            position += 1
        value, position = decoder.raw_decode(text, position)
        end = position
        while text[position].isspace():
            position += 1
        comma = position if text[position] == ',' else None
        members.append((key, start, end, comma, value))
        if comma is None:
            assert text[position] == '}'
            break
        position += 1
    return members


def deduplicate_reviewed(text, ids):
    """Keep the last audited occurrence; never remove/reformat an unrelated record."""
    members = bundle_members(text)
    last = {key: index for index, (key, *_) in enumerate(members)}
    deletions = []
    for index, (key, start, end, comma, value) in enumerate(members):
        if key in ids and index != last[key]:
            line_start = text.rfind('\n', 0, start) + 1
            if not text[line_start:start].strip():
                start = line_start
            deletions.append((start, comma + 1))
    for start, end in reversed(deletions):
        text = text[:start] + text[end:]
    counts = collections.Counter(key for key, *_ in bundle_members(text))
    assert all(counts[id] == 1 for id in ids)
    return text


def replace(path, text):
    """Validate every result before writing; replace each file atomically."""
    with tempfile.NamedTemporaryFile(mode='w', dir=path.parent, delete=False,
                                     encoding='utf-8', newline='') as handle:
        staging = pathlib.Path(handle.name)
        handle.write(text)
    try:
        staging.chmod(path.stat().st_mode & 0o777)
        staging.replace(path)
    finally:
        staging.unlink(missing_ok=True)


def counted(values, keys=None):
    counts = collections.Counter(values)
    return {k: counts[k] for k in keys} if keys else dict(counts)


def load_baseline(ref=BASELINE_REF, directory=None):
    if directory:
        return {path: (directory / path).read_text() for path in OUTPUTS}
    return {path: subprocess.check_output(['git', 'show', ref + ':' + path],
                                         cwd=ROOT).decode('utf-8') for path in OUTPUTS}


def index_decisions(rows, count):
    result = {d['id']: d for d in rows}
    assert len(rows) == len(result) == count, f'Expected {count} unique decisions'
    return result


def validate_decision(d):
    assert d['new_removal'] in RATINGS and d['reason'].strip() and '\n' not in d['reason'], d['id']
    verdict = d['new_verdict']
    assert set(verdict) == {'action', 'benefits', 'reason'}, d['id']
    assert verdict['action'] == ('Uninstall' if d['new_removal'] == 'Recommended' else 'Keep'), d['id']
    assert isinstance(verdict['benefits'], list) and set(verdict['benefits']) <= {'privacy', 'performance'}, d['id']
    assert not (verdict['action'] == 'Keep' and verdict['benefits']), d['id']
    assert verdict['reason'].endswith(d['reason']), d['id']


def tracker_lines(description):
    return [line for line in description.splitlines() if line.startswith('Trackers (static scan):')]


def first_pass(packages, review, history):
    """Replay approved v1 mutable fields, preserving original sources and relationships."""
    assert set(packages) == set(review['packages']) == set(history)
    for id, d in history.items():
        assert packages[id]['removal'] == d['old_removal'], id
        assert packages[id]['verdict'] == d['old_verdict'], id
        assert tracker_lines(packages[id]['description']) == tracker_lines(d['new_description']), id
        packages[id].update(removal=d['new_removal'], description=d['new_description'],
                            verdict=copy.deepcopy(d['new_verdict']))
        record = review['packages'][id]
        record.update(rationale=d['reason'], confidence=d['new_confidence'])
        if 'new_features' in d:
            record['features'] = copy.deepcopy(d['new_features'])
        record.setdefault('device_evidence', {})['rerating_2026_10_02'] = {
            'decision': 'rerating-2026-10-02.json#' + id,
            'persistent': d['evidence'].get('package_manager', {}).get('persistent', False),
            'shared_user_ids': d['evidence']['shared_user_ids'],
            'core_bindings': d['evidence']['core_bindings'],
            'queried_by_core': d['evidence']['queried_by_core'],
            'overlay_user0': d['evidence'].get('overlay_user0'),
            'apex_snapshot': d['evidence'].get('apex_snapshot'),
            'current_apex_containers': d['evidence']['current_apex_containers'],
            'confidence_basis': 'Removal conclusion; package purpose alone does not establish safety.',
        }
        uncertainty = ('No pre-reset crash logs or removal sequence survived. Post-reset observations do not '
                       'prove all features or future activation/setup paths work.')
        record['uncertainties'] = list(dict.fromkeys(record['uncertainties'] + [uncertainty]))


def build_outputs(baseline, history_rows, system_rows, user_rows):
    history = index_decisions(history_rows, 740)
    systems = index_decisions(system_rows, 545)
    users = index_decisions(user_rows, 221)
    assert not (set(systems) & set(users)), 'System and user decisions overlap'
    decisions = {**systems, **users}
    new_ids = [id for id, d in users.items() if d.get('new')]
    assert len(new_ids) == 26 and set(decisions) - set(history) == set(new_ids)
    assert set(systems) == {id for id, d in history.items() if d['evidence']['identity_type'] == 'system'}
    packages = json.loads(baseline[OUTPUTS[0]], object_pairs_hook=unique_object)
    verdicts = json.loads(baseline[OUTPUTS[1]], object_pairs_hook=unique_object)
    review = json.loads(baseline[OUTPUTS[2]], object_pairs_hook=unique_object)
    findings = json.loads(baseline[OUTPUTS[3]], object_pairs_hook=unique_object)
    assert set(packages) == set(verdicts) == set(findings['packages']) == set(history)
    assert all(packages[id]['verdict'] == verdicts[id] for id in history)
    original_counts = counted(p['removal'] for p in packages.values())
    historical_audit = copy.deepcopy(review['audit'])
    first_pass(packages, review, history)
    first_counts = counted(p['removal'] for p in packages.values())
    assert not (set(new_ids) & set(json.loads(baseline[OUTPUTS[-1]]))), 'A new ID already exists in the bundle'
    relation_template = review['packages'][next(iter(review['packages']))]['manifest_relationships']
    for id, d in decisions.items():
        validate_decision(d)
        if id in new_ids:
            assert d['old_removal'] is None and d['description'] and d['confidence'] in ('high', 'medium', 'low'), id
            packages[id] = {'list': d['list'], 'description': d['description'], 'dependencies': [],
                            'neededBy': [], 'labels': [], 'removal': d['new_removal'],
                            'source': SOURCE, 'verdict': copy.deepcopy(d['new_verdict'])}
            relations = {key: ([] if isinstance(value, list) else
                               'No manifest-relationship capture was performed for this post-audit addition.')
                         for key, value in relation_template.items()}
            review['packages'][id] = {
                'confidence': d['confidence'], 'sources': copy.deepcopy(d['sources']),
                'features': copy.deepcopy(d['features']),
                'uncertainties': ['No tracker scan or manifest-relationship capture was done for this post-audit package; label and supplied feature evidence do not establish tracker absence.'],
                'rationale': d['reason'], 'reviewed': True, 'display_label': d['label'],
                'apk_labels': [d['label']], 'manifest_relationships': relations,
                'tracker_status': 'not_scanned', 'source': SOURCE,
                'device_evidence': {'tracker_scan_complete': False, 'manifest_relationship_capture': False,
                                    'label_basis': d['evidence']['label_from']},
                'baseline_context': 'Added after the October 1 inventory from the reviewed October 2 user-app input.',
            }
            findings['packages'][id] = {
                'status': 'not_scanned', 'tracker_names': [], 'apks': [],
                'note': 'Added on October 2; no tracker scan or manifest-relationship capture was performed.',
            }
        else:
            assert d['old_removal'] == packages[id]['removal'], id + ': first-pass rating mismatch'
        record = review['packages'][id]
        description = d['description'] if d['description'] is not None else packages[id]['description']
        lines = description.splitlines()
        assert len(lines) >= 2 and lines[1].startswith('Removing breaks: '), id
        assert description.count('\nRemoving breaks: ') == 1, id
        assert tracker_lines(description) == tracker_lines(packages[id]['description']), id
        packages[id].update(removal=d['new_removal'], description=description, verdict=copy.deepcopy(d['new_verdict']))
        verdicts[id] = copy.deepcopy(d['new_verdict'])
        record.update(rationale=d['reason'], rating_basis=DEFINITIONS[d['new_removal']])
        facts = {'decision': ('rerating-v2-system.json' if id in systems else 'rerating-v2-user.json') + '#' + id,
                 'identity_type': 'system' if id in systems else 'user-installed',
                 'description_inherited_from_v1': d['description'] is None}
        if id in systems:
            record['confidence'] = d['evidence']['decision_basis']['removal_confidence']
            facts.update({key: copy.deepcopy(d['evidence'][key]) for key in (
                'package_manager', 'active_shared_user', 'privileged_shared_uid', 'persistent_running',
                'overlay_targets', 'core_bindings', 'previous_core_bindings', 'queried_by_core',
                'current_apex_containers', 'boot_observed_removed', 'decision_basis') if key in d['evidence']})
            for key in ('positive_inert_exception', 'guarded_core_query', 'historical_shared_uid_inactive',
                        'keyboard_replacement', 'consolidation', 'review_override'):
                if key in d['evidence']:
                    facts[key] = copy.deepcopy(d['evidence'][key])
            if d['new_removal'] == 'Recommended':
                guard_system(d, record)
        else:
            facts['supplied_user_evidence'] = copy.deepcopy(d['evidence'])
        record.setdefault('device_evidence', {})['rerating_v2_2026_10_02'] = facts
    assert set(packages) == set(verdicts) == set(review['packages']) == set(findings['packages']) == set(decisions)
    spec = importlib.util.spec_from_file_location('audit_publisher', ROOT / 'scripts/build-package-audit.py')
    publisher = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(publisher)
    bundle = deduplicate_reviewed(publisher.overlay_text(baseline[OUTPUTS[-1]], packages), decisions)
    audit = review['audit']
    audit.update(
        rerating_date='2026-10-02', rerating_version=2,
        source=SOURCE, rating_definitions=DEFINITIONS,
        package_count=766, apk_package_count=724, apex_count=42,
        system_package_count=545, user_package_count=221,
        removal_counts=counted((p['removal'] for p in packages.values()), RATINGS),
        system_removal_counts=counted((packages[id]['removal'] for id in systems), RATINGS),
        user_removal_counts=counted((packages[id]['removal'] for id in users), RATINGS),
        verdict_counts=counted((v['action'] for v in verdicts.values()), ('Uninstall', 'Keep')),
        confidence_counts=counted((r['confidence'] for r in review['packages'].values()), ('high', 'medium', 'low')),
        low_confidence_packages=[id for id, r in review['packages'].items() if r['confidence'] == 'low'],
        verdict_rule='Uninstall if and only if removal is Recommended; otherwise Keep.',
        superseded_removal_counts=original_counts, first_pass_removal_counts=first_counts,
        re_review_added_package_count=26, re_review_added_package_ids=new_ids,
        tracker_not_scanned_package_count=26,
        bundled_package_count_after=len(json.loads(bundle)),
        historical_october_1_audit=historical_audit,
        scope='The original 740 identities plus 26 reviewed post-reset user apps: 545 system/APEX and 221 user-installed IDs; absence after reset does not remove an audit identity.',
    )
    audit['limitations'] = [
        'The October 1 ratings and first-pass recommendation definition are superseded after the owner-reported bootloop.',
        'No phone mutations or removal experiments were performed; failed-boot logs and original raw audit are unavailable.',
        'Five October 2 reboot records and 58 surviving removals support current boot safety; removal timestamps and complete feature/core-function tests are unavailable.',
        'Core-query exceptions are individually scoped to optional feed, telemetry, feedback or guarded call-filter paths; other carrier/provisioning paths remain protected.',
        'Original tracker findings and manifest relationships are retained, with their October 1 version/hash limits.',
        'The 26 additions have no tracker scan or manifest-relationship capture; not_scanned never means tracker-free.',
        'Approved user decisions are preserved; the SwiftKey replacement text still names HeliBoard, also recommended for removal. FUTO is retained.',
        'User descriptions with null inherit v1 unchanged; supplied Source lines on additions are preserved.',
    ]
    return dict(zip(OUTPUTS, [encoded(packages), encoded(verdicts), encoded(review), encoded(findings), bundle]))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline-ref', default=BASELINE_REF,
                        help='Read-only Git baseline (default: HEAD captured before this re-rating)')
    parser.add_argument('--baseline-dir', type=pathlib.Path, help='Read original outputs from a fixture directory instead of Git')
    parser.add_argument('--history', type=pathlib.Path, default=AUDIT / 'rerating-2026-10-02.json')
    parser.add_argument('--system-decisions', type=pathlib.Path, default=AUDIT / 'rerating-v2-system.json')
    parser.add_argument('--user-decisions', type=pathlib.Path, default=AUDIT / 'rerating-v2-user.json')
    parser.add_argument('--check', action='store_true', help='Check all outputs without writing')
    args = parser.parse_args()
    outputs = build_outputs(load_baseline(args.baseline_ref, args.baseline_dir),
                            read(args.history), read(args.system_decisions), read(args.user_decisions))
    for relative, text in outputs.items():
        json.loads(text, object_pairs_hook=None if relative == OUTPUTS[-1] else unique_object)
        if args.check:
            assert (ROOT / relative).read_text() == text, 'Output differs from decisions: ' + relative
    if not args.check:
        for relative, text in outputs.items():
            replace(ROOT / relative, text)
    audit = json.loads(outputs[OUTPUTS[2]])['audit']
    print(encoded({key: audit[key] for key in ('removal_counts', 'system_removal_counts',
                                             'user_removal_counts', 'verdict_counts', 'confidence_counts')}).strip())


if __name__ == '__main__':
    main()
