package app.marmalade.stt.speech

import app.marmalade.stt.install.SttEngineCatalog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [EngineHolds] (#78): while a surface holds the engine, its sessions don't
 * free the model; the last hold to close frees it unless Keep engine ready is
 * on; a preloading hold loads and warms up the active engine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EngineHoldsTest {

    /** Counts loads, warm-ups and releases. A session loads the model if needed, as the real engine does. */
    private class CountingEngine : SttEngine {
        override val descriptor = SttEngineCatalog.default
        override var isLoaded = false
            private set
        var loads = 0
        var warmUps = 0
        var releases = 0

        override suspend fun load() {
            if (isLoaded) return
            loads++
            isLoaded = true
        }

        override suspend fun warmUp() {
            warmUps++
            load()
        }

        override fun listen(options: SessionOptions): Flow<SttEvent> = flow {
            emit(SttEvent.Listening)
            load()
            emit(SttEvent.Ready)
            emit(SttEvent.NoSpeech)
        }

        override fun stop() = Unit

        override fun release() {
            if (!isLoaded) return
            releases++
            isLoaded = false
        }

        override fun drop() = release()
    }

    private val engine = CountingEngine()
    private var keepLoaded = false
    private var installed = true

    private fun TestScope.holds() = EngineHolds(
        scope = this,
        keepLoaded = { keepLoaded },
        activeEngine = { engine.takeIf { installed } },
        builtEngine = { engine },
    )

    /** One session, ended the way SpeechEngineManager.listen ends it. */
    private suspend fun EngineHolds.session() {
        engine.listen(SessionOptions()).onCompletion { sessionEnded(engine) }.toList()
    }

    @Test
    fun `two sessions under one hold load the model once, and closing the hold frees it`() = runTest {
        val holds = holds()
        val hold = holds.hold(preload = false)
        holds.session()
        holds.session()
        assertEquals(1, engine.loads)
        assertEquals(0, engine.releases)
        assertTrue(engine.isLoaded)

        hold.close()
        advanceUntilIdle()
        assertEquals(1, engine.releases)
        assertFalse(engine.isLoaded)
    }

    @Test
    fun `with Keep engine ready on, neither a session nor the last hold frees the model`() = runTest {
        keepLoaded = true
        val holds = holds()
        holds.session()
        val hold = holds.hold(preload = false)
        holds.session()
        hold.close()
        advanceUntilIdle()
        assertEquals(1, engine.loads)
        assertEquals(0, engine.releases)
        assertTrue(engine.isLoaded)
    }

    @Test
    fun `a session without a hold frees the model when it ends, as before`() = runTest {
        val holds = holds()
        holds.session()
        holds.session()
        assertEquals(2, engine.loads)
        assertEquals(2, engine.releases)
        assertFalse(engine.isLoaded)
    }

    @Test
    fun `holds nest, and closing one twice counts once`() = runTest {
        val holds = holds()
        val keyboard = holds.hold(preload = false)
        val dictate = holds.hold(preload = false)
        holds.session()

        keyboard.close()
        keyboard.close()
        advanceUntilIdle()
        assertTrue(holds.held)
        assertTrue(engine.isLoaded)

        dictate.close()
        advanceUntilIdle()
        assertFalse(holds.held)
        assertEquals(1, engine.releases)
    }

    @Test
    fun `a hold opened again before the freeing runs keeps the model`() = runTest {
        val holds = holds()
        val hold = holds.hold(preload = false)
        holds.session()
        hold.close()
        val again = holds.hold(preload = false) // the panel shown again at once
        advanceUntilIdle()
        assertTrue(engine.isLoaded)
        assertEquals(0, engine.releases)
        again.close()
        advanceUntilIdle()
        assertFalse(engine.isLoaded)
    }

    @Test
    fun `a preloading hold loads and warms up the active engine`() = runTest {
        val holds = holds()
        val hold = holds.hold(preload = true)
        advanceUntilIdle()
        assertEquals(1, engine.warmUps)
        assertTrue(engine.isLoaded)

        holds.session() // instant: already loaded, and kept
        assertEquals(1, engine.loads)
        assertTrue(engine.isLoaded)

        hold.close()
        advanceUntilIdle()
        assertFalse(engine.isLoaded)
    }

    @Test
    fun `a preloading hold with no engine installed loads nothing`() = runTest {
        installed = false
        val holds = holds()
        val hold = holds.hold(preload = true)
        advanceUntilIdle()
        assertEquals(0, engine.warmUps)
        assertEquals(0, engine.loads)
        hold.close()
        advanceUntilIdle()
        assertEquals(0, engine.releases)
    }

    @Test
    fun `a preload that runs after its hold closed loads nothing`() = runTest {
        val holds = holds()
        holds.hold(preload = true).close()
        advanceUntilIdle()
        assertEquals(0, engine.loads)
    }

    @Test
    fun `an already loaded engine isn't warmed up again`() = runTest {
        keepLoaded = true
        val holds = holds()
        holds.session()
        holds.hold(preload = true)
        advanceUntilIdle()
        assertEquals(0, engine.warmUps)
        assertEquals(1, engine.loads)
    }

    @Test
    fun `the manager's warm-up preloads under a hold with Keep engine ready off, and not without one`() = runTest {
        val holds = holds()
        holds.preload()
        assertEquals(0, engine.loads)
        holds.hold(preload = false)
        holds.preload()
        assertEquals(1, engine.warmUps)
        assertTrue(engine.isLoaded)
    }
}
