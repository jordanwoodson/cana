import json
import pathlib
import subprocess
import tempfile
import os
import unittest

SOURCE = pathlib.Path(__file__).resolve().parent

def entry(description='Audited function.', **changes):
    return {'list': 'Oem', 'description': description, 'dependencies': [], 'neededBy': [],
            'labels': [], 'removal': 'Expert', 'source': 'Test-reviewed audit', **changes}

class OverlayMergeTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix='cana-overlay-test-')
        self.addCleanup(temporary.cleanup)
        self.root = pathlib.Path(temporary.name)
        fixture = self.root / 'fixture'
        fixture.mkdir(exist_ok=True)
        for name in ('aosp', 'carrier', 'google', 'misc', 'oem'):
            records = [{'id': 'android', 'description': 'Existing framework.', 'removal': 'unsafe'}] if name == 'aosp' else []
            (fixture / (name + '.json')).write_text(json.dumps(records))
        (self.root / 'fallback.json').write_text(json.dumps({'com.example.fallback': entry('Fallback entry.')}))
        (self.root / 'supplement.json').write_text(json.dumps([{'id': 'com.example.supplement', 'description': 'Supplement entry.', 'removal': 'delete'}]))
        (self.root / 'result.json').write_text('previous validated output')

    def run_merge(self, overlay=None, second=None):
        command = ['sudo', '-n', 'docker', 'run', '--rm', '--network=none', '--user', f'{os.getuid()}:{os.getgid()}',
                   '--mount', f'type=bind,source={self.root},target=/work',
                   '--mount', f'type=bind,source={SOURCE / "uad_lists.php"},target=/tool/uad_lists.php,readonly',
                   'php:8.4-cli-alpine', 'php', '/tool/uad_lists.php', '/work/fixture', '/work/result.json',
                   '--uadng', '/work/fallback.json', '--supplement', '/work/supplement.json']
        if overlay is not None:
            (self.root / 'overlay.json').write_text(json.dumps(overlay))
            command += ['--overlay', '/work/overlay.json']
        if second is not None:
            (self.root / 'second.json').write_text(json.dumps(second))
            command += ['--overlay', '/work/second.json']
        return subprocess.run(command, capture_output=True, text=True)

    def test_existing_merge_without_overlay_preserves_all_sources(self):
        result = self.run_merge()
        self.assertEqual(0, result.returncode, result.stderr)
        data = json.loads((self.root / 'result.json').read_text())
        self.assertEqual({'android', 'com.example.supplement', 'com.example.fallback'}, set(data))

    def test_generated_data_is_readable_by_server_worker(self):
        result = self.run_merge()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(0o644, (self.root / 'result.json').stat().st_mode & 0o777)

    def test_reviewed_overlay_overrides_every_source_and_adds_missing_packages(self):
        overlay = {key: entry('Reviewed ' + key, dependencies=['android'], labels=['verified-test'])
                   for key in ('android', 'com.example.supplement', 'com.example.fallback', 'com.example.new')}
        result = self.run_merge(overlay)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(overlay, json.loads((self.root / 'result.json').read_text()))

    def test_cana_verdict_is_kept_and_validated(self):
        verdict = {'action': 'Uninstall', 'benefits': ['privacy'], 'reason': 'Privacy: sends diagnostics.'}
        result = self.run_merge({'com.example.new': entry(verdict=verdict)})
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(verdict, json.loads((self.root / 'result.json').read_text())['com.example.new']['verdict'])
        for bad in ({'action': 'Disable', 'benefits': [], 'reason': 'x'}, {'action': 'Keep', 'benefits': ['speed'], 'reason': 'x'},
                    {'action': 'Keep', 'benefits': [], 'reason': ''}, 'Uninstall'):
            with self.subTest(verdict=bad):
                (self.root / 'result.json').write_text('previous validated output')
                self.assertNotEqual(0, self.run_merge({'com.bad': entry(verdict=bad)}).returncode)
                self.assertEqual('previous validated output', (self.root / 'result.json').read_text())

    def test_later_reviewed_overlay_wins_deterministically(self):
        first = {'android': entry('First review.')}
        second = {'android': entry('Later review.')}
        result = self.run_merge(first, second)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(second['android'], json.loads((self.root / 'result.json').read_text())['android'])

    def test_invalid_overlay_cannot_replace_previous_validated_output(self):
        malformed = [[], {'com.bad': entry('')}, {'com.bad': entry(removal='Delete')},
                     {'com.bad': entry(dependencies='android')}, {'not a package': entry()},
                     {'com.bad': entry(source='')}, {'com.bad': entry(neededBy=[42])}]
        for overlay in malformed:
            with self.subTest(overlay=overlay):
                (self.root / 'result.json').write_text('previous validated output')
                result = self.run_merge(overlay)
                self.assertNotEqual(0, result.returncode)
                self.assertEqual('previous validated output', (self.root / 'result.json').read_text())

    def test_browser_pages_use_reviewed_ids_labels_and_risks(self):
        reviewed = {'android': entry('Reviewed framework.', list='Aosp', removal='Unsafe'),
                    'com.example.new': entry('A newly researched app.', list='Misc', removal='Advanced',
                                             verdict={'action': 'Uninstall', 'benefits': [], 'reason': 'Dead weight here.'})}
        self.assertEqual(0, self.run_merge(reviewed).returncode)
        review = {'packages': {'com.example.new': {'apk_labels': ['New app'], 'confidence': 'high',
                    'features': ['example feature'], 'sources': [{'url': 'https://example.org/app', 'kind': 'primary'}]}}}
        (self.root / 'review.json').write_text(json.dumps(review))
        command = ['sudo', '-n', 'docker', 'run', '--rm', '--network=none', '--user', f'{os.getuid()}:{os.getgid()}',
                   '--mount', f'type=bind,source={self.root},target=/work',
                   '--mount', f'type=bind,source={SOURCE},target=/tool,readonly',
                   'php:8.4-cli-alpine', 'php', '/tool/apply_browser_lists.php', '/work/fixture',
                   '/work/result.json', '/work/review.json']
        result = subprocess.run(command, capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)
        categories = {name: json.loads((self.root / 'fixture' / (name + '.json')).read_text())
                      for name in ('aosp', 'carrier', 'google', 'misc', 'oem')}
        records = {item['id']: item for group in categories.values() for item in group}
        self.assertEqual(set(json.loads((self.root / 'result.json').read_text())), set(records))
        self.assertEqual('unsafe', records['android']['removal'])
        self.assertEqual('replace', records['com.example.new']['removal'])
        self.assertEqual('New app', records['com.example.new']['label'])
        self.assertIn('A newly researched app.', records['com.example.new']['description'])
        self.assertIn('example feature', records['com.example.new']['description'])
        self.assertIn('Test-reviewed audit', records['com.example.new']['description'])
        self.assertIn('Cana verdict: Uninstall. Dead weight here.', records['com.example.new']['description'])
        self.assertEqual(['https://example.org/app'], records['com.example.new']['web'])

if __name__ == '__main__':
    unittest.main()
