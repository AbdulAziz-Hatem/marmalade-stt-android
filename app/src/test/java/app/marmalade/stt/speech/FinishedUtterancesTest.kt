package app.marmalade.stt.speech

import app.marmalade.stt.install.SttEngineCatalog
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pre-speech phantom (Parakeet ja's 「うん」, #60; Whisper's and Qwen3's, #57): dropped
 * before speech, kept as a reply.
 */
class FinishedUtterancesTest {

    private fun pause(phantom: String? = "うん") =
        FinishedUtterances(EndOfDictation.PAUSE, emptyList()) { FinishedUtterances.isPhantom(it, phantom) }

    private fun engine(engineId: String) =
        FinishedUtterances(EndOfDictation.PAUSE, emptyList(), SttEngineCatalog.byId(engineId)!!::isPreSpeechPhantom)

    private fun FinishedUtterances.say(vararg texts: String): List<SttEvent> =
        texts.flatMap { finished(it, Quiet.SPEECH) } + ended()

    private fun List<SttEvent>.finals() = filterIsInstance<SttEvent.Final>().map { it.text }

    @Test
    fun `a pre-speech うん followed by speech is dropped`() {
        assertEquals(listOf("今日は雨です"), pause().say("うん", "今日は雨です").finals())
        assertEquals(listOf("今日は雨です"), pause().say("うん。", "", "今日は雨です").finals())
    }

    @Test
    fun `a lone うん reply survives, sent when the session ends`() {
        val texts = pause()
        assertTrue(texts.finished("うん", Quiet.SPEECH).isEmpty())
        assertEquals(listOf(SttEvent.Final("うん", nearSilent = false)), texts.ended())
    }

    @Test
    fun `only the session's first text is held`() {
        assertEquals(listOf("今日は雨です", "うん"), pause().say("今日は雨です", "うん").finals())
    }

    @Test
    fun `うんうん and longer replies are never held`() {
        val texts = pause()
        assertEquals(listOf("うんうん"), texts.finished("うんうん", Quiet.SPEECH).finals())
        assertEquals(listOf("うん、わかった"), pause().finished("うん、わかった", Quiet.SPEECH).finals())
    }

    @Test
    fun `other engines are unaffected`() {
        val texts = pause(phantom = null)
        assertEquals(listOf("うん"), texts.finished("うん", Quiet.SPEECH).finals())
        assertTrue(texts.ended().isEmpty())
    }

    @Test
    fun `Tap stop - a lone reply is the Final, a pre-speech one is not kept`() {
        val tap = { FinishedUtterances(EndOfDictation.TAP_STOP, emptyList()) { FinishedUtterances.isPhantom(it, "うん") } }
        assertEquals(listOf("うん"), tap().say("うん").finals())
        val texts = tap()
        texts.finished("うん", Quiet.SPEECH)
        assertEquals(listOf(SttEvent.Partial("今日は")), texts.finished("今日は", Quiet.SPEECH))
        assertEquals(listOf("今日は"), texts.ended().finals())
    }

    @Test
    fun `Whisper's distinctive phrases and おわり are held before speech, at any level`() {
        assertEquals(listOf("今日は雨です"), engine("whisper-turbo").say("ご視聴ありがとうございました", "今日は雨です").finals())
        assertEquals(listOf("今日は雨です"), engine("whisper-turbo").say("おわり", "今日は雨です").finals())
        assertEquals(listOf("Hallo zusammen."), engine("whisper-turbo").say("Untertitelung des ZDF, 2020", "Hallo zusammen.").finals())
        // Alone, it was real (or the near-silence gate decides): sent when the session ends.
        assertEquals(listOf("おわり"), engine("whisper-turbo").say("おわり").finals())
    }

    @Test
    fun `an opening E aí is not held`() {
        assertEquals(listOf("E aí", "tudo bem?"), engine("whisper-turbo").say("E aí", "tudo bem?").finals())
        assertEquals(listOf("Gracias.", "Nos vemos."), engine("whisper-turbo").say("Gracias.", "Nos vemos.").finals())
    }

    @Test
    fun `Qwen3's システム is held, its other system words are not`() {
        assertEquals(listOf("今日は雨です。"), engine(SttEngineCatalog.QWEN3).say("システム。", "今日は雨です。").finals())
        assertEquals(listOf("Sistema.", "Hola."), engine(SttEngineCatalog.QWEN3).say("Sistema.", "Hola.").finals())
    }

    @Test
    fun `each engine holds only its own phrases`() {
        assertEquals(
            listOf("ご視聴ありがとうございました", "今日は雨です"),
            engine(SttEngineCatalog.PARAKEET_ULTRA).say("ご視聴ありがとうございました", "今日は雨です").finals(),
        )
        assertEquals(listOf("システム。", "今日は雨です。"), engine("whisper-turbo").say("システム。", "今日は雨です。").finals())
    }

    @Test
    fun `a held phrase keeps its level`() {
        val texts = engine("whisper-turbo")
        assertTrue(texts.finished("おわり", Quiet.SILENT).isEmpty())
        assertEquals(listOf(SttEvent.Final("おわり", nearSilent = true, silent = true)), texts.ended())
    }

    @Test
    fun `a session with only a held うん is not NoSpeech`() {
        assertFalse(pause().say("うん").contains(SttEvent.NoSpeech))
    }

    @Test
    fun `the offline session drops the engine's phantom before speech`() = runTest(timeout = TEST_TIMEOUT) {
        val vad = FakeVad(speechFromSample = 1, speechUntilSample = 4 * HOP, segmentsAt = listOf(2 * HOP, 4 * HOP))
        lateinit var s: StreamingSession
        val mic = FakeMic(List(6) { loudHop }, afterScript = { s.stop() })
        s = StreamingSession(
            FakeTranscriber(listOf("うん", "今日は雨です")), vad, SessionOptions(), openMic = { _, _ -> mic },
            nowMs = slowClock(), isPreSpeechPhantom = SttEngineCatalog.byId(SttEngineCatalog.PARAKEET_JA)!!::isPreSpeechPhantom,
        )
        assertEquals(listOf("今日は雨です"), s.run().toList().finals())
    }
}
