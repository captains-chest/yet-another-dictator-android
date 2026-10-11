# Yet Another Dictator — Android

Native Android companion to the [macOS Dictator app](https://github.com/captains-chest/yet-another-dictator)
and the [iOS app](https://github.com/captains-chest/yet-another-dictator-ios).

Fully offline dictation that works in any text field via a keyboard (an Android input method).
Uses a Kotlin port of the shared [DictationCore](https://github.com/captains-chest/DictationCore)
logic and whisper.cpp.

## Design goals

- **Simple and robust** — same philosophy as the macOS app: no fancy features, just reliable dictation.
- **Platform native** — a keyboard with one big mic button, Material You colours, Danish and English UI.
- **Hold** = push-to-talk, **tap** = toggle (same dual-mode as the macOS hotkey and the iOS keyboard).
- **Smaller model** — Whisper Small (q5_1) via whisper.cpp. Offline, on-device.
- **Shared core** — pure logic lives in `core`, a tested Kotlin port of DictationCore (ADR 0005).

## Architecture

Recording, transcription and typing all run in the keyboard. Unlike iOS, an Android keyboard may
use the microphone and has the memory for a Whisper model, so there is no hand-off (ADR 0001).

```
Keyboard (DictatorIme, an InputMethodService)
  └─ MicrophoneRecorder: AudioRecord → CaptureBuffer (16 kHz mono float)
  └─ WhisperTranscriber → JNI → whisper.cpp (bundled Whisper Small q5_1, read from the APK)
  └─ DictationController
  └─ ImeTextInserter → InputConnection.commitText (chunked, cancellable), SmartJoin context
App (MainActivity, Jetpack Compose)
  └─ Microphone permission, keyboard setup, language, model status, a field to try it, licenses
core (Kotlin/JVM module, no Android dependencies)
  └─ DictationController, SmartJoin, TranscriptCleaner, TextChunker, SpeechActivity,
     DictationPrompt, DictationLanguage, CaptureBuffer, AppVersion, WhatsNew
```

No audio and no history are written. The microphone is open only while a dictation is recording,
so Android's microphone indicator is only on then.

## Build

Requires JDK 17+, the Android SDK (compileSdk 37) and NDK 28.2.13676358 with CMake 3.31.6.
Gradle downloads whisper.cpp 1.9.5 (pinned by SHA-256) the first time it builds the native code.

```sh
scripts/fetch-model.sh          # once; verifies Whisper Small q5_1 into model_pack/src/main/assets/model (gitignored)
./gradlew bundleRelease         # the AAB for Play: base (~5 MB) + install-time model asset pack
scripts/build-universal-apk.sh  # one installable APK with the model, for sideload testing
```

The model lives in the install-time asset pack `model_pack` (ADR 0004), so `assembleDebug` APKs
don't contain it and say they can't transcribe; use `build-universal-apk.sh` to test dictation.
The model is never downloaded by the app.

Releases: push a `v*` tag with a matching CHANGELOG section. The Release workflow builds a signed
AAB and universal APK, attaches them to a GitHub release, and uploads the AAB as a draft to Play's
internal track once the `PLAY_SERVICE_ACCOUNT_JSON` secret exists. The upload key lives only in
repository secrets (and an offline backup); Google holds the app signing key (Play App Signing).
Store listing, Data safety and content rating answers: `docs/play-store.md`. Privacy policy:
https://captains-chest.com/dictator/privacy

```sh
./gradlew :core:test            # the ported DictationCore tests
./gradlew ktlintCheck lint      # Kotlin style and Android lint
scripts/build-universal-apk.sh && ./gradlew -PtestBuildType=release assembleReleaseAndroidTest
adb install build/dictator-universal.apk && adb install app/build/outputs/apk/androidTest/release/app-release-androidTest.apk
adb shell am instrument -w com.captainduckay.dictator.android.test/androidx.test.runner.AndroidJUnitRunner   # JFK sample, JNI + model
```

Then: install, open Dictator, allow the microphone, tap **Turn on keyboard**, enable Dictator, and
switch to it in the **Try it** field.

## Permissions

Each one is required, and none of them grant network access.

- **`RECORD_AUDIO`:** the keyboard records only while a dictation is running.
- **`BIND_INPUT_METHOD`** (on the keyboard service): lets Android bind the keyboard. Android warns
  that a keyboard can collect what you type; Dictator stores nothing and has no network access.

There is **no `INTERNET` permission**, so the app cannot reach the network at all. Backups are off.

## Requirements

- Android 12 (API 31) or later, arm64 phones (x86_64 is built for the emulator) (ADR 0003).
- Dependencies are pinned in `gradle/libs.versions.toml`; Dependabot proposes bumps as PRs.
  whisper.cpp and the model are pinned by SHA-256 and bumped by hand.

## Privacy rules (same as the macOS and iOS apps)

These are requirements for every change:

- **Offline dictation.** Audio and text never leave the device. The model is bundled and the app
  has no network permission. Adding one needs an ADR.
- **No clipboard.** Text goes in through `InputConnection.commitText`, never the clipboard.
- **Least privilege.** Only the microphone, and only while dictating. Nothing typed or said is
  stored or logged.

## Status

The keyboard, the app, the JNI bridge and the bundled model are in place, and CI builds and tests
them. Not yet measured on a real phone: latency, peak memory, Danish/English quality, and how the
keyboard behaves in apps with unusual text fields (ADR 0001, 0004). The model ships as a Play
install-time asset pack (ADR 0004).

## Conventions

Conventional commits, a CHANGELOG (Keep a Changelog), ADRs in `docs/adr/` for decisions, pinned
dependencies, small PRs merged on green CI. These are the same conventions as the macOS app.

## License

MIT © 2026 CaptainDuckay. See [LICENSE](LICENSE). Third-party components are credited in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
