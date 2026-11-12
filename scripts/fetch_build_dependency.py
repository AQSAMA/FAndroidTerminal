#!/usr/bin/env python3
"""Fetch the pinned source build input without checking study repositories into Git."""
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def git(*args, cwd):
    return subprocess.check_output(['git', *args], cwd=cwd, text=True).strip()


def fetch(root=ROOT):
    lock = json.loads((root / 'dependencies.lock.json').read_text())
    for source in lock['sources']:
        if source['kind'] != 'build-dependency':
            continue
        destination = root / source['path']
        revision = source['commit']
        if destination.is_symlink():
            raise SystemExit(f'Refusing symlink checkout: {destination}')
        if (destination / '.git').exists():
            if git('remote', 'get-url', 'origin', cwd=destination) != source['url']:
                raise SystemExit(f'Unexpected dependency origin: {destination}')
            if git('status', '--porcelain', '--untracked-files=normal', cwd=destination):
                raise SystemExit(f'Preserve local dependency edits before fetching: {destination}')
            head = subprocess.run(['git', 'rev-parse', '--verify', 'HEAD'], cwd=destination, text=True, capture_output=True)
            if head.returncode == 0 and head.stdout.strip() == revision:
                print(f"Ready: {source['name']} at {revision}")
                continue
        else:
            if destination.exists() and any(destination.iterdir()):
                raise SystemExit(f'Refusing nonempty dependency directory: {destination}')
            destination.mkdir(parents=True, exist_ok=True)
            git('init', '--quiet', cwd=destination)
            git('remote', 'add', 'origin', source['url'], cwd=destination)
        git('fetch', '--no-tags', '--depth', '1', 'origin', revision, cwd=destination)
        git('checkout', '--detach', revision, cwd=destination)
        if git('rev-parse', 'HEAD', cwd=destination) != revision:
            raise SystemExit(f'Dependency revision mismatch: {destination}')
        print(f"Fetched: {source['name']} at {revision}")


if __name__ == '__main__':
    fetch()
