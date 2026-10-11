#!/usr/bin/env bash
# Downloads the pinned speech model into build/model/<model-id>/ and verifies every file against
# scripts/model-manifest.txt, the same manifest the app ships and checks (ADR 0006). For local and
# device testing only: the model is never bundled; the app downloads it after install.
# Usage: scripts/fetch-model.sh [model-id]   (default: every model in the manifest)
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
manifest="$root/scripts/model-manifest.txt"
only="${1:-}"

sha256() {
  if command -v sha256sum >/dev/null; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi
}

while read -r id expected _bytes remote local; do
  [[ -z "$id" || "$id" == \#* ]] && continue
  [[ -n "$only" && "$id" != "$only" ]] && continue
  case "$local" in /* | *..*) echo "refusing unsafe path: $local" >&2; exit 1 ;; esac
  file="$root/build/model/$id/$local"
  mkdir -p "$(dirname "$file")"
  if [[ -f "$file" && "$(sha256 "$file")" == "$expected" ]]; then
    echo "ok       $id/$local"
    continue
  fi
  echo "fetching $id/$local"
  curl --proto '=https' --fail --location --retry 3 --continue-at - --silent --show-error -o "$file.part" "https://huggingface.co/$remote"
  actual="$(sha256 "$file.part")"
  if [[ "$actual" != "$expected" ]]; then
    rm -f "$file.part"
    echo "SHA-256 mismatch for $local: expected $expected, got $actual" >&2
    exit 1
  fi
  mv "$file.part" "$file"
  echo "verified $id/$local"
done < "$manifest"
