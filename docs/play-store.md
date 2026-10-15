# Google Play: listing, Data safety and content rating

Drafts to paste into Play Console. Keep them true to the README's privacy rules; if the app ever
gains a permission or network access, update this file and the privacy policy in the same PR.

- Package: `com.captainduckay.dictator.android`
- Privacy policy: https://captains-chest.com/dictator/privacy
- Support / website: https://captains-chest.com · Contact email: the developer account's address
- Category: **Productivity** · Tags: Productivity
- Price: Free · Ads: **No** · In-app purchases: **No**

## Store listing — English (en-US)

**App name** (30): `Dictator: Offline Dictation`

**Short description** (80):
`Private English dictation. A floating button. Works offline.`

**Full description** (4000):

```
Dictator turns speech into text in any app, right on your phone. Nothing you say leaves the device.

Focus a text field and the microphone appears. Tap it and speak into the field you are already using.
Your keyboard stays. Tap again to finish, or hold the button while you talk and let go when you
are done. Dictator types the text where your cursor is, with the right spacing and capital letters.

• Private. Speech recognition runs on your phone with OpenAI's Whisper model. Audio never leaves the
  device; the one-time model download (about 190 MB, Wi-Fi recommended) is the only network use.
• English. Choose Automatic or English with Whisper, or Parakeet (English only).
• Nothing is stored. Audio is kept in memory only while a dictation is transcribed, then
  discarded. Dictator keeps no history and never uses the clipboard.
• Microphone only while you dictate. Android's microphone indicator shows exactly when Dictator
  listens.
• Simple on purpose. One button, no accounts, no ads, no tracking.

Dictator is open source (MIT) and part of the Yet Another Dictator family for macOS, iOS and
Android: https://github.com/captains-chest/yet-another-dictator-android

Requires Android 12 or later. After install, download the speech model once (about 190 MB). Turn
Dictator on under Accessibility; the microphone then appears when a text field is focused and
types into it. No draw-over-other-apps permission is needed.
```

## Store listing — Danish (da-DK)

Withdrawn 2026-10-15: Dictator is English-only. Do not publish a da-DK listing.

## Graphics still needed

- App icon 512 × 512 PNG (from the iOS/macOS icon).
- Feature graphic 1024 × 500.
- At least 2 phone screenshots (setup screen, the floating button over a text field), ideally EN and DA.

## Data safety

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **No** |
| (Follows) Is all user data encrypted in transit? | Not applicable: no data leaves the device |
| Do you provide a way for users to request that their data is deleted? | Not applicable: nothing is collected |

Why "No" is accurate: Google's definition of "collect" is transmitting data off the device. Audio
is processed only on the device, in memory. The `INTERNET` permission is used only to download the
speech model from Hugging Face (ADR 0006); nothing from the user is sent. Nothing is
shared with third parties; there are no SDKs for analytics, ads or crash reporting. The listing
will then show "No data collected" and "No data shared".

## Content rating (IARC questionnaire)

- Category: **Utility, productivity, communication or other**.
- Violence, sexuality, language, controlled substances, gambling: **No** to all.
- Does the app allow users to interact or exchange content with other users? **No**.
- Does the app share the user's current physical location with other users? **No**.
- Does the app allow users to purchase digital goods? **No**.
- Is the app a web browser or search engine? **No**.
- Expected result: PEGI 3 / ESRB Everyone / USK 0.

## Other App content declarations

- **Target audience:** 18 and over (avoids the Families policy; the app is a general tool).
- **Ads:** no ads.
- **App access:** all functionality is available without an account; no login instructions.
- **Government app / financial features / health:** No.
- **News app:** No.
- **Permissions:** `RECORD_AUDIO` is a normal runtime permission (no special declaration form).
  Describe it if asked: "The microphone is used only while the user dictates, for on-device speech
  recognition. Audio is not stored or transmitted."
- **Accessibility service:** Declare it. It is the core feature: typing dictated text into the
  focused field. It reads only that field, in memory, and stores nothing. The in-app disclosure
  and the service description say this.
- **Foreground service, special use:** the floating button the user turns on. Subtype in the
  manifest: a dictation button that stays on screen so the user can dictate without switching
  keyboards. The microphone type is taken while the button is showing; capture still starts only
  when the user dictates.
