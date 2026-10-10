package com.captainduckay.dictator.android

/** The JNI surface of `whisper_jni.cpp`. Handles are opaque pointers; 0 means failure. */
internal object WhisperNative {
    init {
        System.loadLibrary("dictator_whisper")
    }

    external fun load(fd: Int, offset: Long, length: Long): Long

    /** Returns null when whisper.cpp failed or was aborted. */
    external fun transcribe(handle: Long, samples: FloatArray, language: String?, prompt: String, threads: Int): String?

    external fun abort(handle: Long)

    external fun free(handle: Long)
}
