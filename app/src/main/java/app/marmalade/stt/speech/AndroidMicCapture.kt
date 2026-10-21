package app.marmalade.stt.speech

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

private const val TAG = "MicCapture"

/**
 * Opens the microphone and starts capture: `AudioRecord` with source
 * `VOICE_RECOGNITION`, the CDD-mandated flat capture path for speech
 * recognition (no telephony DSP; don't switch to `MIC`), 16 kHz mono PCM16.
 *
 * Needs RECORD_AUDIO at runtime. The caller asks for it; a missing grant
 * surfaces here as [MicCaptureException].
 *
 * @param hopSamples samples per emitted hop at [MIC_SAMPLE_RATE].
 * @param hardwareEffects attach the platform `NoiseSuppressor` and
 *   `AutomaticGainControl` to the record session where the device has them.
 *   On the VOICE_RECOGNITION path they are the only NS/AGC in the chain.
 * @param attribution an attribution context for another app (Android 12+,
 *   `Context.createContext` with the caller as next attribution source):
 *   the RecognitionService records for the app that asked, so Android checks
 *   and blames that app's mic access, and lets it through while that app is
 *   in the foreground and ours is not (#90). Null: the mic is ours.
 * @throws MicCaptureException when the mic can't be opened or started.
 */
// Permission is the caller's job; a missing grant is caught below as SecurityException.
@SuppressLint("MissingPermission")
fun openMicCapture(hopSamples: Int, hardwareEffects: Boolean, attribution: Context? = null): MicCapture {
    val minBuf = AudioRecord.getMinBufferSize(
        MIC_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
    )
    val bufferBytes = maxOf(minBuf * 2, hopSamples * 2 * 4)
    val record = try {
        if (attribution != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AudioRecord.Builder()
                .setContext(attribution)
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(MIC_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                )
                .setBufferSizeInBytes(bufferBytes)
                .build()
        } else {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MIC_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes,
            )
        }
    } catch (e: SecurityException) {
        throw MicCaptureException("Microphone permission not granted", e)
    } catch (e: IllegalArgumentException) {
        throw MicCaptureException("Microphone configuration rejected", e)
    } catch (e: UnsupportedOperationException) {
        throw MicCaptureException("Microphone configuration rejected", e)
    }
    if (record.state != AudioRecord.STATE_INITIALIZED) {
        record.release()
        throw MicCaptureException("Couldn't initialize the microphone")
    }

    var noiseSuppressor: NoiseSuppressor? = null
    var agc: AutomaticGainControl? = null
    if (hardwareEffects) {
        noiseSuppressor = if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(record.audioSessionId)?.also { it.enabled = true }
        } else {
            null
        }
        agc = if (AutomaticGainControl.isAvailable()) {
            AutomaticGainControl.create(record.audioSessionId)?.also { it.enabled = true }
        } else {
            null
        }
        Log.i(
            TAG,
            "Mic effects: noiseSuppressor=${noiseSuppressor?.enabled ?: "unavailable"} " +
                "agc=${agc?.enabled ?: "unavailable"}",
        )
    }

    fun releaseAll() {
        runCatching { noiseSuppressor?.release() }
        runCatching { agc?.release() }
        record.release()
    }

    try {
        record.startRecording()
    } catch (e: Exception) {
        releaseAll()
        throw MicCaptureException("Couldn't start recording", e)
    }
    // startRecording() can return without starting (e.g. another app holds
    // the mic); every read would then fail.
    if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
        releaseAll()
        throw MicCaptureException("Couldn't start recording (microphone busy?)")
    }
    return AndroidMicCapture(record, noiseSuppressor, agc, hopSamples)
}

private class AndroidMicCapture(
    private val record: AudioRecord,
    private val noiseSuppressor: NoiseSuppressor?,
    private val agc: AutomaticGainControl?,
    private val hopSamples: Int,
) : MicCapture {

    private val closed = AtomicBoolean(false)

    override val hops: Flow<FloatArray> = flow {
        val buffer = ShortArray(hopSamples)
        while (currentCoroutineContext().isActive && !closed.get()) {
            // Blocks in JNI with no suspension point; close() stopping and
            // releasing the AudioRecord is what unblocks it.
            val read = record.read(buffer, 0, buffer.size)
            when {
                read > 0 -> emit(FloatArray(read) { buffer[it] / 32768.0f })
                closed.get() -> break
                read == 0 -> continue
                // ERROR_DEAD_OBJECT, ERROR_INVALID_OPERATION, ...: retrying
                // would spin on the same error, so end the capture instead.
                else -> throw MicCaptureException("Microphone read failed (AudioRecord error $read)")
            }
        }
    }

    override val hasPlatformAgc: Boolean = agc?.enabled == true

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { noiseSuppressor?.release() }
        runCatching { agc?.release() }
        runCatching { record.stop() }
        runCatching { record.release() }
    }
}
