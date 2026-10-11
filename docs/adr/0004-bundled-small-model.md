# 4. Whisper Small (q5_1) is bundled in the APK; the app never downloads

- Status: Accepted
- Date: 2026-10-10

## Context

The macOS and iOS apps bundle the model and never let WhisperKit download (iOS ADR 0003). The same
rule keeps the Android app free of any network code: the app has **no INTERNET permission at all**.
Danish needs a multilingual model; Base and Tiny are noticeably worse at Danish than Small.

## Decision

- Bundle `ggml-small-q5_1.bin` (Whisper Small, multilingual, 5-bit quantised, 190 MB), the same
  model size class as iOS, pinned to an immutable Hugging Face commit and verified by SHA-256 in
  `scripts/model-manifest.txt`.
- `scripts/fetch-model.sh` downloads and verifies it into `app/src/main/assets/model/`
  (gitignored) at build time, mirroring the iOS `scripts/fetch-model.sh`. The model is stored
  uncompressed in the APK and read in place.
- A build without the model still works and says clearly that it can't transcribe (like iOS).
- CI's release APK is built with the model, so it's installable and works; debug APKs are not.

## Consequences

- The APK is about 195 MB. That is fine for sideloading. Google Play limits the base APK/AAB
  download to 200 MB, so publishing on Play will need the model in an **install-time asset pack**
  (Play Asset Delivery). That still installs with the app and needs no network code of our own.
- Peak memory, latency and Danish/English quality on a mid-range phone are still to be measured
  before considering a larger model (e.g. Large-v3 Turbo q5) or a smaller one for low-end devices.
