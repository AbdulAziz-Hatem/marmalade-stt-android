package app.marmalade.stt.textproc

import java.text.Normalizer

/**
 * Rule-based cleanup of speech-recognition output (ARCHITECTURE.md D7):
 * deterministic, on-device, no model. Build one per [config] and reuse it;
 * construction compiles the rules, [process] is cheap and thread-safe.
 *
 * Stages, in order (details in `textproc/README.md`). Stages 5, 7 and the
 * commands of 8 are English-only and run only when [TextProcConfig.isEnglish];
 * the Japanese part of 6 runs only for Japanese ("ja") text with a kana or kanji in it.
 *
 * 1. normalise (NFC, whitespace, control characters, `<unk>`; Qwen3's `<|…|>` tokens)
 * 2. hallucination filter ([TextProcConfig.phantomFilter]): sound tags, decoder loops, phantom
 *    phrases, each recogniser's own ([TextProcConfig.recognizer], [Phantoms.isPhantom])
 * 3. snippets, whose text is then protected from every later stage
 * 4. replacement dictionary; with [TextProcConfig.lowerCaseLatin], short lower-case Latin runs
 *    outside its replacements in capitals ("fbi" → "FBI")
 * 5. filler removal
 * 6. punctuation and spacing tidy-up; in Japanese also: no space between two CJK characters,
 *    and 。 at the end of a Final
 * 7. numbers as digits ([TextProcConfig.numbersAsDigits], [Itn])
 * 8. spoken commands and punctuation, joined onto the existing text with
 *    spacing (none between two Chinese or Japanese characters) and sentence capitalisation
 *    (none in text with a kana or kanji)
 */
class TextPipeline(val config: TextProcConfig) {
    private val snippetMatcher = PhraseMatcher(config.snippets.map { it.trigger })
    private val replacementMatcher = PhraseMatcher(config.replacements.map { it.from })
    private val english = config.isEnglish
    private val french = config.languageCode == "fr"
    private val japanese = config.languageCode == "ja"
    private val qwen3 = config.recognizer == Recognizer.QWEN3 || config.recognizer == Recognizer.ANY
    private val fillers = if (english && config.removeFillers) Fillers(config.fillerWords) else null
    private val commands = Commands(english && config.spokenCommands, english && config.spokenPunctuation)

    init {
        require(config.snippets.size <= Words.PLACEHOLDER_LIMIT) { "too many snippets" }
    }

    /**
     * Cleans one final utterance and returns how to change the text before the cursor.
     *
     * @param utterance raw recogniser output for one utterance.
     * @param before the text already before the cursor (the transcript so far, or what an
     *   input method can read). Used for spacing, capitalisation and "scratch that". Empty
     *   when there is no prior text.
     * @param nearSilent the engine's judgement that this utterance's audio was near-silent
     *   (low RMS or low VAD speech probability). With [PhantomFilter.NEAR_SILENT] this is what
     *   lets a phantom "Thanks for watching!" be dropped while a spoken one is kept.
     * @param silent the audio was quieter still: digital silence or the mic floor, far below
     *   any soft speech. Only then do the ordinary phantoms go ("Thank you.", "Gracias.", 「我」),
     *   which a user may really say quietly. Implies [nearSilent].
     */
    fun process(utterance: String, before: String = "", nearSilent: Boolean = false, silent: Boolean = false): TextEdit {
        var text = normalize(utterance)
        if (qwen3) text = stripSpecialTokens(text)
        if (config.phantomFilter != PhantomFilter.OFF) {
            text = Phantoms.stripNonSpeech(text)
            text = Phantoms.cutLoops(text)
            val always = config.phantomFilter == PhantomFilter.ALWAYS
            if ((always || nearSilent || silent) &&
                Phantoms.isPhantom(text, config.languageCode, config.recognizer, ordinaryToo = always || silent)
            ) {
                return TextEdit.NONE
            }
        }
        if (text.isEmpty()) return TextEdit.NONE
        // Japanese rules only for Japanese text: on a Japanese phone a multilingual engine may hear English.
        val japaneseText = japanese && KANJI_OR_KANA.containsMatchIn(text)

        text = expandSnippets(text)
        // Acronyms only between the dictionary's matches: its own text ("gh -> github") keeps its case.
        val between: (String) -> String = if (config.lowerCaseLatin && japaneseText) ::capitalizeAcronyms else { t -> t }
        text = replacementMatcher.replace(text, between) { i, matched ->
            adaptCase(matched, config.replacements[i].to).replace(Words.SENTINEL.toString(), "")
        }
        fillers?.let { text = it.remove(text) }
        text = tidy(text, french)
        if (japaneseText) text = endSentence(SPACE_IN_CJK.replace(text, ""))
        if (english && config.numbersAsDigits) text = Itn.apply(text)

        var buf = before
        for (token in commands.split(text)) {
            buf = when (token) {
                is Token.Text -> join(buf, token.text)
                Token.NewLine -> buf.trimEndHorizontal() + "\n"
                Token.NewParagraph -> buf.trimEndHorizontal().let {
                    it + when {
                        it.endsWith("\n\n") -> ""
                        it.endsWith("\n") -> "\n"
                        else -> "\n\n"
                    }
                }
                Token.Scratch -> scratch(buf)
                is Token.Punct -> buf.trimEndHorizontal().trimEnd(*SPOKEN_PUNCT_REPLACES) + token.mark
            }
        }
        val prefix = before.commonPrefixWith(buf).length
        return TextEdit(before.length - prefix, buf.substring(prefix))
    }

    /**
     * Cleans a live partial (the utterance in progress) for display. Only the cheap parts
     * run: stage 1 and, unless [TextProcConfig.phantomFilter] is off, stage 2's sound-tag
     * strip and loop cut, so a decoder loop ("E E E …") or a noise caption ("(buzzing)")
     * doesn't flash on screen. Everything else waits for the Final, whose [process] output
     * replaces the partial anyway; the phantom-phrase drop needs the Final's audio judgement.
     * (The stricter [Phantoms.cutTailRepeats] runs in the engine's PartialStabilizer, #28,
     * before words become stable: here it would take back words already shown as stable.)
     *
     * @return the text to show, "" when nothing is left.
     */
    fun cleanPartial(partial: String): String {
        val text = normalize(partial).let { if (qwen3) stripSpecialTokens(it) else it }
        if (config.phantomFilter == PhantomFilter.OFF) return text
        return Phantoms.cutLoops(Phantoms.stripNonSpeech(text))
    }

    /** Snippet triggers become placeholders; [restore] swaps the saved text back in. */
    private fun expandSnippets(text: String): String {
        val out = snippetMatcher.replace(text) { i, _ -> Words.placeholder(i) }
        // A trigger said on its own replaces the whole utterance, Whisper's punctuation included.
        val bare = out.filter { it.isLetterOrDigit() || Words.isPlaceholder(it) }
        return if (bare.length == 1 && Words.isPlaceholder(bare[0])) Words.PLACEHOLDER.find(out)!!.value else out
    }

    private fun restore(text: String): String {
        if (config.snippets.isEmpty()) return text
        return Words.PLACEHOLDER.replace(text) { config.snippets[Words.placeholderIndex(it.value)].text }
    }

    /** Appends dictated [raw] text to [buf] with a joining space and sentence capitalisation. */
    private fun join(buf: String, raw: String): String {
        var text = raw.trim()
        if (!text.any { it.isWordy() }) return buf
        var b = buf
        // capitalizeFirst stops at a placeholder, so a snippet at a sentence start keeps its case.
        // Chinese and Japanese have no capitals: in their text a Latin word keeps its own case ("npm。", "iPhone").
        if (config.capitalizeSentences && !Words.hasCjk(text) && atSentenceStart(b)) {
            text = Words.capitalizeFirst(text)
        }
        text = restore(text)
        if (text.isEmpty()) return buf // a snippet with empty text
        if (text[0] in CLOSING) b = b.trimEndHorizontal()
        // Chinese and Japanese put no space between sentences either: 「です。明日も」. A switch to
        // another script keeps it: 「東京」 + "and it was great".
        val space = b.isNotEmpty() && !b.last().isWhitespace() && b.last() !in OPENING && text[0] !in CLOSING &&
            !(Words.isCjk(b.codePointBefore(b.length)) && Words.isCjk(text.codePointAt(0)))
        return b + (if (space) " " else "") + text
    }

    private fun Char.isWordy() = !isWhitespace() && this !in ".,;:!?…-"

    private companion object {
        val CONTROL = Regex("[\\p{Cc}&&[^\\s]]")
        // Parakeet ja writes this for a character outside its vocabulary: 「骨<unk>う品」.
        const val UNK = "<unk>"
        // Parakeet ja sometimes splits a sentence with one: 「静かに まを開けた」.
        val SPACE_IN_CJK = Regex("(?<=[${Words.CJK}]) (?=[${Words.CJK}])")
        // A kana or kanji: text with one is Japanese (or Chinese, which shares the kanji).
        val KANJI_OR_KANA = Regex("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}]")
        // A run of ASCII letters and digits with no capital, starting with a letter.
        val LOWER_LATIN_RUN = Regex("(?<![A-Za-z0-9])[a-z][a-z0-9]*(?![A-Za-z0-9])")
        const val ACRONYM_MAX_LETTERS = 4
        val SPACE_BEFORE_PUNCT = Regex(" +(?=[,.!?;:…]+(?:\\s|$))") // not before ".NET"
        // French typography puts a space before ! ? ; : ("Et après ?"), so only , . … lose theirs.
        val SPACE_BEFORE_PUNCT_FR = Regex(" +(?=[,.…]+(?:\\s|$))")
        val COMMA_BEFORE_STOP = Regex("[,;:]+\\s*(?=[.!?])")
        val STRAY_AFTER_STOP = Regex("(?<=[.!?])\\s+[.,;:]+(?=\\s|$)")
        val LEADING_JUNK = Regex("^(?:[\\s,;:!?]|\\.(?![\\p{L}\\p{N}]))+") // keeps ".NET"
        val SENTENCE_END = Regex("(?<!\\.\\.)[.!?。！？][\"'”’)\\]]*$")
        val LAST_BOUNDARY = Regex("[.!?][\"'”’)\\]]*(?=\\s)|\\n")
        const val OPENING = "([{“‘\n"
        const val CLOSING = ",.!?;:…)]}”’%"
        val SPOKEN_PUNCT_REPLACES = charArrayOf('.', ',', '!', '?', ';', ':')
        const val FINAL_CLOSERS = ".!?…\"'”’)]"

        /** A chat-template token such as `<|endoftext|>` or `<|im_end|>`. */
        val SPECIAL_TOKEN = Regex("<\\|[^|<>\\s]{1,40}\\|>")
        val END_TOKEN = Regex("<\\|(?:endoftext|im_end)\\|>")

        /**
         * Qwen3-ASR sometimes leaks its chat template's tokens into the text and goes on after
         * its end token (#57: "system<|endoftext|>#", "<|endoftext|>#system"): everything from
         * an end token on goes, and any other `<|…|>` token.
         */
        fun stripSpecialTokens(s: String): String {
            if (!s.contains("<|")) return s
            val kept = END_TOKEN.find(s)?.let { s.substring(0, it.range.first) } ?: s
            return SPECIAL_TOKEN.replace(kept, " ").replace(Words.WHITESPACE, " ").trim()
        }

        /** NFC, strip control characters, the placeholder sentinel and `<unk>`, collapse whitespace. */
        fun normalize(s: String): String {
            val nfc = Normalizer.normalize(s, Normalizer.Form.NFC)
            return CONTROL.replace(nfc, "").replace(Words.SENTINEL.toString(), "").replace(UNK, "")
                .replace(Words.WHITESPACE, " ").trim()
        }

        /**
         * Parakeet ja ([TextProcConfig.lowerCaseLatin]): its vocabulary has only lower-case
         * Latin letters, so it writes acronyms as "fbi", "f1", "m16". A run of ASCII letters and
         * digits with at most [ACRONYM_MAX_LETTERS] letters, starting with a letter and with no
         * capital, becomes upper case. Longer words ("safari"), mixed case ("iPhone") and runs
         * starting with a digit ("11n") stay. Engines that write case (Whisper, Qwen3) never
         * get it, so their "web" or romaji "desu" stay too.
         */
        fun capitalizeAcronyms(s: String): String = LOWER_LATIN_RUN.replace(s) { m ->
            if (m.value.count { it.isLetter() } <= ACRONYM_MAX_LETTERS) m.value.uppercase() else m.value
        }

        /**
         * Japanese: a Final that ends in a letter or digit gets 「。」. The Japanese models end
         * only some utterances with a mark, and a Final is one VAD segment ended by a pause,
         * so mostly a sentence (#60: Parakeet ja punctuation F1 26 → 56 on FLEURS-ja).
         * Nothing is added after any mark or bracket, after a snippet (its placeholder is
         * not a letter), or to empty text. Parakeet ja's vocabulary has no 「？」, so a question
         * gets 「。」 too, which is fine in formal Japanese.
         */
        fun endSentence(s: String): String =
            if (s.isNotEmpty() && Character.isLetterOrDigit(s.codePointBefore(s.length))) "$s。" else s

        /** Repairs the punctuation and spacing that filler and dictionary edits leave behind. */
        fun tidy(s: String, french: Boolean): String {
            var t = s.replace(Words.WHITESPACE, " ")
            t = (if (french) SPACE_BEFORE_PUNCT_FR else SPACE_BEFORE_PUNCT).replace(t, "")
            t = COMMA_BEFORE_STOP.replace(t, "")
            t = STRAY_AFTER_STOP.replace(t, "")
            return LEADING_JUNK.replace(t, "").trim()
        }

        fun String.trimEndHorizontal() = trimEnd { it == ' ' || it == '\t' || it == '\u00A0' }

        /** True at the start of the text, after a line break, or after . ! ? (not an ellipsis). */
        fun atSentenceStart(buf: String): Boolean {
            val b = buf.trimEndHorizontal()
            return b.isEmpty() || b.endsWith('\n') || SENTENCE_END.containsMatchIn(b)
        }

        /**
         * "Scratch that": if the text ends with line breaks, remove them. Otherwise remove
         * the last sentence: everything after the last . ! ? that is followed by whitespace,
         * or after the last line break. With no such boundary everything goes.
         */
        fun scratch(buf: String): String {
            val b = buf.trimEndHorizontal()
            if (b.endsWith('\n')) return b.trimEnd()
            val core = b.trimEnd { it in FINAL_CLOSERS }
            val cut = LAST_BOUNDARY.findAll(core).lastOrNull() ?: return ""
            return core.substring(0, cut.range.last + 1).trimEndHorizontal()
        }

        /** Replacement text capitalisation; see [Replacement]. */
        fun adaptCase(matched: String, to: String): String {
            if (to.any { it.isUpperCase() }) return to
            val letters = matched.filter { it.isLetter() }
            if (letters.length >= 2 && letters.all { it.isUpperCase() }) return to.uppercase()
            if (matched.firstOrNull { it.isLetter() }?.isUpperCase() == true) return Words.capitalizeFirst(to)
            return to
        }
    }
}
