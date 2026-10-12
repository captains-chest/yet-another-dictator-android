# 6. Download the model after install, with the user's consent

- Status: Accepted
- Date: 2026-10-11
- Supersedes: [ADR 0004](0004-bundled-small-model.md)

## Context

ADR 0004 bundled Whisper Small (190 MB) in an install-time asset pack and kept the app free of any
network permission. That makes every install and update carry the model, needs a separate path for
sideloading, and doesn't scale to more or larger models (for example a Dansk-only model). The macOS
and iOS apps are moving to the same approach.

## Decision

- The model is **not bundled**. The `model_pack` asset pack is removed; Play and sideload use the same
  APK and the same download path.
- `scripts/model-manifest.txt` (`<model-id> <sha256> <bytes> <huggingface-path> <local-path>`) is
  copied into the APK's assets at build time. **It is the trust anchor and is never fetched from the
  network.** Files are pinned to immutable Hugging Face commits.
- The container app shows the model name and size, recommends Wi-Fi, and downloads only when the user
  taps **Download**. By default the download waits for an unmetered (Wi-Fi) network; mobile data is
  used only if the user ticks "Also download over mobile data".
- `ModelDownloadWorker` (WorkManager) downloads into app-private `filesDir/models/<model-id>/`
  (backups are already off). It resumes with HTTP Range requests, verifies each file's SHA-256 before
  moving it into place, retries with exponential backoff, and writes a `.complete` marker last.
  Models no longer in the manifest are deleted.
- The app gains `INTERNET` and `ACCESS_NETWORK_STATE`, used only by that worker. The worker is the
  only network code. Audio and text never leave the device.
- The model-id column lets a Dansk-only model be added later as extra manifest lines.

## Consequences

- Privacy wording changes everywhere to: "Audio never leaves the device; the one-time model download
  is the only network use." The Play listing and Data safety answers are updated (no user data is
  transmitted, so "No data collected" still holds).
- Dictation does not work until the model is downloaded; the button says so and points to the app.
- The APK is about 190 MB smaller; updates no longer re-deliver the model.
- Long downloads may be stopped by WorkManager's execution limit; resume and retry continue them.
- On-device tests either download in the app or use `scripts/fetch-model.sh` + `scripts/push-model.sh`.
