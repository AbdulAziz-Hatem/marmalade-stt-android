package app.marmalade.stt.speech

/**
 * What a session does with the text of each finished utterance, per
 * [EndOfDictation] mode (#43). Shared by [StreamingSession] and
 * [OnlineSession]; pure, so the rules are tested once.
 *
 * - [EndOfDictation.PAUSE] and [EndOfDictation.SMART_TURN]: each utterance is
 *   a [SttEvent.Final] of its own. (For Smart Turn the session hands over a
 *   whole turn, decoded once, as one utterance.)
 * - [EndOfDictation.TAP_STOP]: utterances are kept, shown as a
 *   [SttEvent.Partial], and joined into one Final when the session ends.
 * - [EndOfDictation.END_WORD]: kept as for Tap stop, until an utterance's own
 *   text ends with an end word standing alone ([EndWords.match]): then one
 *   Final (with [SttEvent.Final.endWord]) of everything kept plus the text
 *   before the word, and keeping starts over. That Final may be empty.
 *
 * An utterance cut at the cap (#48, [cut]) isn't finished: in End word its
 * part so far is held and matched together with the rest at the
 * utterance's real end, so a cut just after a mid-sentence end word doesn't
 * end the dictation, and an end word just after the cut still does. The
 * other modes treat the part as a finished utterance.
 *
 * A pre-speech phantom ([isPreSpeechPhantom]) as the session's first text
 * is held back: the engine writes it for the noise, breath or click before
 * speech, as a Final of its own, often too loud for the near-silence gate.
 * Parakeet ja's 「うん」 (#60: 28 of 200 FLEURS-ja clips, every one followed
 * by real speech), Whisper's distinctive phrases and 「おわり」, Qwen3's 「システム」
 * (#57, [SttEngineDescriptor.isPreSpeechPhantom]). When more text follows it
 * is dropped; when the session ends without more, it was real and is sent
 * then. So a 「うん」 said first, then a pause long enough to end the
 * utterance, then more speech, loses the 「うん」.
 *
 * @param end the session's effective mode ([SessionOptions.effectiveEnd]).
 */
internal class FinishedUtterances(
    private val end: EndOfDictation,
    private val endWords: List<String>,
    private val isPreSpeechPhantom: (String) -> Boolean = { false },
) {
    companion object {
        /** [text] is [phantom] alone, punctuation and spaces aside (「うん。」, not 「うんうん」). */
        fun isPhantom(text: String, phantom: String?): Boolean =
            phantom != null && text.filter { it.isLetterOrDigit() } == phantom
    }

    private val _kept = mutableListOf<String>()
    private var keptQuiet = Quiet.SILENT
    private var anyFinal = false

    // The session's first text, if it is the pre-speech phantom, until we know whether speech follows.
    private var phantom: Pair<String, Quiet>? = null
    private var anyText = false

    // End word: the parts of an utterance cut at the cap, until its real end.
    private val held = mutableListOf<String>()
    private var heldQuiet = Quiet.SILENT

    /** The texts kept for a later Final (Tap stop and End word); partials show them first. */
    val kept: List<String> get() = _kept + held

    /**
     * The utterance in progress was cut at the cap: [text] is its part so
     * far, [quiet] that part's level. Returns the events to send.
     */
    fun cut(text: String, quiet: Quiet): List<SttEvent> {
        if (end != EndOfDictation.END_WORD) return finished(text, quiet)
        if (text.isNotEmpty()) {
            held += text
            heldQuiet = heldQuiet and quiet
        }
        return if (kept.isEmpty()) emptyList() else listOf(SttEvent.Partial(kept.joinToString(" ")))
    }

    /**
     * One utterance's [text] (trimmed, possibly empty) is done. [quiet]: how
     * quiet its audio was. [atStop]: flushed by stop, so no Partial is sent
     * for it (the last Final follows at once). Returns the events to send.
     */
    fun finished(text: String, quiet: Quiet, atStop: Boolean = false): List<SttEvent> {
        if (held.isNotEmpty()) {
            // The real end of an utterance cut at the cap: match the whole of it.
            val whole = (held + listOfNotNull(text.takeIf { it.isNotEmpty() })).joinToString(" ")
            val wholeQuiet = if (text.isEmpty()) heldQuiet else heldQuiet and quiet
            held.clear()
            heldQuiet = Quiet.SILENT
            return finished(whole, wholeQuiet, atStop)
        }
        if (text.isNotEmpty()) {
            if (!anyText && isPreSpeechPhantom(text)) {
                phantom = text to quiet
                return emptyList()
            }
            anyText = true
            phantom = null // more speech followed: it was noise
        }
        if (end == EndOfDictation.END_WORD) {
            val before = EndWords.match(text, endWords)
            if (before != null) {
                val texts = _kept + listOfNotNull(before.takeIf { it.isNotEmpty() })
                // The word alone says nothing about whether the kept speech was real.
                val keptAndBefore = if (before.isEmpty()) keptQuiet else keptQuiet and quiet
                _kept.clear()
                keptQuiet = Quiet.SILENT
                anyFinal = true
                return listOf(SttEvent.Final(texts.joinToString(" "), keptAndBefore, endWord = true))
            }
        }
        if (text.isEmpty()) return emptyList()
        return when (end) {
            EndOfDictation.PAUSE, EndOfDictation.SMART_TURN -> {
                anyFinal = true
                listOf(SttEvent.Final(text, quiet))
            }
            EndOfDictation.TAP_STOP, EndOfDictation.END_WORD -> {
                _kept += text
                keptQuiet = keptQuiet and quiet
                if (atStop) emptyList() else listOf(SttEvent.Partial(_kept.joinToString(" ")))
            }
        }
    }

    /** The session is over: the kept texts as the last Final, or [SttEvent.NoSpeech] if it produced nothing. */
    fun ended(): List<SttEvent> = when {
        held.isNotEmpty() -> finished("", Quiet.SILENT, atStop = true) + ended()
        phantom != null -> {
            // Nothing followed: a real reply.
            val (text, quiet) = phantom!!
            phantom = null
            anyText = true
            finished(text, quiet, atStop = true) + ended()
        }
        _kept.isNotEmpty() -> listOf(SttEvent.Final(_kept.joinToString(" "), keptQuiet))
        !anyFinal -> listOf(SttEvent.NoSpeech) // not an error, but the caller must learn the session is over
        else -> emptyList()
    }
}
