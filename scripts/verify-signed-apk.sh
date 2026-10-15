#!/usr/bin/env bash
# Checks that release APKs are properly signed (APK Signature Scheme v2 or v3; minSdk 31 gets v3 only) and not with an
# Android debug key, then writes <file>.sha256 next to each given file (APKs and AABs).
#   scripts/verify-signed-apk.sh dist/*.apk dist/*.aab
set -euo pipefail
trap 'echo "verify-signed-apk.sh: failed at line $LINENO: $BASH_COMMAND" >&2' ERR

apksigner="$ANDROID_HOME/build-tools/$(ls "$ANDROID_HOME/build-tools" | sort -V | tail -1)/apksigner"
for file in "$@"; do
  if [[ "$file" == *.apk ]]; then
    out="$("$apksigner" verify --verbose --print-certs "$file")"
    grep -qE "Verified using v(2|3) scheme \(APK Signature Scheme v(2|3)\): true" <<<"$out" \
      || { echo "$file: not signed with APK Signature Scheme v2 or v3" >&2; exit 1; }
    if grep -q "CN=Android Debug" <<<"$out"; then
      echo "$file: signed with an Android debug key, not the upload key" >&2
      exit 1
    fi
    grep -E "Signer #1 certificate (DN|SHA-256 digest)" <<<"$out"
  fi
  (cd "$(dirname "$file")" && sha256sum "$(basename "$file")" > "$(basename "$file").sha256")
  cat "$file.sha256"
done
