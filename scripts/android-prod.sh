#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

./gradlew installRelease
adb shell am start -n com.example.gtmonitor/.MainActivity
