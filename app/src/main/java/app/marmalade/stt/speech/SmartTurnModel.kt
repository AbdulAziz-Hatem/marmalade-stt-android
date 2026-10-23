package app.marmalade.stt.speech

import android.content.Context
import android.util.Log
import app.marmalade.stt.speech.turn.OnnxTurnDetector
import app.marmalade.stt.speech.turn.SmartTurnAsset
import app.marmalade.stt.speech.turn.TurnDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where Smart Turn sessions get their detector (#43): the bundled
 * [SmartTurnAsset] model (#44), loaded once and kept while the app runs
 * (8.7 MB). [SpeechEngineManager.listen] asks for it when the surface's end
 * of speech is [EndOfDictation.SMART_TURN]; null (the model failed to load)
 * makes the session behave as Pause.
 */
@Singleton
class SmartTurnModel @Inject constructor(@ApplicationContext private val context: Context) {

    private var detector: TurnDetector? = null
    private var failureLogged = false

    /**
     * The loaded detector, or null when it failed to load. The first call
     * loads it (blocking, ~100 ms); call it off the main thread.
     */
    @Synchronized
    fun detectorOrNull(): TurnDetector? {
        detector?.let { return it }
        return try {
            val model = context.assets.open(SmartTurnAsset.PATH).use { it.readBytes() }
            OnnxTurnDetector(model, SpeedKnobs.smartTurnThreads ?: OnnxTurnDetector.DEFAULT_THREADS).also {
                detector = it
                Log.i(TAG, "Smart Turn loaded")
            }
        } catch (e: Exception) {
            if (!failureLogged) Log.w(TAG, "Couldn't load the Smart Turn model; Smart Turn behaves as Pause", e)
            failureLogged = true
            null
        } catch (e: LinkageError) {
            // ONNX Runtime's native library is missing or broken.
            if (!failureLogged) Log.w(TAG, "ONNX Runtime failed to load; Smart Turn behaves as Pause", e)
            failureLogged = true
            null
        }
    }

    private companion object {
        const val TAG = "SmartTurnModel"
    }
}
