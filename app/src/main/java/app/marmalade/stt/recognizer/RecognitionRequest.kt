package app.marmalade.stt.recognizer

import android.annotation.SuppressLint
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.speech.RecognizerIntent
import app.marmalade.stt.data.SpeechPrefs
import app.marmalade.stt.install.SttEngineDescriptor
import app.marmalade.stt.speech.MIC_SAMPLE_RATE
import app.marmalade.stt.speech.PcmFormat
import app.marmalade.stt.speech.SessionOptions
import kotlin.math.roundToLong

/**
 * What a caller asked for in its `RecognizerIntent` extras (#90), for the
 * RecognitionService and the speech-input activity. Which extras we honour,
 * and why the others don't apply, is the table in ARCHITECTURE.md
 * ("Speech APIs").
 *
 * @property language `EXTRA_LANGUAGE`, a BCP-47 tag ([requestedLanguage]), or null.
 * @property partials `EXTRA_PARTIAL_RESULTS`.
 * @property maxResults `EXTRA_MAX_RESULTS`, at least 1. We return one
 *   hypothesis (two with [formatting]), never more than this.
 * @property formatting `EXTRA_ENABLE_FORMATTING` was set: each result list
 *   carries the formatted text, then the raw text.
 * @property hidePartialTrailingPunctuation `EXTRA_HIDE_PARTIAL_TRAILING_PUNCTUATION`.
 * @property completeSilenceMs `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS`:
 *   the pause that ends the input, instead of the user's end of speech.
 *   Within [MIN_PAUSE_MS]..[MAX_PAUSE_MS].
 * @property minimumLengthMs `EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS`: the
 *   request doesn't end on its own before this long. At most [MAX_MINIMUM_LENGTH_MS].
 * @property segmented `EXTRA_SEGMENTED_SESSION`: results come per utterance
 *   (`onSegmentResults`), then `onEndOfSegmentedSession`.
 * @property audioSource the format of the caller's `EXTRA_AUDIO_SOURCE`
 *   stream ([PcmFormat]: Android's defaults where an extra is missing), or
 *   null to use the mic. The descriptor itself stays in the intent.
 * @property webSearch `EXTRA_LANGUAGE_MODEL` is `LANGUAGE_MODEL_WEB_SEARCH`:
 *   Android's hint for a short query ([shortCommand]).
 */
data class RecognitionRequest(
    val language: String? = null,
    val partials: Boolean = false,
    val maxResults: Int = DEFAULT_MAX_RESULTS,
    val formatting: Boolean = false,
    val hidePartialTrailingPunctuation: Boolean = false,
    val completeSilenceMs: Long? = null,
    val minimumLengthMs: Long? = null,
    val segmented: Segmented? = null,
    val audioSource: PcmFormat? = null,
    val webSearch: Boolean = false,
) {
    /** The kind of segmented session asked for (the extra's value names the extra that ends it). */
    enum class Segmented {
        /** Segments until the caller closes its audio source. */
        AUDIO_SOURCE,

        /** Segments, and the session runs at least [minimumLengthMs]. */
        MINIMUM_LENGTH,

        /** Segments until a pause of [completeSilenceMs]. */
        COMPLETE_SILENCE,
    }

    /**
     * What ends the request on its own ([OneShotEnd]). A caller's stream ends
     * like the mic (#116): a live one may stay open long after the speech
     * (Waze holds its pipe 10 s and leaves the end of speech to us), and one
     * that closes first ends the request anyway, with what was heard. A
     * session segmented by the stream runs to the stream's end, and so does
     * a [fromFile] stream (the descriptor is a regular file): a recording has
     * no latency to win, and its caller wants all of it, as before #116.
     */
    fun end(fromFile: Boolean = false): OneShotEnd = when {
        segmented == Segmented.AUDIO_SOURCE || (audioSource != null && fromFile) -> OneShotEnd.AudioEnd
        segmented == Segmented.COMPLETE_SILENCE && completeSilenceMs != null -> OneShotEnd.Pause(completeSilenceMs)
        else -> OneShotEnd.FirstEnd
    }

    /**
     * A short command (#120), which ends with Pause unless the user chose an
     * end of speech for other apps (`SpeechPrefs.endFor`): a caller's own
     * live audio (a pipe or socket, not a [fromFile] recording: Waze, car and
     * assistant apps send commands this way), or a [webSearch] request. A
     * session segmented by that live stream is one too (#124: Waze asks for
     * one and stops at our first `onEndOfSpeech`); one segmented by a pause
     * or a minimum length asked for many utterances, and isn't.
     */
    fun shortCommand(fromFile: Boolean = false): Boolean = when (segmented) {
        null -> webSearch || (audioSource != null && !fromFile)
        Segmented.AUDIO_SOURCE -> audioSource != null && !fromFile
        else -> false
    }

    /**
     * The longest the request may run, from its start, before it is stopped
     * (what was heard is still transcribed): [MAX_MIC_LENGTH_MS] from the
     * mic. Null for a caller's stream, which [app.marmalade.stt.speech.StreamCapture]
     * bounds by its audio instead (a file may be read faster than real time).
     */
    fun maxLengthMs(): Long? = if (audioSource == null) MAX_MIC_LENGTH_MS else null

    /**
     * The caller's pause, as the engine's end of utterance, in seconds: a plain
     * request's `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS`. A segmented
     * session ended by it keeps the user's end of utterance for its segments.
     */
    fun enginePauseSeconds(): Float? =
        completeSilenceMs?.takeIf { segmented != Segmented.COMPLETE_SILENCE }?.let { it / 1000f }

    /**
     * The request as the speak-now sheet runs it: a plain one-shot from the
     * mic. A segmented session and a caller's audio stream are
     * RecognitionService features (the sheet is the user speaking to it,
     * once), so their extras are dropped; the pause of a segmented
     * `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS` session then applies
     * as a plain request's ([enginePauseSeconds]).
     */
    fun forSheet(): RecognitionRequest = copy(segmented = null, audioSource = null)

    /**
     * The cleanup language for this request when [engine] runs it (the
     * route's engine, or null for the active one), or null when the stored
     * settings' applies. The caller's [language] counts as the chosen
     * recognition language for this request only ([SpeechPrefs.forRequestLanguage]).
     */
    fun cleanupLanguage(prefs: SpeechPrefs, engine: SttEngineDescriptor?): String? {
        val requestPrefs = prefs
            .let { if (language != null) it.forRequestLanguage(language) else it }
            .let { if (engine != null) it.copy(selectedEngineId = engine.id) else it }
        return if (requestPrefs != prefs) requestPrefs.cleanupLanguage() else null
    }

    /**
     * The results list for [text] ([raw]: the same before `:textproc`):
     * the formatted text, then the raw text when [formatting] asked for both
     * and [maxResults] allows two.
     */
    fun hypotheses(text: String, raw: String): ArrayList<String> =
        if (formatting && maxResults >= 2) arrayListOf(text, raw) else arrayListOf(text)

    /** A partial as the caller wants it: without trailing punctuation if asked. */
    fun partial(text: String): String =
        if (hidePartialTrailingPunctuation) text.trimEnd { it.isWhitespace() || it in TRAILING_PUNCTUATION } else text

    companion object {
        /**
         * The confidence we report for each result (`CONFIDENCE_SCORES`,
         * `EXTRA_CONFIDENCE_SCORES`): -1, Android's "unavailable". Our
         * engines give no confidence, and apps that read the array unguarded
         * mustn't crash on a missing one.
         */
        const val CONFIDENCE_UNAVAILABLE = -1f

        /**
         * The shortest and longest pause a caller may ask for: the Settings
         * slider's start, and 5 s ([SessionOptions.MAX_CALLER_SILENCE_SECONDS]).
         * Longer would hold the mic open on the caller's say-so.
         */
        val MIN_PAUSE_MS = (SessionOptions.MIN_SILENCE_RANGE_START * 1000).roundToLong()
        val MAX_PAUSE_MS = (SessionOptions.MAX_CALLER_SILENCE_SECONDS * 1000).roundToLong()

        /** The longest minimum length a caller may ask for: a minute. */
        const val MAX_MINIMUM_LENGTH_MS = 60_000L

        /**
         * The longest a request from the mic runs (#90): ten minutes, ample
         * for a segmented session, so no caller holds the mic and the engine
         * for ever (the keyboard and Dictate would be turned away busy).
         */
        const val MAX_MIC_LENGTH_MS = 600_000L

        private const val TRAILING_PUNCTUATION = ".,;:!?。、，！？…"

        /**
         * The request [intent] describes. Read with the intent's own
         * accessors, never `intent.extras`: that copies the bundle, and a
         * copy leaves the parcel the intent arrived in (with its duplicate of
         * an `EXTRA_AUDIO_SOURCE` descriptor) open until the garbage
         * collector gets to it, so a caller writing after the end never sees
         * its pipe close (#90). The service takes the descriptor first; once
         * every parcelable extra has been read, Android frees that parcel. An
         * extra carrying other parcelables that nobody reads can still keep
         * it alive.
         */
        // The API 33+ extra names are plain strings: on older versions they are simply absent.
        @SuppressLint("InlinedApi")
        fun from(intent: Intent, sdkInt: Int = Build.VERSION.SDK_INT): RecognitionRequest {
            // Callers pass these as Int or Long (the docs say both), or another Number: Waze's sampling
            // rate arrives as a Serializable (#116, `getIntExtra` logged "expected Integer but value
            // was a android.os.Parcel$LazyValue"). Typed on Android 13+, so a caller can't make us
            // deserialise a class of its choosing (Android checks the class before reading it);
            // anything that isn't a number counts as absent, and so does one that can't be read at all.
            fun long(key: String): Long? = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getSerializableExtra(key, Number::class.java)?.toLong()
                } else {
                    // Untyped: before Android 13 the first read unparcels every extra anyway.
                    @Suppress("DEPRECATION")
                    (intent.getSerializableExtra(key) as? Number)?.toLong()
                }
            }.getOrNull()?.takeIf { it > 0 }
            val segmented = when (intent.getStringExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION)) {
                RecognizerIntent.EXTRA_AUDIO_SOURCE -> Segmented.AUDIO_SOURCE
                RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS -> Segmented.MINIMUM_LENGTH
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS -> Segmented.COMPLETE_SILENCE
                else -> null
            }
            val audioSource = if (intent.hasExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE)) {
                // Read like the lengths: zero or less counts as absent; beyond Int is a format
                // StreamCapture refuses (ERROR_AUDIO), never one wrapped round into range.
                fun int(key: String, default: Int): Int = long(key)?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: default
                PcmFormat(
                    sampleRate = int(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, MIC_SAMPLE_RATE),
                    channels = int(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1),
                    encoding = int(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT),
                )
            } else {
                null
            }
            return RecognitionRequest(
                language = requestedLanguage(intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE)),
                partials = intent.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false),
                maxResults = intent.getIntExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 0).let { if (it < 1) DEFAULT_MAX_RESULTS else it },
                formatting = intent.getStringExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING) != null,
                hidePartialTrailingPunctuation = intent.getBooleanExtra(RecognizerIntent.EXTRA_HIDE_PARTIAL_TRAILING_PUNCTUATION, false),
                completeSilenceMs = long(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS)
                    ?.coerceIn(MIN_PAUSE_MS, MAX_PAUSE_MS),
                minimumLengthMs = long(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS)
                    ?.coerceAtMost(MAX_MINIMUM_LENGTH_MS),
                // An audio-source session needs the audio; without it, it's a plain request. Segmented
                // sessions have their callbacks from Android 13.
                segmented = segmented?.takeUnless { (it == Segmented.AUDIO_SOURCE && audioSource == null) || sdkInt < Build.VERSION_CODES.TIRAMISU },
                audioSource = audioSource,
                webSearch = intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL) == RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH,
            )
        }

        /** Without `EXTRA_MAX_RESULTS` "the recognizer chooses": we have room for both hypotheses. */
        private const val DEFAULT_MAX_RESULTS = 5
    }
}
