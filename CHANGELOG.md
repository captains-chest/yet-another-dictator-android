# Changelog

All notable changes are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- A second speech model: NVIDIA Parakeet TDT 0.6B v2, English only and the most accurate (about
  661 MB). Whisper Small stays available, and is the one to use for Dansk.
- On first launch Dictator asks which model to use, best first, with the size, accuracy and speed of
  each. It downloads nothing until you choose and tap Download, and only the model you chose, still
  over Wi-Fi unless you allow mobile data. A Whisper Small download from before stays in use without
  asking. Switch models or delete one you no longer use under Models.

### Changed
- Releases attach a `.sha256` next to the signed APK and AAB. The Release workflow checks the APK
  signature with `apksigner` and refuses a debug key, and stops early if a signing secret is missing.
- Dictation is one floating microphone. It appears when a text field is focused, and it is hidden
  otherwise. Tap to start and stop, hold to talk, drag to move it. Your own keyboard stays, and
  text is typed into the focused field. There is no second shortcut that turns dictation on and
  off. The Dictator keyboard is gone, which also removes the error some apps showed when switching
  away from it (ADR 0007, supersedes the keyboard interaction in ADR 0001).
- Transcription uses the length of the clip instead of a full 30-second Whisper window, and runs on
  the phone's fast cores, so a short dictation finishes sooner. Same model.
- The speech model is no longer bundled. After install, the app shows its size (about 190 MB) and
  downloads it only when you tap Download, over Wi-Fi unless you allow mobile data. The download
  resumes, retries and checks every file against a SHA-256 manifest built into the app (ADR 0006,
  supersedes ADR 0004). Audio never leaves the device; the model download is the only network use,
  so the app now has the `INTERNET` permission.
- Third-party notices: OpenAI's Whisper weights are MIT licensed, not Apache 2.0.

### Added
- Undo Last Dictation on the floating microphone (parity with Mac and iOS). Shown after a
  successful insert while Ready; hidden after typing elsewhere or when Try again / Discard are up.
- `scripts/build-universal-apk.sh` builds one APK for sideloading.
- Release workflow: on a `v*` tag, a signed AAB and a signed universal APK on a GitHub release,
  and a draft upload to Play's internal track once a Play service account is configured.
- Play store listing, Data safety and content rating answers in `docs/play-store.md`.

## [0.1.0] - 2026-10-10

### Added
- Dictator keyboard (an Android input method) that records, transcribes on the phone with
  whisper.cpp and Whisper Small, and types the text into the focused field (ADR 0001, 0002).
- Tap to toggle, hold to talk, same as the macOS and iOS apps.
- Setup app with microphone, keyboard, language (Automatic, Dansk, English) and model sections, a
  field to try the keyboard, What's New and license notices. English and Danish.
- `core` module: a Kotlin port of DictationCore with its unit tests (ADR 0005).
- SHA-256 pinned model manifest and `scripts/fetch-model.sh` (ADR 0004).
- CI: unit tests, ktlint, Android lint, debug APK and an unsigned release APK with the model.
