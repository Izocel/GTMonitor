#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

bash scripts/clean.sh
bash scripts/build.sh
bash scripts/assemble.sh
bash scripts/assemble-prod.sh
bash scripts/bundle.sh
bash scripts/bundle-prod.sh
