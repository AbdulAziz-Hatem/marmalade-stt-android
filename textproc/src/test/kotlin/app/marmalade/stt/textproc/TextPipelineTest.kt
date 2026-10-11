package app.marmalade.stt.textproc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Table-driven tests. Each case feeds [Case.utterances] through one pipeline in
 * order, starting from [Case.before], and checks the final text.
 */
class TextPipelineTest {
    private data class Case(
        val name: String,
        val utterances: List<String>,
        val expected: String,
        val before: String = "",
        val config: TextProcConfig = TextProcConfig(),
        val nearSilent: Boolean = false,
        val silent: Boolean = false,
    )

    private fun case(
        name: String,
        input: String,
        expected: String,
        before: String = "",
        config: TextProcConfig = TextProcConfig(),
        nearSilent: Boolean = false,
        silent: Boolean = false,
    ) = Case(name, listOf(input), expected, before, config, nearSilent, silent)

    private fun run(cases: List<Case>) {
        val failures = cases.mapNotNull { c ->
            val pipeline = TextPipeline(c.config)
            val actual = c.utterances.fold(c.before) { text, u ->
                pipeline.process(u, text, c.nearSilent, c.silent).applyTo(text)
            }
            if (actual == c.expected) null else "${c.name}: expected <${c.expected}> but was <$actual>"
        }
        if (failures.isNotEmpty()) fail("${failures.size} of ${cases.size} failed:\n" + failures.joinToString("\n"))
    }

    private val plain = TextProcConfig(removeFillers = false, spokenCommands = false, capitalizeSentences = false)
    private val punct = TextProcConfig(spokenPunctuation = true)

    /**
     * #57: each recogniser's phrases, in two tiers. Distinctive phrases (subtitle credits,
     * outros) go from near-silent audio in any language; ordinary ones ("Gracias.", 「我」, "Thank
     * you.") only from silent audio and only in their own language. Real speech stays.
     */
    @Test fun multilingualPhantoms() {
        val whisper = TextProcConfig(recognizer = Recognizer.WHISPER)
        fun w(language: String) = whisper.copy(language = language)
        val qwen3 = TextProcConfig(recognizer = Recognizer.QWEN3)
        val other = TextProcConfig(recognizer = Recognizer.OTHER) // Parakeet Ultra, Nemotron, …
        val canary = TextProcConfig(recognizer = Recognizer.CANARY, language = "de")
        run(listOf(
            // Distinctive, near-silent, any language.
            case("ja: turbo's phrase dropped when near-silent", "ご視聴ありがとうございました", "", config = w("ja"), nearSilent = true),
            case("ja: with a full stop", "ご視聴ありがとうございました。", "", config = w("ja"), nearSilent = true),
            case("ja: said aloud, it stays", "ご視聴ありがとうございました。", "ご視聴ありがとうございました。", config = w("ja")),
            case("ja: said aloud without a mark, it gets one", "ご視聴ありがとうございました", "ご視聴ありがとうございました。",
                config = w("ja")),
            case("ja: followed by real speech, it stays", "ご視聴ありがとうございました。明日も来ます。",
                "ご視聴ありがとうございました。明日も来ます。", config = w("ja"), nearSilent = true),
            case("ja: inside a sentence, it stays", "皆さん、ご視聴ありがとうございました。",
                "皆さん、ご視聴ありがとうございました。", config = w("ja"), nearSilent = true),
            case("en phone, Whisper on Auto writes Japanese for noise", "ご視聴ありがとうございました。", "",
                config = w("en"), nearSilent = true),
            case("en phone, Korean outro", "다음 영상에서 만나요.", "", config = w("en"), nearSilent = true),
            case("en phone, Russian credit", "Субтитры сделал DimaTorzok", "", config = w("en"), nearSilent = true),
            case("de: ZDF credit with any year", "Untertitelung des ZDF, 2017", "", config = w("de"), nearSilent = true),
            case("digits match only digits: no number", "Untertitelung des ZDF", "Untertitelung des ZDF",
                config = w("de"), nearSilent = true),
            case("digits match only digits: a word", "Untertitelung des ZDF, Teil zwei", "Untertitelung des ZDF, Teil zwei",
                config = w("de"), nearSilent = true),
            case("tr: credit in capitals, on an English phone", "ALTYAZI M.K.", "", config = w("en"), nearSilent = true),
            case("tr: dotted capital I", "İZLEDİĞİNİZ İÇİN TEŞEKKÜR EDERİM.", "", config = w("tr"), nearSilent = true),
            case("en: full-width mark", "Thanks for watching！", "", config = w("en"), nearSilent = true),
            case("en: full-width mark, then real speech", "Thanks for watching！See you at five.",
                "Thanks for watching！See you at five.", config = w("en"), nearSilent = true),
            case("en: full-width question kept", "Really？", "Really？", config = w("en"), silent = true),
            // Ordinary: silent audio only (digital silence, the mic floor), own language only.
            case("es: a quiet Gracias (-55 dBFS: near-silent, not silent) stays", "Gracias.", "Gracias.",
                config = w("es"), nearSilent = true),
            case("es: Gracias from silence goes", "Gracias.", "", config = w("es"), silent = true),
            case("es: Gracias said aloud stays", "Gracias.", "Gracias.", config = w("es")),
            case("en: a quiet Thank you stays", "Thank you.", "Thank you.", config = w("en"), nearSilent = true),
            case("de: English Thank you from silence goes (Whisper on Auto)", "Thank you.", "", config = w("de"), silent = true),
            case("en: German Vielen Dank from silence stays", "Vielen Dank.", "Vielen Dank.", config = w("en"), silent = true),
            case("de: Vielen Dank from silence goes", "Vielen Dank.", "", config = w("de"), silent = true),
            case("zh: 我 from silence goes", "我", "", config = w("zh"), silent = true),
            case("en: 我 stays in English, even from silence", "我", "我", config = w("en"), silent = true),
            case("en: 你 stays in English", "你", "你", config = w("en"), silent = true),
            case("pt: a quiet E aí stays", "E aí", "E aí", config = w("pt"), nearSilent = true),
            case("pt-BR: E aí from silence goes", "E aí", "", config = w("pt-BR"), silent = true),
            case("pt: E aí, tudo bem? stays even from silence", "E aí, tudo bem?", "E aí, tudo bem?",
                config = w("pt"), silent = true),
            case("ja: a quiet おわり stays", "おわり", "おわり。", config = w("ja"), nearSilent = true),
            case("ja: おわり。おわり。 from silence goes", "おわり。おわり。", "", config = w("ja"), silent = true),
            case("ja: はい is a reply and is on no list", "はい。", "はい。", config = w("ja"), silent = true),
            case("ALWAYS drops ordinary ones too", "Gracias.", "",
                config = w("es").copy(phantomFilter = PhantomFilter.ALWAYS)),
            // Each recogniser's own phrases.
            case("Qwen3: The system. near-silent goes", "The system.", "", config = qwen3, nearSilent = true),
            case("Qwen3: システム in Japanese", "システム。", "", config = qwen3.copy(language = "ja"), nearSilent = true),
            case("Qwen3: The system. said aloud stays", "The system.", "The system.", config = qwen3),
            case("Ultra: The system. stays", "The system.", "The system.", config = other, silent = true),
            case("Whisper: The system. stays", "The system.", "The system.", config = w("en"), silent = true),
            case("Ultra: Thank you. stays", "Thank you.", "Thank you.", config = other, silent = true),
            case("Qwen3: Whisper's outro stays", "Thanks for watching!", "Thanks for watching!", config = qwen3,
                nearSilent = true),
            case("Canary: T S F", "T S F", "", config = canary, nearSilent = true),
            case("Whisper: T S F stays", "T S F", "T S F", config = w("de"), nearSilent = true),
            // Qwen3's leaked chat-template tokens.
            case("Qwen3: end token and what follows go", "Hello there.<|im_end|>#", "Hello there.", config = qwen3),
            case("Qwen3: leaked token after a phantom", "system<|endoftext|>#", "", config = qwen3, nearSilent = true),
            case("Qwen3: nothing before the end token", "<|endoftext|>#system", "", config = qwen3),
            case("Qwen3: another token goes", "<|zh|>你好", "你好", config = qwen3.copy(language = "zh")),
            case("Whisper: no token stripping", "Type <|endoftext|> here.", "Type <|endoftext|> here.", config = w("en")),
        ))
    }

    @Test fun hallucinationFilter() = run(listOf(
        case("phantom dropped when silent", "Thank you.", "", silent = true),
        case("ordinary phantom kept when only near-silent", "Thank you.", "Thank you.", nearSilent = true),
        case("distinctive phantom dropped when near-silent", "Thanks for watching!", "", nearSilent = true),
        case("phantom kept when speech was loud", "Thank you.", "Thank you."),
        case("several phantom sentences", "Thanks for watching! Bye.", "", silent = true),
        case("Qwen3 noise phantom dropped when near-silent", "The system is now running.", "", nearSilent = true),
        case("curly apostrophe and caps", "THANK YOU!", "", silent = true),
        case("phrase inside a real sentence survives", "Thank you for the help.", "Thank you for the help.", nearSilent = true),
        case("phantom plus real sentence survives", "Thank you. See you at five.", "Thank you. See you at five.", nearSilent = true),
        case("ALWAYS mode ignores the flag", "Thanks for watching!", "",
            config = TextProcConfig(phantomFilter = PhantomFilter.ALWAYS)),
        case("OFF mode keeps phantoms", "Thank you.", "Thank you.", nearSilent = true,
            config = TextProcConfig(phantomFilter = PhantomFilter.OFF)),
        case("blank-audio tag dropped", "[BLANK_AUDIO]", ""),
        case("square-bracket tags stripped from real text", "That's funny. [Music]", "That's funny."),
        case("parenthesised tag alone dropped", "(laughs)", ""),
        case("real parenthetical kept", "Call me (after five) tomorrow.", "Call me (after five) tomorrow."),
        case("noise caption after speech", "We need 25 millimeters long. (buzzer buzzing)", "We need 25 millimeters long."),
        case("noise caption mid-sentence", "I think (coughs) we should go.", "I think we should go."),
        case("two-word noise caption", "Hello (wind howling) there.", "Hello there."),
        case("three-word noise caption", "(soft music playing) Welcome back.", "Welcome back."),
        case("capitalised noise caption", "Nice. (Laughs)", "Nice."),
        case("all-caps caption", "(MUSIC PLAYING) Hello there.", "Hello there."),
        case("unknown all-caps word in brackets", "Hi (BLANK_AUDIO)", "Hi"),
        case("parenthetical with a sound word is kept", "The (music was loud) part.", "The (music was loud) part."),
        case("lowercase parenthetical kept", "See the notes (page 4) first.", "See the notes (page 4) first."),
        case("asterisk caption alone dropped", "* Musik *", ""),
        case("asterisk captions alone dropped", "*Klopfen* *Gelach*", ""),
        case("asterisk caption with a sound word dropped", "*groan*", ""),
        case("asterisk group without a known sound word kept", "*Hiereräusch*", "*Hiereräusch*", nearSilent = true),
        case("asterisk noise caption after speech", "Hello there. *coughs*", "Hello there."),
        case("asterisk word in a sentence kept", "That was *really* good.", "That was *really* good."),
        case("asterisk word alone kept", "*important*", "*Important*"),
        case("asterisk word alone kept when near-silent", "*important*", "*Important*", nearSilent = true),
        case("asterisk capitals kept", "*IMPORTANT*", "*IMPORTANT*"),
        case("asterisk name kept", "*Felix*", "*Felix*"),
        case("asterisks around digits kept", "5 * 3 * 2", "5 * 3 * 2"),
        case("lone brackets from hum", "( ( ( ( (", ""),
        case("one lone bracket", "(", ""),
        case("noise caption kept when filter OFF", "Hi (buzzing)", "Hi (buzzing)",
            config = TextProcConfig(phantomFilter = PhantomFilter.OFF)),
        case("music notes", "♪ ♪", ""),
        case("tags kept when filter OFF", "[BLANK_AUDIO]", "[BLANK_AUDIO]",
            config = TextProcConfig(phantomFilter = PhantomFilter.OFF)),
        case("punctuation-only result is nothing", "...", ""),
        case("whitespace-only is nothing", "   ", ""),
    ))

    /** Loops from the #14/#15 host benchmark outputs (tools/asr-bench), shortened. */
    @Test fun decoderLoops() = run(listOf(
        case("Canary letter loop to the token cap is dropped", "E ".repeat(135).trim(), ""),
        case("loop after real words is cut from its start", "It was the " + "T ".repeat(40).trim(), "It was the"),
        case("loop with trailing punctuation", "Get Get Get Get Get Get Get Get.", ""),
        case("filler loop keeps what came before", "Um, " + "Uh, ".repeat(54).trim(), "Um",
            config = TextProcConfig(removeFillers = false)),
        case("recovered loop keeps one copy", "No " + "the ".repeat(12) + "scrolling was uh different",
            "No the scrolling was different"),
        case("letter loop before real words is dropped whole", "T ".repeat(32) + "Looks a bit like a.",
            "Looks a bit like a."),
        case("loop cut short by the token cap", "Um, " + "Uh, ".repeat(54) + "U", "Um",
            config = TextProcConfig(removeFillers = false)),
        case("Whisper letter loop after a word", "XL, " + "X, ".repeat(9) + "X", "XL"),
        // Whisper on 1.7 s of stop-flush noise on the Pixel 8a (#20). "It's" keys as "its", not
        // "it", so it isn't part of the loop and stays.
        case("stop-flush loop after a different word", "It's it it it it it it it it", "It's"),
        case("two-letter cycle to the cap", "It was " + "S E ".repeat(40) + "S", "It was"),
        case("two-word stutter keeps one copy", "I think " + "you know ".repeat(7) + "it works", "I think you know it works"),
        case("a short spelled name stays", "my name is S M I T H", "My name is S M I T H"),
        case("five repeats are speech", "no no no no no", "No no no no no"),
        case("repeated digits are speech", "call five five five five five five zero one four two",
            "Call 555-555-0142"),
        case("repeated digit characters are speech", "PIN 0 0 0 0 0 0", "PIN 0 0 0 0 0 0"),
        case("three repeats are speech", "yeah yeah yeah", "Yeah yeah yeah"),
        case("different words are not a loop", "a b c d e f g h", "A b c d e f g h"),
        case("loops kept when filter OFF", "E E E E E E E", "E E E E E E E",
            config = TextProcConfig(phantomFilter = PhantomFilter.OFF)),
    ))

    /** Loops with no spaces in them (#37): units glued by punctuation, runs of one letter. */
    @Test fun loopsWithoutSpaces() {
        // Whisper turbo on English meeting audio (#37 multilingual run), verbatim.
        val xlsx = "It's an " + "xlsx.".repeat(29) + "xxlsx.xx.xxx.xxx.xxx.xxxx." + "x".repeat(46)
        val cases = listOf(
            Triple("Whisper unit loop and its degenerate tail keep one copy", xlsx, "It's an xlsx"),
            // Nemotron 3.5 on Chinese (#37), verbatim.
            Triple("Thai letter run inside Chinese goes whole", "港神อออออออออออออออ出道近二十年", "港神出道近二十年"),
            Triple("letter run at the very end", "我们走吧" + "อ".repeat(40), "我们走吧"),
            Triple("letter run alone leaves nothing", "อ".repeat(200), ""),
            Triple("letter run as its own word", "It was " + "a".repeat(30) + " fine", "It was fine"),
            Triple("mixed scripts and words", "Hello 港神" + "อ".repeat(20) + "出道 world", "Hello 港神出道 world"),
            Triple("a removed last word takes the comma before it", "Hello, " + "อ".repeat(20), "Hello"),
            Triple("…and a CJK comma", "你好， " + "อ".repeat(20), "你好"),
            Triple("a removed word mid-sentence keeps the comma", "Hello, " + "อ".repeat(20) + " world", "Hello, world"),
            Triple("drawn-out word keeps one letter", "Nooooooooooooo!", "No!"),
            Triple("drawn-out kana after kana", "すごい" + "い".repeat(12), "すごい"),
            Triple("drawn-out kana after kanji", "凄い" + "い".repeat(12), "凄い"),
            Triple("unit loop before more text in the same word", "你好".repeat(7) + "，我是小明", "你好，我是小明"),
            Triple("unit copy cut short by the cap", "Open " + "xlsx.".repeat(8) + "xl", "Open xlsx"),
            Triple("punctuation after the loop stays", "Open the file." + "docx.".repeat(8), "Open the file.docx."),
            Triple("hyphen-joined unit loop", "It's a " + "bye-".repeat(9) + "bye now", "It's a bye now"),
            Triple("many laughs become one", "ha".repeat(10), "ha"),
        )
        val failures = cases.mapNotNull { (name, input, expected) ->
            val actual = Phantoms.cutLoops(input)
            if (actual == expected) null else "$name: expected <$expected> but was <$actual>"
        }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))

        val survivors = listOf(
            "haha", "lalala", "hahahaha", "bye-bye", "so-so", "tsk tsk", "can-can", "Baden-Baden", "pooh-pooh",
            "xlsx.xlsx.xlsx.xlsx.xlsx", "1.1.1.1", "1.1.1.1.1.1.1.1.1", "a.b.c.d", "...", "!!!", "!!!!!!!!!!!!",
            "……………", "謝謝", "谢谢谢谢", "哈哈哈哈哈哈", "ㅋㅋㅋㅋㅋㅋㅋㅋ", "www", "wwwwwwww", "zzz", "Hmmmm",
            "Hmmmmmmmmm", "aaaa", "1000000", "100000000000000", "wwww.example.com", "ーーー", "あ" + "ー".repeat(12),
            "Mississippi", "Ich habe Baden-Baden besucht.", "ok so it's an xlsx file",
        )
        for (s in survivors) assertSame(s, Phantoms.cutLoops(s), "changed <$s>")

        // Through the pipeline: Finals and partials both get the cut.
        run(listOf(case("Final", xlsx, "It's an xlsx"), case("Final, Chinese", "港神อออออออออออออออ出道近二十年", "港神出道近二十年")))
        assertEquals("It's an xlsx", TextPipeline(TextProcConfig()).cleanPartial(xlsx))
    }

    /** Live partials: only normalisation, sound tags and loops (#19). */
    @Test fun cleanPartial() {
        val cases = listOf(
            Triple("loop at the end is removed", "It was the " + "E ".repeat(20).trim(), "It was the"),
            Triple("loop alone leaves nothing", "E ".repeat(40).trim(), ""),
            Triple("recovered stutter keeps one copy", "No " + "the ".repeat(8) + "scrolling", "No the scrolling"),
            Triple("noise caption stripped", "Hello (buzzing) there", "Hello there"),
            Triple("tag alone leaves nothing", "[BLANK_AUDIO]", ""),
            Triple("whitespace normalised", "  hello \n  wor ", "hello wor"),
            // The 3-repeat tail cut is the stabilizer's (#28), not cleanPartial's.
            Triple("tail repeat left to the stabilizer", "this is a test test test", "this is a test test test"),
            // No capitalisation, fillers, numbers or phantom drop: the Final does those.
            Triple("ordinary text unchanged", "um so twenty five thank you", "um so twenty five thank you"),
        )
        val pipeline = TextPipeline(TextProcConfig())
        val failures = cases.mapNotNull { (name, input, expected) ->
            val actual = pipeline.cleanPartial(input)
            if (actual == expected) null else "$name: expected <$expected> but was <$actual>"
        }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))

        val off = TextPipeline(TextProcConfig(phantomFilter = PhantomFilter.OFF))
        assertEquals("E E E E E E E (buzzing)", off.cleanPartial(" E E E E E E E  (buzzing)"))
    }

    /** The live-partial tail cut (#28): three repeats at the end keep one copy; Finals need six. */
    @Test fun tailRepeats() {
        val cases = listOf(
            Triple("tail repeat keeps one copy", "this is a test test test", "this is a test"),
            Triple("seven repeats keep one copy", "this is a" + " test".repeat(7), "this is a test"),
            Triple("tail repeat of a block", "go to the shop the shop the shop", "go to the shop"),
            Triple("a word cut short, and the comma after the kept copy", "a test, test, test, te", "a test"),
            Triple("tail repeat with an unfinished block", "the shop the shop the shop the sh", "the shop"),
            Triple("tail repeat with a whole first word after it", "the shop the shop the shop the", "the shop"),
            Triple("two repeats stay", "yeah yeah", "yeah yeah"),
            Triple("repeat before the end stays", "no no no I said", "no no no I said"),
            Triple("tail digits stay", "call five five five", "call five five five"),
            Triple("single-letter tail goes", "It was E E E", "It was"),
            Triple("but I and a are words", "and then I I I", "and then I"),
            // whisper-base-en on LibriSpeech 3729-6852-0003 (#28 harness).
            Triple("punctuation after the repeats goes too", "pronounced \"skura\" and \"s\" and \"s\" and \"s\" and \"",
                "pronounced \"skura\" and \"s\""),
            Triple("punctuation alone at the end stays", "a test -", "a test -"),
            Triple("ordinary text unchanged", "um so twenty five thank you", "um so twenty five thank you"),
        )
        val failures = cases.mapNotNull { (name, input, expected) ->
            val actual = Phantoms.cutTailRepeats(input)
            if (actual == expected) null else "$name: expected <$expected> but was <$actual>"
        }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))
    }

    @Test fun fillers() = run(listOf(
        case("leading capitalised filler hands over its capital", "Um, I think so.", "I think so."),
        case("filler between commas", "I was, uh, going home.", "I was going home."),
        case("filler after a comma", "Yes, um I agree.", "Yes, I agree."),
        case("filler before a full stop", "I think, uh.", "I think."),
        case("filler mid-sentence", "It is um fine.", "It is fine."),
        case("repeated fillers", "Um, uh, so it works.", "So it works."),
        case("filler after a sentence", "Right. Uh, so next.", "Right. So next."),
        case("filler alone", "Um.", ""),
        case("um inside words is safe", "Umbrellas and summer drums.", "Umbrellas and summer drums."),
        case("uh-oh is not a filler", "Uh-oh, it broke.", "Uh-oh, it broke."),
        case("all-caps ER is a word", "Take him to the ER now.", "Take him to the ER now."),
        case("err is a word", "To err is human.", "To err is human."),
        case("mm is millimetres", "It is 5 mm wide.", "It is 5 mm wide."),
        case("filler with ellipsis", "So um... we wait.", "So we wait."),
        case("hmm", "Hmm, let me check.", "Let me check."),
        case("fillers kept when off", "Um, hi.", "Um, hi.", config = TextProcConfig(removeFillers = false)),
        case("custom filler list", "Like, it was ok.", "It was ok.",
            config = TextProcConfig(fillerWords = listOf("like"))),
        case("default list not used with a custom one", "Um, fine.", "Um, fine.",
            config = TextProcConfig(fillerWords = listOf("like"))),
    ))

    /** #41: the English-only rules are skipped for other languages; the rest runs everywhere. */
    @Test fun languages() {
        val de = TextProcConfig(language = "de", spokenPunctuation = true)
        val fr = TextProcConfig(language = "fr", spokenPunctuation = true)
        val es = TextProcConfig(language = "es", spokenPunctuation = true)
        val ja = TextProcConfig(language = "ja", spokenPunctuation = true)
        run(listOf(
            case("de: er is he, not a filler", "Er sagt, er kommt morgen.", "Er sagt, er kommt morgen.", config = de),
            case("en: the same er is a filler", "Er sagt, er kommt morgen.", "Sagt, kommt morgen.",
                config = TextProcConfig()),
            case("de: English commands don't fire", "Das war gut. Scratch that. New line.",
                "Das war gut. Scratch that. New line.", config = de),
            case("de: English number words stay", "Die Band heißt twenty one pilots.",
                "Die Band heißt twenty one pilots.", config = de),
            case("de: spoken punctuation off", "Die Periode period", "Die Periode period", config = de),
            case("fr: hm and ah stay", "Hm, ah bon, d'accord.", "Hm, ah bon, d'accord.", config = fr),
            case("fr: keeps the space before ! and :", "Attention : c'est chaud !", "Attention : c'est chaud !", config = fr),
            case("fr: still tidies the space before a comma", "Oui , bien sûr.", "Oui, bien sûr.", config = fr),
            case("en: removes the space before ?", "And then ?", "And then?", config = TextProcConfig()),
            case("fr: joins and capitalises", "et après ?", "C'est fini. Et après ?", before = "C'est fini.", config = fr),
            case("es: ah stays", "Ah, ya entiendo, er, no.", "Ah, ya entiendo, er, no.", config = es),
            case("es: numbers stay words", "Son twenty five euros.", "Son twenty five euros.", config = es),
            case("ja: passes through", "えーと、明日は雨です。", "えーと、明日は雨です。", config = ja),
            case("ja: an English filler in Japanese output stays", "Um、明日です。", "Um、明日です。", config = ja),
            case("de: sound tags still go", "[Musik] Hallo zusammen.", "Hallo zusammen.", config = de),
            case("de: hallucination filter still drops", "Thank you.", "", config = de, silent = true),
            case("de: dictionary still applies", "ich nutze kubernetes", "Ich nutze Kubernetes",
                config = de.copy(replacements = listOf(Replacement("kubernetes", "Kubernetes")))),
            case("de: snippets still apply", "meine adresse", "Hauptstraße 1",
                config = de.copy(snippets = listOf(Snippet("meine adresse", "Hauptstraße 1")))),
            case("region tag en-GB is English", "Um, twenty five.", "25.", config = TextProcConfig(language = "en-GB")),
            case("region tag de-AT is not", "Er kommt.", "Er kommt.", config = TextProcConfig(language = "de-AT")),
            case("unknown language skips the English rules", "Um, er kommt.", "Um, er kommt.",
                config = TextProcConfig(language = "")),
        ))
    }

    /** #60: Parakeet ja's raw output, from the FLEURS-ja and homophone benchmark runs. */
    @Test fun japanese() {
        val ja = TextProcConfig(language = "ja")
        val pja = ja.copy(lowerCaseLatin = true)
        val jaSnippet = pja.copy(snippets = listOf(Snippet("メール", "max@example.com"), Snippet("住所", "東京都")))
        run(listOf(
            // <unk>, any language.
            case("ja: <unk> removed", "中世の終<unk>に向かって", "中世の終に向かって。", config = ja),
            case("ja: <unk> between spaces leaves no space", "骨 <unk> う品", "骨う品。", config = ja),
            case("en: <unk> removed", "The <unk> report is ready.", "The report is ready."),
            case("<unk> alone is nothing", "<unk>", "", config = ja),
            // Sentence end.
            case("ja: 。 appended after kana", "明日来ますか", "明日来ますか。", config = ja),
            case("ja: 。 appended after kanji", "今日は雨", "今日は雨。", config = ja),
            case("ja: 。 appended after a digit", "会議は3時", "会議は3時。", config = ja),
            case("ja: 。 not doubled", "今日は雨です。", "今日は雨です。", config = ja),
            case("ja: nothing after ？", "本当ですか？", "本当ですか？", config = ja),
            case("ja: nothing after !", "すごい!", "すごい!", config = ja),
            case("ja: nothing after 、", "それで、", "それで、", config = ja),
            case("ja: nothing after a closing quote", "彼は「はい」", "彼は「はい」", config = ja),
            case("ja: nothing after …", "それは…", "それは…", config = ja),
            // Snippets.
            case("ja: nothing after a snippet", "連絡先は、メール", "連絡先は、max@example.com", config = jaSnippet),
            case("ja: a snippet's letters are not acronyms", "住所 メール", "東京都 max@example.com", config = jaSnippet),
            case("en: no full stop added", "see you tomorrow", "See you tomorrow"),
            case("unknown language: no 。", "今日は雨", "今日は雨", config = TextProcConfig(language = "")),
            // うん: the pre-speech one is dropped in the app (FinishedUtterances), never here.
            case("ja: a lone うん reply kept", "うん", "うん。", config = ja),
            case("ja: うんうん kept after text", "うんうん", "今日は雨です。うんうん。", before = "今日は雨です。", config = ja),
            // Acronyms: only for Parakeet ja (lowerCaseLatin), whose vocabulary has no capitals.
            case("ja: fbi", "fbiの捜査官", "FBIの捜査官。", config = pja),
            case("ja: letters and digits", "f1とm16", "F1とM16。", config = pja),
            case("ja: one letter inside katakana", "ジョンfケネディ", "ジョンFケネディ。", config = pja),
            case("ja: four letters", "usocの選手", "USOCの選手。", config = pja),
            case("ja: five letters stay", "それでsafariを開く", "それでsafariを開く。", config = pja),
            case("ja: mixed case stays", "新しいiPhoneとpH", "新しいiPhoneとpH。", config = pja),
            case("ja: digit first stays", "11nの規格", "11nの規格。", config = pja),
            case("ja: acronym at the end", "それはemo", "それはEMO。", config = pja),
            case("ja: Whisper's lower case stays", "webサイトでdesuと書く", "webサイトでdesuと書く。", config = ja),
            case("en: short lower-case words stay", "the fbi and f1", "The fbi and f1"),
            case("ja: no sentence capitals on Latin words", "iPhoneを買った", "iPhoneを買った。", config = ja),
            case("ja: a dictionary entry keeps its lower case", "えぬぴーえむ", "npm。",
                config = ja.copy(replacements = listOf(Replacement("えぬぴーえむ", "npm")))),
            case("ja: a dictionary replacement is not upper-cased", "ページは gh です", "ページは github です。",
                config = pja.copy(replacements = listOf(Replacement("gh", "github")))),
            case("ja: acronyms still run around a replacement", "fbi と gh", "FBI と github。",
                config = pja.copy(replacements = listOf(Replacement("gh", "github")))),
            // English heard on a Japanese phone (Ultra, or Whisper on Auto): no Japanese rules.
            case("ja: English speech stays English", "the app is ok", "The app is ok", config = ja),
            case("ja: English speech stays English, Parakeet flags too", "the app is ok", "The app is ok",
                config = pja),
            case("ja: English after Japanese gets its space and capital", "and it was great", "東京。 And it was great",
                before = "東京。", config = ja),
            // Spacing.
            case("ja: space between kana and kanji removed", "静かに まを開けた", "静かにまを開けた。", config = ja),
            case("ja: space next to Latin kept", "ジョン f ケネディ", "ジョン F ケネディ。", config = pja),
            case("zh: in-utterance space kept outside Japanese", "你好 世界", "你好 世界", config = TextProcConfig(language = "zh")),
            Case("ja: no space between utterances", listOf("今日は雨です", "明日も雨です"), "今日は雨です。明日も雨です。", config = ja),
            case("ja: space before Latin after 。", "fbiです", "以上。 FBIです。", before = "以上。", config = pja),
            case("no joining space at a script switch only when both sides are CJK", "and it was great",
                "東京 and it was great", before = "東京", config = TextProcConfig(language = "en")),
            case("ja: no space before a full-width mark", "？", "本当？", before = "本当", config = ja),
            case("zh: no joining space, whatever the language", "明天见。", "你好。明天见。", before = "你好。",
                config = TextProcConfig(language = "zh")),
            case("half-width katakana joins without a space", "ｶﾀｶﾅ", "ｶﾀｶﾅｶﾀｶﾅ", before = "ｶﾀｶﾅ",
                config = TextProcConfig(language = "")),
            case("prolonged sound mark joins without a space", "です", "ラーメンーです", before = "ラーメンー",
                config = TextProcConfig(language = "")),
            case("ko: Hangul keeps its joining space", "내일 봐요.", "안녕하세요. 내일 봐요.", before = "안녕하세요.",
                config = TextProcConfig(language = "ko")),
            Case("en: English joins unchanged", listOf("Hello there.", "how are you?"), "Hello there. How are you?"),
        ))
        // Partials get <unk> removed but never 。.
        val p = TextPipeline(pja)
        assertEquals("中世の終に向かって", p.cleanPartial("中世の終<unk>に向かって"))
        assertEquals("うん", p.cleanPartial("うん"))
        assertEquals("fbiの", p.cleanPartial("fbiの"))
    }

    @Test fun spokenCommands() = run(listOf(
        case("new line", "Dear John, new line, thanks for everything.", "Dear John,\nThanks for everything."),
        case("new line punctuated by Whisper", "Hello. New line. How are you?", "Hello.\nHow are you?"),
        case("newline as one word", "one newline two", "One\nTwo"),
        case("new paragraph", "First part. New paragraph. Second part.", "First part.\n\nSecond part."),
        case("new paragraph after new line is one blank line", "A new line new paragraph B", "A\n\nB"),
        case("new paragraph after existing text", "New paragraph. Hello.", "Intro.\n\nHello.", before = "Intro."),
        case("new line with trailing space before it", "new line", "Hi\n", before = "Hi "),
        case("commands off", "Hello new line world", "Hello new line world",
            config = TextProcConfig(spokenCommands = false)),
        case("scratch that removes previous sentence in the same utterance",
            "I'll come at five. Scratch that. I'll come at six.", "I'll come at six."),
        case("scratch that removes previous sentence of earlier text",
            "Scratch that.", "First one.", before = "First one. Second one."),
        case("scratch that at the start with nothing before", "Scratch that. Hello.", "Hello."),
        case("scratch that on unpunctuated text removes it all", "scratch that", "", before = "hello there"),
        case("scratch that twice", "Scratch that, scratch that.", "One.", before = "One. Two. Three."),
        case("scratch that removes a line break first", "Scratch that.", "Hello.", before = "Hello.\n"),
        case("scratch that stops at a line break", "Scratch that.", "Line one\n", before = "Line one\nLine two"),
        case("scratch that mid-utterance without punctuation", "one two scratch that three", "Three"),
        case("scratch that with a quoted sentence end", "Scratch that.", "He said \"hi.\"",
            before = "He said \"hi.\" Then left."),
        // Known limitation (README): the phrase fires in ordinary speech too.
        case("scratch that fires even in ordinary speech", "Scratch that itch.", "Itch."),
        case("scratched is not scratch", "I scratched that.", "I scratched that."),
        case("scratch that's is speech", "Scratch that's a good idea.", "Keep. Scratch that's a good idea.", before = "Keep."),
        case("new line's is speech", "The new line's color", "The new line's color"),
    ))

    @Test fun spokenPunctuation() = run(listOf(
        case("period and question mark", "hello period how are you question mark", "Hello. How are you?", config = punct),
        case("comma", "yes comma I agree", "Yes, I agree", config = punct),
        case("spoken comma replaces Whisper's", "Hello, comma, how are you?", "Hello, how are you?", config = punct),
        case("spoken period replaces Whisper's comma", "I'm done, period.", "I'm done.", config = punct),
        case("exclamation point", "wow exclamation point", "Wow!", config = punct),
        case("full stop", "that's all full stop", "That's all.", config = punct),
        case("colon and semicolon", "note colon one semicolon two", "Note: one; two", config = punct),
        case("off by default: period is a word", "The trial period ended.", "The trial period ended."),
        case("on: period is punctuation even mid-sentence", "the trial period ended", "The trial. Ended", config = punct),
        case("spoken period after typed space", "period", "Done.", before = "Done ", config = punct),
    ))

    @Test fun replacements() {
        val rules = TextProcConfig(
            replacements = listOf(
                Replacement("kubernetes", "Kubernetes"),
                Replacement("git hub", "GitHub"),
                Replacement("new york", "New York City"),
                Replacement("york", "Yorkshire"),
                Replacement("teh", "the"),
                Replacement("cafe", "café"),
                Replacement("don", "Donald"),
                Replacement("basically", ""),
                Replacement("sed", "C:\\temp\\$1"),
                Replacement("naïve", "naive"),
                Replacement("re\u0301sume\u0301", "CV"), // typed with combining accents
                Replacement("Zoe\u0308", "Zoë"),
                Replacement("zoë", "shadowed"), // same phrase as the line above once both are NFC
            ),
        )
        run(listOf(
            case("verbatim when target has capitals", "i use kubernetes daily", "I use Kubernetes daily", config = rules),
            case("multi-word source, flexible whitespace", "push to git  hub", "Push to GitHub", config = rules),
            case("overlapping: longest wins", "I love new york", "I love New York City", config = rules),
            case("overlapping: shorter still works alone", "Old york is nice", "Old Yorkshire is nice", config = rules),
            case("case follows match: lower", "fix teh bug", "Now fix the bug", before = "Now", config = rules),
            case("case follows match: title", "Teh end", "The end", config = rules),
            case("case follows match: all caps", "TEH END", "THE END", config = rules),
            case("no cascading", "x", "Y", config = TextProcConfig(replacements = listOf(Replacement("x", "y"), Replacement("y", "z")))),
            case("duplicate source: first wins", "x", "Y", config = TextProcConfig(replacements = listOf(Replacement("x", "y"), Replacement("X", "z")))),
            case("whole word only", "tehran is far", "Tehran is far", config = rules),
            case("apostrophe blocks inside a word", "don't go", "Don't go", config = rules),
            case("possessive still matches", "don's car", "Donald's car", config = rules),
            case("unicode target", "a cafe latte", "A café latte", config = rules),
            case("unicode source, decomposed input normalised", "so nai\u0308ve", "So naive", config = rules),
            case("decomposed source matches precomposed input", "my résumé is ready", "My CV is ready", config = rules),
            case("decomposed source matches decomposed input", "my re\u0301sume\u0301 is ready", "My CV is ready", config = rules),
            case("duplicate after NFC: first wins", "hi zoë", "Hi Zoë", config = rules),
            case("empty target deletes", "it basically works", "It works", config = rules),
            case("target is literal, not a regex replacement", "sed", "C:\\temp\\$1", config = rules),
        ))
    }

    @Test fun snippets() {
        val cfg = TextProcConfig(
            snippets = listOf(
                Snippet("my email", "max@example.com"),
                Snippet("my signature", "Best,\nMax"),
                Snippet("um snippet", "um, new line"),
                Snippet("my cafe\u0301", "Café Marmalade, 1 Example Road"), // trigger typed with a combining accent
                Snippet("my naïve line", "(precomposed trigger)"),
            ),
        )
        run(listOf(
            case("inline", "Send it to my email please.", "Send it to max@example.com please.", config = cfg),
            case("decomposed trigger matches precomposed speech", "Meet at my café.", "Meet at Café Marmalade, 1 Example Road.", config = cfg),
            case("precomposed trigger matches decomposed speech", "My nai\u0308ve line", "(precomposed trigger)", config = cfg),
            case("whole utterance drops Whisper punctuation", "My signature.", "Best,\nMax", config = cfg),
            case("sentence start keeps the snippet's case", "My email is the best one.", "max@example.com is the best one.", config = cfg),
            case("snippet text is protected from other rules", "Um snippet.", "um, new line", config = cfg),
            case("snippet joins with a space", "My email.", "Contact: max@example.com", before = "Contact:", config = cfg),
            case("trigger must be whole words", "my emailing list", "My emailing list", config = cfg),
            case("private-use characters in input are kept", "hi \uE000 there", "Hi \uE000 there", config = cfg),
            case("placeholder sentinel in input is dropped", "hi\uFDD0 there", "Hi there", config = cfg),
            case("snippet with empty text inserts nothing", "Nothing.", "",
                config = TextProcConfig(snippets = listOf(Snippet("nothing", "")))),
            case("private-use replacement output is not a snippet", "apple logo and my email", "\uF8FF And max@example.com",
                config = cfg.copy(replacements = listOf(Replacement("apple logo", "\uF8FF")))),
        ))
    }

    /** #62: triggers in and next to scripts written without spaces. */
    @Test fun cjkTriggers() {
        val ja = TextProcConfig(
            language = "ja",
            lowerCaseLatin = true,
            replacements = listOf(
                Replacement("gh", "github"),
                Replacement("メール", "mail"),
                Replacement("東京", "Tokyo"),
                Replacement("えぬぴーえむ", "npm"),
            ),
            snippets = listOf(Snippet("住所", "1-1 Chiyoda")),
        )
        val fullWidth = TextProcConfig(
            language = "ja",
            replacements = listOf(Replacement("gh", "github"), Replacement("ｇｈ", "GitHub")),
        )
        val zh = TextProcConfig(
            language = "zh",
            replacements = listOf(Replacement("邮件", "email"), Replacement("gh", "github")),
            snippets = listOf(Snippet("我的地址", "1 Example Road")),
        )
        val ko = TextProcConfig(
            language = "ko",
            replacements = listOf(Replacement("메일", "email"), Replacement("gh", "github")),
            snippets = listOf(Snippet("내 주소", "1 Example Road")),
        )
        run(listOf(
            // Japanese: kanji and hiragana match anywhere; a katakana run is one word.
            case("ja: kanji mid-sentence", "私は東京に住んでいます", "私はTokyoに住んでいます。", config = ja),
            case("ja: kanji at the start", "東京は雨", "Tokyoは雨。", config = ja),
            case("ja: kanji at the end", "行き先は東京", "行き先はTokyo。", config = ja),
            case("ja: kanji inside a longer kanji word", "東京都に行く", "Tokyo都に行く。", config = ja),
            case("ja: hiragana mid-sentence", "これはえぬぴーえむです", "これはnpmです。", config = ja),
            case("ja: katakana mid-sentence", "連絡先はメールです", "連絡先はmailです。", config = ja),
            case("ja: katakana at the start", "メールを送る", "mailを送る。", config = ja),
            case("ja: katakana at the end", "連絡先はメール", "連絡先はmail。", config = ja),
            case("ja: katakana not inside a longer katakana word", "メールアドレスを送る", "メールアドレスを送る。", config = ja),
            case("ja: katakana not after katakana, but after a Latin letter", "Eメールとボイスメール", "Emailとボイスメール。",
                config = ja),
            case("ja: katakana after the long-vowel mark is inside a word", "ルーメール", "ルーメール。", config = ja),
            case("ja: snippet mid-sentence", "私の住所は秘密", "私の1-1 Chiyodaは秘密。", config = ja),
            case("ja: snippet alone", "住所", "1-1 Chiyoda", config = ja),
            // Latin next to kana or kanji counts as a whole word.
            case("ja: Latin trigger before kana", "ghのページ", "githubのページ。", config = ja),
            case("ja: Latin trigger after kanji", "詳細はgh", "詳細はgithub。", config = ja),
            case("ja: Latin trigger still not inside a Latin word", "ghostのページ", "ghostのページ。", config = ja),
            case("ja: Latin trigger not before a digit", "gh2のページ", "GH2のページ。", config = ja),
            // Full-width digits and Latin letters are words of their own, not CJK.
            case("ja: Latin trigger not before a full-width digit", "gh２のページ", "gh２のページ。", config = fullWidth),
            case("ja: Latin trigger not after a full-width letter", "ａghのページ", "ａghのページ。", config = fullWidth),
            case("ja: full-width trigger before kana", "ｇｈのページ", "GitHubのページ。", config = fullWidth),
            case("ja: full-width trigger in any case", "ＧＨのページ", "GitHubのページ。", config = fullWidth),
            case("ja: full-width trigger not inside a full-width word", "ｇｈｏｓｔのページ", "ｇｈｏｓｔのページ。",
                config = fullWidth),
            // Chinese.
            case("zh: mid-sentence", "请发邮件给我", "请发email给我", config = zh),
            case("zh: at the start", "邮件已发送", "email已发送", config = zh),
            case("zh: at the end", "我收到了邮件", "我收到了email", config = zh),
            case("zh: snippet mid-sentence", "寄到我的地址吧", "寄到1 Example Road吧", config = zh),
            case("zh: Latin trigger next to Han", "打开gh页面", "打开github页面", config = zh),
            // Korean: words start after a space, particles join the end.
            case("ko: mid-sentence with a particle", "새 메일을 보냈어요", "새 email을 보냈어요", config = ko),
            case("ko: at the start", "메일 보냈어요", "Email 보냈어요", config = ko),
            case("ko: at the end", "새 메일", "새 email", config = ko),
            case("ko: not inside a longer word", "이메일을 보냈어요", "이메일을 보냈어요", config = ko),
            case("ko: not at the start of a longer word", "메일함을 열었어요", "메일함을 열었어요", config = ko),
            case("ko: two particles", "메일에서는 안 보여요", "Email에서는 안 보여요", config = ko),
            case("ko: copula", "그건 메일이에요", "그건 email이에요", config = ko),
            case("ko: particle then more Hangul is a longer word", "메일이야기", "메일이야기", config = ko),
            case("ko: two-word snippet not inside a longer word", "내 주소록을 열어요", "내 주소록을 열어요", config = ko),
            case("ko: two-word snippet", "내 주소로 보내 주세요", "1 Example Road로 보내 주세요", config = ko),
            case("ko: Latin trigger before a particle", "그건 gh에서 봤어요", "그건 github에서 봤어요", config = ko),
            // English is unchanged: a CJK neighbour is a boundary, a Latin one is not.
            case("en: Latin trigger still whole words", "ghost and gh", "Ghost and github",
                config = TextProcConfig(replacements = listOf(Replacement("gh", "github")))),
        ))
    }

    @Test fun joiningAndCapitalisation() = run(listOf(
        Case("two utterances get a space", listOf("Hello there.", "how are you?"), "Hello there. How are you?"),
        Case("mid-sentence join keeps case", listOf("I went to the", "Store."), "I went to the Store."),
        Case("after an ellipsis no capital", listOf("I was thinking...", "maybe not."), "I was thinking... maybe not."),
        Case("after a new line capital", listOf("Hi new line", "there"), "Hi\nThere"),
        case("existing trailing space not doubled", "world", "Hello world", before = "Hello "),
        case("capital after typed sentence", "and more", "Done. And more", before = "Done."),
        case("no capital mid typed sentence", "and more", "so and more", before = "so"),
        case("no space before a closing apostrophe", "’s fine", "It’s fine", before = "It"),
        case("leading punctuation of an utterance is dropped", ", right?", "Okay right?", before = "Okay"),
        case("no space after opening bracket", "maybe", "(maybe", before = "("),
        case("digit first is not capitalised", "3 apples.", "3 apples."),
        case("capitalisation off", "hello. world", "hello. world", config = plain),
        case("capitalisation never lowercases", "NASA said so.", "x NASA said so.", before = "x"),
        case("whitespace and control characters normalised", "  hello\t\u0007 world  ", "Hello world"),
        case("space before punctuation tidied", "Hello , world .", "Hello, world."),
        case(".NET keeps its dot", "I use .NET daily", "I use .NET daily"),
        case(".NET at the start keeps its dot", ".NET is great", ".NET is great"),
        case("accented capital", "élan vital", "Élan vital"),
        case("non-Latin script passes through", "привет мир", "Привет мир"),
        case("emoji with ZWJ survives", "family 👨\u200D👩\u200D👧 here", "Family 👨\u200D👩\u200D👧 here"),
    ))

    @Test fun editIsMinimal() {
        val p = TextPipeline(TextProcConfig())
        assertEquals(TextEdit(0, " Two."), p.process("Two.", "One."))
        assertEquals(TextEdit(5, ""), p.process("Scratch that.", "One. Two."))
        assertEquals(TextEdit.NONE, p.process("Thank you.", "One.", silent = true))
        assertTrue(TextEdit.NONE.isEmpty)
    }

    @Test fun deterministicAndFast() {
        val p = TextPipeline(
            TextProcConfig(
                spokenPunctuation = true,
                replacements = (1..500).map { Replacement("word$it", "Word$it") },
                snippets = (1..100).map { Snippet("snip $it", "text $it") },
            ),
        )
        val input = "Um, so I think word42 is, uh, fine comma and snip 7 new line scratch that. " +
            "Then we go to the store and buy some milk period question mark word499 done."
        val first = p.process(input, "Earlier text.")
        repeat(2_000) { p.process(input, "Earlier text.") } // warm-up
        // Fastest of several batches: a busy machine (a parallel native build)
        // slows some batches down, but an algorithmic regression slows them all.
        val batch = 1_000
        val perCallMicros = (1..5).minOf {
            val start = System.nanoTime()
            repeat(batch) { assertEquals(first, p.process(input, "Earlier text.")) }
            (System.nanoTime() - start) / batch / 1_000.0
        }
        // ~0.5 ms unloaded, ~1.1 ms at load 15-25 (a sibling's emulator + native build). 5 ms is
        // still invisible next to a decode and still catches an algorithmic blow-up.
        assertTrue(perCallMicros < 5_000.0, "took $perCallMicros µs per utterance")
        println("TextPipeline: $perCallMicros µs per utterance")
    }
}
