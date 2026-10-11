package app.marmalade.stt.ui

import app.marmalade.stt.install.SttEngineCatalog
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ByteFormatTest {

    private val us = Locale.US
    private val en = ByteUnits.English

    // Android's units in these locales (ICU's short units, what
    // ByteUnits.forLocale reads on a phone).
    private val fr = ByteUnits("\u202Fo", "\u202Fko", "\u202FMo", "\u202FGo")
    private val ru = ByteUnits(" Б", " кБ", " МБ", " ГБ")
    private val ko = ByteUnits("byte", "kB", "MB", "GB")
    private val ar = ByteUnits(" بايت", " كيلوبايت", " م.ب", " غ.ب")

    @Test
    fun `sizes use decimal units with one decimal under ten`() {
        assertEquals("0 B", formatBytes(0L, us, en))
        assertEquals("850 B", formatBytes(850L, us, en))
        assertEquals("1.5 kB", formatBytes(1_500L, us, en))
        assertEquals("836 kB", formatBytes(835_554L, us, en))
        assertEquals("1.5 MB", formatBytes(1_500_000L, us, en))
        assertEquals("9.9 MB", formatBytes(9_940_000L, us, en))
        assertEquals("1.2 GB", formatBytes(1_240_000_000L, us, en))
    }

    @Test
    fun `Parakeet Ultra reads as 671 MB`() {
        // 670,577,016 bytes: rounded, not truncated.
        assertEquals("671 MB", formatBytes(SttEngineCatalog.byId(SttEngineCatalog.PARAKEET_ULTRA)!!.sizeBytes, us, en))
    }

    @Test
    fun `rounding never prints a four-digit number or ten point zero`() {
        assertEquals("1.0 MB", formatBytes(999_999L, us, en))
        assertEquals("1.0 GB", formatBytes(999_950_000L, us, en))
        assertEquals("10 MB", formatBytes(9_960_000L, us, en))
    }

    @Test
    fun `negative sizes read as zero`() {
        assertEquals("0 B", formatBytes(-5L, us, en))
    }

    @Test
    fun `progress shares the total's unit and precision`() {
        assertEquals(ByteProgress("123", "299 MB"), byteProgress(123_456_789L, 298_876_082L, us, en))
        assertEquals(ByteProgress("0", "299 MB"), byteProgress(0L, 298_876_082L, us, en))
        // Under 10 of a unit both sides keep one decimal.
        assertEquals(ByteProgress("0.4", "1.5 MB"), byteProgress(400_000L, 1_500_000L, us, en))
        // Bytes done still read in MB, not KB, when the total is in MB.
        assertEquals(ByteProgress("0", "299 MB"), byteProgress(300_000L, 298_876_082L, us, en))
    }

    @Test
    fun `progress clamps done into 0 to total`() {
        assertEquals(ByteProgress("299", "299 MB"), byteProgress(400_000_000L, 298_876_082L, us, en))
        assertEquals(ByteProgress("0", "299 MB"), byteProgress(-1L, 298_876_082L, us, en))
    }

    @Test
    fun `numbers follow the locale`() {
        assertEquals("1,5 MB", formatBytes(1_500_000L, Locale.GERMANY, en))
    }

    @Test
    fun `units follow the locale`() {
        assertEquals("671\u202FMo", formatBytes(670_577_016L, Locale.FRANCE, fr))
        assertEquals("1,5\u202FGo", formatBytes(1_500_000_000L, Locale.FRANCE, fr))
        assertEquals("836 кБ", formatBytes(835_554L, Locale.forLanguageTag("ru"), ru))
        assertEquals("1.5MB", formatBytes(1_500_000L, Locale.KOREA, ko))
        assertEquals(ByteProgress("123", "299\u202FMo"), byteProgress(123_456_789L, 298_876_082L, Locale.FRANCE, fr))
    }

    @Test
    fun `Arabic gets Arabic-Indic digits and its own units`() {
        val arabic = Locale.forLanguageTag("ar")
        assertEquals("٦٧١ م.ب", formatBytes(670_577_016L, arabic, ar))
        assertEquals("١٫٥ غ.ب", formatBytes(1_500_000_000L, arabic, ar))
        assertEquals(ByteProgress("١٢٣", "٢٩٩ م.ب"), byteProgress(123_456_789L, 298_876_082L, arabic, ar))
    }

    @Test
    fun `percent and fraction handle an unknown total`() {
        assertEquals(0, progressPercent(10L, 0L))
        assertEquals(0f, progressFraction(10L, 0L))
        assertEquals(41, progressPercent(123_456_789L, 298_876_082L))
        assertEquals(100, progressPercent(500L, 400L))
        assertEquals(1f, progressFraction(500L, 400L))
        assertEquals(0.5f, progressFraction(50L, 100L))
    }
}
