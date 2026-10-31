#!/usr/bin/env bash
# Copies ./test-models onto the connected device/emulator for instrumented tests.
set -euo pipefail
DIR="${1:-test-models}"
adb shell mkdir -p /data/local/tmp/models
for f in "$DIR"/*; do
  adb push "$f" /data/local/tmp/models/ >/dev/null
  echo "pushed $(basename "$f")"
done
adb shell chmod -R a+rX /data/local/tmp/models
