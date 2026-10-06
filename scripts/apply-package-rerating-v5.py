#!/usr/bin/env python3
"""Reproduce the v5 publication: rebuild v4 from its pinned baseline, then apply rerating-v5.json.

v5 records the 2026-10-06 privacy sweep of the Metro Motorola Edge 2025 (read-only wireless ADB,
no reboot, no package changed): it annotates the installed system packages that carry telemetry,
ad SDKs, other-carrier leftovers or cloud AI features with the evidence gathered that day (APK dex
scan, network bytes since boot, scheduled jobs, special-access grants and the tailnet DNS log), and
extends the audit to every installed user app. Two Expert packages move to Advanced on that
evidence (Motorola Device Management and Gametime); a handful of user apps move to Recommended as
duplicates or unused tracker-heavy builds; and the user apps installed after the October 1 audit
are added with store metadata and Exodus Privacy tracker reports. Only read-only git show queries
are used; no phone is needed.

Like v4, v5 writes only the docs/package-audit outputs; tools/debloat-server builds the served
Moto Edge 2025 list from motorola_packages.json.
"""
import argparse
import copy
import json
import pathlib
import sys


sys.dont_write_bytecode = True
ROOT = pathlib.Path(__file__).resolve().parents[1]
def _load(name, relative):
    import importlib.util
    spec = importlib.util.spec_from_file_location(name, ROOT / relative)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


v2 = _load('rerating_v2', 'scripts/apply-package-rerating.py')
v3 = _load('rerating_v3', 'scripts/apply-package-rerating-v3.py')
v4 = _load('rerating_v4', 'scripts/apply-package-rerating-v4.py')

AUDIT = v2.AUDIT
AUDIT_V5 = ROOT / 'docs/package-audit/2026-10-06'
OUTPUTS = v4.OUTPUTS
RATINGS = v2.RATINGS
SOURCE = 'Cana Motorola Edge 2025 audit (2026-10-06 v5 privacy sweep)'
AUDIT_DATE = '2026-10-06'
MARKER = 'Privacy sweep 2026-10-06'
# The only system rating changes v5 makes, both Expert -> Advanced with positive evidence.
RELAXED = {'com.motorola.ccc.devicemanagement', 'com.motorola.gamemode'}
PREFIX = {'Recommended': 'Recommend removal: ', 'Advanced': 'Keep (Advanced): ',
          'Expert': 'Keep (Expert): ', 'Unsafe': 'Do not remove: '}


def validate_v5(d, packages, system_ids):
    v2.validate_decision(d)
    id, e = d['id'], d['evidence']
    assert d['kind'] in ('update', 'add'), id
    assert d['confidence'] in ('high', 'medium'), id
    assert e['basis'] == 'privacy_sweep' and e['audit_date'] == AUDIT_DATE, id
    assert isinstance(e['installed_user0'], bool), id
    assert d['tier'] in ('S1', 'S2', 'S3', 'S4', 'U1', 'U2', 'U3', 'U4', 'U5'), id
    assert d['new_verdict']['reason'].startswith(PREFIX[d['new_removal']]), id
    lines = d['description'].splitlines()
    assert len(lines) >= 2 and lines[1].startswith('Removing breaks: '), id
    assert MARKER in d['description'], id
    if d['kind'] == 'update':
        current = packages[id]
        assert d['old_removal'] == current['removal'], id + ': v4 rating mismatch'
        assert v2.tracker_lines(d['description']) == v2.tracker_lines(current['description']), id
        assert d['description'].startswith(current['description'].splitlines()[0]), id
        if d['new_removal'] != d['old_removal']:
            if id in system_ids:
                assert id in RELAXED and (d['old_removal'], d['new_removal']) == ('Expert', 'Advanced'), id
                assert e['installed_user0'] and e['restored_user0_at'], id
                assert e['retained_hard_dependents'] == [], id
            else:
                # User apps only ever move to Recommended here: duplicates or unused tracker-heavy builds.
                assert (d['old_removal'], d['new_removal']) == ('Advanced', 'Recommended'), id
                assert e['identity_type'] == 'user-installed', id
    else:
        assert id not in packages and id not in system_ids, id
        a = d['add']
        assert a['list'] in ('Misc', 'Google', 'Carrier'), id
        assert a['display_label'] and isinstance(a['features'], list) and a['features'], id
        assert isinstance(a['sources'], list) and a['sources'], id
        assert e['identity_type'] == 'user-installed', id


def apply_v5(texts, rows, system_rows):
    packages = json.loads(texts[OUTPUTS[0]], object_pairs_hook=v2.unique_object)
    verdicts = json.loads(texts[OUTPUTS[1]], object_pairs_hook=v2.unique_object)
    review = json.loads(texts[OUTPUTS[2]], object_pairs_hook=v2.unique_object)
    decisions = {d['id']: d for d in rows}
    assert len(decisions) == len(rows), 'Duplicate v5 decision'
    system_ids = {d['id'] for d in system_rows}
    v4_counts = copy.deepcopy(review['audit']['removal_counts'])
    added = 0
    for id, d in decisions.items():
        validate_v5(d, packages, system_ids)
        evidence = {'decision': '../2026-10-06/rerating-v5.json#' + id, **copy.deepcopy(d['evidence'])}
        if d['kind'] == 'update':
            packages[id].update(removal=d['new_removal'], description=d['description'],
                                verdict=copy.deepcopy(d['new_verdict']))
            verdicts[id] = copy.deepcopy(d['new_verdict'])
            record = review['packages'][id]
            record.update(rationale=d['reason'], confidence=d['confidence'],
                          rating_basis=v2.DEFINITIONS[d['new_removal']])
            record.setdefault('device_evidence', {})['privacy_sweep_v5_2026_10_06'] = evidence
        else:
            a = d['add']
            packages[id] = {
                'list': a['list'], 'description': d['description'], 'dependencies': [], 'neededBy': [],
                'labels': copy.deepcopy(a['labels']), 'removal': d['new_removal'], 'source': SOURCE,
                'verdict': copy.deepcopy(d['new_verdict']),
            }
            verdicts[id] = copy.deepcopy(d['new_verdict'])
            review['packages'][id] = {
                'confidence': d['confidence'], 'sources': copy.deepcopy(a['sources']),
                'features': copy.deepcopy(a['features']),
                'uncertainties': ['Catalogued on 2026-10-06 from the installed package list, store metadata and '
                                  'the Exodus Privacy report; the APK itself was not inspected.'],
                'rationale': d['reason'], 'reviewed': True, 'apk_labels': [a['display_label']],
                'display_label': a['display_label'], 'manifest_relationships': {},
                'tracker_status': copy.deepcopy(a['tracker_status']),
                'device_evidence': {'privacy_sweep_v5_2026_10_06': evidence},
                'rating_basis': v2.DEFINITIONS[d['new_removal']], 'source': SOURCE,
            }
            added += 1
    # Keep the three files sorted by package id like the earlier layers.
    packages = dict(sorted(packages.items()))
    verdicts = dict(sorted(verdicts.items()))
    review['packages'] = dict(sorted(review['packages'].items()))
    assert set(packages) == set(verdicts) == set(review['packages'])
    assert all(packages[id]['verdict'] == verdicts[id] for id in packages)
    assert all(p['verdict']['action'] == ('Uninstall' if p['removal'] == 'Recommended' else 'Keep')
               for p in packages.values())
    audit = review['audit']
    audit.update(
        rerating_date=AUDIT_DATE, rerating_version=5, source=SOURCE,
        package_count=len(packages),
        removal_counts=v2.counted((p['removal'] for p in packages.values()), RATINGS),
        system_removal_counts=v2.counted((packages[id]['removal'] for id in system_ids), RATINGS),
        user_removal_counts=v2.counted((p['removal'] for id, p in packages.items() if id not in system_ids), RATINGS),
        verdict_counts=v2.counted((v['action'] for v in verdicts.values()), ('Uninstall', 'Keep')),
        confidence_counts=v2.counted((r['confidence'] for r in review['packages'].values()), ('high', 'medium', 'low')),
        low_confidence_packages=[id for id, r in review['packages'].items() if r['confidence'] == 'low'],
        v4_removal_counts=v4_counts,
        v5_changed_package_count=len(decisions) - added,
        v5_added_package_count=added,
        v5_rating_change_count=sum(d['kind'] == 'update' and d['old_removal'] != d['new_removal']
                                   for d in decisions.values()),
        v5_privacy_sweep='docs/package-audit/2026-10-06/privacy-sweep.md',
    )
    audit['limitations'] = audit['limitations'] + [
        'The 2026-10-06 privacy sweep was read-only and judged privacy, advertising and bloat concerns; '
        'its tier labels are the owner\'s removal plan, not boot tests.',
        'User apps added by v5 were catalogued from store metadata and Exodus Privacy reports without '
        'inspecting the APK; their tracker lines come from Exodus, not from Cana\'s static scan.',
        'Tailnet DNS evidence covers only time the phone resolved names through AdGuard Home over Tailscale.',
    ]
    return {OUTPUTS[0]: v2.encoded(packages), OUTPUTS[1]: v2.encoded(verdicts), OUTPUTS[2]: v2.encoded(review)}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--baseline-ref', default=v2.BASELINE_REF)
    parser.add_argument('--baseline-dir', type=pathlib.Path)
    parser.add_argument('--decisions', type=pathlib.Path, default=AUDIT_V5 / 'rerating-v5.json')
    parser.add_argument('--check', action='store_true', help='Check all outputs without writing')
    args = parser.parse_args()
    system_rows = v2.read(AUDIT / 'rerating-v2-system.json')
    texts = v2.build_outputs(v2.load_baseline(args.baseline_ref, args.baseline_dir),
                             v2.read(AUDIT / 'rerating-2026-10-02.json'), system_rows,
                             v2.read(AUDIT / 'rerating-v2-user.json'))
    texts = v3.apply_v3(texts, v2.read(AUDIT / 'rerating-v3.json'), system_rows)
    texts = v4.apply_v4(texts, v2.read(v4.AUDIT_V4 / 'rerating-v4.json'), system_rows)
    outputs = apply_v5(texts, v2.read(args.decisions), system_rows)
    assert apply_v5(texts, v2.read(args.decisions), system_rows) == outputs, 'v5 is not deterministic'
    for relative, text in outputs.items():
        json.loads(text, object_pairs_hook=v2.unique_object)
        if args.check:
            assert (ROOT / relative).read_text() == text, 'Output differs from decisions: ' + relative
    if not args.check:
        for relative, text in outputs.items():
            v2.replace(ROOT / relative, text)
    audit = json.loads(outputs[OUTPUTS[2]])['audit']
    print(v2.encoded({key: audit[key] for key in ('package_count', 'removal_counts', 'system_removal_counts',
                                                 'user_removal_counts', 'verdict_counts', 'confidence_counts',
                                                 'v5_changed_package_count', 'v5_added_package_count',
                                                 'v5_rating_change_count')}).strip())


if __name__ == '__main__':
    main()
