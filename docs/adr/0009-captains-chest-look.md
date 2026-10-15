# 9. The Captain's Chest look

- Status: Accepted
- Date: 2026-10-15
- Supersedes: the dynamic-colour bullet of [ADR 0003](0003-android-12-minimum.md) and the colours of
  the floating button in [ADR 0007](0007-floating-button.md) (its fixed footprint still applies)

## Context

The setup screen was a plain Material 3 list in wallpaper colours, and the floating button a plain
circle with a microphone. It worked, but it did not look like a 2026 app or like it belonged to
Captain's Chest. Four directions were prototyped side by side (branch
`t3/2026-app-design-prototypes`, `docs/prototypes/ui-2026-prototype.html`): Material 3 Expressive,
a hardware "instrument", aurora glass, and Material 3 Expressive in the captains-chest.com theme.
The last one was chosen.

## Decision

- **One fixed theme**, light or dark mode alike: the night sea of captains-chest.com. Cards are wood
  boards with a 3 dp ink outline, a hard ink drop shadow and brass rivets; icons sit on gold coins;
  names and titles are serif; section labels are green small caps. Material You dynamic colour is
  no longer used.
- **Setup is one board of three steps** (microphone, typing, floating mic), each with a switch that
  shows whether it is done and a tap that does the next thing. The speech model is a radio card with
  "In use" or the download size in a right-aligned pill. The trial field is parchment.
- **The floating button is a moss "cookie"** (a scalloped circle) with an ink outline and a hard
  shadow, and the white Dictator mark when idle. Listening turns it gold with a dark stop square and
  it turns slowly; transcribing spins it with a light-moss ring; loading dims it; an error turns it
  rust. All of it still fits the fixed bounds of ADR 0007, so nothing appears beside the button.
- The Dictator mark is a vector (`ic_logo`), traced from the launcher icon.
- English only: the setup screen has no language choice, and Whisper is always told English.

## Consequences

- The app no longer follows the wallpaper. Every colour is in `Theme.kt` (`Chest`) and, for the
  button drawn outside Compose, in `BubblePanel`.
- Contrast is checked against the dark boards: cream text, muted gold footnotes, light-moss accents.
