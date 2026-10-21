package app.marmalade.stt.speech

import app.marmalade.stt.install.EngineFamily
import app.marmalade.stt.install.FileRole
import app.marmalade.stt.install.SttEngineCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Qwen3-ASR's output cap and KV cache length (#50, `docs/benchmarks/2026-10-qwen3-speed.md` §4). */
class Qwen3LimitsTest {

    @Test
    fun `the output cap is never below sherpa's 128 and grows with the utterance`() {
        for (seconds in listOf(0.5, 2.0, 6.0)) assertEquals(QWEN3_SHERPA_MAX_NEW_TOKENS, qwen3MaxNewTokens(seconds))
        assertEquals(190, qwen3MaxNewTokens(10.0))
        assertEquals(488, qwen3MaxNewTokens(28.6))
    }

    @Test
    fun `the densest language measured fits, with its slack`() {
        // Most tokens per second of any host decode: Hindi 15.3, Korean 7.3, Chinese 4.5.
        for (seconds in listOf(1.0, 3.0, 6.0, 10.0, 20.0, 28.6)) {
            assertTrue("$seconds s", qwen3MaxNewTokens(seconds) >= 15.3 * seconds + 20)
        }
        // Whole 25-27 s Hindi utterances needed up to 294 tokens.
        assertTrue(qwen3MaxNewTokens(25.0) >= 294)
    }

    @Test
    fun `the KV cache holds prompt and audio at the longest utterance plus the cap`() {
        // The longest decode: Silero's 28 s plus the 0.6 s pre-roll; prompt and audio take 390 positions there.
        val longest = MAX_UTTERANCE_SECONDS + 0.6
        assertTrue(390 + qwen3MaxNewTokens(longest) <= QWEN3_MAX_TOTAL_LEN)
    }

    @Test
    fun `the KV cache length reaches sherpa's model config`() {
        val qwen3 = SttEngineCatalog.byId(SttEngineCatalog.QWEN3)!!
        val config = offlineModelConfig(qwen3, { role -> "/models/${qwen3.id}/${role.name}" }, "zh")
        assertEquals(QWEN3_MAX_TOTAL_LEN, config.qwen3Asr.maxTotalLen)
        assertEquals("/models/${qwen3.id}/${FileRole.DECODER.name}", config.qwen3Asr.decoder)
    }

    @Test
    fun `each decode's stream gets the cap, and the hint when there is one`() {
        assertEquals(
            mapOf("language" to "Chinese", "max_new_tokens" to "128"),
            offlineStreamOptions(EngineFamily.QWEN3_ASR, "zh", 6.0),
        )
        assertEquals(mapOf("max_new_tokens" to "350"), offlineStreamOptions(EngineFamily.QWEN3_ASR, "", 20.0))
        // A hint Qwen3 has no name for falls back to auto-detect.
        assertEquals(mapOf("max_new_tokens" to "488"), offlineStreamOptions(EngineFamily.QWEN3_ASR, "nb", 28.6))
        assertEquals(
            mapOf("language" to "Hindi", "max_new_tokens" to "488"),
            offlineStreamOptions(EngineFamily.QWEN3_ASR, "hi", 28.6),
        )
        for (family in listOf(EngineFamily.NEMO_TRANSDUCER, EngineFamily.CANARY)) {
            assertEquals(emptyMap<String, String>(), offlineStreamOptions(family, "en", 20.0))
        }
    }
}
