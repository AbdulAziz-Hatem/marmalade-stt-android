package app.marmalade.stt.recognizer

import android.app.Application
import android.content.Intent
import android.media.AudioFormat
import android.os.ParcelFileDescriptor
import android.speech.RecognizerIntent
import app.marmalade.stt.speech.PcmFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Parsing a caller's `RecognizerIntent` extras (#90). Real Intents and Bundles, so Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RecognitionRequestTest {

    private val pipes = mutableListOf<ParcelFileDescriptor>()

    @After
    fun closePipes() = pipes.forEach { it.close() }

    private fun request(build: Intent.() -> Unit = {}) =
        RecognitionRequest.from(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply(build))

    /** Any readable descriptor will do: the request only notes that the caller sent one. */
    private fun Intent.withAudioSource(): Intent {
        val pipe = ParcelFileDescriptor.createPipe().also { pipes += it }
        return putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
    }

    @Test
    fun `no extras is the default request`() {
        assertEquals(RecognitionRequest(), request())
        assertEquals(RecognitionRequest(), RecognitionRequest.from(Intent()))
        val defaults = RecognitionRequest()
        assertNull(defaults.language)
        assertFalse(defaults.partials)
        assertEquals(5, defaults.maxResults)
        assertFalse(defaults.formatting)
    }

    @Test
    fun `the language is trimmed, and a blank one is none`() {
        assertEquals("de-DE", request { putExtra(RecognizerIntent.EXTRA_LANGUAGE, " de-DE ") }.language)
        assertNull(request { putExtra(RecognizerIntent.EXTRA_LANGUAGE, "  ") }.language)
    }

    @Test
    fun `plain boolean extras`() {
        val r = request {
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_HIDE_PARTIAL_TRAILING_PUNCTUATION, true)
        }
        assertTrue(r.partials)
        assertTrue(r.hidePartialTrailingPunctuation)
    }

    @Test
    fun `max results - absent, zero or negative is the recognizer's choice, else as asked`() {
        fun max(value: Int?) = request { if (value != null) putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, value) }.maxResults
        assertEquals(5, max(null))
        assertEquals(5, max(0))
        assertEquals(5, max(-3))
        assertEquals(1, max(1))
        assertEquals(3, max(3))
    }

    @Test
    fun `formatting is on for either formatting value`() {
        for (value in listOf(RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY, RecognizerIntent.FORMATTING_OPTIMIZE_LATENCY)) {
            assertTrue(value, request { putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, value) }.formatting)
        }
    }

    @Test
    fun `silence and minimum length parse as Int or Long, and zero or less is ignored`() {
        val asInt = request {
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000)
        }
        assertEquals(2000L, asInt.completeSilenceMs)
        assertEquals(5000L, asInt.minimumLengthMs)
        val asLong = request {
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 7000L)
        }
        assertEquals(2500L, asLong.completeSilenceMs)
        assertEquals(7000L, asLong.minimumLengthMs)
        val notPositive = request {
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 0)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, -1L)
        }
        assertNull(notPositive.completeSilenceMs)
        assertNull(notPositive.minimumLengthMs)
    }

    /**
     * A caller's own Serializable that can't be read here, as a class only
     * the caller has (ClassNotFoundException in our process) can't.
     */
    class Unreadable : java.io.Serializable {
        private fun readObject(input: java.io.ObjectInputStream): Unit = throw java.io.InvalidObjectException("not here")
    }

    @Test
    fun `a length extra holding something else is ignored, never a crash`() {
        val intent = Intent().apply {
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, Unreadable())
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, "5000")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
        }
        // Over a parcel, as from another app.
        val parcel = android.os.Parcel.obtain()
        val received = try {
            intent.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            Intent.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
        // (Robolectric's Parcel lacks Android 13's check of the class name against Number before
        // reading, so whether the value is read at all is checked on Android, not here.)
        val request = RecognitionRequest.from(received)
        assertNull(request.completeSilenceMs)
        assertNull(request.minimumLengthMs) // a String isn't a length
        assertEquals("de-DE", request.language) // the rest of the request stands
    }

    @Test
    fun `the pause and the minimum length are bounded, so no caller holds the mic for ever`() {
        val huge = request {
            putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, Long.MAX_VALUE)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, Long.MAX_VALUE)
        }
        assertEquals(5_000L, huge.completeSilenceMs)
        assertEquals(60_000L, huge.minimumLengthMs)
        // The segmented pause, which the engine's own clamp never sees, too.
        assertEquals(OneShotEnd.Pause(5_000), huge.end())
        val tiny = request { putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1) }
        assertEquals(300L, tiny.completeSilenceMs)
    }

    @Test
    fun `a mic request is capped at ten minutes, a stream is bounded by its audio instead`() {
        assertEquals(600_000L, RecognitionRequest().maxLengthMs())
        assertNull(RecognitionRequest(audioSource = PcmFormat()).maxLengthMs())
    }

    @Test
    fun `before Android 13 there is no segmented session`() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS)
        assertNull(RecognitionRequest.from(intent, sdkInt = 32).segmented)
        assertEquals(RecognitionRequest.Segmented.MINIMUM_LENGTH, RecognitionRequest.from(intent, sdkInt = 33).segmented)
    }

    @Test
    fun `each segmented-session value`() {
        fun segmented(value: String?) = request { putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, value) }.segmented
        assertEquals(
            RecognitionRequest.Segmented.MINIMUM_LENGTH,
            segmented(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS),
        )
        assertEquals(
            RecognitionRequest.Segmented.COMPLETE_SILENCE,
            segmented(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS),
        )
        assertNull(segmented("something else"))
        assertNull(segmented(null))
        // Ended by the caller's stream: only with a stream.
        val withStream = request {
            putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
            withAudioSource()
        }
        assertEquals(RecognitionRequest.Segmented.AUDIO_SOURCE, withStream.segmented)
    }

    @Test
    fun `an audio-source session without a stream is a plain request`() {
        val r = request { putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE) }
        assertNull(r.segmented)
        assertNull(r.audioSource)
    }

    @Test
    fun `a stream without format extras gets Android's defaults`() {
        val r = request { withAudioSource() }
        assertEquals(PcmFormat(sampleRate = 16_000, channels = 1, encoding = AudioFormat.ENCODING_PCM_16BIT), r.audioSource)
        assertNull(r.segmented)
    }

    @Test
    fun `a stream's format extras are read`() {
        val r = request {
            withAudioSource()
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 48_000)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 2)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_FLOAT)
        }
        assertEquals(PcmFormat(48_000, 2, AudioFormat.ENCODING_PCM_FLOAT), r.audioSource)
    }

    @Test
    fun `a stream's format extras are read whatever number type the caller used`() {
        // #116: Waze's sampling rate arrived as a Parcel LazyValue (a Serializable), and getIntExtra
        // fell back to 16 kHz. Over a parcel, as from another app.
        val intent = Intent().apply {
            withAudioSource()
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, java.math.BigDecimal(48_000))
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 2L)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, java.util.concurrent.atomic.AtomicInteger(AudioFormat.ENCODING_PCM_FLOAT))
        }
        val parcel = android.os.Parcel.obtain()
        val received = try {
            intent.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            Intent.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
        assertEquals(PcmFormat(48_000, 2, AudioFormat.ENCODING_PCM_FLOAT), RecognitionRequest.from(received).audioSource)
        received.getParcelableExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, ParcelFileDescriptor::class.java)?.close()
    }

    @Test
    fun `a stream's format extra that isn't a usable number - the default, or a format we refuse`() {
        val r = request {
            withAudioSource()
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 0)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, "2")
            // Beyond Int: never wrapped round into a rate that looks valid.
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, (1L shl 32) + AudioFormat.ENCODING_PCM_16BIT)
        }
        assertEquals(PcmFormat(16_000, 1, Int.MAX_VALUE), r.audioSource)
    }

    @Test
    fun `hypotheses - the raw text follows only with formatting and room for two`() {
        assertEquals(listOf("Hello."), RecognitionRequest().hypotheses("Hello.", "hello"))
        assertEquals(listOf("Hello.", "hello"), RecognitionRequest(formatting = true).hypotheses("Hello.", "hello"))
        assertEquals(listOf("Hello.", "hello"), RecognitionRequest(formatting = true, maxResults = 2).hypotheses("Hello.", "hello"))
        assertEquals(listOf("Hello."), RecognitionRequest(formatting = true, maxResults = 1).hypotheses("Hello.", "hello"))
    }

    @Test
    fun `partials lose trailing punctuation only when asked`() {
        val hide = RecognitionRequest(hidePartialTrailingPunctuation = true)
        assertEquals("Hello, world", hide.partial("Hello, world. "))
        assertEquals("Wait", hide.partial("Wait?!…"))
        assertEquals("こんにちは", hide.partial("こんにちは。"))
        assertEquals("你好", hide.partial("你好，"))
        assertEquals("", hide.partial(""))
        // Punctuation inside stays.
        assertEquals("a. b", hide.partial("a. b."))
        assertEquals("Hello, world. ", RecognitionRequest().partial("Hello, world. "))
    }
}
