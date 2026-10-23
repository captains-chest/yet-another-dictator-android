# 10. Two Parakeet models; Whisper Small and whisper.cpp are removed

- Status: Accepted
- Date: 2026-10-23
- Supersedes: [ADR 0002](0002-whisper-cpp.md) (whisper.cpp) and the Whisper Small half of
  [ADR 0008](0008-choose-the-speech-model.md). ADR 0008's picker, consent and download rules, and
  ADR 0006's Wi-Fi default, resume and verification, still apply unchanged.

## Context

Whisper Small through whisper.cpp was far too slow on a real Android phone: long dictations took
much longer to transcribe than to speak. It was also the least accurate model offered, and kept a
second native runtime (whisper.cpp, built with the NDK and CMake) in every build, beside sherpa-onnx.
Parakeet TDT 0.6B v2 already runs through sherpa-onnx and is the most accurate, but it is a 661 MB
download. A smaller, faster option is needed for phones where 0.6B is too heavy.

NVIDIA Parakeet TDT-CTC 110M (`nvidia/parakeet-tdt_ctc-110m`, CC-BY-4.0) is a hybrid FastConformer
with a TDT and a CTC decoder that writes cased, punctuated English. k2-fsa exports it for
sherpa-onnx as a single CTC model (`model.int8.onnx`, 131.7 MB, plus `tokens.txt`). NVIDIA reports
an average WER of about 7.5% over the eight Open ASR Leaderboard test sets (AMI 15.88, Earnings-22
12.42, GigaSpeech 10.52, LibriSpeech clean 2.4 / other 5.2, SPGI 2.54, TED-LIUM 4.16, VoxPopuli
6.91), against 6.05% for 0.6B v2; those are NVIDIA's numbers for the full-precision model.
The int8 export scores 3.00% WER on LibriSpeech test-clean (2.93% at FP32), per the VocaHQ card.
k2-fsa's docs report a real-time factor of 0.066 on two desktop threads. None of this is measured
on a phone yet.

## Decision

- Two models, best first, both English only and both through **sherpa-onnx 1.13.8** on the CPU with
  greedy search:
  1. **Parakeet TDT 0.6B v2** (`parakeet-tdt-0.6b-v2-int8`, about 661 MB), unchanged:
     `modelType = "nemo_transducer"`, recommended, preselected in the picker.
  2. **Parakeet TDT-CTC 110M** (`parakeet-tdt-ctc-110m-en-int8`, about 132 MB): "Smallest ·
     fastest". It loads as NeMo CTC (`OfflineNemoEncDecCtcModelConfig`, `modelType = "nemo_ctc"`),
     **not** as a transducer: the export holds only the CTC branch, in one file.
     `core`'s `RecognizerSetup` names each model's recogniser and files, and tests check them
     against the manifest.
- 0.6B stays first because the ranking is by accuracy (ADR 0008) and its download size is shown
  next to it. If phone tests show the 110M is close enough, making it the recommendation is a one-line
  change to the enum order.
- **Source of the 110M.** The official file is k2-fsa's release asset
  `sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8.tar.bz2`
  (github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models), which is not on Hugging Face, and the
  app downloads only from Hugging Face (ADR 0006). The manifest pins the int8 files at
  `VocaHQ/sherpa-onnx-nemo-parakeet-tdt-ctc-110m-en-int8@43869f7`, which were checked
  byte-for-byte against that release asset on 2026-10-23: `model.int8.onnx` SHA-256
  `9177a914…81fc1a` (131,652,171 bytes) and `tokens.txt` `450e56bd…e6cd10` (9,953 bytes). Since
  the SHA-256 in the signed APK is the trust anchor, the mirror can't substitute other bytes.
- **Whisper Small, whisper.cpp, the JNI bridge and the CMake/NDK build are removed.** The app has
  no native code of its own any more; only sherpa-onnx's prebuilt, already stripped libraries
  remain. CI no longer installs the NDK or CMake.
- **Migration from a stored Whisper choice:** the stored id (`ggml-small-q5_1`) is listed in
  `SpeechModelId.retiredIds` and reads as "nothing chosen". At launch, an already downloaded
  Parakeet model is adopted as before (ADR 0008); otherwise the ranked picker appears and nothing is
  downloaded until the user picks a model and taps Download. It is not silently mapped to the 110M:
  that would start a new 132 MB choice the user never made, and the picker shows both sizes.
- **Leftover Whisper files:** at launch the app deletes `filesDir/models/ggml-small-q5_1/` (about
  190 MB that no engine can read any more), on a background thread, and nothing else. Other folders
  not in the manifest are still only removed when a download starts, as before.

## Consequences

- The APK and AAB lose the whisper.cpp library (`libdictator_whisper.so`) for both ABIs, and builds
  no longer need the NDK.
- Users of Whisper Small pick again once after updating; their 190 MB is freed.
- Two Parakeet models from one runtime: one native dependency to bump by hand (sherpa-onnx), and no
  multilingual model. Dictator is English-only, so that costs nothing today.
- Like the 0.6B, the 110M cannot read a very long recording in one pass. On a desktop with
  sherpa-onnx 1.13.8, 45 s decodes in under a second, but 420 s fails in the first attention layer
  (the encoder's positional table stops at 5,000 frames, about 400 s) after using over 3 GB of
  memory. Long dictations therefore depend on cutting the audio at pauses into pieces of at most
  30 s, as proposed for both models in PR #21; this ADR does not change that code.
- Measure on a real arm64 phone: speed of both models against the old Whisper Small, accuracy,
  punctuation, and memory, with short and long (several-minute) dictations.
