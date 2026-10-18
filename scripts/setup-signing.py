#!/usr/bin/env python3
"""Generate locally backed-up channel keys and upload with an authorized GitHub CLI."""
import argparse
import base64
import json
import os
from pathlib import Path
import secrets
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('channel', choices=['preview', 'production'])
parser.add_argument('--repo', default='AQSAMA/FAndroidTerminal')
parser.add_argument('--backup-dir', type=Path, required=True, help='Persistent private directory outside this repository')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
backup = args.backup_dir.expanduser().resolve()
if backup == root or root in backup.parents: raise SystemExit('Choose a backup directory outside the repository')
subprocess.run(['gh','auth','status'], check=True)
# Refuse overwriting an established channel identity, even if a local backup is missing.
known = json.loads(subprocess.check_output(['gh','secret','list','--repo',args.repo,'--env',args.channel,'--json','name'], text=True))
if any(s['name']=='ANDROID_KEYSTORE_BASE64' for s in known): raise SystemExit('Signing identity already configured; refusing rotation')
backup.mkdir(parents=True, exist_ok=True); backup.chmod(0o700)
key = backup / f'{args.channel}.p12'
credentials = backup / f'{args.channel}.credentials.json'
if key.exists() or credentials.exists(): raise SystemExit('Backup already exists. Reuse it manually; do not regenerate an identity.')
password = secrets.token_urlsafe(48)
values = {'ANDROID_KEYSTORE_PASSWORD': password, 'ANDROID_KEY_PASSWORD': password, 'ANDROID_KEY_ALIAS': f'fandroidterminal-{args.channel}'}
env = os.environ.copy(); env['KEYSTORE_PASSWORD'] = password
subprocess.run(['keytool','-genkeypair','-noprompt','-storetype','PKCS12','-keystore',str(key),'-alias',values['ANDROID_KEY_ALIAS'],'-keyalg','RSA','-keysize','3072','-validity','10000','-dname',f'CN=FAndroidTerminal {args.channel}, OU=Android, O=AQSAMA','-storepass:env','KEYSTORE_PASSWORD','-keypass:env','KEYSTORE_PASSWORD'], env=env, check=True)
key.chmod(0o600)
credentials.write_text(json.dumps(values, indent=2)+'\n'); credentials.chmod(0o600)
values['ANDROID_KEYSTORE_BASE64'] = base64.b64encode(key.read_bytes()).decode()
for name, value in values.items():
    subprocess.run(['gh','secret','set',name,'--repo',args.repo,'--env',args.channel], input=value, text=True, check=True)
print(f'{args.channel} signing uploaded. Private backup: {backup}. Preserve this directory; GitHub cannot return the key.')
