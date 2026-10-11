package app.marmalade.stt.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The translations' machine checks (#51), so a new or changed English string
 * can't ship untranslated or broken. Every shipped locale must have every
 * translatable key of `res/values/strings*.xml`, in the file of the same
 * name, with the same format placeholders, its language's CLDR plural
 * categories, escaped apostrophes and quotes, brand and engine names kept
 * as they are, and the English-only spoken words left in English.
 *
 * A new English string: add it to every `values-<locale>` file too, or this
 * fails. This test is the gate: lint's MissingTranslation only warns, and
 * nothing runs lint automatically.
 */
class TranslationsTest {

    private val res = File("src/main/res")

    private class Text(val file: String, val raw: String)

    private class Resources(
        val strings: Map<String, Text>,
        val untranslatable: Set<String>,
        val plurals: Map<String, Map<String, Text>>,
    )

    private fun load(dir: File): Resources {
        val strings = mutableMapOf<String, Text>()
        val untranslatable = mutableSetOf<String>()
        val plurals = mutableMapOf<String, Map<String, Text>>()
        val files = dir.listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }!!.sorted()
        for (file in files) {
            // Parsing is the "valid XML" check: a malformed file throws here.
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val raw = file.readText()
            val root = doc.documentElement
            for (i in 0 until root.childNodes.length) {
                val el = root.childNodes.item(i) as? Element ?: continue
                val name = el.getAttribute("name")
                when (el.tagName) {
                    "string" -> {
                        assertTrue("${file.path}: $name twice", name !in strings && name !in untranslatable)
                        if (el.getAttribute("translatable") == "false") {
                            untranslatable += name
                        } else {
                            strings[name] = Text(file.name, rawString(raw, name))
                        }
                    }
                    "plurals" -> {
                        assertTrue("${file.path}: $name twice", name !in plurals)
                        val items = mutableMapOf<String, Text>()
                        val block = Regex("""<plurals name="$name"[^>]*>(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
                            .find(raw)!!.groupValues[1]
                        for (m in ITEM.findAll(block)) items[m.groupValues[1]] = Text(file.name, m.groupValues[2])
                        plurals[name] = items
                    }
                }
            }
        }
        return Resources(strings, untranslatable, plurals)
    }

    /** The element's source text, escapes and all, which is what aapt2 compiles; "" for `<string name="x"/>`. */
    private fun rawString(xml: String, name: String): String {
        // Self-closing first: the open/close pattern would run on into the next element.
        if (Regex("""<string name="$name"[^>]*/>""").containsMatchIn(xml)) return ""
        return Regex("""<string name="$name"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.get(1)
            ?: error("no <string name=\"$name\">")
    }

    private val english by lazy { load(File(res, "values")) }

    private fun locale(dir: String) = load(File(res, "values-$dir"))

    @Test
    fun theShippedLocalesAgreeEverywhere() {
        val dirs = res.listFiles()!!.map { it.name }
            .filter { it.startsWith("values-") && File(res, it).listFiles()!!.any { f -> f.name.startsWith("strings") } }
            .map { it.removePrefix("values-") }.toSet()
        assertEquals("values-<locale> directories with strings", LOCALES.keys, dirs)

        val config = Regex("""android:name="([^"]+)"""").findAll(File(res, "xml/locales_config.xml").readText())
            .map { it.groupValues[1] }.toSet()
        assertEquals("locales_config.xml", setOf("en") + LOCALES.keys.map { tag(it) }, config)

        val filters = Regex("""localeFilters \+= listOf\(([^)]*)\)""").find(File("build.gradle.kts").readText())!!
            .groupValues[1].split(',').map { it.trim().trim('"') }.filter { it.isNotEmpty() }.toSet()
        assertEquals("localeFilters in app/build.gradle.kts", setOf("en") + LOCALES.keys, filters)
    }

    @Test
    fun everyLocaleHasEveryKeyInTheSameFile() {
        for (dir in LOCALES.keys) {
            val loc = locale(dir)
            val missing = english.strings.keys - loc.strings.keys
            val extra = loc.strings.keys - english.strings.keys
            assertTrue("$dir is missing strings: $missing", missing.isEmpty())
            assertTrue("$dir has strings English doesn't (or translatable=\"false\" ones): $extra", extra.isEmpty())
            assertTrue("$dir translates untranslatable strings", loc.untranslatable.isEmpty())
            assertEquals("$dir plurals", english.plurals.keys, loc.plurals.keys)
            for ((name, text) in loc.strings) {
                assertEquals("$dir/$name file", english.strings.getValue(name).file, text.file)
                assertTrue("$dir/$name is empty", text.raw.isNotBlank())
            }
            for ((name, items) in loc.plurals) {
                assertEquals("$dir/$name file", english.plurals.getValue(name).values.first().file, items.values.first().file)
            }
        }
    }

    @Test
    fun placeholdersMatchEnglish() {
        for (dir in LOCALES.keys) {
            val loc = locale(dir)
            for ((name, text) in loc.strings) {
                assertEquals("$dir/$name placeholders", placeholders(english.strings.getValue(name).raw), placeholders(text.raw))
            }
            for ((name, items) in loc.plurals) {
                val want = placeholders(english.plurals.getValue(name).getValue("other").raw)
                for ((quantity, text) in items) {
                    assertEquals("$dir/$name[$quantity] placeholders", want, placeholders(text.raw))
                }
            }
        }
    }

    @Test
    fun pluralsUseEachLanguagesCldrCategories() {
        for ((name, items) in english.plurals) assertEquals("values/$name", setOf("one", "other"), items.keys)
        for ((dir, categories) in LOCALES) {
            for ((name, items) in locale(dir).plurals) assertEquals("$dir/$name quantities", categories, items.keys)
        }
    }

    /**
     * aapt2 rejects an unescaped apostrophe and silently drops unescaped double
     * quotes, and trims leading and trailing whitespace; a double-escaped
     * entity such as `&amp;#160;` would show as text.
     */
    @Test
    fun apostrophesAndQuotesAreEscaped() {
        for (dir in LOCALES.keys + "") {
            val loc = if (dir == "") english else locale(dir)
            val texts = loc.strings.map { it.key to it.value.raw } +
                loc.plurals.flatMap { (name, items) -> items.map { "$name[${it.key}]" to it.value.raw } }
            for ((name, raw) in texts) {
                assertTrue("$dir/$name: unescaped ' (write \\' or ’)", !Regex("""(?<!\\)'""").containsMatchIn(raw))
                assertTrue("$dir/$name: unescaped \" (write \\\" or typographic quotes)", !Regex("""(?<!\\)"""").containsMatchIn(raw))
                assertTrue("$dir/$name: starts with @ or ?", !raw.startsWith("@") && !raw.startsWith("?"))
                assertTrue("$dir/$name: leading or trailing whitespace, which aapt2 trims (write \\u0020)", raw == raw.trim())
                assertTrue("$dir/$name: double-escaped entity (&amp;#…; shows literally)", "&amp;#" !in raw)
            }
        }
    }

    @Test
    fun brandAndEngineNamesStayAsTheyAre() {
        for (dir in LOCALES.keys) {
            val loc = locale(dir)
            for ((name, text) in english.strings) {
                for (term in KEEP) {
                    if (term in text.raw) {
                        assertTrue("$dir/$name drops “$term”", term in loc.strings.getValue(name).raw)
                    }
                }
            }
        }
    }

    /** Voice commands, filler words, number examples and the end word only work in English. */
    @Test
    fun spokenEnglishWordsStayEnglish() {
        for (dir in LOCALES.keys) {
            val loc = locale(dir)
            for ((name, words) in SPOKEN_ENGLISH) {
                assertTrue("values/$name lost one of $words", words.all { quoted(it).containsMatchIn(english.strings.getValue(name).raw) })
                for (word in words) {
                    assertTrue("$dir/$name must keep the English “$word”", quoted(word).containsMatchIn(loc.strings.getValue(name).raw))
                }
            }
        }
    }

    /** Each locale names itself, so names and sorting follow the UI's language (rememberUiLocale). */
    @Test
    fun uiLocaleIsEachFilesOwnTag() {
        assertEquals("en", english.strings.getValue("ui_locale").raw)
        for (dir in LOCALES.keys) {
            assertEquals("$dir/ui_locale", tag(dir), locale(dir).strings.getValue("ui_locale").raw)
        }
    }

    /** The privacy screen shows this note above the English policy in a translated UI only. */
    @Test
    fun englishShowsNoPrivacyNote() {
        assertEquals("", english.strings.getValue("about_privacy_english_note").raw)
    }

    private companion object {
        /**
         * The shipped locales' resource qualifiers and their CLDR cardinal plural
         * categories (CLDR 46, unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html).
         * es, fr, it and pt have "many" for large round numbers (1 000 000); in
         * cs "many" is for fractions, in ru, uk and pl "other" is.
         */
        val LOCALES = mapOf(
            "de" to setOf("one", "other"),
            "es" to setOf("one", "many", "other"),
            "fr" to setOf("one", "many", "other"),
            "hi" to setOf("one", "other"),
            "it" to setOf("one", "many", "other"),
            "ja" to setOf("other"),
            "pt-rBR" to setOf("one", "many", "other"),
            "zh-rCN" to setOf("other"),
            "ar" to setOf("zero", "one", "two", "few", "many", "other"),
            "cs" to setOf("one", "few", "many", "other"),
            "in" to setOf("other"),
            "ko" to setOf("other"),
            "nl" to setOf("one", "other"),
            "pl" to setOf("one", "few", "many", "other"),
            "ru" to setOf("one", "few", "many", "other"),
            "sv" to setOf("one", "other"),
            "tr" to setOf("one", "other"),
            "uk" to setOf("one", "few", "many", "other"),
            "vi" to setOf("other"),
            "zh-rTW" to setOf("other"),
        )

        /** A qualifier's BCP-47 tag: "pt-rBR" is "pt-BR"; Indonesian's legacy qualifier "in" is "id". */
        fun tag(dir: String) = if (dir == "in") "id" else dir.replace("-r", "-")

        val ITEM = Regex("""<item quantity="(\w+)">(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)

        /** Format specifiers as a sorted list, so "%2$s … %1$s" reordering passes and a dropped or added one fails. */
        val SPEC = Regex("""%(\d+\$)?[-#+0,(]*\d*(\.\d+)?[a-zA-Z%]""")

        fun placeholders(raw: String): List<String> = SPEC.findAll(raw).map { it.value }.sorted().toList()

        /** Names that appear untranslated wherever English uses them. */
        val KEEP = listOf(
            "Marmalade", "Parakeet", "Canary", "Nemotron", "Whisper", "Granite", "Qwen3", "Smart Turn",
            "FUTO", "GitHub", "Silero VAD", "ONNX Runtime", "LibriSpeech", "FLEURS", "AMI", "PRIVACY.md",
            "NVIDIA", "OpenAI", "IBM", "Alibaba", "Moondream", "CTC", "WER", "RTF", "RSS",
        )

        /** The English words a string quotes, in any language's quotation marks. */
        val SPOKEN_ENGLISH = mapOf(
            "cleanup_fillers_desc" to listOf("um", "uh", "er", "hmm", "umbrella"),
            "cleanup_commands_desc" to listOf("new line", "new paragraph", "scratch that"),
            "cleanup_punctuation_desc" to listOf("period", "comma", "question mark"),
            "cleanup_itn_desc" to listOf("Twenty five percent", "25%", "nine thirty pm", "9:30 pm", "three boxes"),
            "settings_end_word_desc" to listOf("over"),
        )

        fun quoted(word: String): Regex {
            // Arabic wraps mixed runs such as «9:30 pm» in a left-to-right isolate (LRI … PDI) so they don't reorder.
            val space = """(?:\s|&#160;|&#8239;|&#x2066;|&#x2069;)?"""
            return Regex("""(?:[“”„«»‹「『‘‚]|\\")""" + space + Regex.escape(word) + space + """(?:[”“»›」』’‘]|\\")""")
        }
    }
}
