# Third-party notices

Yet Another Dictator for Android is MIT licensed (see `LICENSE`). It depends on the following
third-party pieces, all under licenses compatible with that. This file, `LICENSE` and the Apache
License 2.0 text ship inside the app (About → Licenses).

| Component | Used for | License | Source |
| --- | --- | --- | --- |
| sherpa-onnx 1.13.8 (Android AAR, includes ONNX Runtime) | Runs the Parakeet models on the CPU | Apache License 2.0, © Xiaomi Corporation and the k2-fsa authors; ONNX Runtime MIT, © Microsoft Corporation | https://github.com/k2-fsa/sherpa-onnx, https://github.com/microsoft/onnxruntime |
| NVIDIA Parakeet TDT 0.6B v2, int8 ONNX export (`csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8`) | English speech model, downloaded only if the user chooses it and confirms (ADR 0008) | CC-BY-4.0, © NVIDIA Corporation; exported to ONNX and quantised by k2-fsa | https://huggingface.co/nvidia/parakeet-tdt-0.6b-v2, https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8 |
| NVIDIA Parakeet TDT-CTC 110M, int8 ONNX export (`sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8`) | English speech model, downloaded only if the user chooses it and confirms (ADR 0010) | CC-BY-4.0, © NVIDIA Corporation; exported to ONNX and quantised by k2-fsa. The app downloads it from the VocaHQ mirror on Hugging Face, byte-identical to the k2-fsa release asset | https://huggingface.co/nvidia/parakeet-tdt_ctc-110m, https://huggingface.co/VocaHQ/sherpa-onnx-nemo-parakeet-tdt-ctc-110m-en-int8, https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models |
| DictationCore 0.1.0 (ported to Kotlin as `core`) | Shared dictation logic | MIT, © 2026 CaptainDuckay | https://github.com/captains-chest/DictationCore |
| Jetpack Compose, AndroidX Activity, Lifecycle, SavedState, WorkManager | User interface; WorkManager runs the model download | Apache License 2.0, © The Android Open Source Project | https://developer.android.com/jetpack/androidx |
| `jfk.wav` (test asset only, not in the app) | Instrumented transcription test | MIT, © The ggml authors (whisper.cpp `samples/`) | https://github.com/ggml-org/whisper.cpp |
| Kotlin standard library, kotlinx.coroutines | Language runtime | Apache License 2.0, © JetBrains s.r.o. and contributors | https://github.com/Kotlin/kotlinx.coroutines |

**Attribution (CC-BY-4.0):** "parakeet-tdt-0.6b-v2" by NVIDIA Corporation, licensed under
CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/). Source:
https://huggingface.co/nvidia/parakeet-tdt-0.6b-v2. Modified: exported to ONNX and quantised to int8
by k2-fsa (https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8); the app
downloads that export unchanged.

**Attribution (CC-BY-4.0):** "parakeet-tdt_ctc-110m" by NVIDIA Corporation, licensed under
CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/). Source:
https://huggingface.co/nvidia/parakeet-tdt_ctc-110m. Modified: exported to ONNX and quantised to
int8 by k2-fsa (https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models, `sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8`); the app downloads
that export unchanged from https://huggingface.co/VocaHQ/sherpa-onnx-nemo-parakeet-tdt-ctc-110m-en-int8, whose files match the k2-fsa release byte for byte.

Each MIT component is used under the MIT license text in `LICENSE`, with its own copyright notice.
The Apache 2.0 components are used under the Apache License 2.0, whose full text is in
`docs/licenses/Apache-2.0.txt`.
