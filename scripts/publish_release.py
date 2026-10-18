#!/usr/bin/env python3
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import tempfile
import urllib.error
import urllib.parse
from publish_preview import api, request, sign_apk

folder = Path(sys.argv[1])
metadata = json.loads((folder / 'release.json').read_text())
source = folder / 'production-unsigned.apk'
repo = os.environ['GITHUB_REPOSITORY']
version = os.environ['VERSION_NAME']
code = int(os.environ['VERSION_CODE'])
if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9.-]+)?', version): raise SystemExit('Invalid version')
if not 0 < code <= 2_100_000_000: raise SystemExit('Invalid version code')
if metadata != {'repository': repo, 'run_id': int(os.environ['GITHUB_RUN_ID']), 'commit': os.environ['GITHUB_SHA'], 'version_code': code, 'version_name': version, 'sha256': hashlib.sha256(source.read_bytes()).hexdigest()}:
    raise SystemExit('Production artifact identity mismatch')
for name in ['ANDROID_KEYSTORE_BASE64','ANDROID_KEYSTORE_PASSWORD','ANDROID_KEY_ALIAS','ANDROID_KEY_PASSWORD']:
    if not os.getenv(name): raise SystemExit(f'Missing Production signing configuration: {name}; see docs/github-setup.md')
out = Path('dist'); out.mkdir(exist_ok=True)
signed = out / f'FAndroidTerminal-{version}.apk'
with tempfile.TemporaryDirectory(prefix='fandroid-production-') as temp:
    sign_apk(source, signed, Path(temp), 'io.github.aqsama.fandroidterminal', code)
metadata['sha256'] = hashlib.sha256(signed.read_bytes()).hexdigest()
(out / 'release.json').write_text(json.dumps(metadata, indent=2)+'\n')
if os.environ.get('PUBLISH_RELEASE') == 'true':
    tag = 'v' + version
    try: api(repo, 'releases/tags/' + tag)
    except urllib.error.HTTPError as error:
        if error.code != 404: raise
    else: raise SystemExit('Refusing to overwrite an existing Production release')
    page = 1
    while True:
        previous = api(repo, f'releases?per_page=100&page={page}')
        for release in previous:
            if release['prerelease'] or release['draft']: continue
            match = re.search(r'<!-- version-code: (\d+) -->', release.get('body') or '')
            if not match: raise SystemExit('Existing Production release has no version-code record; inspect it before publishing')
            if int(match.group(1)) >= code: raise SystemExit('Production version code would not increase')
        if len(previous) < 100: break
        page += 1
    body = f'Source: `{metadata["commit"]}`\n\nSHA-256: `{metadata["sha256"]}`\n\n<!-- version-code: {code} -->'
    release = api(repo, 'releases', data={'tag_name':tag,'target_commitish':metadata['commit'],'name':version,'body':body,'draft':True,'prerelease':False}, method='POST')
    request(release['upload_url'].split('{')[0]+'?name='+urllib.parse.quote(signed.name), data=signed.read_bytes(), method='POST')
    api(repo, f'releases/{release["id"]}', data={'draft':False}, method='PATCH')
    print('Production release published:', tag)
else: print('Signed artifact only; no Production release published.')
