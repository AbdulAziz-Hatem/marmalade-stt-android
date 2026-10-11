# Add project specific ProGuard rules here.

# Hilt: keep generated components
-keep class dagger.hilt.** { *; }
-keep class **_HiltComponents* { *; }

# Strip debug/verbose logging from RELEASE builds (R8 removes these calls and
# their argument computation). Info/warn/error are kept for crash diagnostics.
# Dictated text must never reach a log line of any level.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

# sherpa-onnx JNI: the native side looks up these classes, their fields
# (GetFieldID on the config data classes) and constructors by name, so R8 must
# not rename or strip anything in the vendored API package.
-keep class com.k2fsa.sherpa.onnx.** { *; }

# ONNX Runtime's Java API (the Granite engine, #32): libonnxruntime4j_jni.so
# finds its classes, constructors and fields by name, as sherpa-onnx's does.
-keep class ai.onnxruntime.** { *; }
