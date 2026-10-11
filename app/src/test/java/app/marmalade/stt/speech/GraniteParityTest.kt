package app.marmalade.stt.speech

import ai.onnxruntime.OrtEnvironment
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * The Kotlin transcriber against the harness's `GraniteTranscriber` on real
 * clips (#32). Needs the 551 MB model, so it runs only when
 * GRANITE_PARITY_DIR points at a directory made by
 * `tools/asr-bench/granite/dump_golden.py parity` and `expect` (see there:
 * `expect` must run on this machine's CPU) with the catalog's
 * `model_int8.onnx` and `tokenizer.json` copied in; otherwise it is skipped.
 */
class GraniteParityTest {

    private fun floats(file: File): FloatArray {
        val buf = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(buf.remaining()).also { buf.get(it) }
    }

    /** ONNX Runtime, CTC and the vocabulary: the harness's own features must give its exact text. */
    @Test
    fun `python features give the harness's text on every clip`() {
        val mismatches = expected.mapNotNull { (id, text) ->
            val actual = transcriber.transcribeFeatures(floats(File(dir, "clips/$id.features.f32")))
            if (actual != text) "$id\n  python: $text\n  kotlin: $actual" else null
        }
        assertEquals(mismatches.joinToString("\n"), 0, mismatches.size)
    }

    /** End to end, from the samples through [GraniteFrontend]. */
    @Test
    fun `samples give the harness's text on every clip`() {
        val mismatches = expected.mapNotNull { (id, text) ->
            val actual = transcriber.transcribe(floats(File(dir, "clips/$id.f32")), 16_000)
            if (actual != text) "$id\n  python: $text\n  kotlin: $actual" else null
        }
        assertEquals(mismatches.joinToString("\n"), 0, mismatches.size)
    }

    /** The formatter's BPE against Python `tokenizers` on every word of the inputs. */
    @Test
    fun `bpe ids match the python tokenizer`() {
        val bpeFile = File(dir, "bpe.tsv")
        assumeTrue("no bpe.tsv (dump_golden.py formatter)", bpeFile.isFile)
        val bpe = GraniteBpe.fromTokenizerJson(Json.parseToJsonElement(File(dir, "tokenizer.json").readText()).jsonObject)
        val bad = bpeFile.readLines().filter { it.isNotBlank() }.mapNotNull { line ->
            val (word, ids) = line.split('\t')
            val actual = bpe.encode(" $word").joinToString(" ")
            if (actual != ids) "$word: python $ids, kotlin $actual" else null
        }
        assertEquals(bad.joinToString("\n"), 0, bad.size)
    }

    /** The #33 formatter end to end against the Python one, on Granite's own output. */
    @Test
    fun `formatter gives the python formatter's text`() {
        val expectedFile = File(dir, "formatter-expected.tsv")
        assumeTrue("no formatter-expected.tsv (dump_golden.py formatter)", expectedFile.isFile)
        val bpe = GraniteBpe.fromTokenizerJson(Json.parseToJsonElement(File(dir, "tokenizer.json").readText()).jsonObject)
        GraniteFormatter.load(OrtEnvironment.getEnvironment(), File(dir, "formatter.int8.onnx").readBytes(), bpe, threads = 2).use { f ->
            val bad = expectedFile.readLines().filter { it.isNotBlank() }.mapNotNull { line ->
                val (input, python) = line.split('\t')
                val kotlin = f.format(input)
                if (kotlin != python) "python: $python\nkotlin: $kotlin" else null
            }
            assertEquals(bad.joinToString("\n"), 0, bad.size)
        }
    }

    companion object {
        private val dir = System.getenv("GRANITE_PARITY_DIR")?.let(::File)
        private lateinit var transcriber: GraniteOfflineTranscriber
        private lateinit var expected: List<List<String>>

        @BeforeClass
        @JvmStatic
        fun load() {
            assumeTrue("GRANITE_PARITY_DIR not set", dir != null && dir.isDirectory)
            transcriber = GraniteOfflineTranscriber.load(File(dir, "model_int8.onnx"), File(dir, "tokenizer.json"), formatterModel = null, threads = 2)
            expected = File(dir, "expected.tsv").readLines().filter { it.isNotBlank() }.map { it.split('\t', limit = 2) }
            assertTrue(expected.isNotEmpty())
        }

        @AfterClass
        @JvmStatic
        fun release() {
            if (::transcriber.isInitialized) transcriber.release()
        }
    }
}
