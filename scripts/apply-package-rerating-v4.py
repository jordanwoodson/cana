#!/usr/bin/env python3
"""Reproduce the v4 publication: rebuild v3 from its pinned baseline, then apply rerating-v4.json.

v4 records the 2026-10-06 read-only runtime audit of the Metro Motorola Edge 2025 (wireless ADB,
no reboot, no package changed): every package still uninstalled for user 0 gets its verified
runtime evidence, three inert other-carrier SystemUI overlays move from Unsafe to Expert, and the
active Metro carrier overlays, Moto AI, Gboard and the carrier-settings apps get restore caveats.
v4 adds no new removal. Only read-only git show queries are used; no phone is needed.

The app no longer ships app/src/main/assets/uad_lists.json (commit 90addc6 switched Cana to the
Android Debloat List), so v4 writes only the docs/package-audit outputs. The served Moto Edge 2025
list is built from motorola_packages.json by tools/debloat-server (see its README).
"""
import argparse
import copy
import importlib.util
import json
import pathlib
import sys


sys.dont_write_bytecode = True
ROOT = pathlib.Path(__file__).resolve().parents[1]


def load(name, relative):
    spec = importlib.util.spec_from_file_location(name, ROOT / relative)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


v2 = load('rerating_v2', 'scripts/apply-package-rerating.py')
v3 = load('rerating_v3', 'scripts/apply-package-rerating-v3.py')

AUDIT = v2.AUDIT
AUDIT_V4 = ROOT / 'docs/package-audit/2026-10-06'
OUTPUTS = v2.OUTPUTS[:3]  # motorola_packages.json, cana_verdicts.json, motorola_review.json
RATINGS = v2.RATINGS
SOURCE = 'Cana Motorola Edge 2025 audit (2026-10-06 v4 runtime audit)'
AUDIT_DATE = '2026-10-06'
# The only rating changes v4 makes: inert, system-disabled other-carrier SystemUI overlays that
# have been absent through every boot since 2026-10-03 with SystemUI healthy.
RELAXED = {'com.motorola.android.systemui.overlay.vzw', 'com.motorola.android.systemui.overlay.sprint',
           'com.motorola.android.systemui.overlay.usc'}


def validate_v4(d, current):
    v2.validate_decision(d)
    id, e = d['id'], d['evidence']
    assert d['old_removal'] == current['removal'], id + ': v3 rating mismatch'
    assert d['confidence'] in ('high', 'medium'), id
    assert e['basis'] == 'runtime_audit' and e['audit_date'] == AUDIT_DATE and e['health'], id
    assert isinstance(e['installed_user0'], bool) and e['absent_at_audit'] == (not e['installed_user0']), id
    assert isinstance(e['observed_boots_after_last_install'], list) and e['uninstall_time_known'] is False, id
    lines = d['description'].splitlines()
    assert len(lines) >= 2 and lines[1].startswith('Removing breaks: ') and d['description'].count('\nRemoving breaks: ') == 1, id
    assert v2.tracker_lines(d['description']) == v2.tracker_lines(current['description']), id
    assert 'Runtime audit 2026-10-06' in d['description'], id
    if d['new_removal'] != d['old_removal']:
        # v4 never recommends anything new; it only relaxes Unsafe to Expert with positive evidence.
        assert id in RELAXED and (d['old_removal'], d['new_removal']) == ('Unsafe', 'Expert'), id
        assert not e['installed_user0'] and e['target_running'] and e['overlay_manager_state'], id
        assert len(e['observed_boots_after_last_install']) >= 3, id
    elif d['new_removal'] == 'Recommended':
        # A Recommended package that is installed again keeps its v3 structural-guard basis.
        assert not e['installed_user0'] or id == 'com.motorola.uxcore', id


def apply_v4(texts, rows, system_rows):
    packages = json.loads(texts[OUTPUTS[0]], object_pairs_hook=v2.unique_object)
    verdicts = json.loads(texts[OUTPUTS[1]], object_pairs_hook=v2.unique_object)
    review = json.loads(texts[OUTPUTS[2]], object_pairs_hook=v2.unique_object)
    decisions = {d['id']: d for d in rows}
    assert len(decisions) == len(rows), 'Duplicate v4 decision'
    v3_counts = copy.deepcopy(review['audit']['removal_counts'])
    for id, d in decisions.items():
        validate_v4(d, packages[id])
        packages[id].update(removal=d['new_removal'], description=d['description'],
                            verdict=copy.deepcopy(d['new_verdict']))
        verdicts[id] = copy.deepcopy(d['new_verdict'])
        record = review['packages'][id]
        record.update(rationale=d['reason'], confidence=d['confidence'],
                      rating_basis=v2.DEFINITIONS[d['new_removal']])
        record.setdefault('device_evidence', {})['runtime_audit_v4_2026_10_06'] = {
            'decision': '../2026-10-06/rerating-v4.json#' + id, **copy.deepcopy(d['evidence'])}
    assert set(packages) == set(verdicts) == set(review['packages'])
    assert all(packages[id]['verdict'] == verdicts[id] for id in packages)
    assert all(p['verdict']['action'] == ('Uninstall' if p['removal'] == 'Recommended' else 'Keep')
               for p in packages.values())
    system_ids = {d['id'] for d in system_rows}
    audit = review['audit']
    audit.update(
        rerating_date=AUDIT_DATE, rerating_version=4, source=SOURCE,
        removal_counts=v2.counted((p['removal'] for p in packages.values()), RATINGS),
        system_removal_counts=v2.counted((packages[id]['removal'] for id in system_ids), RATINGS),
        user_removal_counts=v2.counted((p['removal'] for id, p in packages.items() if id not in system_ids), RATINGS),
        verdict_counts=v2.counted((v['action'] for v in verdicts.values()), ('Uninstall', 'Keep')),
        confidence_counts=v2.counted((r['confidence'] for r in review['packages'].values()), ('high', 'medium', 'low')),
        low_confidence_packages=[id for id, r in review['packages'].items() if r['confidence'] == 'low'],
        v3_removal_counts=v3_counts,
        v4_changed_package_count=len(decisions),
        v4_rating_change_count=sum(d['old_removal'] != d['new_removal'] for d in decisions.values()),
        v4_runtime_audit='docs/package-audit/2026-10-06/audit-report.md',
        bundle_note='Since commit 90addc6 the app bundles the Android Debloat List; this audit is served separately by tools/debloat-server as the Moto Edge 2025 list.',
    )
    audit['limitations'] = audit['limitations'] + [
        'The 2026-10-06 runtime audit was read-only: no package was changed and the phone was not rebooted, so it verifies the state the owner had reached, not every feature path.',
        'Uninstall times are not recorded by Package Manager; "observed boots after last install" counts boots after the package was last (re)installed, not boots it is known to have been absent for.',
        'Mutable carrier overlays (SystemUI, setup, launcher) are enabled only at boot by com.motorola.coresettingsext; a copy restored with pm install-existing stays disabled until a reboot or cmd overlay enable.',
    ]
    return {OUTPUTS[0]: v2.encoded(packages), OUTPUTS[1]: v2.encoded(verdicts), OUTPUTS[2]: v2.encoded(review)}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--baseline-ref', default=v2.BASELINE_REF)
    parser.add_argument('--baseline-dir', type=pathlib.Path)
    parser.add_argument('--decisions', type=pathlib.Path, default=AUDIT_V4 / 'rerating-v4.json')
    parser.add_argument('--check', action='store_true', help='Check all outputs without writing')
    args = parser.parse_args()
    system_rows = v2.read(AUDIT / 'rerating-v2-system.json')
    texts = v2.build_outputs(v2.load_baseline(args.baseline_ref, args.baseline_dir),
                             v2.read(AUDIT / 'rerating-2026-10-02.json'), system_rows,
                             v2.read(AUDIT / 'rerating-v2-user.json'))
    texts = v3.apply_v3(texts, v2.read(AUDIT / 'rerating-v3.json'), system_rows)
    outputs = apply_v4(texts, v2.read(args.decisions), system_rows)
    assert apply_v4(texts, v2.read(args.decisions), system_rows) == outputs, 'v4 is not deterministic'
    for relative, text in outputs.items():
        json.loads(text, object_pairs_hook=v2.unique_object)
        if args.check:
            assert (ROOT / relative).read_text() == text, 'Output differs from decisions: ' + relative
    if not args.check:
        for relative, text in outputs.items():
            v2.replace(ROOT / relative, text)
    audit = json.loads(outputs[OUTPUTS[2]])['audit']
    print(v2.encoded({key: audit[key] for key in ('removal_counts', 'system_removal_counts', 'user_removal_counts',
                                                 'verdict_counts', 'confidence_counts', 'v4_changed_package_count',
                                                 'v4_rating_change_count')}).strip())


if __name__ == '__main__':
    main()
