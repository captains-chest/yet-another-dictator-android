#!/usr/bin/env bash
# Builds one installable APK (base + the model asset pack) from the release AAB, for sideload
# testing. Signs with the upload key when ANDROID_UPLOAD_KEYSTORE and its passwords are set
# (as in CI), otherwise with the local debug key.
#   scripts/fetch-model.sh && scripts/build-universal-apk.sh   →  build/dictator-universal.apk
set -euo pipefail
umask 077

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"
version="1.18.3"
sha="a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29"
jar="build/bundletool-$version.jar"
mkdir -p build
if [[ ! -f "$jar" ]] || ! echo "$sha  $jar" | sha256sum -c --status; then
  curl --fail --location --silent --show-error -o "$jar" \
    "https://github.com/google/bundletool/releases/download/$version/bundletool-all-$version.jar"
  echo "$sha  $jar" | sha256sum -c --status || { echo "bundletool SHA-256 mismatch" >&2; exit 1; }
fi

[[ -f model_pack/src/main/assets/model/ggml-small-q5_1.bin ]] || { echo "Run scripts/fetch-model.sh first." >&2; exit 1; }
./gradlew --quiet bundleRelease

signing=()
if [[ -n "${ANDROID_UPLOAD_KEYSTORE:-}" ]]; then
  # bundletool reads passwords from files; keep them private and delete them on exit.
  secrets="$(mktemp -d)"
  trap 'rm -rf "$secrets"' EXIT
  printf '%s' "$ANDROID_UPLOAD_KEYSTORE_PASSWORD" > "$secrets/ks"
  printf '%s' "$ANDROID_UPLOAD_KEY_PASSWORD" > "$secrets/key"
  signing=(--ks="$ANDROID_UPLOAD_KEYSTORE" --ks-pass="file:$secrets/ks"
    --ks-key-alias="$ANDROID_UPLOAD_KEY_ALIAS" --key-pass="file:$secrets/key")
fi
java -jar "$jar" build-apks --bundle=app/build/outputs/bundle/release/app-release.aab \
  --output=build/dictator.apks --mode=universal --overwrite "${signing[@]}"
unzip -o -q -d build build/dictator.apks universal.apk
mv build/universal.apk build/dictator-universal.apk
echo "build/dictator-universal.apk"
