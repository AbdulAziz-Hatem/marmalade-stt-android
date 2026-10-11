// Vendored from sherpa-onnx v1.13.8 (https://github.com/k2-fsa/sherpa-onnx),
// sherpa-onnx/kotlin-api/OnlineStream.kt at commit 11afbd009a7f8c08f4bcf2fc1b265d0df4670fbf.
// Copyright Xiaomi Corporation and the sherpa-onnx contributors.
// Licensed under the Apache License, Version 2.0; see assets/licenses/Apache-2.0.txt
// and NOTICE.md. Unmodified apart from this header. Keep in step with the
// third_party/sherpa-onnx submodule: the JNI side reads these classes by name.

package com.k2fsa.sherpa.onnx

class OnlineStream(var ptr: Long = 0) {
    init {
        require(ptr != 0L) { "Failed to create native OnlineStream" }
    }

    fun acceptWaveform(samples: FloatArray, sampleRate: Int) =
        acceptWaveform(ptr, samples, sampleRate)

    fun inputFinished() = inputFinished(ptr)

    fun setOption(key: String, value: String) = setOption(ptr, key, value)

    fun getOption(key: String): String = getOption(ptr, key)

    protected fun finalize() {
        if (ptr != 0L) {
            delete(ptr)
            ptr = 0
        }
    }

    fun release() = finalize()

    fun use(block: (OnlineStream) -> Unit) {
        try {
            block(this)
        } finally {
            release()
        }
    }

    private external fun acceptWaveform(ptr: Long, samples: FloatArray, sampleRate: Int)
    private external fun inputFinished(ptr: Long)
    private external fun setOption(ptr: Long, key: String, value: String)
    private external fun getOption(ptr: Long, key: String): String
    private external fun delete(ptr: Long)


    companion object {
        init {
            System.loadLibrary("sherpa-onnx-jni")
        }
    }
}
