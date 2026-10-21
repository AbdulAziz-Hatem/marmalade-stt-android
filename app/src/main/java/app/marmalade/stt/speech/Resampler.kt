package app.marmalade.stt.speech

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * Streaming sample-rate converter for mono float PCM, any rate to any rate
 * ([StreamCapture]: a caller's 8–192 kHz audio to [MIC_SAMPLE_RATE], #90).
 *
 * Band-limited interpolation: each output sample is a windowed-sinc (Blackman,
 * [ZERO_CROSSINGS] each side) weighted sum of the input around its position.
 * The sinc's cutoff sits at [ROLLOFF] of the lower of the two Nyquist
 * frequencies, so downsampling low-passes first: 48 kHz audio loses what is
 * above about 7.2 kHz instead of folding it back into the speech band. The
 * kernel is one table shared by every rate ([KERNEL], interpolated linearly),
 * so an odd rate such as 44 056 Hz costs no more than 48 kHz. Weights are
 * normalised per output sample, so the gain is exactly 1 at DC whatever the
 * fractional position. The kernel is centred: no delay.
 *
 * Output sample n sits at input position n × from / to (exact, in Longs, so
 * no drift on long streams). [process] returns what the input so far fully
 * determines; [flush] the rest, treating the input past its end as silence.
 * In all, ⌈input samples × to / from⌉ samples come out. Not thread-safe.
 */
internal class Resampler(private val fromRate: Int, private val toRate: Int) {

    companion object {
        /** Kernel half-width, in zero crossings of the sinc. */
        const val ZERO_CROSSINGS = 16

        /** Cutoff as a fraction of the lower Nyquist frequency: room for the window's transition band. */
        const val ROLLOFF = 0.9

        /** Table points per zero crossing. Linear interpolation between them errs by about 1e-6. */
        private const val RESOLUTION = 512

        /** sinc(u) × Blackman(u / [ZERO_CROSSINGS]) for u in [0, ZERO_CROSSINGS]. */
        private val KERNEL: FloatArray by lazy {
            FloatArray(ZERO_CROSSINGS * RESOLUTION + 1) { i ->
                val u = i.toDouble() / RESOLUTION
                if (u >= ZERO_CROSSINGS) {
                    0f
                } else {
                    val sinc = if (i == 0) 1.0 else sin(PI * u) / (PI * u)
                    val x = u / ZERO_CROSSINGS
                    (sinc * (0.42 + 0.5 * cos(PI * x) + 0.08 * cos(2 * PI * x))).toFloat()
                }
            }
        }
    }

    init {
        require(fromRate > 0 && toRate > 0) { "bad rates $fromRate -> $toRate" }
    }

    /** The sinc's cutoff relative to the input's Nyquist frequency. */
    private val cutoff = ROLLOFF * minOf(1.0, toRate.toDouble() / fromRate)

    /** Input samples on each side of an output's position that reach it. */
    private val reach = ceil(ZERO_CROSSINGS / cutoff).toInt()

    private var held = FloatArray(4 * reach)
    private var heldSize = 0
    private var heldStart = 0L // input index of held[0]
    private var received = 0L // input samples so far
    private var produced = 0L // output samples so far

    /** Feed [samples]; returns the output they complete (possibly none). */
    fun process(samples: FloatArray): FloatArray {
        dropUnneeded()
        if (heldSize + samples.size > held.size) held = held.copyOf(maxOf(heldSize + samples.size, held.size * 2))
        samples.copyInto(held, destinationOffset = heldSize)
        heldSize += samples.size
        received += samples.size
        // Output n needs input up to floor(n × from / to) + reach.
        val ready = received - reach
        return produce(if (ready <= 0) 0 else ceilDiv(ready * toRate, fromRate.toLong()))
    }

    /** The input has ended: the remaining output. */
    fun flush(): FloatArray = produce(ceilDiv(received * toRate, fromRate.toLong()))

    /** Output samples [produced] until [limit]. */
    private fun produce(limit: Long): FloatArray {
        val out = FloatArray((limit - produced).coerceAtLeast(0).toInt())
        for (i in out.indices) {
            val position = produced * fromRate
            out[i] = sampleAt(position / toRate, (position % toRate).toDouble() / toRate)
            produced++
        }
        return out
    }

    /** The output at input position [center] + [frac]; input outside what was received counts as silence. */
    private fun sampleAt(center: Long, frac: Double): Float {
        var sum = 0.0
        var weights = 0.0
        for (k in center - reach + 1..center + reach) {
            val w = kernel(abs(center - k + frac) * cutoff)
            weights += w
            if (k in 0 until received) sum += held[(k - heldStart).toInt()] * w
        }
        return (sum / weights).toFloat().coerceIn(-1f, 1f)
    }

    private fun kernel(u: Double): Double {
        if (u >= ZERO_CROSSINGS) return 0.0
        val x = u * RESOLUTION
        val i = x.toInt()
        val a = KERNEL[i]
        return a + (x - i) * (KERNEL[i + 1] - a)
    }

    /** Forget the input no future output reaches. */
    private fun dropUnneeded() {
        val firstNeeded = produced * fromRate / toRate - reach + 1
        val n = (firstNeeded - heldStart).coerceIn(0, heldSize.toLong()).toInt()
        if (n == 0) return
        held.copyInto(held, destinationOffset = 0, startIndex = n, endIndex = heldSize)
        heldSize -= n
        heldStart += n
    }

    private fun ceilDiv(a: Long, b: Long) = (a + b - 1) / b
}
