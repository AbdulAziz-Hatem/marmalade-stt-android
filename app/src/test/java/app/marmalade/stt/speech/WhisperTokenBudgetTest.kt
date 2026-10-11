package app.marmalade.stt.speech

import app.marmalade.stt.install.EngineFamily
import kotlin.math.ceil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Whisper's per-language token budget (#54, sherpa patch 0005). */
class WhisperTokenBudgetTest {

    @Test
    fun `English, Japanese and Hindi get a budget, every other language keeps Whisper's 224`() {
        assertEquals(52, whisperMaxNewTokens("en", 7.0))
        assertEquals(79, whisperMaxNewTokens("ja", 6.86))
        assertEquals(250, whisperMaxNewTokens("hi", 12.0))
        for (language in listOf("", "zh", "ko", "de", "ar")) assertNull(language, whisperMaxNewTokens(language, 7.0))
    }

    @Test
    fun `the budget covers the densest Finals measured, with slack`() {
        // Most tokens a second of any Final through the app's session: English 4.8, Japanese 8.1, Hindi 17.2.
        val densest = mapOf("en" to 4.8, "ja" to 8.1, "hi" to 17.2)
        for ((language, rate) in densest) {
            for (seconds in listOf(0.5, 1.0, 3.0, 10.0, 20.0)) {
                assertTrue("$language $seconds s", whisperMaxNewTokens(language, seconds)!! >= ceil(rate * seconds) + 5)
            }
        }
    }

    @Test
    fun `no decode's budget passes what Whisper's decoder holds`() {
        for (language in WHISPER_TOKEN_BUDGETS.keys) {
            val seconds = UtteranceCap.maxDecodeSamples(EngineFamily.WHISPER, language).toDouble() / MIC_SAMPLE_RATE
            assertTrue("$language at $seconds s", whisperMaxNewTokens(language, seconds)!! <= WHISPER_MAX_TOKENS)
        }
    }

    @Test
    fun `each Whisper decode's stream gets the budget as max_new_tokens`() {
        assertEquals(mapOf("max_new_tokens" to "70"), offlineStreamOptions(EngineFamily.WHISPER, "en", 10.0))
        assertEquals(mapOf("max_new_tokens" to "210"), offlineStreamOptions(EngineFamily.WHISPER, "hi", 10.0))
        assertEquals(emptyMap<String, String>(), offlineStreamOptions(EngineFamily.WHISPER, "zh", 10.0))
        assertEquals(emptyMap<String, String>(), offlineStreamOptions(EngineFamily.WHISPER, "", 10.0))
    }
}
