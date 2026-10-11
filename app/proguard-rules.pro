# JNI resolves Java_com_captainduckay_dictator_android_WhisperNative_* by class and method name.
-keep class com.captainduckay.dictator.android.WhisperNative { native <methods>; }

# Entry points of the on-device JFK test (app/src/androidTest), which can run against the release build.
-keep class com.captainduckay.dictator.android.WhisperTranscriber { public *; }
-keep class com.captainduckay.dictator.android.SpeechModel { public *; }
-keep class com.captainduckay.dictator.android.ModelDownloadWorker { public *; }
