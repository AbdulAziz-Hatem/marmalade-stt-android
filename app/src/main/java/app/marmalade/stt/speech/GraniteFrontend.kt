package app.marmalade.stt.speech

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Granite Speech 5.0's audio features (#32): a port of `asrbench.granite.features`
 * (tools/asr-bench), itself a port of transformers' GraniteSpeech5FeatureExtractor.
 *
 * 16 kHz mono → log-mel + deltas, two frames stacked per row: `[T, 320]`,
 * T = ceil((samples / 160) / 2). The steps, as torchaudio does them:
 * 512-point FFT of 25 ms periodic-Hann frames every 10 ms (centred, reflect
 * padding), power spectrum, 80 HTK mel bins, log10 floored 8 decades below
 * the clip's loudest bin, then /4 + 1; deltas over 3 frames; pairs of
 * [mel + delta] rows stacked. Computed in doubles like the numpy reference
 * (`GraniteFrontendTest` holds it to the Python golden vectors).
 */
internal object GraniteFrontend {
    const val SAMPLE_RATE = 16_000
    const val N_FFT = 512
    const val WIN_LENGTH = 400
    const val HOP_LENGTH = 160
    const val N_MELS = 80
    const val STACK = 2
    const val WIDTH = STACK * 2 * N_MELS

    /** Decades of power below the clip's maximum (80 dB). */
    private const val LOGMEL_FLOOR = 8.0

    /** The encoder subsamples by 4 more, so shorter input has no output frame. */
    const val MIN_SAMPLES = 4 * STACK * HOP_LENGTH

    private const val N_FREQS = N_FFT / 2 + 1

    /**
     * torchaudio's `melscale_fbanks(257, 0, 8000, 80, 16000, norm=None, mel_scale="htk")`,
     * `[257 * 80]` row-major. Rounded to float as the reference's filterbank is.
     */
    private val melFilterbank: DoubleArray by lazy {
        fun hzToMel(f: Double) = 2595.0 * log10(1.0 + f / 700.0)
        fun melToHz(m: Double) = 700.0 * (10.0.pow(m / 2595.0) - 1.0)
        val melMax = hzToMel(SAMPLE_RATE / 2.0)
        val melStep = melMax / (N_MELS + 1) // np.linspace: index * step
        val fPts = DoubleArray(N_MELS + 2) { melToHz(it * melStep) }
        val out = DoubleArray(N_FREQS * N_MELS)
        for (k in 0 until N_FREQS) {
            val freq = (SAMPLE_RATE / 2).toDouble() * k / (N_FREQS - 1)
            for (m in 0 until N_MELS) {
                val down = -(fPts[m] - freq) / (fPts[m + 1] - fPts[m])
                val up = (fPts[m + 2] - freq) / (fPts[m + 2] - fPts[m + 1])
                out[k * N_MELS + m] = max(0.0, min(down, up)).toFloat().toDouble()
            }
        }
        out
    }

    /** Periodic Hann of 400 samples, zero-padded to 512 in the middle (torch.stft). */
    private val window: DoubleArray by lazy {
        val left = (N_FFT - WIN_LENGTH) / 2
        DoubleArray(N_FFT).also { w ->
            for (n in 0 until WIN_LENGTH) w[left + n] = 0.5 - 0.5 * cos(2.0 * PI * n / WIN_LENGTH)
        }
    }

    /** Stacked features, row-major `[frames(samples) * WIDTH]`. */
    fun features(samples: FloatArray): FloatArray {
        val melFrames = samples.size / HOP_LENGTH
        val nFrames = STACK * ((melFrames + STACK - 1) / STACK)
        // An odd frame count needs one more frame than the signal covers: zero-pad it.
        val need = (nFrames - 1) * HOP_LENGTH + 1
        val x = if (samples.size < need) samples.copyOf(need) else samples
        require(x.size > N_FFT / 2) { "too short for reflect padding: ${x.size} samples" }

        val half = N_FFT / 2
        val padded = DoubleArray(x.size + N_FFT)
        for (i in x.indices) padded[half + i] = x[i].toDouble()
        for (i in 0 until half) {
            padded[i] = x[half - i].toDouble()
            padded[half + x.size + i] = x[x.size - 2 - i].toDouble()
        }

        val fft = RealFft512()
        val frame = DoubleArray(N_FFT)
        val power = DoubleArray(N_FREQS)
        val fb = melFilterbank
        val logmel = DoubleArray(nFrames * N_MELS)
        var top = Double.NEGATIVE_INFINITY
        for (t in 0 until nFrames) {
            val start = t * HOP_LENGTH
            for (n in 0 until N_FFT) frame[n] = padded[start + n] * window[n]
            fft.power(frame, power)
            for (m in 0 until N_MELS) {
                var e = 0.0
                for (k in 0 until N_FREQS) e += power[k] * fb[k * N_MELS + m]
                val l = log10(max(e, 1e-10))
                logmel[t * N_MELS + m] = l
                if (l > top) top = l
            }
        }
        val floor = top - LOGMEL_FLOOR
        for (i in logmel.indices) logmel[i] = max(logmel[i], floor) / 4.0 + 1.0

        // compute_deltas(win_length=3): (x[t+1] - x[t-1]) / 2, edges replicated.
        val out = FloatArray(nFrames * 2 * N_MELS)
        for (t in 0 until nFrames) {
            val prev = max(t - 1, 0) * N_MELS
            val next = min(t + 1, nFrames - 1) * N_MELS
            val row = t * 2 * N_MELS
            for (m in 0 until N_MELS) {
                out[row + m] = logmel[t * N_MELS + m].toFloat()
                out[row + N_MELS + m] = ((logmel[next + m] - logmel[prev + m]) / 2.0).toFloat()
            }
        }
        // [nFrames, 160] row-major is already [nFrames / 2, 320]: each pair of rows is one stacked row.
        return out
    }

    /** Rows of [features] for [samples] samples. */
    fun frames(samples: Int): Int = (samples / HOP_LENGTH + STACK - 1) / STACK
}

/** In-place radix-2 FFT of 512 real samples, reporting |X[k]|² for k = 0..256. */
private class RealFft512 {
    private val n = GraniteFrontend.N_FFT
    private val re = DoubleArray(n)
    private val im = DoubleArray(n)

    fun power(input: DoubleArray, out: DoubleArray) {
        for (i in 0 until n) {
            re[BIT_REVERSED[i]] = input[i]
            im[BIT_REVERSED[i]] = 0.0
        }
        var size = 2
        while (size <= n) {
            val halfSize = size / 2
            val step = n / size
            var start = 0
            while (start < n) {
                for (j in 0 until halfSize) {
                    val wr = COS[j * step]
                    val wi = -SIN[j * step]
                    val a = start + j
                    val b = a + halfSize
                    val tr = re[b] * wr - im[b] * wi
                    val ti = re[b] * wi + im[b] * wr
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
                }
                start += size
            }
            size *= 2
        }
        for (k in out.indices) out[k] = re[k] * re[k] + im[k] * im[k]
    }

    private companion object {
        val COS = DoubleArray(GraniteFrontend.N_FFT / 2) { cos(2.0 * PI * it / GraniteFrontend.N_FFT) }
        val SIN = DoubleArray(GraniteFrontend.N_FFT / 2) { sin(2.0 * PI * it / GraniteFrontend.N_FFT) }
        val BIT_REVERSED = IntArray(GraniteFrontend.N_FFT) { i ->
            Integer.reverse(i) ushr (32 - Integer.numberOfTrailingZeros(GraniteFrontend.N_FFT))
        }
    }
}
