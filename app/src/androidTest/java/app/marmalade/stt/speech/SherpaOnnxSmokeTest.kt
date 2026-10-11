package app.marmalade.stt.speech

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device smoke test for the from-source native runtime (issue #2):
 * libsherpa-onnx-jni.so loads, finds libonnxruntime.so, and builds a Silero
 * VAD from the model bundled in assets. A second of silence must not count
 * as speech.
 */
@RunWith(AndroidJUnit4::class)
class SherpaOnnxSmokeTest {

    @Test
    fun loadsJniAndRunsBundledSileroVad() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val vad = Vad(
            assetManager = assets,
            config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(model = "vad/silero_vad.onnx"),
                sampleRate = 16_000,
                numThreads = 1,
            ),
        )
        try {
            // 1 s of silence in 512-sample windows, the model's frame size.
            val window = FloatArray(512)
            repeat(16_000 / 512) { vad.acceptWaveform(window) }
            vad.flush()
            assertFalse("silence detected as speech", vad.isSpeechDetected())
            assertTrue("silence produced a speech segment", vad.empty())
        } finally {
            vad.release()
        }
    }
}
