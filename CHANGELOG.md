# Changelog

All notable changes are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Changed
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
