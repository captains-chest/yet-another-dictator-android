# 8. Let the user choose the speech model; download only that one

- Status: Accepted; amended by [ADR 0010](0010-parakeet-lineup.md) (the models are now Parakeet
  TDT 0.6B v2 and Parakeet TDT-CTC 110M; Whisper Small is removed)
- Note (2026-10-15): Dictator is English-only; Dansk is no longer offered.
- Date: 2026-10-14
- Supersedes: the "Whisper Small is the model" part of [ADR 0006](0006-download-model-after-install.md)
  (its consent, Wi-Fi default, resume and verification rules still apply) and, for that model,
  [ADR 0002](0002-whisper-cpp.md).

## Context

Whisper Small is the weakest of the candidates measured on 14 Oct 2026 (Open ASR Leaderboard).
NVIDIA Parakeet TDT 0.6B v2 is much more accurate for English (6.05 average WER), has no 30-second
window, and sherpa-onnx ships a proven Android runtime for exactly this model. It is English only,
so Whisper Small stays for Automatic or English. The macOS and iOS apps get the same choice.

## Decision

- Two models, best first: **Parakeet TDT 0.6B v2** (`parakeet-tdt-0.6b-v2-int8`, sherpa-onnx int8
  export `csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8@1ab9323`, encoder/decoder/joiner +
  tokens, about 661 MB) run by **sherpa-onnx 1.13.8** (`modelType = "nemo_transducer"`, CPU, greedy
  search), and **Whisper Small** with whisper.cpp (unchanged).
- **No automatic download.** On launch, only if no model has been chosen, a dialog lists the models
  best first with size, languages, accuracy and speed. Choosing one downloads nothing: the Model
  section then offers the download exactly as in ADR 0006 (Wi-Fi only unless mobile data is ticked).
  A copy that already has a verified Whisper Small download adopts it as the choice and isn't asked.
- The choice is stored in the app's SharedPreferences (`speechModel`) through `core`'s `ModelChoice`.
  The Models list switches model (same opt-in download) and deletes models not in use; switching
  waits while a download runs. The floating button reloads the model when the choice changes or a
  download finishes, but never during a dictation.
- Both models are pinned file by file (SHA-256 and size) in the bundled `model-manifest.txt` under
  their own model-id, and use the same `ModelDownloadWorker` (Range resume, retry, verification,
  `.complete` marker), now told which model to fetch.
- sherpa-onnx isn't on Maven Central. `app/build.gradle.kts` downloads the official release AAR at
  build time and refuses it unless it matches a pinned SHA-256. It adds about 32 MB of native code
  per ABI (ONNX Runtime included); the Play AAB is split per ABI.
- Parakeet is English only; the language setting notes that it doesn't apply.

## Consequences

- Parakeet is a larger download than Whisper Small (661 vs 190 MB) and needs more memory while
  loaded. Measure latency and memory on a real arm64 phone.
- One more pinned native dependency, outside Dependabot; bump it by hand like whisper.cpp.
