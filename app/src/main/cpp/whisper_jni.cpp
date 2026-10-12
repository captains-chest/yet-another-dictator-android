// JNI bridge to whisper.cpp. Kept deliberately small: load a model from an open file descriptor
// (the downloaded model file, ADR 0006), transcribe one clip, abort, free. No network, no files written.

#include <jni.h>
#include <android/log.h>
#include <unistd.h>

#include <sched.h>

#include <atomic>
#include <cstdio>
#include <string>

#include "whisper.h"

#define TAG "DictatorWhisper"

namespace {

struct Engine {
    whisper_context *context = nullptr;
    std::atomic<bool> abort{false};
};

struct FileLoader {
    FILE *file;
    long remaining;
};

size_t loader_read(void *ctx, void *output, size_t read_size) {
    auto *loader = static_cast<FileLoader *>(ctx);
    if (loader->remaining <= 0) return 0;
    size_t wanted = read_size > static_cast<size_t>(loader->remaining) ? static_cast<size_t>(loader->remaining) : read_size;
    size_t got = fread(output, 1, wanted, loader->file);
    loader->remaining -= static_cast<long>(got);
    return got;
}

bool loader_eof(void *ctx) {
    auto *loader = static_cast<FileLoader *>(ctx);
    return loader->remaining <= 0 || feof(loader->file);
}

void loader_close(void *) {}

bool should_abort(void *user_data) {
    return static_cast<Engine *>(user_data)->abort.load();
}

void quiet_log(ggml_log_level level, const char *text, void *) {
    // Never log transcript text: whisper.cpp only logs model/system messages here.
    if (level >= GGML_LOG_LEVEL_WARN) __android_log_print(ANDROID_LOG_WARN, TAG, "%s", text);
}

// Little cores make Whisper slower if a worker lands on one. Prefer the fast cluster, and keep the
// thread count to that cluster. Capacities are Android's own cpu_capacity files; a machine without
// them (or where every core is equal) just uses the fallback.
struct FastCores {
    cpu_set_t set{};
    int chosen = 0;
    bool pin = false;
};

FastCores detect_fast_cores() {
    FastCores picked;
    CPU_ZERO(&picked.set);
    int capacities[256];
    int count = 0;
    int best = 0;
    for (; count < 256; ++count) {
        char path[96];
        std::snprintf(path, sizeof(path), "/sys/devices/system/cpu/cpu%d/cpu_capacity", count);
        FILE *file = std::fopen(path, "r");
        if (file == nullptr) break;
        int capacity = 0;
        if (std::fscanf(file, "%d", &capacity) != 1) capacity = 0;
        std::fclose(file);
        capacities[count] = capacity;
        if (capacity > best) best = capacity;
    }
    if (count == 0 || best <= 0) return picked;
    // Half of the fastest core still includes the big cluster and leaves the little cores out.
    const int threshold = best / 2;
    for (int cpu = 0; cpu < count; ++cpu) {
        if (capacities[cpu] >= threshold) {
            CPU_SET(cpu, &picked.set);
            picked.chosen += 1;
        }
    }
    picked.pin = picked.chosen > 0;
    return picked;
}

const FastCores &fast_cores() {
    static const FastCores cached = detect_fast_cores();
    return cached;
}

void pin_calling_thread() {
    const FastCores &cores = fast_cores();
    if (cores.pin) sched_setaffinity(0, sizeof(cores.set), &cores.set);
}

int fast_thread_count(int fallback) {
    const FastCores &cores = fast_cores();
    if (!cores.pin) return fallback < 2 ? 2 : fallback;
    int threads = cores.chosen > 6 ? 6 : cores.chosen;
    if (threads < 2) threads = 2;
    return threads;
}

// Encoder frames are 20 ms (the mel hop is 10 ms, then the stride halves it). audio_ctx is that
// count. The default context is the full 30 s even when the clip is a sentence, which is most of
// the wait. A few extra frames keep the last syllable inside the window.
int encoder_frames_for(int sample_count) {
    constexpr int kSamplesPerFrame = 320;
    constexpr int kFullWindowFrames = 1500;
    constexpr int kTailFrames = 8;
    constexpr int kMinimumFrames = 16;
    int frames = (sample_count + kSamplesPerFrame - 1) / kSamplesPerFrame + kTailFrames;
    if (frames < kMinimumFrames) frames = kMinimumFrames;
    return frames >= kFullWindowFrames ? 0 : frames;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_captainduckay_dictator_android_WhisperNative_load(JNIEnv *, jobject, jint fd, jlong offset, jlong length) {
    whisper_log_set(quiet_log, nullptr);
    int own = dup(fd);
    if (own < 0) return 0;
    FILE *file = fdopen(own, "rb");
    if (file == nullptr) {
        close(own);
        return 0;
    }
    if (fseek(file, static_cast<long>(offset), SEEK_SET) != 0) {
        fclose(file);
        return 0;
    }
    FileLoader state{file, static_cast<long>(length)};
    whisper_model_loader loader{};
    loader.context = &state;
    loader.read = loader_read;
    loader.eof = loader_eof;
    loader.close = loader_close;

    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;
    // Flash attention is the default and stays on for the CPU build; it is the faster attention path.
    params.flash_attn = true;
    pin_calling_thread();
    whisper_context *context = whisper_init_with_params(&loader, params);
    fclose(file);
    if (context == nullptr) return 0;
    auto *engine = new Engine();
    engine->context = context;
    return reinterpret_cast<jlong>(engine);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_captainduckay_dictator_android_WhisperNative_transcribe(
    JNIEnv *env, jobject, jlong handle, jfloatArray samples, jstring language, jstring prompt, jint threads) {
    auto *engine = reinterpret_cast<Engine *>(handle);
    if (engine == nullptr) return nullptr;
    engine->abort.store(false);

    const char *language_chars = language != nullptr ? env->GetStringUTFChars(language, nullptr) : nullptr;
    const char *prompt_chars = env->GetStringUTFChars(prompt, nullptr);

    pin_calling_thread();
    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = fast_thread_count(threads);
    params.translate = false;
    params.no_timestamps = true;
    params.single_segment = true;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_special = false;
    params.print_timestamps = false;
    params.suppress_blank = true;
    // Temperature 0 already uses one decoder. best_of only applies to the hotter retries, and 5 of
    // those is what makes a difficult clip take several times longer.
    params.greedy.best_of = 1;
    params.audio_ctx = encoder_frames_for(env->GetArrayLength(samples));
    params.language = language_chars != nullptr ? language_chars : "auto";
    params.detect_language = false;
    params.initial_prompt = prompt_chars;
    params.abort_callback = should_abort;
    params.abort_callback_user_data = engine;

    jsize count = env->GetArrayLength(samples);
    jfloat *data = env->GetFloatArrayElements(samples, nullptr);
    int result = whisper_full(engine->context, params, data, count);
    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);

    std::string text;
    if (result == 0) {
        int segments = whisper_full_n_segments(engine->context);
        for (int i = 0; i < segments; ++i) text += whisper_full_get_segment_text(engine->context, i);
    }
    if (language_chars != nullptr) env->ReleaseStringUTFChars(language, language_chars);
    env->ReleaseStringUTFChars(prompt, prompt_chars);
    if (result != 0) return nullptr;
    return env->NewStringUTF(text.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_captainduckay_dictator_android_WhisperNative_abort(JNIEnv *, jobject, jlong handle) {
    auto *engine = reinterpret_cast<Engine *>(handle);
    if (engine != nullptr) engine->abort.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_captainduckay_dictator_android_WhisperNative_free(JNIEnv *, jobject, jlong handle) {
    auto *engine = reinterpret_cast<Engine *>(handle);
    if (engine == nullptr) return;
    whisper_free(engine->context);
    delete engine;
}
