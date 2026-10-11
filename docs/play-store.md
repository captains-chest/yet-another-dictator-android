# Google Play: listing, Data safety and content rating

Drafts to paste into Play Console. Keep them true to the README's privacy rules; if the app ever
gains a permission or network access, update this file and the privacy policy in the same PR.

- Package: `com.captainduckay.dictator.android`
- Privacy policy: https://captains-chest.com/dictator/privacy
- Support / website: https://captains-chest.com · Contact email: the developer account's address
- Category: **Productivity** · Tags: Keyboard, Productivity
- Price: Free · Ads: **No** · In-app purchases: **No**

## Store listing — English (en-US)

**App name** (30): `Dictator: Offline Dictation`

**Short description** (80):
`Private dictation keyboard for Danish and English. Works offline, on your phone.`

**Full description** (4000):

```
Dictator turns speech into text in any app, right on your phone. Nothing you say leaves the device.

Switch to the Dictator keyboard in any text field, tap the big microphone and speak. Tap again to
finish, or hold the button while you talk and let go when you are done. Dictator types the text
where your cursor is, with the right spacing and capital letters.

• Fully offline. Speech recognition runs on your phone with OpenAI's Whisper model. Dictator has no
  internet permission at all, so it cannot send your voice or your text anywhere.
• Danish and English. Pick a language or let Dictator detect it.
• Nothing is stored. Audio is kept in memory only while a dictation is transcribed, then
  discarded. Dictator keeps no history and never uses the clipboard.
• Microphone only while you dictate. Android's microphone indicator shows exactly when Dictator
  listens.
• Simple on purpose. One button, no accounts, no ads, no tracking.

Dictator is open source (MIT) and part of the Yet Another Dictator family for macOS, iOS and
Android: https://github.com/captains-chest/yet-another-dictator-android

Requires Android 12 or later. The speech model (about 190 MB) is installed together with the app.
```

## Store listing — Danish (da-DK)

**App-navn**: `Dictator: Offline diktering`

**Kort beskrivelse**:
`Privat dikteringstastatur til dansk og engelsk. Virker offline på din telefon.`

**Fuld beskrivelse**:

```
Dictator omsætter tale til tekst i alle apps, direkte på din telefon. Intet af det, du siger,
forlader enheden.

Skift til Dictator-tastaturet i et tekstfelt, tryk på den store mikrofon, og tal. Tryk igen for at
afslutte, eller hold knappen nede, mens du taler, og slip, når du er færdig. Dictator skriver
teksten, hvor markøren står, med de rigtige mellemrum og store bogstaver.

• Helt offline. Talegenkendelsen kører på din telefon med OpenAI's Whisper-model. Dictator har slet
  ikke adgang til internettet og kan derfor ikke sende din stemme eller din tekst nogen steder hen.
• Dansk og engelsk. Vælg et sprog, eller lad Dictator finde det selv.
• Intet gemmes. Lyden findes kun i hukommelsen, mens en diktering bliver omsat, og slettes derefter.
  Dictator gemmer ingen historik og bruger aldrig udklipsholderen.
• Mikrofonen bruges kun, mens du dikterer. Androids mikrofonindikator viser præcis, hvornår
  Dictator lytter.
• Enkel med vilje. Én knap, ingen konti, ingen reklamer, ingen sporing.

Dictator er open source (MIT) og en del af Yet Another Dictator-familien til macOS, iOS og Android:
https://github.com/captains-chest/yet-another-dictator-android

Kræver Android 12 eller nyere. Talemodellen (ca. 190 MB) installeres sammen med appen.
```

## Graphics still needed

- App icon 512 × 512 PNG (from the iOS/macOS icon).
- Feature graphic 1024 × 500.
- At least 2 phone screenshots (setup screen, keyboard in use), ideally EN and DA.

## Data safety

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **No** |
| (Follows) Is all user data encrypted in transit? | Not applicable: no data leaves the device |
| Do you provide a way for users to request that their data is deleted? | Not applicable: nothing is collected |

Why "No" is accurate: Google's definition of "collect" is transmitting data off the device. Audio
is processed only on the device, in memory, and the app has no `INTERNET` permission. Nothing is
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
  recognition in the keyboard. Audio is not stored or transmitted."
- **Keyboard (IME):** Play shows users Android's standard keyboard warning. Nothing to declare,
  but reviewers may test it: the listing explains how to enable it.
