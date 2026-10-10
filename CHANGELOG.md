# Changelog

All notable changes are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

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
