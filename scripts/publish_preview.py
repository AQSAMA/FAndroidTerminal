#!/usr/bin/env python3
"""Trusted-main-only signer. Never executes downloaded scripts or checks out PR code."""
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import urllib.error
import urllib.parse
import urllib.request
import zipfile

MARKER = '<!-- fandroid-preview -->'

class StripCrossHostAuth(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        result = super().redirect_request(req, fp, code, msg, headers, newurl)
        if result and urllib.parse.urlparse(req.full_url).netloc != urllib.parse.urlparse(newurl).netloc:
            result.remove_header('Authorization')
        return result

OPENER = urllib.request.build_opener(StripCrossHostAuth())

def request(url, *, data=None, method=None, binary=False):
    if not url.startswith(('https://api.github.com/', 'https://uploads.github.com/')):
        raise ValueError('Unsupported GitHub URL')
    headers = {'Authorization': 'Bearer ' + os.environ['GH_TOKEN'], 'Accept': 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28'}
    if data is not None:
        if isinstance(data, bytes): headers['Content-Type'] = 'application/vnd.android.package-archive'
        else: data = json.dumps(data).encode(); headers['Content-Type'] = 'application/json'
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    with OPENER.open(req, timeout=60) as response:
        body = response.read(210 * 1024 * 1024)
    return body if binary else (json.loads(body) if body else None)

def api(repo, suffix, **kwargs):
    return request(f'https://api.github.com/repos/{repo}/{suffix}', **kwargs)

def extract_artifact(data, directory):
    expected = {'unsigned-preview.apk', 'bootstrap-debug.apk', 'build.json'}
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        names = archive.namelist()
        if len(names) != 3 or set(names) != expected: raise ValueError('Unexpected artifact entries')
        if sum(i.file_size for i in archive.infolist()) > 200 * 1024 * 1024: raise ValueError('Artifact too large')
        for entry in archive.infolist():
            if entry.flag_bits & 1 or (entry.external_attr >> 16) & 0o170000 == 0o120000: raise ValueError('Unexpected artifact type')
            if entry.filename == 'build.json' and entry.file_size > 16384: raise ValueError('Metadata too large')
            (directory / entry.filename).write_bytes(archive.read(entry))
    meta = json.loads((directory / 'build.json').read_text())
    for name in expected - {'build.json'}:
        if hashlib.sha256((directory / name).read_bytes()).hexdigest() != meta['sha256'][name]: raise ValueError('Artifact digest mismatch')
    return meta

def validate_source(run, meta, pr, repo):
    if run['event'] != 'pull_request' or run['conclusion'] != 'success': raise ValueError('Not a successful PR run')
    if run['head_repository']['full_name'] != repo or pr['head']['repo']['full_name'] != repo: raise ValueError('Fork signing is disabled')
    if run['path'].split('@')[0] != '.github/workflows/ci.yml': raise ValueError('Unexpected workflow')
    if pr['state'] != 'open' or pr['number'] not in [p['number'] for p in run['pull_requests']]: raise ValueError('PR no longer open or unrelated')
    if meta['repository'] != repo or meta['run_id'] != run['id'] or meta['run_attempt'] != run['run_attempt']: raise ValueError('Run identity mismatch')
    if meta['commit'] != run['head_sha'] or meta['commit'] != pr['head']['sha']: raise ValueError('Stale or mismatched PR build')
    if not re.fullmatch('[a-f0-9]{40}', meta['commit']): raise ValueError('Invalid commit')
    if meta['version_code'] != 1000 + run['run_number']: raise ValueError('Unexpected version code')

def sign_apk(source, destination, temp, expected_package, version_code):
    tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/37.0.0'
    badging = subprocess.check_output([str(tools / 'aapt'), 'dump', 'badging', str(source)], text=True)
    if not re.search(r"package: name='" + re.escape(expected_package) + r"' versionCode='" + str(version_code) + "'", badging):
        raise ValueError('Unexpected APK identity/version')
    key = temp / 'signing.p12'
    key.write_bytes(base64.b64decode(os.environ['ANDROID_KEYSTORE_BASE64'], validate=True)); key.chmod(0o600)
    try:
        subprocess.run([str(tools / 'apksigner'), 'sign', '--ks', str(key), '--ks-key-alias', os.environ['ANDROID_KEY_ALIAS'],
                        '--ks-pass', 'env:ANDROID_KEYSTORE_PASSWORD', '--key-pass', 'env:ANDROID_KEY_PASSWORD',
                        '--out', str(destination), str(source)], check=True)
        subprocess.run([str(tools / 'apksigner'), 'verify', '--verbose', str(destination)], check=True)
    finally: key.unlink(missing_ok=True)

def main():
    repo = os.environ['GITHUB_REPOSITORY']
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repo): raise ValueError('Invalid repository')
    run_id = int(os.environ['SOURCE_RUN_ID'])
    run = api(repo, f'actions/runs/{run_id}')
    artifacts = api(repo, f'actions/runs/{run_id}/artifacts')['artifacts']
    matching = [a for a in artifacts if a['name'] == 'android-build' and not a['expired']]
    if not matching:
        print('No APK artifact (documentation-only or expired build); nothing to publish.'); return
    if len(matching) != 1: raise ValueError('Ambiguous build artifact')
    with tempfile.TemporaryDirectory(prefix='fandroid-publish-') as temporary:
        temp = Path(temporary)
        data = request(matching[0]['archive_download_url'], binary=True)
        meta = extract_artifact(data, temp)
        pr_number = int(meta['pr'])
        pr = api(repo, f'pulls/{pr_number}')
        validate_source(run, meta, pr, repo)
        name = f'FAndroidTerminal-pr-{pr_number}-build-{run["run_number"]}-{run["run_attempt"]}.apk'
        signed = temp / name
        sign_apk(temp / 'unsigned-preview.apk', signed, temp, 'io.github.aqsama.fandroidterminal.preview', meta['version_code'])
        # Recheck immediately before publication so a newer commit/closure is not silently replaced.
        validate_source(run, meta, api(repo, f'pulls/{pr_number}'), repo)
        tag = f'preview-pr-{pr_number}'
        body = f'Preview of PR #{pr_number}\n\nSource: `{meta["commit"]}`\nVersion code: {meta["version_code"]}\nBuild: https://github.com/{repo}/actions/runs/{run_id}\n\nSHA-256: `{hashlib.sha256(signed.read_bytes()).hexdigest()}`'
        try: release = api(repo, f'releases/tags/{tag}')
        except urllib.error.HTTPError as error:
            if error.code != 404: raise
            release = api(repo, 'releases', data={'tag_name': tag, 'target_commitish': meta['commit'], 'name': f'Preview PR #{pr_number}', 'body': body, 'prerelease': True, 'make_latest': 'false'}, method='POST')
        if not release['prerelease']: raise ValueError('Refusing to replace a non-preview release')
        previous_order = re.search(r'<!-- preview-order: (\d+):(\d+) -->', release.get('body') or '')
        if previous_order and tuple(map(int, previous_order.groups())) >= (run['run_number'], run['run_attempt']):
            print('An equal or newer Preview is already published.'); return
        body += f"\n\n<!-- preview-order: {run['run_number']}:{run['run_attempt']} -->"
        api(repo, f'git/refs/tags/{tag}', data={'sha': meta['commit'], 'force': True}, method='PATCH')
        existing = next((a for a in release['assets'] if a['name'] == name), None)
        if existing:
            api(repo, f'releases/assets/{existing["id"]}', method='DELETE')
        asset = request(release['upload_url'].split('{')[0] + '?name=' + urllib.parse.quote(name), data=signed.read_bytes(), method='POST')
        api(repo, f'releases/{release["id"]}', data={'body': body, 'prerelease': True, 'make_latest': 'false'}, method='PATCH')
        # Versioned assets avoid a stale earlier run overwriting a newer file name.
        for old in release['assets']:
            if old['name'].startswith('FAndroidTerminal-pr-') and old['id'] != (existing or {}).get('id'):
                api(repo, f'releases/assets/{old["id"]}', method='DELETE')
        comment = f'{MARKER}\n[Download Preview APK]({asset["browser_download_url"]})\n\nBuild {run["run_number"]} · source `{meta["commit"][:12]}` · [checks](https://github.com/{repo}/actions/runs/{run_id})\n\nInstalls alongside Production, Dev and Termux. Requires the same Preview signing identity for updates.'
        comments = api(repo, f'issues/{pr_number}/comments?per_page=100')
        previous = next((c for c in comments if c['user']['login'] == 'github-actions[bot]' and MARKER in c['body']), None)
        if previous: api(repo, f'issues/comments/{previous["id"]}', data={'body': comment}, method='PATCH')
        else: api(repo, f'issues/{pr_number}/comments', data={'body': comment}, method='POST')
        print(f'Published Preview for PR #{pr_number}: {meta["commit"][:12]}')

if __name__ == '__main__': main()
