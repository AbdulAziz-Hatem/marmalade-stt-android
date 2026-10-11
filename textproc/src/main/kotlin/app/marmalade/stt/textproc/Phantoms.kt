package app.marmalade.stt.textproc

import java.util.Locale

/**
 * Recogniser hallucinations: Whisper's silence phrases and sound tags, and
 * decoder loops. Public so an engine can test a result without running the
 * whole pipeline.
 */
object Phantoms {
    /** Whisper's non-speech tags: `[BLANK_AUDIO]`, `[Music]`, music notes. */
    private val NON_SPEECH = Regex("\\[[\\p{L}\\p{M}_ '’-]{1,40}]|[♪♫]+")

    /** A bracketed group such as `(laughs)` or `(after five)`; [stripNonSpeech] decides which are tags. */
    private val PAREN_TAG = Regex("\\(([\\p{L}\\p{M}_ '’-]{1,40})\\)")

    /** Whisper's other caption style, `*Klopfen*` or `* Musik *` (#57): removed only when it names a sound. */
    private val STAR_TAG = Regex("\\*\\s*([\\p{L}\\p{M}_ '’-]{1,40}?)\\s*\\*")

    /** Lone opening brackets, like the "( ( ( (" Whisper writes for mains hum. */
    private val STRAY_BRACKETS = Regex("(?<!\\S)[(\\[]+(?:\\s+[(\\[]+)*(?!\\S)")
    /** Sentence ends: ". ! ?" before a space, or a Chinese or Japanese full stop or mark anywhere. */
    private val SENTENCE_SPLIT = Regex("(?<=[.!?])\\s+|(?<=[。！？])")
    private val NOT_WORDY = Regex("[^\\p{L}\\p{M}\\p{N}' ]")
    private val DIGITS = Regex("\\p{N}+")

    /** Sound words from Whisper's and Qwen3's noise captions. A tag needs at least one. */
    private val NOISE_WORDS = setOf(
        "applause", "applauding", "clapping", "laugh", "laughs", "laughing", "laughter", "chuckles", "chuckling",
        "giggles", "giggling", "cough", "coughs", "coughing", "sigh", "sighs", "sighing", "sneezes", "sneezing",
        "sniffs", "sniffing", "sniffles", "breathing", "breathes", "gasps", "gasping", "groans", "groaning",
        "clears", "throat", "music", "singing", "sings", "humming", "hums", "whistling", "whistles",
        "buzzing", "buzzes", "buzzer", "beep", "beeps", "beeping", "ringing", "rings", "click", "clicks", "clicking",
        "tapping", "taps", "typing", "knocking", "knocks", "banging", "bangs", "thud", "thumping", "rustling",
        "rattling", "static", "noise", "noises", "silence", "inaudible", "indistinct", "unintelligible", "crosstalk",
        "mumbling", "mumbles", "murmuring", "chattering", "chatter", "footsteps", "howling", "blowing", "rushing",
        "splashing", "dripping", "thunder", "revving", "roaring", "rumbling", "whirring", "whooshing", "hissing",
        "crackling", "sizzling", "barking", "barks", "meowing", "chirping", "crying", "cries", "sobbing",
        "screaming", "screams", "shouting", "cheering", "cheers", "honking", "siren", "sirens", "alarm",
        "vibrating", "creaking", "creaks", "slams", "blank_audio",
        // #57: Whisper turbo's captions in other languages ("* Musik *", "*Klopfen*", "*Gelach*", "*cris*", "*groan*").
        "musik", "klopfen", "applaus", "gelach", "cris", "groan",
    )

    /** Words that may sit next to a sound word in a tag: "(dog barking)", "(soft music playing)". */
    private val NOISE_CONTEXT = setOf(
        "a", "an", "the", "and", "of", "in", "on", "with", "background", "distant", "soft", "softly", "loud",
        "loudly", "faint", "gentle", "upbeat", "dramatic", "man", "woman", "men", "women", "people", "person",
        "child", "baby", "crowd", "audience", "dog", "dogs", "bird", "birds", "wind", "water", "rain", "engine",
        "car", "cars", "traffic", "door", "phone", "keyboard", "bell", "bells", "playing", "plays", "continues",
        "fades", "sound", "sounds", "effect", "effects", "speaking", "talking", "foreign", "language", "audio",
    )

    /**
     * True for a noise caption like "buzzing", "wind howling" or "MUSIC": all capitals (with
     * [caps]), or at most four words that are all sound or context words with at least one
     * sound word. A real parenthetical like "after five" or "the music was loud" is not a tag.
     */
    private fun isNoiseTag(inner: String, caps: Boolean = true): Boolean {
        val letters = inner.filter { it.isLetter() }
        if (caps && letters.length >= 2 && letters.all { it.isUpperCase() }) return true
        val words = inner.lowercase(Locale.ROOT).split(Words.WHITESPACE).filter { it.isNotEmpty() }
        return words.size in 1..4 && words.any { it in NOISE_WORDS } && words.all { it in NOISE_WORDS || it in NOISE_CONTEXT }
    }

    /**
     * Removes non-speech tags. Square-bracket tags, music notes, lone opening brackets and
     * noise captions in round brackets or asterisks ("(buzzing)", "(MUSIC)", "*coughs*",
     * "* Musik *") go wherever they are. Any other round-bracket group only goes when the
     * utterance is nothing but such groups. Any other asterisk group stays, even alone: a
     * user may really write "*important*" (#57's review).
     */
    fun stripNonSpeech(text: String): String {
        val keepSpeech: (MatchResult) -> CharSequence = { if (isNoiseTag(it.groupValues[1])) " " else it.value }
        var t = NON_SPEECH.replace(text, " ")
        t = STAR_TAG.replace(PAREN_TAG.replace(t, keepSpeech)) { if (isNoiseTag(it.groupValues[1], caps = false)) " " else it.value }
        t = STRAY_BRACKETS.replace(t, " ").replace(Words.WHITESPACE, " ").trim()
        return if (PAREN_TAG.replace(t, "").isBlank()) "" else t
    }

    /** A block repeated this many times in a row is a decoder loop, not speech. Real speech peaks at 3–4 ("no no no"). */
    const val LOOP_MIN_REPEATS = 6

    /** The longest block a loop may cycle through: "S E S E S E …" has 2. */
    private const val LOOP_MAX_PERIOD = 3

    /** Digits repeat for real ("five five five five five five zero one four two" is a phone number). */
    private val DIGIT_WORDS = setOf("zero", "oh", "o", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine")

    /**
     * Cuts decoder loops: a word, letter or block of up to [LOOP_MAX_PERIOD] of them repeated
     * [LOOP_MIN_REPEATS] or more times in a row ("E E E E …", "the the the …", "S E S E S E …"),
     * compared without case and punctuation. Canary falls into these about once in 200
     * utterances and then repeats until its token cap; Whisper and Nemotron do it more rarely
     * (#14, #15, #18).
     *
     * A loop that runs to the end of the utterance is the decoder never finding its way out, so
     * it is cut from its first word: "E E E … E" becomes "". The end counts even when the cap
     * cut the last repeat short ("Uh, Uh, U"). A loop followed by more words is a stutter the
     * decoder recovered from, so one copy stays: "No the the … the scrolling" becomes
     * "No the scrolling". Single letters are never kept: "T T T … T Looks" becomes "Looks".
     * Digits and digit words are never a loop.
     *
     * Loops with no spaces in them are cut first, inside each space-separated word (in
     * Chinese, Japanese or Thai that can be the whole utterance): see [cutLoopsInWord].
     * Words left empty by that cut go; when that is the last word, a comma, semicolon or
     * colon before it goes too, as for a loop of words ("Hello, ออออ…" leaves "Hello").
     */
    fun cutLoops(text: String): String {
        val words = text.split(' ')
        val cut = words.map(::cutLoopsInWord)
        if (cut == words) return cutWordLoops(text)
        val kept = cut.filterIndexed { i, w -> w.isNotEmpty() || words[i].isEmpty() }.toMutableList()
        val lastWord = words.indexOfLast { it.isNotEmpty() }
        val lastKept = kept.indexOfLast { it.isNotEmpty() }
        if (cut[lastWord].isEmpty() && lastKept >= 0) kept[lastKept] = kept[lastKept].trimEnd(*CLAUSE_MARKS)
        return cutWordLoops(kept.joinToString(" "))
    }

    /** Marks that join a clause to what follows; one left at the end of the text goes. */
    private val CLAUSE_MARKS = charArrayOf(',', ';', ':', '，', '；', '：', '、')

    /** [cutLoops] for loops of whole words. */
    private fun cutWordLoops(text: String): String {
        val words = text.split(' ')
        if (words.size < LOOP_MIN_REPEATS) return text
        val keys = words.map { w -> w.filter { it.isLetterOrDigit() }.lowercase(Locale.ROOT) }
        val loopable = keys.map { it.isNotEmpty() && it !in DIGIT_WORDS && !it.all(Char::isDigit) }
        val out = ArrayList<String>(words.size)
        var i = 0
        while (i < words.size) {
            // The period whose repeats cover the most words from i.
            var period = 0
            var end = i
            for (p in 1..LOOP_MAX_PERIOD) {
                if (i + p > words.size || (i until i + p).any { !loopable[it] }) break
                var j = i + p
                while (j + p <= words.size && (0 until p).all { keys[j + it] == keys[i + it] }) j += p
                if ((j - i) / p >= LOOP_MIN_REPEATS && j > end) {
                    period = p
                    end = j
                }
            }
            if (period == 0) {
                out += words[i++]
                continue
            }
            val block = keys.subList(i, i + period)
            val rest = keys.subList(end, keys.size).filter { it.isNotEmpty() }
            val cutShort = rest.size <= period && rest.indices.all { k ->
                if (k == rest.lastIndex) block[k].startsWith(rest[k]) else block[k] == rest[k]
            }
            if (cutShort) {
                // Runs to the end: the decoder never recovered. "Um, Uh, Uh, …" leaves "Um".
                if (out.isNotEmpty()) out[out.lastIndex] = out.last().trimEnd(',', ';', ':')
                break
            }
            if (block.any { it.length > 1 }) out.addAll(words.subList(i, i + period))
            i = end
        }
        return if (out.size == words.size) text else out.joinToString(" ")
    }

    /**
     * One letter repeated this many times in a row inside a word is a decoder loop. Loops run
     * on to the token cap, so they are long: Nemotron's "อ" runs inside Chinese are 15 to 200+,
     * Whisper's "xxxx…" tail 46. Real runs are short: laughter ("哈哈哈哈", "ㅋㅋㅋㅋ", "www")
     * and drawn-out words ("Hmmmm", "Sooooo") stay well under ten.
     */
    const val LETTER_LOOP_MIN_RUN = 10

    /**
     * A unit of 2 to 10 letters repeated [LOOP_MIN_REPEATS] or more times with nothing or one
     * joining mark between the copies; the last copy needs no joiner after it.
     */
    private val UNIT_LOOP = Regex("""((?:\p{L}\p{M}*){2,10}?)([.,/\-，、]?)(?:\1\2){${LOOP_MIN_REPEATS - 2},}\1""")

    /** "xxxx": one letter repeated, which [UNIT_LOOP] leaves to [LETTER_RUN]. */
    private val ONE_LETTER_REPEATED = Regex("""(\p{L}\p{M}*)\1+""")

    /** A letter with its combining marks, [LETTER_LOOP_MIN_RUN] or more times. Not modifier letters (Lm). */
    private val LETTER_RUN = Regex("""([\p{Lu}\p{Ll}\p{Lt}\p{Lo}]\p{M}*)\1{${LETTER_LOOP_MIN_RUN - 1},}""")

    /**
     * Cuts the loops [cutWordLoops] can't see because they have no spaces in them, inside one
     * space-separated [word] (in Chinese, Japanese or Thai that can be the whole utterance).
     * Only letters loop: digits ("1000000", "1.1.1.1") and punctuation ("...", "!!!") never do.
     *
     * - A unit of 2 to 10 letters repeated [LOOP_MIN_REPEATS] or more times back to back, with
     *   or without one joining mark (`. , - /` or `，、`) between the copies, keeps one copy:
     *   "xlsx.xlsx.xlsx.xlsx.xlsx.xlsx" becomes "xlsx". The copy stays even when the loop
     *   runs to the end of the utterance, unlike a loop of words: the first copy is glued to
     *   the word the speaker said ("It's an xlsx.xlsx.…"), so it is that word, not noise. Up to
     *   the word's last letter, what follows the loop goes too when it is made only of the
     *   unit's letters and joiner: that is the decoder stumbling out of the loop
     *   ("….xxlsx.xx.xxx.xxxx", or a copy cut short by the token cap), as [cutWordLoops] treats
     *   an unfinished copy. Punctuation after the last letter stays. "haha", "lalala",
     *   "bye-bye" and "Baden-Baden" have too few copies, and "a.b.c.d" too short a unit.
     * - A run of [LETTER_LOOP_MIN_RUN] or more of one letter (with its combining marks) goes
     *   whole: "港神อออ…อ出道" becomes "港神出道". A stray repeated letter is noise, so no copy
     *   stays, as single letters don't in [cutWordLoops]. The exception is a run that
     *   continues a word in the same script, which is a drawn-out sound: "Nooooooooooo"
     *   becomes "No", "すごいいいいいいいいいい" "すごい" (kana count as the script of kanji).
     *   Modifier letters such as the Japanese long-vowel mark "ー" never count: repeating
     *   them is how a long sound is written.
     *
     * The unit cut runs first, so the "xxxx…" that ends Whisper's "xlsx" loop goes with the
     * loop instead of leaving its "." behind.
     */
    private fun cutLoopsInWord(word: String): String = cutLetterRuns(cutUnitLoops(word))

    private fun cutUnitLoops(word: String): String {
        if (word.length < 2 * LOOP_MIN_REPEATS) return word
        val lastLetter = word.indexOfLast { it.isLetter() || it.isMark() } + 1
        val out = StringBuilder()
        var from = 0 // word[from until …] is not in out yet
        var search = 0
        while (true) {
            val m = UNIT_LOOP.find(word, search) ?: break
            val unit = m.groupValues[1]
            if (ONE_LETTER_REPEATED.matches(unit)) {
                search = m.range.first + 1
                continue
            }
            val joiner = m.groupValues[2]
            out.append(word, from, m.range.first).append(unit)
            var end = m.range.last + 1
            if (end < lastLetter && (end until lastLetter).all { word[it] in unit || word[it] in joiner }) end = lastLetter
            from = end
            search = end
        }
        return if (from == 0) word else out.append(word, from, word.length).toString()
    }

    private fun cutLetterRuns(word: String): String {
        if (word.length < LETTER_LOOP_MIN_RUN) return word
        return LETTER_RUN.replace(word) { m ->
            var i = m.range.first
            while (i > 0 && word[i - 1].isMark()) i--
            val before = if (i > 0) word.codePointBefore(i) else -1
            val drawnOut = before != -1 && Character.isLetter(before) && script(before) == script(m.value.codePointAt(0))
            if (drawnOut) m.groupValues[1] else ""
        }
    }

    private fun Char.isMark() = category.code[0] == 'M'

    /** The Unicode script, with kana counted as Han: Japanese mixes them within a word. */
    private fun script(codePoint: Int): Character.UnicodeScript = when (val s = Character.UnicodeScript.of(codePoint)) {
        Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> Character.UnicodeScript.HAN
        else -> s
    }

    /** A block repeated this many times at the end of a partial is cut by [cutTailRepeats]. */
    const val TAIL_REPEATS = 3

    /**
     * Cuts a repeat at the end of a live partial: a word or block of up to [LOOP_MAX_PERIOD]
     * words repeated [TAIL_REPEATS] or more times in a row at the very end ("a test test test")
     * keeps one copy ("a test"), compared without case and punctuation, until the end is no
     * longer a repeat. An unfinished copy after the repeats ("test test test te", "the shop
     * the shop the shop the sh") goes with them, and so does a comma after the copy kept ("a
     * test, test, test," leaves "a test"). A block of single letters ("E E E") leaves nothing,
     * as in [cutLoops], except "a" and "I"; digits and digit words are never cut. Punctuation-only
     * tokens after the repeats go with them.
     *
     * For live partials only (#28, the engine's PartialStabilizer): a partial's audio ends
     * wherever the speaker is, often mid-word, and small Whisper models then repeat the last
     * word until their token cap. Real "no no no" loses two words here, but the Final replaces
     * the partial, and Finals keep [cutLoops]'s [LOOP_MIN_REPEATS].
     */
    fun cutTailRepeats(text: String): String {
        var words = text.split(' ').filter { it.isNotEmpty() }
        var cutAny = false
        while (true) {
            val keys = words.map { w -> w.filter { it.isLetterOrDigit() }.lowercase(Locale.ROOT) }
            // Punctuation after the repeats ('and "s" and "s" and "s" and "') goes with them.
            val end = keys.indexOfLast { it.isNotEmpty() } + 1
            val cut = tailRepeat(keys.subList(0, end)) ?: break
            words = words.subList(0, cut)
            cutAny = true
        }
        if (cutAny && words.isNotEmpty()) words = words.dropLast(1) + words.last().trimEnd(',', ';', ':')
        val out = words.joinToString(" ")
        return if (out == text) text else out
    }

    /**
     * Where [cutTailRepeats] cuts [keys], or null if their end isn't a repeat: the reading that
     * cuts the most. [keys] may end in an unfinished copy of the block, its first `rest` words
     * with the last one possibly cut short: "the shop the shop the shop the sh" is three
     * "the shop" and "the sh", not "the" and three "shop the" and "sh".
     */
    private fun tailRepeat(keys: List<String>): Int? {
        var best: Int? = null
        for (p in 1..LOOP_MAX_PERIOD) {
            for (rest in 0..p) {
                val end = keys.size - rest // where the whole copies end
                if (end < p * TAIL_REPEATS) continue
                val block = keys.subList(end - p, end)
                if (block.any { it.isEmpty() || it in DIGIT_WORDS || it.all(Char::isDigit) }) continue
                val unfinished = (0 until rest).all { k ->
                    val key = keys[end + k]
                    key.isNotEmpty() && if (k == rest - 1) block[k].startsWith(key) else block[k] == key
                }
                if (!unfinished) continue
                var start = end - p
                while (start - p >= 0 && (0 until p).all { keys[start - p + it] == block[it] }) start -= p
                if ((end - start) / p < TAIL_REPEATS) continue
                // One copy stays, unless it is single letters (a hallucination, not a word).
                val cut = if (block.all { it.length == 1 && it != "a" && it != "i" }) start else start + p
                if (best == null || cut < best) best = cut
            }
        }
        return best
    }

    /**
     * One recogniser's phantom phrases (#57): what it writes for silence or noise. Every
     * phrase is written as the recogniser wrote it and compared by [phraseKey]: case,
     * punctuation and the number in it don't matter.
     *
     * @param distinctive phrases nobody dictates as a whole message: subtitle credits,
     *   "thanks for watching". Dropped when the audio was near-silent, in every language: the
     *   text shows its own language (Whisper on Auto may write Japanese for noise on an
     *   English phone).
     * @param ordinary words and sentences a user may really say on their own ("Gracias",
     *   "Thank you", 「我」), by language. Dropped only when the audio was silent (digital
     *   silence or the mic floor, far below soft speech) and only in their own language;
     *   the list under [EVERY_LANGUAGE] counts in all of them.
     * @param preSpeech the phrases held back when they come first in a session
     *   ([isPreSpeechPhantom]).
     */
    class PhantomList(distinctive: Set<String>, ordinary: Map<String, Set<String>>, preSpeech: Set<String>) {
        internal val distinctive: Set<String> = distinctive.mapTo(HashSet(), ::phraseKey)
        internal val ordinary: Map<String, Set<String>> = ordinary.mapValues { (_, v) -> v.mapTo(HashSet(), ::phraseKey) }
        internal val preSpeech: Set<String> = preSpeech.mapTo(HashSet(), ::phraseKey)

        internal fun matches(key: String, language: String, ordinaryToo: Boolean): Boolean =
            key in distinctive || ordinaryToo &&
                (key in ordinary[language].orEmpty() || key in ordinary[EVERY_LANGUAGE].orEmpty())
    }

    /** The [PhantomList.ordinary] key for a list that counts in every language. */
    const val EVERY_LANGUAGE = "*"

    /** [WHISPER]'s distinctive phrases, which are also its pre-speech ones. */
    private val WHISPER_DISTINCTIVE = setOf(
        // English, from marmalade-stt-cli's filter and common YouTube-subtitle leftovers
        "thanks for watching", "thank you for watching", "thanks for listening", "thank you for listening",
        "please subscribe", "like and subscribe",
        "ترجمة نانسي قنقر", // ar "translation by Nancy Qunqar": 25 / 44
        "اشتركوا في القناة", // ar "subscribe to the channel": 15 / 5
        "Untertitelung des ZDF, 2020", // 6 / 0, any year
        "Gracias por ver el video.", // 24 / 34
        "¡Suscríbete al canal!", // 11 / 9
        "Sous-titrage ST' 501", // 8 / 4
        "Il nostro corso gratuito è www.mesmerism.info", // 5 / 0
        "ご視聴ありがとうございました", // ja "thank you for watching": 43 / 48; lead-ins: turbo 2 of 100 FLEURS-ja, ACFT 13 to 17 (#55)
        "다음 영상에서 만나요.", // ko "see you in the next video": 37 / 45
        "Teksting av Nicolai Winther", // nb: 46 / 50
        "Dziękuje za oglądanie.", "Dzięki za oglądanie!", // pl: 27 / 42, 8 / 1
        "Wielkie prawa zastrzeżone.", // pl: 2 / 0
        "Продолжение следует...", // ru "to be continued": 36 / 46
        "Субтитры сделал DimaTorzok", "Субтитры создавал DimaTorzok", // ru: 4 / 4, 3 / 0
        "Altyazı M.K.", // tr: 33 / 45
        "İzlediğiniz için teşekkür ederim.", // tr "thanks for watching": 17 / 5
        "Hãy subscribe cho kênh Ghiền Mì Gõ Để không bỏ lỡ những video hấp dẫn", // vi: 17 / 38
        "Hãy subscribe cho kênh La La School Để không bỏ lỡ những video hấp dẫn", // vi: 17 / 1
        "Cảm ơn các bạn đã theo dõi và hẹn gặp lại.", "Cảm ơn các bạn đã theo dõi.", // vi: 10 / 6, 6 / 5
        "字幕志愿者 李宗盛", "字幕志愿者 杨栋梁", "字幕志愿者 杨茜茜", // zh subtitle credits: 8 / 3, 2 / 0, 1 / 0
        "请不吝点赞 订阅 转发 打赏支持明镜与点点栏目", // zh: 8 / 0
    )

    /**
     * Whisper turbo (and #55's ACFT fine-tune). Measured with `tools/asr-bench/run_57.sh`
     * (`docs/benchmarks/2026-10-phantoms.md`): 27 noise clips (silence, the mic floor, room
     * tone, a breath, taps, clicks, hum; 1 to 10 s) with every language hint the app offers,
     * with and without gain: 50 clips per hint, digital silence counted once. Counts are
     * turbo / ACFT clips. A lead-in is FLEURS audio before speech that became a segment of
     * its own. Listed: what one model wrote alone on 3 or more clips with one hint (2 for
     * subtitle credits, outros and thanks). Left out: yes/no answers and fillers (はい, sí, 嗯,
     * 아, 好), ordinary sentences seen on fewer than 10 clips ("Das war's", "Zobaczcie").
     */
    val WHISPER = PhantomList(
        distinctive = WHISPER_DISTINCTIVE,
        ordinary = mapOf(
            // English everywhere: on Auto, Whisper writes English for noise whatever the phone's
            // language (every Auto clip in #57). From marmalade-stt-cli's filter.
            EVERY_LANGUAGE to setOf(
                "thank you", "thank you very much", "thank you so much", "thanks", "subscribe",
                "bye", "bye bye", "goodbye", "you", "okay", "so",
            ),
            "ar" to setOf("شكرا", "موسيقى"), // "thanks" 7 / 0, "music" 3 / 1
            "de" to setOf(
                "Vielen Dank.", // 17 / 11
                "Das war's für heute.", // 0 / 5
                "Ich bin der Fall.", "Ich bin der Karte.", "Ich bin ein.", // 2 / 5, 3 / 2, 3 / 0
                "Musik", // 6 / 10, mostly as "* Musik *"
            ),
            "es" to setOf("Gracias."), // 9 / 0
            "hi" to setOf("झाल", "प्रप्राइब दो"), // 32 / 50, 3 / 0
            "it" to setOf("Grazie a tutti.", "Grazie."), // 37 / 50, 8 / 0
            "ja" to setOf("おわり"), // "the end": lead-ins, 15 of 100 FLEURS-ja clips (4 without the AGC stand-in)
            "ko" to setOf("감사합니다."), // "thank you": 7 / 0
            "pl" to setOf("Dziękuję.", "KONIEC"), // 4 / 0, 1 / 2
            "pt" to setOf("E aí", "Obrigado."), // 44 / 50, 3 / 0
            "ru" to setOf("Спасибо."), // 2 / 0
            "zh" to setOf("我可以做的", "我", "你"), // 10 / 11, 3 / 18, 1 / 15
        ),
        // The distinctive phrases, and おわり: the FLEURS-ja lead-ins measure -30 to -36 dBFS, a
        // breath or a click before the first word, too loud for any level gate.
        preSpeech = WHISPER_DISTINCTIVE + "おわり",
    )

    /**
     * Qwen3-ASR 0.6B writes "system" for noise under every language hint (8 to 41 of 50 clips
     * per Latin-script hint, and with zh and ru) or its translation. Distinctive in any
     * language for Qwen3 alone: no other engine's text is checked against it.
     */
    val QWEN3 = PhantomList(
        distinctive = setOf(
            "the system is now running", // #14
            "system", "a system", "the system", "systeme", "systemus", // en 8, 7 clips; de/es/fr/it/nl 3 to 9
            "el sistema", "sistema", "le système", "un système", "système", "il sistema", "o sistema", // 2 to 9
            "система", "системы", "システム", "시스템", "系统", // 10, 3, 34, 24, 21
        ),
        ordinary = emptyMap(),
        // システム before speech: 14 of 100 FLEURS-ja lead-ins (13 without the AGC stand-in), -30 to
        // -54 dBFS. The others never came before speech in #57.
        preSpeech = setOf("システム", "the system is now running"),
    )

    /** Canary 180M Flash: "T S F" on 30 of 50 noise clips with the de hint, 3 with en. */
    val CANARY = PhantomList(distinctive = setOf("T S F"), ordinary = emptyMap(), preSpeech = emptySet())

    private fun listsFor(recognizer: Recognizer): List<PhantomList> = when (recognizer) {
        Recognizer.ANY -> listOf(WHISPER, QWEN3, CANARY)
        Recognizer.WHISPER -> listOf(WHISPER)
        Recognizer.QWEN3 -> listOf(QWEN3)
        Recognizer.CANARY -> listOf(CANARY)
        Recognizer.OTHER -> emptyList()
    }

    /**
     * True when [text] is nothing but [recognizer]'s phantom phrases, e.g. "Thanks for
     * watching! Bye." or 「ご視聴ありがとうございました」. A phrase inside a longer sentence doesn't
     * count. [ordinaryToo]: the ordinary tier counts too ([PhantomList.ordinary], in
     * [language], a [TextProcConfig.languageCode]); the caller passes it only for silent audio.
     */
    fun isPhantom(
        text: String,
        language: String = "",
        recognizer: Recognizer = Recognizer.ANY,
        ordinaryToo: Boolean = true,
    ): Boolean {
        val lists = listsFor(recognizer)
        val keys = sentenceKeys(text)
        return keys.isNotEmpty() && keys.all { k -> lists.any { it.matches(k, language, ordinaryToo) } }
    }

    /**
     * True when [text] is nothing but [recognizer]'s [PhantomList.preSpeech] phrases: what it
     * writes for the breath or click before the first word. The app holds such a text back
     * when it comes first in a session and drops it when speech follows (as #60 does for
     * Parakeet ja's 「うん」), at any level.
     */
    fun isPreSpeechPhantom(text: String, recognizer: Recognizer): Boolean {
        val lists = listsFor(recognizer)
        val keys = sentenceKeys(text)
        return keys.isNotEmpty() && keys.all { k -> lists.any { k in it.preSpeech } }
    }

    private fun sentenceKeys(text: String): List<String> =
        text.split(SENTENCE_SPLIT).map(::phraseKey).filter { it.isNotEmpty() }

    /**
     * How a sentence is compared with the phrase lists: lower case, punctuation as spaces,
     * every run of digits as "0" (Whisper's subtitle credits carry a year), and Turkish
     * dotted and dotless i as a plain "i" ("ALTYAZI" and "İZLEDİĞİNİZ" match "altyazı" and
     * "izlediğiniz" whatever the locale).
     */
    private fun phraseKey(sentence: String): String = NOT_WORDY
        .replace(sentence.lowercase(Locale.ROOT).replace("i̇", "i").replace('ı', 'i').replace('’', '\''), " ")
        .replace(DIGITS, "0")
        .trim().replace(Words.WHITESPACE, " ")
}
