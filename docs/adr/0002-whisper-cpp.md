# 2. On-device recognition with whisper.cpp through JNI

- Status: Accepted
- Date: 2026-10-10

## Context

The engine must be fully offline, good at Danish and English, and on par with the macOS and iOS
apps (Whisper through WhisperKit). Options:

| Option | Offline | Danish | Notes |
| --- | --- | --- | --- |
| Android `SpeechRecognizer` (on-device, API 31+) | Only if the device has the language pack | Depends on the OEM and Google's packs; often missing or cloud-only | No control over the model, quality differs per phone, results differ from our other apps. Rejected. |
| TFLite Whisper (e.g. `whisper.tflite` ports) | Yes | Mostly English-only tiny/base exports | Few maintained multilingual exports, own tokenizer and decoding loop to write. Rejected. |
| ONNX Runtime (e.g. sherpa-onnx Whisper) | Yes | Yes (multilingual Whisper) | Viable. Larger runtime (+10 MB), int8 Small is ~360 MB, a second decoder implementation to trust. |
| **whisper.cpp via JNI/NDK** | Yes | Yes (multilingual Whisper) | Mature, MIT, small (~3 MB native library), runs quantised ggml models (Small q5_1 is 190 MB), supports initial prompts, language selection, and abort callbacks, which our controller needs. |

## Decision

Use **whisper.cpp 1.9.5**, built from its release tarball pinned by SHA-256 in
`app/src/main/cpp/CMakeLists.txt`, with a ~150-line JNI bridge (`whisper_jni.cpp`). The model is
loaded straight from the uncompressed APK asset through a file-descriptor loader; nothing is copied.
Decoding is greedy, with `DictationPrompt` as the initial prompt, timestamps off, the chosen
language (or "auto"), and an abort callback wired to coroutine cancellation.

Native code is built for **arm64-v8a** (phones) and **x86_64** (emulator) with baseline CPU
features only (no `-march=native`, no dotprod/fp16), so the same library runs on every supported
phone, including Cortex-A53 budget devices.

## Consequences

- Dependabot can't bump whisper.cpp (it's a CMake download); bump URL and hash together by hand,
  and update `THIRD_PARTY_NOTICES.md`.
- Baseline ARMv8 code leaves speed on the table on newer phones. Follow-up, once measured on a real
  device: build an extra armv8.2-a+dotprod+fp16 variant and choose at runtime from the CPU features.
- Debug builds compile whisper.cpp with `-O3` too; unoptimised it is unusably slow.

## Update (2026-10-11)

On a phone, Small was accurate and slow. `whisper_full` was encoding the full 30-second context
even for a short clip, and a difficult clip retried with five decoders. Decoding now sets
`audio_ctx` to the clip (plus a short tail), uses one greedy decoder, asks the scheduler for the
fast cores, and pins the worker to that cluster. The model is unchanged. A per-CPU dotprod build
is still open if this is not enough.
