#!/usr/bin/env python3
"""Reproduce the v3 publication: rebuild v2 from its pinned baseline, then apply rerating-v3.json.

v3 changes 82 of the 766 audited IDs after the October 2 phone re-audit: boot-tested removals,
packages the firmware refuses to uninstall, and new removals that pass every v2 structural guard.
Only read-only git show queries are used; no phone is needed.
"""
import argparse
import copy
import importlib.util
import json
import pathlib
import sys


sys.dont_write_bytecode = True
ROOT = pathlib.Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('rerating_v2', ROOT / 'scripts/apply-package-rerating.py')
v2 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v2)

AUDIT = v2.AUDIT
OUTPUTS = v2.OUTPUTS
RATINGS = v2.RATINGS
SOURCE = 'Cana Motorola Edge 2025 audit (2026-10-02 v3 phone re-audit)'
BASES = {'boot_test', 'removal_refused', 'structural_guard'}
# Packages com.motorola.setup references, with the traced call in rerating-v3.json evidence.
SETUP_QUERY_EXCEPTIONS = {'com.motorola.uxcore', 'com.motorola.spaces', 'com.motorola.demo'}


def guard_structural(d, system, record):
    """A new, untested removal must pass the same guard as every v2 Recommended system package."""
    evidence = copy.deepcopy(system['evidence'])
    if d['id'] in SETUP_QUERY_EXCEPTIONS:
        finding = d['evidence']['setup_query']
        assert finding['client'] == 'com.motorola.setup' and finding['finding'], d['id']
        clients = {x if isinstance(x, str) else x.get('client') for x in evidence['queried_by_core']}
        assert clients <= {'com.motorola.setup'}, d['id'] + ': queried by another core client'
        evidence['queried_by_core'] = []
    # v2's decision_basis confidence described its Keep decision; the guard judges this decision.
    evidence['decision_basis'] = dict(evidence['decision_basis'], removal_confidence=d['confidence'])
    record = dict(record, confidence=d['confidence'])
    v2.guard_system({'id': d['id'], 'evidence': evidence}, record)


def apply_v3(texts, rows, system_rows):
    packages = json.loads(texts[OUTPUTS[0]], object_pairs_hook=v2.unique_object)
    verdicts = json.loads(texts[OUTPUTS[1]], object_pairs_hook=v2.unique_object)
    review = json.loads(texts[OUTPUTS[2]], object_pairs_hook=v2.unique_object)
    systems = {d['id']: d for d in system_rows}
    decisions = {d['id']: d for d in rows}
    assert len(decisions) == len(rows), 'Duplicate v3 decision'
    v2_counts = review['audit']['removal_counts']
    for id, d in decisions.items():
        v2.validate_decision(d)
        assert d['old_removal'] == packages[id]['removal'], id + ': v2 rating mismatch'
        assert d['confidence'] in ('high', 'medium') and d['evidence']['basis'] in BASES, id
        lines = d['description'].splitlines()
        assert lines[1].startswith('Removing breaks: ') and d['description'].count('\nRemoving breaks: ') == 1, id
        assert v2.tracker_lines(d['description']) == v2.tracker_lines(packages[id]['description']), id
        basis = d['evidence']['basis']
        if basis == 'boot_test':
            assert d['new_removal'] == 'Recommended' and d['evidence']['observed_boots'] and d['evidence']['health'], id
        elif basis == 'removal_refused':
            assert d['new_removal'] != 'Recommended' and 'non-disable' in d['evidence']['result'], id
        else:
            assert d['new_removal'] == 'Recommended' and not d['evidence']['boot_tested'], id
            assert id in systems and d['evidence']['installed_user0'], id
            guard_structural(d, systems[id], review['packages'][id])
        packages[id].update(removal=d['new_removal'], description=d['description'],
                            verdict=copy.deepcopy(d['new_verdict']))
        verdicts[id] = copy.deepcopy(d['new_verdict'])
        record = review['packages'][id]
        record.update(rationale=d['reason'], confidence=d['confidence'],
                      rating_basis=v2.DEFINITIONS[d['new_removal']])
        record.setdefault('device_evidence', {})['rerating_v3_2026_10_02'] = {
            'decision': 'rerating-v3.json#' + id, **copy.deepcopy(d['evidence'])}
    assert set(packages) == set(verdicts) == set(review['packages'])
    assert all(packages[id]['verdict'] == verdicts[id] for id in packages)
    assert all(p['verdict']['action'] == ('Uninstall' if p['removal'] == 'Recommended' else 'Keep')
               for p in packages.values())
    spec = importlib.util.spec_from_file_location('audit_publisher', ROOT / 'scripts/build-package-audit.py')
    publisher = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(publisher)
    bundle = v2.deduplicate_reviewed(publisher.overlay_text(texts[OUTPUTS[-1]], packages), set(packages))
    audit = review['audit']
    system_ids = set(systems)
    audit.update(
        rerating_date='2026-10-02', rerating_version=3, source=SOURCE,
        removal_counts=v2.counted((p['removal'] for p in packages.values()), RATINGS),
        system_removal_counts=v2.counted((packages[id]['removal'] for id in system_ids), RATINGS),
        user_removal_counts=v2.counted((p['removal'] for id, p in packages.items() if id not in system_ids), RATINGS),
        verdict_counts=v2.counted((v['action'] for v in verdicts.values()), ('Uninstall', 'Keep')),
        confidence_counts=v2.counted((r['confidence'] for r in review['packages'].values()), ('high', 'medium', 'low')),
        low_confidence_packages=[id for id, r in review['packages'].items() if r['confidence'] == 'low'],
        v2_removal_counts=v2_counts,
        v3_changed_package_count=len(decisions),
        v3_basis_counts=v2.counted(d['evidence']['basis'] for d in decisions.values()),
        bundled_package_count_after=len(json.loads(bundle)),
    )
    audit['limitations'] = audit['limitations'] + [
        'v3 boot tests cover two reboots on 2026-10-02 (22:27 with no changes, 22:31 after 35 removals); they show those removals boot cleanly together, not that every feature works.',
        'The 21 new v3 removals pass the v2 structural guard but were not boot-tested; their descriptions say so.',
        'Gboard and Moto Installer are kept because the firmware refuses pm uninstall --user 0 ("package is non-disable").',
    ]
    return {OUTPUTS[0]: v2.encoded(packages), OUTPUTS[1]: v2.encoded(verdicts),
            OUTPUTS[2]: v2.encoded(review), OUTPUTS[3]: texts[OUTPUTS[3]], OUTPUTS[-1]: bundle}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline-ref', default=v2.BASELINE_REF)
    parser.add_argument('--baseline-dir', type=pathlib.Path)
    parser.add_argument('--decisions', type=pathlib.Path, default=AUDIT / 'rerating-v3.json')
    parser.add_argument('--check', action='store_true', help='Check all outputs without writing')
    args = parser.parse_args()
    system_rows = v2.read(AUDIT / 'rerating-v2-system.json')
    texts = v2.build_outputs(v2.load_baseline(args.baseline_ref, args.baseline_dir),
                             v2.read(AUDIT / 'rerating-2026-10-02.json'), system_rows,
                             v2.read(AUDIT / 'rerating-v2-user.json'))
    outputs = apply_v3(texts, v2.read(args.decisions), system_rows)
    assert apply_v3(texts, v2.read(args.decisions), system_rows) == outputs, 'v3 is not deterministic'
    for relative, text in outputs.items():
        json.loads(text, object_pairs_hook=None if relative == OUTPUTS[-1] else v2.unique_object)
        if args.check:
            assert (ROOT / relative).read_text() == text, 'Output differs from decisions: ' + relative
    if not args.check:
        for relative, text in outputs.items():
            v2.replace(ROOT / relative, text)
    audit = json.loads(outputs[OUTPUTS[2]])['audit']
    print(v2.encoded({key: audit[key] for key in ('removal_counts', 'system_removal_counts', 'user_removal_counts',
                                                 'verdict_counts', 'confidence_counts', 'v3_basis_counts')}).strip())


if __name__ == '__main__':
    main()
