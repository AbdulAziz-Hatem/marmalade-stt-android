package app.marmalade.stt.data

import app.marmalade.stt.textproc.Replacement
import app.marmalade.stt.textproc.Snippet
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The replacement dictionary and snippets as JSON, one preferences key each:
 * an array of objects in list order, e.g. `[{"from":"teh","to":"the"}]`.
 * Order matters (the first of two equal left sides wins), which is why this
 * isn't a string set.
 *
 * Decoding never throws: a value that isn't the expected shape decodes to an
 * empty list, and an entry with a missing or non-string field is skipped, so
 * a damaged file costs the user those entries, not every dictation.
 */
object TextProcJson {

    fun encodeReplacements(rules: List<Replacement>): String = encode(rules) { put(FROM, it.from); put(TO, it.to) }

    fun decodeReplacements(json: String?): List<Replacement> = decode(json) { obj ->
        val from = obj.string(FROM) ?: return@decode null
        val to = obj.string(TO) ?: return@decode null
        Replacement(from, to)
    }

    fun encodeSnippets(snippets: List<Snippet>): String = encode(snippets) { put(TRIGGER, it.trigger); put(TEXT, it.text) }

    fun decodeSnippets(json: String?): List<Snippet> = decode(json) { obj ->
        val trigger = obj.string(TRIGGER) ?: return@decode null
        val text = obj.string(TEXT) ?: return@decode null
        Snippet(trigger, text)
    }

    private fun <T> encode(items: List<T>, fields: JsonObjectBuilder.(T) -> Unit): String =
        buildJsonArray { items.forEach { add(buildJsonObject { fields(it) }) } }.toString()

    private fun <T : Any> decode(json: String?, entry: (JsonObject) -> T?): List<T> {
        if (json.isNullOrEmpty()) return emptyList()
        val array = try {
            Json.parseToJsonElement(json) as? JsonArray
        } catch (e: SerializationException) {
            null
        } ?: return emptyList()
        return array.mapNotNull { (it as? JsonObject)?.let(entry) }
    }

    private fun JsonObject.string(key: String): String? =
        (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

    private const val FROM = "from"
    private const val TO = "to"
    private const val TRIGGER = "trigger"
    private const val TEXT = "text"
}
