# Changelog

All notable changes are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.5.0] - 2026-10-27

### Changed
- Speech models are now Parakeet only. Next to Parakeet 0.6B (most accurate, about 661 MB) there is
  a new choice, NVIDIA Parakeet TDT-CTC 110M (smallest and fastest, about 132 MB, with punctuation
  and capitals). It downloads only after you pick it, with the usual Wi-Fi or mobile data question
  (ADR 0010).
- If you had picked Whisper Small, the model picker shows again on first launch so you can choose a
  Parakeet model. An already downloaded Parakeet model is used straight away instead. The old Whisper
  download (about 190 MB) is deleted in the background, and nothing else is touched.

### Removed
- Whisper Small and the whisper.cpp native library (JNI bridge and CMake build). The app is about
  5.6 MB smaller uncompressed (about 1 MB smaller to download on a phone), and builds no longer need
  the Android NDK or CMake.

### Fixed
- Dictations longer than 30 seconds no longer lose words or run out of memory. Parakeet read the
  whole recording at once, which takes a lot of memory for a long dictation and fails past about
  400 seconds. Long recordings are now cut at a pause into pieces of at most 30 seconds, and no
  piece is too short to be transcribed, as on Mac and iOS.

## [0.4.0] - 2026-10-21

### Added
- Quick Settings tile ("Floating mic") to show or hide the floating microphone, matching the setup
  toggle. Add it once under Edit tiles; state stays in sync with the app and the notification stop
  action.

### Changed
- New look, from captains-chest.com: wood boards, gold coins and moss on a night-sea background, in
  light and dark mode alike. Setup is three steps with switches (microphone, typing, floating mic),
  and the speech model is a radio card. The floating button is a moss cookie with the Dictator mark;
  it turns gold with a stop square while listening (ADR 0009).
- While dictating, a plank opens beside the floating button, toward the middle of the screen:
  level bars and a timer while listening, "Writing" while transcribing, then "Typed N words · Undo"
  for a few seconds. The button itself stays where it is (ADR 0007).
- Holding the floating button starts recording after half a second instead of right away, so it is
  easy to press and drag it somewhere else.
- Dictator is English-only. The Dansk language option is removed, and so is the Language section:
  Whisper is always told English. Parakeet remains English only. The Danish UI locale (`values-da`) is removed.

## [0.3.0] - 2026-10-15

### Changed
- One button. The floating microphone no longer needs "Display over other apps": the
  `SYSTEM_ALERT_WINDOW` permission and its setup step are gone (it was never used, the microphone is
  an accessibility overlay). Setup notes that Android's own accessibility shortcut button can be
  turned off, and how sideloaded installs get past "App was denied access" (Allow restricted
  settings).
- The microphone is icon only, with a fixed size and position in every state: red with a pulsing
  level ring while listening, a spinning ring while transcribing, amber with a shake on an error.
  Undo and Discard are a small badge on the button instead of text labels.

## [0.2.0] - 2026-10-15

### Added
- Choose your speech model. On first launch Dictator lists the models best first, with the size,
  languages, accuracy and speed of each. Switch models or delete one you no longer use under Models.
- A second speech model: NVIDIA Parakeet TDT 0.6B v2 (English only, the most accurate, about
  661 MB), run on the phone with sherpa-onnx. Whisper Small (about 190 MB) stays available and is
  the one to use for Dansk. Parakeet is licensed CC-BY-4.0 and credited in the license notices.
- Dictation is one floating microphone. It appears when a text field is focused and is hidden
  otherwise. Tap to start and stop, hold to talk, drag to move it. Your own keyboard stays (ADR 0007).
- Undo Last Dictation on the floating microphone, as on Mac and iOS.
- Signed releases: a signed universal APK for sideloading and a signed AAB, each with a `.sha256`
  checksum, on the GitHub release. The workflow checks the APK signature and refuses a debug key.

### Changed
- No automatic download. Models are no longer bundled; Dictator downloads only the model you chose,
  and only after you tap Download, over Wi-Fi unless you allow mobile data. Downloads resume, retry
  and are checked against a SHA-256 manifest built into the app (ADR 0006, ADR 0008). Audio never
  leaves the device; the model download is the only network use (`INTERNET` permission).
  A Whisper Small download from before stays in use without asking.
- The Dictator keyboard is gone, which also removes the error some apps showed when switching away
  from it.
- Whisper transcribes only the length of the clip, on the fast cores, so short dictations finish
  sooner.
- Third-party notices: OpenAI's Whisper weights are MIT licensed, not Apache 2.0.

### Fixed
- Long dictations could lose text: a dictation is now typed in one field write instead of chunks
  that could overwrite each other.
- An empty field's placeholder ("Message", "Search") is no longer treated as text, so dictation no
  longer lands next to it or gets spaced against it.

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
