# 1. The keyboard records, transcribes and types; the app only sets things up

- Status: Accepted
- Date: 2026-10-10

## Context

Dictation must work in any text field, so typing happens in a keyboard: an Android
`InputMethodService` that commits text through `InputConnection`. On iOS the keyboard extension
can't record and has a tiny memory budget, so the container app records and hands the transcript
over through an App Group (iOS ADR 0001). Android has neither limit:

- **Microphone.** An input method is an ordinary service in the app's own process. While the
  keyboard is showing, its window is visible, so it may use the microphone with the app's
  `RECORD_AUDIO` permission (this is how Gboard's voice typing works). No foreground service and
  no background-microphone exemption are needed.
- **Memory.** The keyboard runs with the app's normal heap and native memory, so it can hold the
  Whisper model (about 190 MB on disk, more while decoding).

## Decision

- The **keyboard** owns the whole pipeline: `MicrophoneRecorder` (AudioRecord, 16 kHz mono float,
  the ported `CaptureBuffer`) → `WhisperTranscriber` (whisper.cpp, ADR 0002) → the ported
  `DictationController` → `ImeTextInserter` (`InputConnection.commitText`, in chunks, with the
  ported `SmartJoin` using `getTextBeforeCursor`).
- The model is loaded once when the keyboard service is created and freed when it is destroyed.
- The **app** asks for the microphone, explains how to turn the keyboard on, stores the language,
  and has a field to try the keyboard. It never records.
- There is no hand-off: no shared file, no IPC, no clipboard, no history. Audio and text exist only
  in memory for the running dictation.

## Consequences

- Much simpler than iOS: no "keep dictation available" session, no persistent microphone
  indicator; the indicator is only on while recording. The microphone opens on press, so there is
  no pre-roll (AudioRecord starts in well under 100 ms; to be measured on device).
- Leaving the text field cancels a running dictation instead of typing into the next field.
- The first dictation after the keyboard service starts waits for the model to load (a second or
  two, to be measured on device).
- If Android kills the keyboard's process under memory pressure, the model reloads next time.
