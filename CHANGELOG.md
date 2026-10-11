# Changelog

All notable changes are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- The model ships as an install-time Play Asset Delivery pack, so the Play base stays about 5 MB
  (ADR 0004). `scripts/build-universal-apk.sh` builds one APK with the model for sideloading.
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
