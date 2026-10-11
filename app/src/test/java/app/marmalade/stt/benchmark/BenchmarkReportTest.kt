package app.marmalade.stt.benchmark

import app.marmalade.stt.install.EngineFamily
import app.marmalade.stt.install.SttEngineDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkReportTest {

    private fun finished(name: String, text: String, raw: String = text, rss: Long? = 512_000_000L) = BenchmarkEvent.Finished(
        BenchmarkResult(
            engineId = name.lowercase(),
            displayName = name,
            text = text,
            rawText = raw,
            loadMs = 1_234L,
            decodeMs = 800L,
            audioMs = 6_500L,
            rtf = 800.0 / 6_500,
            peakRssBytes = rss,
        ),
    )

    private val broken = BenchmarkEvent.Failed(
        SttEngineDescriptor(
            id = "broken",
            displayName = "Broken",
            description = 0,
            family = EngineFamily.WHISPER,
            languages = listOf("en"),
            licenseId = "MIT",
            licenseKey = "test",
            files = emptyList(),
        ),
        BenchmarkEvent.Stage.LOAD,
        "missing file",
    )

    /** The table's lines (those starting with a pipe). */
    private fun tableLines(md: String) = md.lines().filter { it.startsWith("|") }

    /** Cells of a table line, splitting on unescaped pipes. */
    private fun cells(line: String) = line.trim().removePrefix("|").removeSuffix("|")
        .split(Regex("(?<!\\\\)\\|")).map { it.trim() }

    @Test
    fun `without a reference there is no WER column`() {
        val md = BenchmarkReport.markdownTable(
            6_500L,
            "Pixel 8a",
            listOf(finished("Whisper", "Hello world", raw = "um hello world")),
            softAgc = false,
        )

        assertTrue(md.startsWith("Marmalade STT benchmark: take 6.5 s, device Pixel 8a\n"))
        assertFalse(md.contains("WER"))
        assertFalse(md.contains("Reference"))
        val lines = tableLines(md)
        assertEquals(3, lines.size)
        assertEquals(
            listOf("Engine", "Soft AGC", "Load", "Decode", "RTF", "Peak RSS (sampled)", "Transcript", "Raw"),
            cells(lines[0]),
        )
        assertEquals(List(8) { "---" }, cells(lines[1]))
        assertEquals(
            listOf("Whisper", "off", "1.2 s", "0.8 s", "0.12", "512 MB", "Hello world", "um hello world"),
            cells(lines[2]),
        )
    }

    @Test
    fun `with a reference every row gets a WER cell`() {
        val md = BenchmarkReport.markdownTable(
            6_500L,
            "Pixel 8a",
            listOf(finished("Whisper", "hello world"), finished("Parakeet", "hello word"), broken),
            softAgc = true,
            reference = "Hello, world.",
        )

        assertTrue(md.contains("\nReference: Hello, world.\n"))
        val lines = tableLines(md)
        assertEquals(5, lines.size)
        assertEquals(
            listOf("Engine", "Soft AGC", "Load", "Decode", "RTF", "Peak RSS (sampled)", "WER", "Transcript", "Raw"),
            cells(lines[0]),
        )
        lines.forEach { assertEquals(9, cells(it).size) }
        // Every row, failed ones too, is labelled with the take's gain path.
        lines.drop(2).forEach { assertEquals("on", cells(it)[1]) }
        assertEquals("0.0 %", cells(lines[2])[6])
        assertEquals("50.0 %", cells(lines[3])[6])
        assertEquals(listOf("Broken", "on", "-", "-", "-", "-", "-", "Failed (load): missing file", ""), cells(lines[4]))
    }

    @Test
    fun `WER is scored on the cleaned transcript and the raw text is kept beside it`() {
        val md = BenchmarkReport.markdownTable(
            6_500L,
            "Pixel 8a",
            listOf(finished("Parakeet", "Hello world.", raw = "um, hello uh world")),
            softAgc = false,
            reference = "Hello world",
        )
        val row = cells(tableLines(md)[2])
        // Raw would score 2 insertions on 2 words: 100 %.
        assertEquals("0.0 %", row[6])
        assertEquals("Hello world.", row[7])
        assertEquals("um, hello uh world", row[8])
    }

    @Test
    fun `pipes and line breaks in text cannot break the table`() {
        val md = BenchmarkReport.markdownTable(
            1_000L,
            "Odd|Model",
            listOf(finished("A|B", "left | right\nnext line")),
            softAgc = false,
        )

        assertTrue(md.contains("device Odd\\|Model"))
        val row = tableLines(md)[2]
        assertEquals(8, cells(row).size)
        assertEquals("A\\|B", cells(row)[0])
        assertEquals("left \\| right next line", cells(row)[6])
        assertEquals("left \\| right next line", cells(row)[7])
    }

    @Test
    fun `an unreadable RSS reads n-a and a failed row keeps the column count`() {
        val md = BenchmarkReport.markdownTable(1_000L, "x", listOf(finished("A", "t", rss = null), broken), softAgc = false)
        val lines = tableLines(md)
        assertEquals("n/a", cells(lines[2])[5])
        assertEquals(8, cells(lines[3]).size)
    }
}
