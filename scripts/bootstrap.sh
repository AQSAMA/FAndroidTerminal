#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
[[ $# == 0 ]] || { echo 'Usage: scripts/bootstrap.sh' >&2; exit 2; }
python3 scripts/fetch_build_dependency.py
python3 scripts/check_repository.py
printf 'Build dependency ready. JDK 17 and Android SDK requirements: docs/development.md\n'
