#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
selection=core
if [[ ${1:-} == --references ]]; then selection=${2:?Expected core, all or none}; shift 2; fi
[[ $# == 0 && $selection =~ ^(core|all|none)$ ]] || { echo 'Usage: scripts/bootstrap.sh [--references core|all|none]' >&2; exit 2; }
if [[ $selection != none ]]; then
  mapfile -t paths < <(python3 - "$selection" <<'PYTHON'
import json,sys
for item in json.load(open('references.lock.json'))['references']:
    if sys.argv[1]=='all' or item['core']: print(item['path'])
PYTHON
)
  git submodule sync -- "${paths[@]}"
  git submodule update --init --depth 1 --jobs 4 -- "${paths[@]}"
fi
python3 scripts/check_repository.py
printf 'Sources ready. JDK 17 and Android SDK requirements: docs/development.md\n'
