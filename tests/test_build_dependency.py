import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'scripts/fetch_build_dependency.py'
spec = importlib.util.spec_from_file_location('fetch_build_dependency', SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class BuildDependencyTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.upstream = self.root / 'upstream'
        self.upstream.mkdir()
        self.git(self.upstream, 'init', '-q')
        self.git(self.upstream, 'config', 'user.email', 'fixture@example.invalid')
        self.git(self.upstream, 'config', 'user.name', 'Fixture')
        (self.upstream / 'source').write_text('first\n')
        self.git(self.upstream, 'add', 'source')
        self.git(self.upstream, 'commit', '-qm', 'first')
        self.revision = self.git(self.upstream, 'rev-parse', 'HEAD')
        (self.upstream / 'source').write_text('second\n')
        self.git(self.upstream, 'commit', '-qam', 'second')
        self.destination = self.root / 'third_party/dependency'
        self.source = {'name': 'fixture', 'kind': 'build-dependency', 'path': 'third_party/dependency',
                       'url': self.upstream.as_uri(), 'commit': self.revision}
        (self.root / 'dependencies.lock.json').write_text(json.dumps({'sources': [self.source]}))

    def git(self, directory, *args):
        return subprocess.check_output(['git', *args], cwd=directory, text=True, stderr=subprocess.DEVNULL).strip()

    def test_fresh_fetch_uses_pinned_commit_with_one_commit_history_and_is_repeatable(self):
        module.fetch(self.root)
        self.assertEqual(self.git(self.destination, 'rev-parse', 'HEAD'), self.revision)
        self.assertEqual(self.git(self.destination, 'rev-list', '--count', 'HEAD'), '1')
        self.assertEqual((self.destination / 'source').read_text(), 'first\n')
        module.fetch(self.root)

    def test_interrupted_initialization_can_resume(self):
        self.destination.mkdir(parents=True)
        self.git(self.destination, 'init', '-q')
        self.git(self.destination, 'remote', 'add', 'origin', self.source['url'])
        module.fetch(self.root)
        self.assertEqual(self.git(self.destination, 'rev-parse', 'HEAD'), self.revision)

    def test_local_edits_are_preserved(self):
        module.fetch(self.root)
        source = self.destination / 'source'
        source.write_text('local work\n')
        with self.assertRaisesRegex(SystemExit, 'Preserve local dependency edits'):
            module.fetch(self.root)
        self.assertEqual(source.read_text(), 'local work\n')

    def test_unrelated_directory_is_preserved(self):
        self.destination.mkdir(parents=True)
        (self.destination / 'notes').write_text('local notes\n')
        with self.assertRaisesRegex(SystemExit, 'nonempty dependency directory'):
            module.fetch(self.root)
        self.assertEqual((self.destination / 'notes').read_text(), 'local notes\n')

    def test_wrong_origin_is_rejected(self):
        module.fetch(self.root)
        self.git(self.destination, 'remote', 'set-url', 'origin', 'https://example.invalid/other.git')
        with self.assertRaisesRegex(SystemExit, 'Unexpected dependency origin'):
            module.fetch(self.root)
