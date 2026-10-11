# 5. `core` is a Kotlin port of DictationCore, kept in sync by its tests

- Status: Accepted
- Date: 2026-10-10

## Context

The macOS and iOS apps share pure logic through the Swift package
[DictationCore](https://github.com/captains-chest/DictationCore). Kotlin can't use a Swift package
without a heavy bridge (Swift on Android, or Kotlin Multiplatform the other way round), which would
be far more machinery than the ~800 lines it would share.

## Decision

The Gradle module `core` (plain Kotlin/JVM, no Android dependencies) is a **line-by-line port, not
shared code**, of DictationCore 0.1.0: `DictationController`, `SmartJoin` and `DictationHistory`,
`TranscriptCleaner`, `TextChunker`, `SpeechActivity`, `DictationPrompt`, `DictationLanguage`,
`CaptureBuffer`, `AudioRingBuffer`, `AppVersion`, `Changelog` and `WhatsNew`. Their unit tests are
ported too, with the same cases and expectations (Swift's `Character` count is matched with
`BreakIterator` grapheme clusters).

Not ported, because Android doesn't need them: `Shortcut`, `Chord`, `KeyLabels` (Mac global
hotkey) and `UpdateCheck` (the Android app has no network access, ADR 0004).

## Keeping them in sync

- The port records which DictationCore version it matches (`0.1.0`, in `THIRD_PARTY_NOTICES.md` and
  this ADR). Behaviour changes land in DictationCore first, with tests.
- Every DictationCore release that changes ported logic gets a matching PR here that ports the
  change **and its tests**. The test names match the Swift ones so the suites can be compared side
  by side.
- Where Kotlin must differ (coroutines instead of Swift concurrency, `StateFlow` instead of
  `@Observable`), the port keeps the same states, the same order of side effects and the same
  user-visible messages.
