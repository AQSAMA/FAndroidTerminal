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
    lock = json.loads((ROOT / 'references.lock.json').read_text())
    index = subprocess.check_output(['git', 'ls-files', '--stage'], cwd=ROOT, text=True)
    gitlinks = {line.split('\t', 1)[1]: line.split()[1] for line in index.splitlines() if line.startswith('160000 ')}
    for item in lock['references']:
        if not re.fullmatch(r'[a-f0-9]{40}', item['commit']): errors.append(f"Invalid commit: {item['name']}")
        if gitlinks.get(item['path']) != item['commit']: errors.append(f"Gitlink/lock mismatch: {item['path']}")
        url = subprocess.check_output(['git', 'config', '-f', '.gitmodules', '--get', f"submodule.{item['path']}.url"], cwd=ROOT, text=True).strip()
        if url != item['url']: errors.append(f"Submodule URL mismatch: {item['path']}")
        checkout = ROOT / item['path']
        if (checkout / '.git').exists():
            actual = subprocess.check_output(['git', '-C', str(checkout), 'rev-parse', 'HEAD'], text=True).strip()
            if actual != item['commit']: errors.append(f"Checkout revision mismatch: {item['path']}")
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
        if 'pull_request_target' in text: errors.append(f"Unexpected privileged PR trigger: {path.name}")
    notices = (ROOT / 'THIRD_PARTY_NOTICES.md').read_text()
    asset = (ROOT / 'app/src/main/assets/open-source-notices.txt').read_text()
    if not asset.startswith(notices): errors.append('App notice asset is stale')
    if errors: raise SystemExit('\n'.join(errors))
    print(f"PASS: {len(lock['references'])} source pins, hygiene, scripts, Actions pins and notices")

if __name__ == '__main__': check()
