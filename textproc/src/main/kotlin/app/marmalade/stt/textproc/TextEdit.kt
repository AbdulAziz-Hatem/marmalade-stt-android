package app.marmalade.stt.textproc

/**
 * The change one utterance makes to the text before the cursor: delete
 * [deleteBefore] characters, then insert [insert].
 *
 * An input method maps this to `deleteSurroundingText(deleteBefore, 0)` and
 * `commitText(insert, 1)`. A screen that owns its buffer uses [applyTo].
 * [deleteBefore] is non-zero only when a command reaches back into existing
 * text ("scratch that", or spoken punctuation replacing a trailing space).
 */
data class TextEdit(val deleteBefore: Int, val insert: String) {
    /** True when the edit changes nothing. */
    val isEmpty: Boolean get() = deleteBefore == 0 && insert.isEmpty()

    /** Applies the edit to [before], which must be the same text passed to [TextPipeline.process]. */
    fun applyTo(before: String): String = before.substring(0, before.length - deleteBefore) + insert

    companion object {
        /** No change. */
        val NONE = TextEdit(0, "")
    }
}
