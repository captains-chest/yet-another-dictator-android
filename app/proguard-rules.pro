# Entry points of the on-device JFK test (app/src/androidTest), which can run against the release build.
-keep class com.captainduckay.dictator.android.ParakeetTranscriber { public *; }
-keep class com.captainduckay.dictator.android.SpeechModel { public *; }
-keep class com.captainduckay.dictator.android.ModelDownloadWorker { public *; }

# sherpa-onnx's native code reads its Kotlin config classes and fields by name over JNI.
-keep class com.k2fsa.sherpa.onnx.** { *; }
