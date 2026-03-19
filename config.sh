#!/bin/bash
set -euo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"

cd "$script_dir/pox_module"
sudo python setup.py develop
