#!/usr/bin/env bash
# Downloads the pinned speech model into model_pack/src/main/assets/model/ (gitignored) and verifies every
# file against scripts/model-manifest.txt. Run once before building an APK that can transcribe.
# This is a build step on your computer; the app itself never downloads anything.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
manifest="$root/scripts/model-manifest.txt"
target="$root/model_pack/src/main/assets/model"
mkdir -p "$target"

sha256() {
  if command -v sha256sum >/dev/null; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi
}

while read -r expected remote local; do
  [[ -z "$expected" || "$expected" == \#* ]] && continue
  file="$target/$local"
  if [[ -f "$file" && "$(sha256 "$file")" == "$expected" ]]; then
    echo "ok       $local"
    continue
  fi
  echo "fetching $local"
  curl --fail --location --retry 3 --silent --show-error -o "$file.part" "https://huggingface.co/$remote"
  actual="$(sha256 "$file.part")"
  if [[ "$actual" != "$expected" ]]; then
    rm -f "$file.part"
    echo "SHA-256 mismatch for $local: expected $expected, got $actual" >&2
    exit 1
  fi
  mv "$file.part" "$file"
  echo "verified $local"
done < "$manifest"
