// Vendored from sherpa-onnx v1.13.8 (https://github.com/k2-fsa/sherpa-onnx),
// sherpa-onnx/kotlin-api/QnnConfig.kt at commit 11afbd009a7f8c08f4bcf2fc1b265d0df4670fbf.
// Copyright Xiaomi Corporation and the sherpa-onnx contributors.
// Licensed under the Apache License, Version 2.0; see assets/licenses/Apache-2.0.txt
// and NOTICE.md. Unmodified apart from this header. Keep in step with the
// third_party/sherpa-onnx submodule: the JNI side reads these classes by name.

package com.k2fsa.sherpa.onnx

data class QnnConfig(
    var backendLib: String = "",
    var contextBinary: String = "",
    var systemLib: String = "",
)
