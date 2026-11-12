#!/usr/bin/env python3
"""Fast source/pin/notice checks; does not claim to compile Kotlin or run Android."""
import ast
import json
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]

def check():
    errors = []
    lock = json.loads((ROOT / 'dependencies.lock.json').read_text())
    index = subprocess.check_output(['git', 'ls-files', '--stage'], cwd=ROOT, text=True)
    if any(line.startswith('160000 ') for line in index.splitlines()):
        errors.append('Tracked source submodules remain')
    tracked = [line.split('\t', 1)[1] for line in index.splitlines()]
    if any(name.startswith(('references/', 'third_party/termlib/')) for name in tracked):
        errors.append('Local source checkouts must remain untracked')
    for item in lock['sources']:
        if not re.fullmatch(r'[a-f0-9]{40}', item['commit']): errors.append(f"Invalid commit: {item['name']}")
        if item['kind'] == 'build-dependency':
            checkout = ROOT / item['path']
            if (checkout / '.git').exists():
                actual = subprocess.check_output(['git', '-C', str(checkout), 'rev-parse', 'HEAD'], text=True).strip()
                if actual != item['commit']: errors.append(f"Checkout revision mismatch: {item['path']}")
        elif not all((ROOT / name).is_file() for name in item['files']):
            errors.append(f"Adapted source missing: {item['name']}")
    for directory in ['references/', 'third_party/termlib/']:
        ignored = subprocess.run(['git', 'check-ignore', '-q', directory], cwd=ROOT)
        if ignored.returncode != 0: errors.append(f"Source directory is not ignored: {directory}")
    files = subprocess.check_output(['git', 'ls-files', '-co', '--exclude-standard'], cwd=ROOT, text=True).splitlines()
    for name in set(files):
        path = ROOT / name
        if not path.is_file() or name.startswith('references/'): continue
        if path.suffix.lower() in {'.jks', '.keystore', '.p12', '.apk'}: errors.append(f"Forbidden generated/private file: {name}")
        if path.suffix in {'.kt', '.kts', '.java', '.c', '.py', '.sh', '.md', '.yml', '.toml', '.json', '.xml'}:
            text = path.read_text()
            if text and not text.endswith('\n'): errors.append(f"Missing final newline: {name}")
            if any(line.rstrip() != line for line in text.splitlines()): errors.append(f"Trailing whitespace: {name}")
        if path.suffix == '.py': ast.parse(path.read_text(), filename=name)
        if path.suffix == '.sh': subprocess.run(['bash', '-n', str(path)], check=True)
    for path in (ROOT / '.github/workflows').glob('*.yml'):
        text = path.read_text()
        for match in re.finditer(r'uses:\s*([^\s#]+)', text):
            action = match.group(1)
            if not action.startswith('./') and not re.search(r'@[a-f0-9]{40}$', action): errors.append(f"Unpinned action in {path.name}: {action}")
        if 'pull_request_target' in text and (path.name != 'preview-cleanup.yml' or 'checkout@' in text): errors.append(f"Unsafe privileged PR trigger: {path.name}")
    notices = (ROOT / 'THIRD_PARTY_NOTICES.md').read_text()
    asset = (ROOT / 'app/src/main/assets/open-source-notices.txt').read_text()
    if not asset.startswith(notices): errors.append('App notice asset is stale')
    if errors: raise SystemExit('\n'.join(errors))
    print(f"PASS: {len(lock['sources'])} source pins, hygiene, scripts, Actions pins and notices")

if __name__ == '__main__': check()
