#!/usr/bin/env python3
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess

root = Path(__file__).resolve().parents[1]
out = root / 'dist'
out.mkdir(exist_ok=True)
files = {}
for source, name in [('app/build/outputs/apk/preview/app-preview-unsigned.apk','unsigned-preview.apk'), ('app/build/outputs/apk/debug/app-debug.apk','bootstrap-debug.apk')]:
    shutil.copyfile(root / source, out / name)
    files[name] = hashlib.sha256((out / name).read_bytes()).hexdigest()
metadata = {
    'schema': 1, 'repository': os.environ['GITHUB_REPOSITORY'],
    'run_id': int(os.environ['GITHUB_RUN_ID']), 'run_attempt': int(os.environ['GITHUB_RUN_ATTEMPT']),
    'run_number': int(os.environ['GITHUB_RUN_NUMBER']), 'pr': int(os.getenv('BUILD_PR') or '0'),
    'commit': subprocess.check_output(['git','rev-parse','HEAD'], cwd=root, text=True).strip(),
    'version_code': 1000 + int(os.environ['GITHUB_RUN_NUMBER']), 'sha256': files,
}
(out / 'build.json').write_text(json.dumps(metadata, indent=2) + '\n')
