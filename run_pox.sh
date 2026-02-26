#!/bin/bash
set -euo pipefail

if [[ ! -f ./pox/pox.py ]]; then
  echo "Missing ./pox/pox.py. Install POX first, then retry." >&2
  exit 1
fi

# Make local assignment modules importable without global installation.
export PYTHONPATH="$(pwd)/pox_module${PYTHONPATH:+:${PYTHONPATH}}"

if [[ -n "${POX_PYTHON:-}" ]]; then
  exec "${POX_PYTHON}" ./pox/pox.py cs640.ofhandler cs640.vnethandler
fi

for py in .venv/bin/python python3 python2.7 python2 python; do
  if command -v "${py}" >/dev/null 2>&1; then
    exec "${py}" ./pox/pox.py cs640.ofhandler cs640.vnethandler
  fi
done

echo "No Python interpreter found (tried .venv/bin/python/python3/python2.7/python2/python)." >&2
exit 1
