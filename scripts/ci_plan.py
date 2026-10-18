#!/usr/bin/env python3
import os
import pathlib
import subprocess
import sys

base = sys.argv[1] if len(sys.argv) > 1 else ''
if base:
    files = subprocess.check_output(['git', 'diff', '--name-only', base, 'HEAD'], text=True).splitlines()
    documentation_only = all(p.startswith(('docs/', 'references/')) or p in {'README.md', 'AGENTS.md', 'references.lock.json', '.gitmodules'} for p in files)
else: documentation_only = False
build = not documentation_only or os.getenv('FORCE_APK') == 'true'
with open(os.environ['GITHUB_OUTPUT'], 'a') as output: output.write(f'build={str(build).lower()}\n')
print('Android build requested' if build else 'Documentation/reference-only change; Android build skipped')
