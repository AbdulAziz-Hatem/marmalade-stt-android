package app.marmalade.stt.ui.components

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Material's `TextButton` draws its label in `primary`, and other presets'
 * and dark mode's `primary` isn't always legible as text (#68). So accent
 * text goes through `MaterialTheme.accent`: `text` (orange 500 in
 * Marmalade's light mode, the brand Max kept on 2026-10-06; ink in the High
 * contrast preset and dark mode) or `ink` (ink everywhere). The app's sources
 * must use [MarmaladeTextButton] (or pass explicit colours) and never colour
 * text with `colorScheme.primary`; `ThemePresetTest` checks the accent
 * colours' contrast per preset.
 */
class ButtonContrastSourceTest {

    private val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()

    @Test
    fun `every TextButton call sets its colours`() {
        val offenders = sources.flatMap { file ->
            val text = file.readText()
            BARE_TEXT_BUTTON.findAll(text)
                .filter { "colors =" !in callArguments(text, it.range.last) }
                .map { "${file.name}:${text.lineAt(it.range.first)}" }
                .toList()
        }
        assertTrue("bare TextButton (use MarmaladeTextButton): $offenders", offenders.isEmpty())
    }

    @Test
    fun `no text is coloured with primary`() {
        val offenders = sources.flatMap { file ->
            val text = file.readText()
            PRIMARY_TEXT.findAll(text).map { "${file.name}:${text.lineAt(it.range.first)}" }.toList()
        }
        assertTrue("text in colorScheme.primary (use MaterialTheme.accent.text or .ink): $offenders", offenders.isEmpty())
    }

    @Test
    fun `the scan sees the sources`() {
        assertTrue(sources.size > 50)
        assertTrue(sources.any { BARE_TEXT_BUTTON.containsMatchIn(it.readText()) })
    }

    /** The argument list of the call whose `(` is at [open]. */
    private fun callArguments(text: String, open: Int): String {
        var depth = 0
        for (i in open until text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> if (--depth == 0) return text.substring(open, i)
            }
        }
        return text.substring(open)
    }

    private fun String.lineAt(index: Int) = substring(0, index).count { it == '\n' } + 1

    private companion object {
        /** A `TextButton(` call: not `MarmaladeTextButton(`, `EngineTextButton(` or a declaration. */
        val BARE_TEXT_BUTTON = Regex("""(?<![\w.])TextButton\(""")

        /** `color = MaterialTheme.colorScheme.primary` (not `containerColor`, not `primaryContainer`). */
        val PRIMARY_TEXT = Regex("""\b(color|linkColor)\s*=\s*MaterialTheme\.colorScheme\.primary\b(?!Container)""")
    }
}
