// JNI bridge to whisper.cpp. Kept deliberately small: load a model from an open file descriptor
// (the downloaded model file, ADR 0006), transcribe one clip, abort, free. No network, no files written.

#include <jni.h>
#include <android/log.h>
#include <unistd.h>

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

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = threads;
    params.translate = false;
    params.no_timestamps = true;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_special = false;
    params.print_timestamps = false;
    params.suppress_blank = true;
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
