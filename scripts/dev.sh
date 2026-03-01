#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

bash scripts/clean.sh
bash scripts/build.sh
bash scripts/android.sh
