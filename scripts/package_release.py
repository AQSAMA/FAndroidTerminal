#!/usr/bin/env python3
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess

out = Path('dist'); out.mkdir(exist_ok=True)
apk = out / 'production-unsigned.apk'
shutil.copyfile('app/build/outputs/apk/release/app-release-unsigned.apk', apk)
metadata = {'repository': os.environ['GITHUB_REPOSITORY'], 'run_id': int(os.environ['GITHUB_RUN_ID']),
            'commit': subprocess.check_output(['git','rev-parse','HEAD'], text=True).strip(),
            'version_code': int(os.environ['VERSION_CODE']), 'version_name': os.environ['VERSION_NAME'],
            'sha256': hashlib.sha256(apk.read_bytes()).hexdigest()}
(out / 'release.json').write_text(json.dumps(metadata, indent=2)+'\n')
