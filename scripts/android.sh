#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

./gradlew installDebug
adb shell am start -n com.example.gtmonitor/.MainActivity
