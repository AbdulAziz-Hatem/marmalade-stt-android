package app.marmalade.stt.speech

import app.marmalade.stt.install.EngineFamily
import app.marmalade.stt.install.LanguageOptions
import app.marmalade.stt.install.SttEngineCatalog
import org.junit.Assert.assertTrue
import org.junit.Test

/** The language hints must reach each model in the form it reads. */
class LanguageHintTest {
    /** Qwen3-ASR takes names, not codes: a code without a name would silently fall back to auto. */
    @Test
    fun everyQwenLanguageOptionHasAName() {
        // Both entries Qwen3-ASR can have (#50: a user engine for Chinese and Korean, or a developer one).
        val qwen3 = listOf(SttEngineCatalog.qwen3Asr(forUsers = true), SttEngineCatalog.qwen3Asr(forUsers = false))
        for (engine in SttEngineCatalog.engines.filter { it.family == EngineFamily.QWEN3_ASR } + qwen3) {
            for (code in engine.languageOptions!!.codes - LanguageOptions.AUTO) {
                assertTrue("${engine.id}: $code", code in QWEN3_LANGUAGE_NAMES)
            }
        }
    }

    /**
     * sherpa-onnx v1.13.8's Whisper language codes (offline-whisper-model-config.cc,
     * GetAllWhisperLanguageCodes). Its decoder exits the process on any other code.
     */
    @Test
    fun everyWhisperLanguageOptionIsAWhisperCode() {
        val whisperCodes = setOf(
            "hi", "cy", "oc", "so", "fr", "az", "eu", "ba", "no", "as", "nl", "bn", "es", "ml", "km", "mk", "sq",
            "mt", "et", "ms", "tr", "bg", "ps", "br", "ht", "tt", "tk", "la", "de", "ur", "ro", "fa", "uk", "mg",
            "lo", "sr", "yo", "id", "da", "pt", "nn", "sn", "sa", "sd", "gl", "ja", "pl", "ru", "ko", "ne", "kn",
            "zh", "be", "ca", "el", "it", "hu", "lt", "ta", "is", "jw", "fi", "bo", "sv", "mi", "hr", "bs", "yi",
            "sk", "lv", "af", "vi", "ha", "mn", "cs", "sl", "pa", "su", "ka", "ln", "lb", "sw", "en", "tl", "hy",
            "te", "he", "my", "haw", "fo", "kk", "si", "tg", "th", "ar", "am", "mr", "uz", "gu",
        )
        val whisper = SttEngineCatalog.engines.filter { it.family == EngineFamily.WHISPER && it.languageOptions != null }
        assertTrue(whisper.isNotEmpty())
        for (engine in whisper) {
            for (code in engine.languageOptions!!.codes - LanguageOptions.AUTO) {
                val sent = WHISPER_LANGUAGE_CODES[code] ?: code
                assertTrue("${engine.id}: $code -> $sent", sent in whisperCodes)
            }
        }
    }

    /**
     * Nemotron 3.5's prompt_dictionary keys, for the hint codes, as read from
     * the 2026-06-11 export's encoder metadata (the keys for its 28
     * languages; re-read 2026-09-27 for #27). "ja", "zh" and "vi" are not keys.
     */
    @Test
    fun everyNemotronLanguageOptionIsAPromptKey() {
        val promptKeys = setOf(
            "en", "es", "fr", "it", "pt", "nl", "de", "tr", "ru", "ar", "hi", "ja-JP", "ko", "vi-VN", "uk", "pl",
            "sv", "cs", "nb", "da", "bg", "fi", "hr", "sk", "zh-CN", "hu", "ro", "et",
        )
        for (engine in SttEngineCatalog.engines.filter { it.family == EngineFamily.STREAMING_TRANSDUCER }) {
            for (code in (engine.languageOptions?.codes ?: emptyList()) - LanguageOptions.AUTO) {
                val key = NEMOTRON_LANGUAGE_KEYS[code] ?: code
                assertTrue("${engine.id}: $code -> $key", key in promptKeys)
            }
        }
    }
}
