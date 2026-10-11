package app.marmalade.stt.speech

import app.marmalade.stt.textproc.Phantoms
import java.util.Locale

/**
 * Steadies the live partials of one utterance (#28). [StreamingSession]
 * re-decodes the growing audio for every partial, and a decode of audio that
 * ends mid-word changes its last words, or makes a small Whisper repeat them
 * ("a test test test test"). Shown raw, the text flickers.
 *
 * LocalAgreement-2 (as in whisper_streaming): words that two partials in a
 * row agree on, after the text already stable, become stable. Stable text is
 * never taken back, so it never flickers; what follows it (the unstable
 * tail) is the latest partial's. Each partial is first cleaned of sound tags
 * ([Phantoms.stripNonSpeech]: Whisper's "[speaking in foreign language]"
 * before the speech gets going would otherwise become stable words) and of a
 * repeat at its end ([Phantoms.cutTailRepeats]), so neither can become
 * stable. A partial's last word is never made stable: its audio is usually
 * cut off mid-word.
 *
 * A new partial may word the stable part differently (the model changed its
 * mind, or a long utterance's older audio was re-decoded, see
 * [StreamingSession.PARTIAL_WINDOW_SAMPLES]). Its tail starts where the
 * stable words best line up with it (fewest word edits, see [alignedEnd]).
 * Words are compared without case and punctuation.
 *
 * The Final replaces all of it, so none of this changes the finished text
 * (on screen, words stable too early stay until then). One per utterance;
 * not thread-safe.
 */
internal class PartialStabilizer {
    private val stable = mutableListOf<String>()
    private var lastTail: List<String> = emptyList()

    /** How many words at the start of the shown text are stable. */
    val stableWords: Int get() = stable.size

    /** Forget the utterance: the next partial starts from nothing. */
    fun reset() {
        stable.clear()
        lastTail = emptyList()
    }

    /** Take the utterance's latest partial [text] and return what to show: stable words, then the tail. */
    fun update(text: String): String {
        val words = Phantoms.cutTailRepeats(Phantoms.stripNonSpeech(text)).split(' ').filter { it.isNotEmpty() }
        val tail = words.subList(alignedEnd(stable.map(::key), words.map(::key), lastTail.map(::key)), words.size)
        // Agreed words, never the last one of this partial.
        var agreed = 0
        while (agreed < minOf(tail.size - 1, lastTail.size) && key(tail[agreed]) == key(lastTail[agreed])) agreed++
        stable += tail.subList(0, agreed)
        lastTail = tail.subList(agreed, tail.size)
        return (stable + lastTail).joinToString(" ")
    }

    private fun key(word: String) = word.filter { it.isLetterOrDigit() }.lowercase(Locale.ROOT)
}

/**
 * Where [stable] ends in [words]: the j for which words[0, j) is the fewest
 * word edits (insertions, deletions, substitutions) away from [stable]. On a
 * tie, the j whose words[j, end) starts with most of [lastTail] (the words
 * the previous partial had after [stable]), then the largest j. So a stable
 * word the model reworded ("Anna" to "Ana") isn't shown twice, and one it
 * dropped doesn't take the next word with it. 0 when [stable] is empty.
 */
internal fun alignedEnd(stable: List<String>, words: List<String>, lastTail: List<String> = emptyList()): Int {
    if (stable.isEmpty()) return 0
    // prev[j]: edits between stable[0, i) and words[0, j), one row per i.
    var prev = IntArray(words.size + 1) { it }
    for (i in 1..stable.size) {
        val row = IntArray(words.size + 1)
        row[0] = i
        for (j in 1..words.size) {
            val sub = prev[j - 1] + if (stable[i - 1] == words[j - 1]) 0 else 1
            row[j] = minOf(sub, prev[j] + 1, row[j - 1] + 1)
        }
        prev = row
    }
    val fewest = prev.min()
    return prev.indices.filter { prev[it] == fewest }
        .maxWith(compareBy<Int> { j -> lastTail.zip(words.subList(j, words.size)).takeWhile { it.first == it.second }.size }.thenBy { it })
}
