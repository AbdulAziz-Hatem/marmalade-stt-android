package app.marmalade.stt.speech.turn

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * The golden values `tools/smart-turn/gen_fixtures.py` wrote to
 * `src/test/resources/smart-turn/` from marmalade-stt-cli's Python, and the
 * synthetic inputs they were computed from, rebuilt here with the same formula.
 */
internal object TurnFixtures {

    /** One case of `index.tsv`: [voiced] samples of [signal], then [zeros] of silence. */
    data class Case(val name: String, val voiced: Int, val zeros: Int, val seed: Int, val probability: Float?) {
        val audio: FloatArray get() = signal(voiced, seed).copyOf(voiced + zeros)
    }

    val cases: List<Case> = text("index.tsv").lines().filter { it.isNotBlank() }.map { line ->
        val (name, voiced, zeros, seed, probability) = line.split('\t')
        Case(name, voiced.toInt(), zeros.toInt(), seed.toInt(), probability.toFloatOrNull())
    }

    /** The frame indices `<case>.frames.f32` keeps in full. */
    val frames: List<Int> = text("frames.txt").trim().split(' ').map { it.toInt() }

    fun floats(name: String): FloatArray {
        val buf = ByteBuffer.wrap(bytes(name)).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(buf.remaining()).also { buf.get(it) }
    }

    /** gen_fixtures.py's `signal`: 4 harmonics of a 140 Hz pitch falling 20 Hz, gated at 5 Hz, plus noise. */
    fun signal(n: Int, seed: Int): FloatArray {
        val noise = noise(n, seed)
        val glide = -20.0 / (n / 16_000.0)
        return FloatArray(n) { i ->
            val t = i / 16_000.0
            val phase = 140.0 * t + 0.5 * glide * t * t
            val voiced = 0.5 * sin(2 * PI * phase) + 0.3 * sin(2 * PI * 2 * phase) +
                0.2 * sin(2 * PI * 3 * phase) + 0.1 * sin(2 * PI * 5 * phase)
            val gate = max(0.0, sin(2 * PI * 2.5 * t))
            (0.4 * gate * voiced + 0.02 * noise[i]).toFloat()
        }
    }

    /** xorshift32 (13, 17, 5) mapped to [-0.5, 0.5), as gen_fixtures.py's `noise`. */
    private fun noise(n: Int, seed: Int): DoubleArray {
        var state = seed
        return DoubleArray(n) {
            state = state xor (state shl 13)
            state = state xor (state ushr 17)
            state = state xor (state shl 5)
            (state ushr 8) / 16_777_216.0 - 0.5
        }
    }

    private fun bytes(name: String): ByteArray =
        checkNotNull(javaClass.classLoader!!.getResourceAsStream("smart-turn/$name")) { "missing smart-turn/$name" }
            .use { it.readBytes() }

    private fun text(name: String) = bytes(name).decodeToString()
}
