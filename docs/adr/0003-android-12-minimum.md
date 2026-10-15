# 3. Android 12 (API 31) is the minimum

- Status: Accepted
- Date: 2026-10-10

## Context

Fewer supported versions means fewer code paths to test, in line with the "minimal but flawless"
rule. The privacy story ("the microphone is only used while you dictate") should be visible to
the user on every supported device.

## Decision

`minSdk = 31` (Android 12), `targetSdk = 36`, `compileSdk = 37`.

- Android 12 added the **microphone privacy indicator** and the quick-settings microphone toggle,
  so users can always see when Dictator listens.
- Material You **dynamic colour** is always available, so the button matches the system without
  a fallback theme. (Superseded by [ADR 0009](0009-captains-chest-look.md): Dictator now has one fixed theme.)
- Practically every Android 12+ phone is 64-bit, so arm64-v8a is the only phone ABI we ship.
- Android 12 and later cover the large majority of active devices in 2026.

## Consequences

- Android 10 and 11 phones can't install the app. Revisit only if users ask.
- No compatibility branches below API 31 anywhere in the code.
