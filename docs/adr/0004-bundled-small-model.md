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
- `scripts/fetch-model.sh` downloads and verifies it into `model_pack/src/main/assets/model/`
  (gitignored) at build time, mirroring the iOS `scripts/fetch-model.sh`.
- The model is an **install-time Play Asset Delivery pack** (`model_pack`). Play installs it
  together with the app, before first launch, and the app reads it through the normal
  `AssetManager`, uncompressed and in place. No download code, no network permission.
- For sideloading, `scripts/build-universal-apk.sh` turns the AAB into one universal APK that
  contains the pack.
- A build without the model still works and says clearly that it can't transcribe (like iOS).
- CI checks that the AAB's base module stays far under Play's 200 MB limit (it is about 5 MB).

## Consequences

- Play's install is about 195 MB (base ~5 MB + 190 MB pack); install-time packs may be up to
  1 GB in total, so a larger model would still fit.
- `assembleDebug` APKs have no model; test dictation with the universal APK.
- Peak memory, latency and Danish/English quality on a mid-range phone are still to be measured
  before considering a larger model (e.g. Large-v3 Turbo q5) or a smaller one for low-end devices.
