#!/bin/bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${REPO_ROOT}"

if [[ ! -d ./pox ]]; then
  echo "Missing ./pox directory. Clone POX first: git clone https://github.com/noxrepo/pox.git pox" >&2
  exit 1
fi

if [[ ! -e ./pox/pox/cs640 ]]; then
  ln -s ../../pox_module/cs640 ./pox/pox/cs640
fi

if [[ -n "${POX_PYTHON:-}" ]]; then
  PY="${POX_PYTHON}"
elif [[ -x .venv/bin/python ]]; then
  PY=".venv/bin/python"
else
  PY=""
  for py in python3 python2.7 python2 python; do
    if command -v "${py}" >/dev/null 2>&1; then
      PY="${py}"
      break
    fi
  done
fi

if [[ -z "${PY}" ]]; then
  echo "No Python interpreter found (tried .venv/bin/python/python3/python2.7/python2/python)." >&2
  exit 1
fi

if "${PY}" -m pip --version >/dev/null 2>&1; then
  exec "${PY}" -m pip install -e ./pox_module
fi

cd pox_module
exec "${PY}" setup.py develop
