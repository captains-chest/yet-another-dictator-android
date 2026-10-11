#!/usr/bin/env bash
# Copies a model fetched by scripts/fetch-model.sh into a debuggable build on a connected device, so
# on-device tests don't need the in-app download. Uses `adb` and `run-as` (debug builds only).
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
id="${1:-ggml-small-q5_1}"
pkg="com.captainduckay.dictator.android"
src="$root/build/model/$id"
[[ -d "$src" ]] || { echo "Run scripts/fetch-model.sh $id first." >&2; exit 1; }

adb shell "run-as $pkg mkdir -p files/models/$id"
for file in "$src"/*; do
  name="$(basename "$file")"
  adb push "$file" "/data/local/tmp/$name" >/dev/null
  adb shell "run-as $pkg cp /data/local/tmp/$name files/models/$id/$name && rm /data/local/tmp/$name"
done
adb shell "run-as $pkg sh -c 'echo $id > files/models/$id/.complete'"
echo "✓ $id installed on the device"
