# One-time repository administration

Committed workflows do not prove external settings or secrets exist. The GitHub connector
can publish code/PRs but does not expose administration/secret endpoints. Run the following
from an authorized maintainer session (Termux is suitable when gh, Python, keytool and git
are installed), or perform equivalent settings through an authenticated GitHub UI.

```sh
scripts/setup-github.sh AQSAMA/FAndroidTerminal
python3 scripts/setup-signing.py preview --backup-dir "$HOME/.local/share/fandroidterminal/signing"
python3 scripts/setup-signing.py production --backup-dir "$HOME/.local/share/fandroidterminal/signing"
```

The setup script configures squash merges, automatic branch deletion, read-only default
Actions token permissions, preview/production environments limited to main, PREVIEW_ENABLED,
and main protection requiring Foundation/Android checks, conversation resolution and PRs.
Zero required approvals allows a sole maintainer to merge their own reviewed PR. It does
not enable automatic merging. Avoid adding a self-review requirement that would deadlock
Production for a one-person repository.

Channel signing generates a different PKCS12/RSA key for each channel. It saves the key and
credentials to a private persistent directory outside this repo before upload. Preserve it.
If upload fails, the local backup survives; finish uploading from that backup instead of
regenerating. GitHub never returns stored secret values. The script refuses established
channel identities and existing backup names to prevent accidental rotation.

Each environment has these four secrets:
ANDROID_KEYSTORE_BASE64, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, ANDROID_KEY_PASSWORD.
Do not commit them. The only repository variable is PREVIEW_ENABLED=true (false pauses
signed Preview publishing). App IDs, dependency versions and ordinary build settings live
in source. Never add Production secrets at repository scope for convenience.

GitHub-hosted standard runners are the initial choice; no Blacksmith/Expo/Supabase accounts
or custom PAT are required for the application build. Signed Preview publication starts
once the bootstrap workflow exists on main and the preview secrets are actually present.
