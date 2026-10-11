package app.marmalade.stt.install

/**
 * One engine's cost on the reference phone, a Pixel 8a.
 *
 * @property relativeCost its decode time ÷ Parakeet Ultra's on the same
 *   audio, at 4 threads (the Pixel 8a's count), both with ONNX Runtime's
 *   memory arena. Parakeet Ultra is 1.0.
 * @property peakRssMb the app process's peak memory while the engine
 *   decodes its longest utterance, for the RAM check ([EngineRecommender]);
 *   see [SpeedBudget.REFERENCE].
 * @property lean the engine without the arena, for phones it fits only that
 *   way (#47, [SpeedBudget.runsLean]); null where that wasn't measured.
 */
data class EngineSpeed(val relativeCost: Double, val peakRssMb: Int, val lean: LeanMode? = null)

/**
 * An engine run without ONNX Runtime's memory arena (#47): less memory for
 * long utterances, but every decode of a new length allocates afresh.
 *
 * @property peakRssMb the peak memory at the longest utterance, as [EngineSpeed.peakRssMb].
 * @property costFactor its decode time ÷ the same engine's with the arena,
 *   over utterances of lengths it hadn't decoded before (as dictation's are).
 */
data class LeanMode(val peakRssMb: Int, val costFactor: Double)

/**
 * How fast an engine is on this phone, as a five-segment meter (5 = fastest),
 * the same number of segments as the quality meter (Max, 2026-10-10, #80).
 */
enum class SpeedTier(val meterFill: Int) {
    FASTEST(5),
    VERY_FAST(4),
    FAST(3),
    MODERATE(2),

    /** Over the phone's budget: the "May be slow on this phone" engines. */
    SLOW(1),
}

/**
 * Which engines are too slow for this phone (#24, #27). Max's rule: on a
 * Pixel 8a, nothing slower than distil-small.en was (the stage-1 default,
 * gone since #45). A faster phone affords more.
 *
 * - Every engine has a [EngineSpeed.relativeCost] against Parakeet Ultra
 *   at 4 recognizer threads (#45; distil-small.en until then). Relative costs
 *   hardly change from phone to phone; the phone's speed does, and so does
 *   its thread count ([threadPenalty], [costsAt]).
 * - The phone's speed factor (2.0 = twice a Pixel 8a) comes from a short CPU
 *   probe that needs no engine: the bundled Silero VAD over the bundled
 *   calibration clip ([speedFactor], `speech/SpeedProbe`).
 * - The budget is that factor, clamped to [MIN_FACTOR]..[MAX_FACTOR] (1.0
 *   until measured), times [REFERENCE_BUDGET], distil-small.en's cost in
 *   Parakeet Ultra units: so 3.57 on a Pixel 8a.
 * - An engine is over budget when its cost exceeds the budget by more than
 *   [TOLERANCE], the benchmark's "about the same" band, so measurement noise
 *   doesn't flag an engine as fast as distil-small.en on the reference phone.
 */
object SpeedBudget {

    /** Where the calibration clip lives in `assets/`: 3.6 s of synthetic English speech. */
    const val CALIBRATION_ASSET = "calibration/speech.wav"

    const val TOLERANCE = 1.25
    const val MIN_FACTOR = 0.25
    const val MAX_FACTOR = 4.0

    /**
     * The budget on the reference phone, in Parakeet Ultra units: what
     * distil-small.en cost there, the ceiling of Max's rule. A HOST ratio
     * like the costs below: 1 ÷ Ultra's 0.28 against distil (benchmark host,
     * 4 threads). #34's Pixel sweep put distil at 3.8–4.7× Parakeet v3, so
     * about 3.5–4.4× Ultra (1.06× v3) on the phone. Revisit it whenever
     * [REFERENCE] is regenerated from a Pixel run: the app can't run distil
     * any more (#45), so `summarize.py --kotlin` prints a suggested value
     * only when its result files include a distil-small-en turn (a run from
     * before #45 on the same phone, at the same threads, with the baseline).
     * #31's Pixel spot check (`docs/benchmarks/2026-10-pixel8a-ort.md`) put
     * distil at 3.48× Ultra, 2.5 % under this, so it stays.
     */
    const val REFERENCE_BUDGET = 3.57

    /**
     * Costs are PROVISIONAL (#27) where the Pixel 8a didn't measure them:
     * host ratios (benchmark host, the same int8 files, #14's uncontended
     * 2-thread RTF on LibriSpeech) moved to 4 threads with #20's measured
     * 2→4 speed-ups (distil 1.26×, Parakeet 1.49×, Nemotron EN 1.37×;
     * Nemotron 3.5 takes Nemotron EN's, every other engine distil's).
     * Replace them with `tools/speed-bench/summarize.py --kotlin` from a
     * Pixel run. They were measured against distil-small.en and divided by
     * Parakeet Ultra's 0.28 when #45 removed distil.
     *
     * #34's Pixel thread sweep agrees where it overlaps (4 threads, screen on,
     * `docs/benchmarks/2026-09-pixel8a-threads.md`): Parakeet v3 at RTF
     * 0.066–0.072 against distil's 0.27–0.31 is 0.22–0.25 (the host's 0.26),
     * and Nemotron 3.5 at about 0.36 is about 1.25 (the host's 1.20).
     *
     * #31's Pixel spot check (`docs/benchmarks/2026-10-pixel8a-ort.md`, 4
     * threads, idle, charging, screen off) measured, in Ultra units: Nemotron
     * 3.5 at 1120 ms 2.67 (and the 560 ms export, which left the app in #37,
     * 4.38), Whisper turbo 9.64, Parakeet 110M 0.378. Only 110M differed from
     * its estimate by more than 15 % (0.321), so it is now the Pixel's value;
     * the others stay.
     *
     * #47's Pixel runs (`docs/benchmarks/2026-10-pixel8a-mem.md`, same
     * conditions) measured every engine on the first decode of each length,
     * against Ultra with ONNX Runtime's arena, as all costs here are. Canary
     * (1.92) and Granite (0.76) differed from their host estimates (2.46,
     * 0.429) by more than 15 % and are the Pixel's now; Qwen3 (5.15 against
     * 5.96), turbo, both Nemotrons and 110M were within it and stay.
     *
     * Memory is the app's (#31 review; the host's Python process read up to
     * twice as high) at the longest utterance the app decodes in one piece
     * ([app.marmalade.stt.speech.MAX_UTTERANCE_SECONDS], 28 s), in MB: the
     * Pixel 8a's peak minus the process's baseline before the load, plus a
     * 180 MB baseline (154-212 MB there), the highest of #31's and #47's runs
     * (#47: two 27 s LibriSpeech utterances among clips of 5-20 s; with the
     * arena the peak depends on the order of the lengths, from 1310 to 1710
     * MB for Ultra).
     *
     * [LeanMode] (#47): the offline engines without the arena, for phones
     * they fit only that way ([runsLean]). Every decode of a new length then
     * allocates afresh: Ultra 1.34x slower, 110M 1.68x, Canary 1.87x, Qwen3
     * 1.63x (first decodes, Pixel). Granite has none: it saved only 30 MB.
     * `tools/speed-bench/summarize.py --kotlin` prints these rows.
     */
    val REFERENCE: Map<String, EngineSpeed> = mapOf(
        "whisper-turbo" to EngineSpeed(9.79, 2270),
        "canary-180m-flash" to EngineSpeed(1.92, 950, LeanMode(610, 1.87)), // Pixel 8a (#47); the host ratio gave 2.46
        // The 560 ms export's cost (4.29, before it left the app in #37) x the host's 1120/560 RTF
        // ratio, both with the app's tail padding (0.67, docs/benchmarks/2026-09-nemotron-chunk.md).
        SttEngineCatalog.NEMOTRON_35_1120MS to EngineSpeed(2.86, 960),
        // Measured with sherpa's 512-position KV cache; #50's 896 (QWEN3_MAX_TOTAL_LEN) adds 88 MB to both.
        "qwen3-asr-0-6b" to EngineSpeed(5.96, 3130 + 88, LeanMode(1790 + 88, 1.63)),
        // #25's engines, measured as a ratio to Parakeet v3 in the same (shared) run: 110M 0.33x,
        // Ultra 1.06x; Parakeet's 2->4 thread speed-up.
        "parakeet-tdt-110m-en" to EngineSpeed(0.378, 830, LeanMode(570, 1.68)), // Pixel 8a (#31); the host ratio gave 0.321
        SttEngineCatalog.PARAKEET_ULTRA to EngineSpeed(1.00, 1710, LeanMode(1180, 1.34)),
        // Pixel 8a (#60, docs/benchmarks/2026-10-pixel8a-ja/, first decodes as #47): cost and lean
        // factor as measured; memory = its own peak (882 MB arena, 798 lean) x Ultra's REFERENCE / Ultra's
        // own peak in the same runs (1530/1274, 1000/944: their shuffled order grew the arena less than
        // #47's ascending one), + 180 MB. The host estimate was 0.92, 1220, 1040.
        SttEngineCatalog.PARAKEET_JA to EngineSpeed(0.98, 1240, LeanMode(1030, 1.14)),
        // Pixel 8a (#47), front-end included; the formatter adds < 1 ms. #26's host ratio (0.45x
        // Parakeet v3 at 4 threads) gave 0.429.
        "granite-speech-5-470m" to EngineSpeed(0.76, 1150),
    )

    /**
     * The probe's time on a Pixel 8a, in ms (`speech/SpeedProbe`), as the app
     * measures it: in the app, screen on (#31, 2026-10-02: 157 ms, stored by
     * the app on Max's Pixel 8a, which then read 1.7x against the old 264).
     * The debug benchmark service reads 238-246 ms on the same phone (5
     * probes a run, idle, screen off): a service isn't in Android's top-app
     * cpuset, so it gets no boost and no Cortex-X3 core (its threads may run
     * on cores 0-7 only). #34's 264 ms came from that service path, under load.
     */
    const val PROBE_REFERENCE_MS = 157L

    /**
     * [engineId] runs without ONNX Runtime's memory arena on a phone with
     * [totalMemBytes] (#47): it has a [LeanMode] and doesn't fit the RAM
     * check ([EngineRecommender.fitsMemory]) with the arena. Phones it fits
     * with the arena keep it: it is faster. (One it fits neither way runs
     * lean too, as the smaller of the two.)
     */
    fun runsLean(engineId: String, totalMemBytes: Long, reference: Map<String, EngineSpeed> = REFERENCE): Boolean {
        val speed = reference[engineId] ?: return false
        return speed.lean != null && !EngineRecommender.fitsMemory(speed.peakRssMb, totalMemBytes)
    }

    /**
     * [reference] as a phone with [totalMemBytes] runs it: an engine that
     * runs lean there ([runsLean]) gets its lean memory and its cost times
     * [LeanMode.costFactor], and no further lean mode. Unchanged for an
     * unknown RAM (null). Applying it twice changes nothing.
     */
    fun forPhone(totalMemBytes: Long?, reference: Map<String, EngineSpeed> = REFERENCE): Map<String, EngineSpeed> {
        if (totalMemBytes == null) return reference
        return reference.mapValues { (id, speed) ->
            val lean = speed.lean
            if (lean == null || !runsLean(id, totalMemBytes, reference)) speed
            else EngineSpeed(speed.relativeCost * lean.costFactor, lean.peakRssMb)
        }
    }

    /** Recognizer threads the relative costs in [REFERENCE] are for: the Pixel 8a's. */
    const val REFERENCE_THREADS = 4

    fun speed(engineId: String): EngineSpeed? = REFERENCE[engineId]

    // The functions below take the reference table as a parameter so tests can use their own.
    // `tooSlow` is this phone's evidence (#30): engines that fell behind real
    // time in a session here (SpeechPrefs.tooSlowEngineIds). It overrides
    // the reference: such an engine is over budget and slow on this phone.

    /** The phone's budget in Parakeet Ultra units, from its speed factor (null: not measured yet). */
    fun budget(speedFactor: Double?): Double = (speedFactor ?: 1.0).coerceIn(MIN_FACTOR, MAX_FACTOR) * REFERENCE_BUDGET

    /**
     * [engine] costs more than [budget] allows, or fell behind on this phone
     * ([tooSlow]). An engine without a measurement otherwise never is.
     */
    fun overBudget(
        engine: SttEngineDescriptor,
        budget: Double,
        reference: Map<String, EngineSpeed> = REFERENCE,
        tooSlow: Set<String> = emptySet(),
    ): Boolean {
        if (engine.id in tooSlow) return true
        val cost = reference[engine.id]?.relativeCost ?: return false
        return cost > budget * TOLERANCE
    }

    /**
     * [engine]'s speed meter on a phone with [budget]: its cost as a share of
     * the budget. Up to 0.2 of it is the fastest tier, up to 0.35 very fast,
     * up to 0.7 fast, up to [TOLERANCE] moderate, beyond that slow (the same
     * line as [overBudget]). On a Pixel 8a (budget 3.57) Parakeet 110M (0.11)
     * is fastest and Parakeet Ultra (0.28) very fast.
     * Slow for an engine in [tooSlow]; otherwise null for an engine without
     * a measurement.
     */
    fun speedTier(
        engine: SttEngineDescriptor,
        budget: Double,
        reference: Map<String, EngineSpeed> = REFERENCE,
        tooSlow: Set<String> = emptySet(),
    ): SpeedTier? {
        if (engine.id in tooSlow) return SpeedTier.SLOW
        val share = (reference[engine.id]?.relativeCost ?: return null) / budget
        return when {
            share <= 0.2 -> SpeedTier.FASTEST
            share <= 0.35 -> SpeedTier.VERY_FAST
            share <= 0.7 -> SpeedTier.FAST
            share <= TOLERANCE -> SpeedTier.MODERATE
            else -> SpeedTier.SLOW
        }
    }

    /**
     * This phone's speed factor against the Pixel 8a from its probe time
     * [probeMs]: the reference time ÷ this phone's, the plain CPU ratio with
     * no thread correction. What threads change is each engine's cost
     * ([costsAt]): Parakeet Ultra itself costs 1.45× at 2 threads, while the
     * budget stays uncorrected, as it was when distil-small.en (flat from 2 to
     * 8 threads on the Pixel 8a, #34) was the unit.
     */
    fun speedFactor(probeMs: Long): Double = PROBE_REFERENCE_MS.toDouble() / probeMs.coerceAtLeast(1)

    /**
     * How much longer an engine of [family] takes with [threads] recognizer
     * threads than with [REFERENCE_THREADS], from the Pixel 8a thread sweep
     * (#34, `docs/benchmarks/2026-09-pixel8a-threads.md`). Threads are
     * clamped to 2–4, as [app.marmalade.stt.speech.RecognizerThreads] gives.
     *
     * - Parakeet (offline NeMo transducer): 1.45× at 2 threads (measured
     *   1.4–1.5×), 1.14× at 3. Granite and Parakeet-ja (CTC), not measured,
     *   are assumed to follow Parakeet: their cost is all encoder.
     * - Streaming transducers (Nemotron): the same at 3 as at 4; 1.37× at 2,
     *   the host's 2→4 gain (#20), since the Pixel's 2-thread turn was
     *   throttled.
     * - Whisper: flat from 2 to 8 threads (its autoregressive decoder
     *   dominates). Canary and Qwen3-ASR, not measured, are assumed to follow
     *   Whisper: their decoders are autoregressive too.
     */
    fun threadPenalty(family: EngineFamily, threads: Int): Double {
        val t = threads.coerceIn(2, REFERENCE_THREADS)
        return when (family) {
            EngineFamily.NEMO_TRANSDUCER, EngineFamily.NEMO_CTC, EngineFamily.GRANITE_CTC -> when (t) {
                2 -> 1.45
                3 -> 1.14
                else -> 1.0
            }
            EngineFamily.STREAMING_TRANSDUCER -> if (t == 2) 1.37 else 1.0
            EngineFamily.WHISPER, EngineFamily.CANARY, EngineFamily.QWEN3_ASR -> 1.0
        }
    }

    /**
     * [reference] with each engine's cost for a phone running [threads]
     * recognizer threads ([threadPenalty]); memory is unchanged. What the
     * budget functions above should be given for this phone
     * ([app.marmalade.stt.data.SpeechPrefs.engineCosts]).
     */
    fun costsAt(threads: Int, reference: Map<String, EngineSpeed> = REFERENCE): Map<String, EngineSpeed> =
        reference.mapValues { (id, speed) ->
            val family = SttEngineCatalog.byId(id)?.family ?: return@mapValues speed
            speed.copy(relativeCost = speed.relativeCost * threadPenalty(family, threads))
        }
}
