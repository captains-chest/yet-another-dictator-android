# Third-party notices

Yet Another Dictator for Android is MIT licensed (see `LICENSE`). It depends on the following
third-party pieces, all under licenses compatible with that. This file, `LICENSE` and the Apache
License 2.0 text ship inside the app (About → Licenses).

| Component | Used for | License | Source |
| --- | --- | --- | --- |
| whisper.cpp 1.9.5 (includes ggml) | On-device speech recognition | MIT, © 2023-2026 The ggml authors | https://github.com/ggml-org/whisper.cpp |
| `ggml-small-q5_1.bin` | Bundled speech model (ggml conversion of Whisper Small, 5-bit quantised; ADR 0004) | MIT (conversion, ggerganov/whisper.cpp on Hugging Face) | https://huggingface.co/ggerganov/whisper.cpp |
| OpenAI Whisper Small weights and tokenizer | The original model the conversion is made from | Apache License 2.0, © OpenAI | https://huggingface.co/openai/whisper-small |
| DictationCore 0.1.0 (ported to Kotlin as `core`) | Shared dictation logic | MIT, © 2026 CaptainDuckay | https://github.com/captains-chest/DictationCore |
| Jetpack Compose, AndroidX Activity, Lifecycle, SavedState | User interface | Apache License 2.0, © The Android Open Source Project | https://developer.android.com/jetpack/androidx |
| `jfk.wav` (test asset only, not in the app) | Instrumented transcription test | MIT, © The ggml authors (whisper.cpp `samples/`) | https://github.com/ggml-org/whisper.cpp |
| Kotlin standard library, kotlinx.coroutines | Language runtime | Apache License 2.0, © JetBrains s.r.o. and contributors | https://github.com/Kotlin/kotlinx.coroutines |

Each MIT component is used under the MIT license text in `LICENSE`, with its own copyright notice.
The Apache 2.0 components are used under the Apache License 2.0, whose full text is in
`docs/licenses/Apache-2.0.txt`.
