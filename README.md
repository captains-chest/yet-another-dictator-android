# Yet Another Dictator — Android

Native Android companion to the [macOS Dictator app](https://github.com/captains-chest/yet-another-dictator)
and the [iOS app](https://github.com/captains-chest/yet-another-dictator-ios).

On-device dictation that works in any text field from a floating microphone. Your keyboard stays.
Uses a Kotlin port of the shared [DictationCore](https://github.com/captains-chest/DictationCore)
logic and whisper.cpp.

## Design goals

- **Simple and robust** — same philosophy as the macOS app: no fancy features, just reliable dictation.
- **Platform native** — a floating microphone, Material You colours, Danish and English UI.
- **Hold** = push-to-talk, **tap** = toggle (same dual-mode as the macOS hotkey and the iOS keyboard).
- **Smaller model** — Whisper Small (q5_1) via whisper.cpp, on-device. Downloaded once after install (190 MB, Wi-Fi recommended).
- **Shared core** — pure logic lives in `core`, a tested Kotlin port of DictationCore (ADR 0005).

## Architecture

Recording, transcription and typing all run in one process. The floating button records and
transcribes; an accessibility service types into the focused field. There is no keyboard and no
hand-off (ADR 0007, which supersedes the interaction in ADR 0001).

```
Floating button (FloatingButtonService)
  └─ MicrophoneRecorder: AudioRecord → CaptureBuffer (16 kHz mono float)
  └─ WhisperTranscriber → JNI → whisper.cpp (Whisper Small q5_1, downloaded once into app storage, ADR 0006)
  └─ DictationController
  └─ AccessibilityFieldEditor → the focused field (chunked, cancellable), SmartJoin context
App (MainActivity, Jetpack Compose)
  └─ Microphone, overlay and accessibility setup, language, model status, a field to try it, licenses
core (Kotlin/JVM module, no Android dependencies)
  └─ DictationController, SmartJoin, FieldEdit, TranscriptCleaner, TextChunker, SpeechActivity,
     DictationPrompt, DictationLanguage, CaptureBuffer, AppVersion, WhatsNew
```

No audio and no history are written. The microphone is open only while a dictation is recording,
so Android's microphone indicator is only on then.

## Build

Requires JDK 17+, the Android SDK (compileSdk 37) and NDK 28.2.13676358 with CMake 3.31.6.
Gradle downloads whisper.cpp 1.9.5 (pinned by SHA-256) the first time it builds the native code.

```sh
./gradlew bundleRelease         # the AAB for Play (~5 MB; no model inside)
scripts/build-universal-apk.sh  # one installable APK, for sideloading
scripts/fetch-model.sh && scripts/push-model.sh   # optional: put the verified model on a debug build via adb
```

No build contains the model (ADR 0006). After install, the app shows the model's size and downloads
it only when you tap **Download** (Wi-Fi only unless you allow mobile data). Every file is checked
against the SHA-256 manifest (`scripts/model-manifest.txt`) that ships inside the signed APK.

Releases: push a `v*` tag with a matching CHANGELOG section. The Release workflow builds a signed
AAB and universal APK, attaches them to a GitHub release, and uploads the AAB as a draft to Play's
internal track once the `PLAY_SERVICE_ACCOUNT_JSON` secret exists. The upload key lives only in
repository secrets (and an offline backup); Google holds the app signing key (Play App Signing).
Store listing, Data safety and content rating answers: `docs/play-store.md`. Privacy policy:
https://captains-chest.com/dictator/privacy

```sh
./gradlew :core:test            # the ported DictationCore tests
./gradlew ktlintCheck lint      # Kotlin style and Android lint
scripts/build-universal-apk.sh && ./gradlew -PtestBuildType=release assembleReleaseAndroidTest   # download the model in the app first
adb install build/dictator-universal.apk && adb install app/build/outputs/apk/androidTest/release/app-release-androidTest.apk
adb shell am instrument -w com.captainduckay.dictator.android.test/androidx.test.runner.AndroidJUnitRunner   # JFK sample, JNI + model
```

Then: install, open Dictator, allow the microphone, allow the button to draw over other apps, turn
Dictator on under Accessibility, tap **Show the button**, focus the **Try it** field and tap the
microphone. The microphone is the only button, and it appears only while a text field is focused.

## Permissions

Each one is required.

- **`RECORD_AUDIO`:** the button records only while a dictation is running.
- **Draw over other apps:** the floating button. Granted in Settings, not as a runtime dialog.
- **Accessibility service:** types into the focused field, and reads only that field to place the
  text. It does not read the rest of the screen, and it stores nothing.
- **Foreground service** (`specialUse` and `microphone`): keeps the button on screen after you leave
  the app. The microphone type is taken when you show the button; the microphone itself opens only
  while a dictation is recording. A notification stays up while the button is showing.
- **`POST_NOTIFICATIONS`:** that notification, on Android 13 and later.

- **`INTERNET`, `ACCESS_NETWORK_STATE`:** only for the one-time model download that you start in
  the app (ADR 0006). No audio, text or analytics are ever sent. Backups are off.

## Requirements

- Android 12 (API 31) or later, arm64 phones (x86_64 is built for the emulator) (ADR 0003).
- Dependencies are pinned in `gradle/libs.versions.toml`; Dependabot proposes bumps as PRs.
  whisper.cpp and the model are pinned by SHA-256 and bumped by hand.

## Privacy rules (same as the macOS and iOS apps)

These are requirements for every change:

- **Audio never leaves the device; the one-time model download is the only network use.** The
  download needs the user's consent, prefers Wi-Fi and is verified against the bundled manifest.
  Any other network use needs an ADR.
- **No clipboard.** Text goes into the focused field through the accessibility service, never the clipboard.
- **Least privilege.** The microphone only while dictating. The overlay and the accessibility
  service exist so the button can sit on screen and type into the current field. Nothing typed or
  said is stored or logged.

## Status

The floating button, the app, the JNI bridge and the model download are in place, and CI builds and
tests them. Not yet measured on a real phone: latency after the shorter Whisper window, peak memory
while the button is showing, Danish/English quality, and how typing behaves in apps whose fields
are not exposed to accessibility (ADR 0007, 0002), and the download on a real network (ADR 0006).

## Conventions

Conventional commits, a CHANGELOG (Keep a Changelog), ADRs in `docs/adr/` for decisions, pinned
dependencies, small PRs merged on green CI. These are the same conventions as the macOS app.

## License

MIT © 2026 CaptainDuckay. See [LICENSE](LICENSE). Third-party components are credited in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
