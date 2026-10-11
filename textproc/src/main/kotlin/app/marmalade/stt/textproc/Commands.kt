package app.marmalade.stt.textproc

/** An utterance split into dictated text and spoken commands. */
internal sealed interface Token {
    data class Text(val text: String) : Token
    data object NewLine : Token
    data object NewParagraph : Token
    data object Scratch : Token
    data class Punct(val mark: String) : Token
}

/**
 * Splits text at spoken commands. A command is a whole phrase (any case, any
 * whitespace between its words); punctuation Whisper put right after it
 * ("New line.") belongs to the command and is dropped.
 */
internal class Commands(editing: Boolean, punctuation: Boolean) {
    private val table: Map<String, Token> = buildMap {
        if (editing) {
            listOf("new line", "newline", "next line").forEach { put(it, Token.NewLine) }
            listOf("new paragraph", "next paragraph").forEach { put(it, Token.NewParagraph) }
            put("scratch that", Token.Scratch)
        }
        if (punctuation) {
            put("period", Token.Punct("."))
            put("full stop", Token.Punct("."))
            put("comma", Token.Punct(","))
            put("question mark", Token.Punct("?"))
            put("exclamation mark", Token.Punct("!"))
            put("exclamation point", Token.Punct("!"))
            put("colon", Token.Punct(":"))
            put("semicolon", Token.Punct(";"))
        }
    }

    private val regex: Regex? = table.keys.takeIf { it.isNotEmpty() }?.let { keys ->
        val body = keys.sortedByDescending { it.length }.joinToString("|") { Words.phrasePattern(it) }
        Regex("(?iu)${Words.START}(?:$body)${Words.COMMAND_END}[.,!?;:]*")
    }

    private val trailingPunct = Regex("[.,!?;:]+$")

    fun split(text: String): List<Token> {
        val r = regex ?: return listOf(Token.Text(text))
        val out = mutableListOf<Token>()
        var last = 0
        for (m in r.findAll(text)) {
            out += Token.Text(text.substring(last, m.range.first))
            // Unicode case folding can match a spelling lowercase() doesn't map back (e.g. "ſ"); keep it as text.
            out += table[Words.key(trailingPunct.replace(m.value, ""))] ?: Token.Text(m.value)
            last = m.range.last + 1
        }
        out += Token.Text(text.substring(last))
        return out
    }
}
