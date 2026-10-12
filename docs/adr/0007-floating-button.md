# 7. A floating button dictates; the user's keyboard stays

- Status: Accepted
- Date: 2026-10-11
- Supersedes: the interaction in [ADR 0001](0001-capture-in-the-keyboard.md)

## Context

ADR 0001 put the microphone on a Dictator keyboard and typed through `InputConnection`. That works,
and it is how the iOS app is shaped, but it is not the interaction people expect on Android. To
dictate, you switch input methods until you reach the one button. Switching away again drops the
current app's input connection; some apps respond with an error. The button also cannot sit on
screen while the normal keyboard is open, which is the whole point of a push-to-talk control.

The pipeline itself (record, transcribe on device, type, no clipboard, nothing stored) is unchanged.

## Decision

- Dictation is a **floating microphone**. Tap toggles, hold talks, drag moves it. The window is an
  accessibility overlay, added by the accessibility service, so a press reaches the button and the
  button stays up after leaving Dictator. A plain draw-over window did neither. It is not an input
  method, and the app no longer ships one.
- There is one button, and it is the microphone icon. It is shown only while a text field is
  focused, and it stays up through a dictation that is still recording, transcribing, or waiting to
  be typed. Android's accessibility shortcut is not a second control: that button only turned
  dictation on and off.
- The user's keyboard stays. Text is inserted by an **accessibility service** that reads only the
  focused editable, in memory, to place the words and to space them (`FieldEdit`, then
  `ACTION_SET_TEXT`). The clipboard is still never used.
- The button is a foreground service while it is on screen, so the process (and the loaded model)
  survive leaving Dictator. It takes the microphone foreground-service type when the user shows it,
  while the app is still open; Android 14 will not add that type later from the background. The
  microphone itself still opens only for a dictation, and the audio is still dropped afterwards.
- The service is started by the user from the app, and stopped from the app or the notification.
  It is not started at boot.

## Consequences

- Setup is three grants: microphone, drawing over other apps, and the accessibility service. The
  accessibility screen tells the user that only the focused field is read.
- Fields that do not expose editable text (some web views, some custom editors) cannot be typed
  into. The button says so. Passwords are refused.
- The model stays in memory while dictation is turned on, including while the microphone is hidden
  because no field is focused. Turning it off from the app or the notification frees the model.
- Switching keyboards is gone, so leaving Dictator can no longer break the app underneath.
