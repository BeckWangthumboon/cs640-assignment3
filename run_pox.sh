#!/bin/bash
set -euo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"

cd "$script_dir"
python ./pox/pox.py cs640.ofhandler cs640.vnethandler
