package app.marmalade.stt.ui.screen

import app.marmalade.stt.data.SpeechPrefs
import app.marmalade.stt.install.SpeedTier
import app.marmalade.stt.install.SttEngineDescriptor

// The short facts the Engines screens print about an engine besides its
// specs (Max's picks from the design lab, 2026-09-28): the A1 card's pills
// and size-line tags, the A3 row's second line and the B1 dialog's pills.
// Plain functions so they are unit-tested; the composables only resolve the
// strings.

/** Between facts on one line: "682 MB download · Streaming · Developer". */
const val FACT_SEPARATOR = " · "

/** One fact about an engine. [engineFacts] lists them in display order. */
sealed interface EngineFact {
    /** The engine recommended for this phone ([SpeechPrefs.recommendedEngineId]). */
    data object Recommended : EngineFact

    /** The engine that runs. */
    data object InUse : EngineFact

    /** Decodes audio as it arrives, in [stepMs] chunks when the export fixes one ([streamingStepMs]). */
    data class Streaming(val stepMs: Int?) : EngineFact

    /** Over this phone's speed budget ([SpeedTier.SLOW]). */
    data object MayBeSlow : EngineFact

    /** Only listed with Settings → Developer engines on (or as the recommended engine); [EngineBadge.DEVELOPER]. */
    data object Developer : EngineFact
}

/**
 * The facts about [engine], in the A3 row's order: recommended, in use,
 * streaming, may be slow, developer. [recommendedId] is
 * [SpeechPrefs.recommendedEngineId]; [showDeveloper] is
 * [SpeechPrefs.developerEngines] ([engineBadges]); [tier] the engine's speed on this phone.
 * The A1 card shows [EngineFact.Recommended] and [EngineFact.MayBeSlow] as
 * pills, [EngineFact.Streaming] and [EngineFact.Developer] after its size,
 * and being in use as its action row.
 */
fun engineFacts(
    engine: SttEngineDescriptor,
    recommendedId: String?,
    showDeveloper: Boolean,
    inUse: Boolean,
    tier: SpeedTier?,
): List<EngineFact> {
    val badges = engineBadges(engine, recommendedId, showDeveloper)
    return buildList {
        if (EngineBadge.RECOMMENDED in badges) add(EngineFact.Recommended)
        if (inUse) add(EngineFact.InUse)
        if (EngineBadge.STREAMING in badges) add(EngineFact.Streaming(streamingStepMs(engine)))
        if (tier == SpeedTier.SLOW) add(EngineFact.MayBeSlow)
        if (EngineBadge.DEVELOPER in badges) add(EngineFact.Developer)
    }
}

/** A streaming engine's chunk ("1120 ms steps", Nemotron 3.5): how often text arrives. Null for the rest. */
fun streamingStepMs(engine: SttEngineDescriptor): Int? = engine.chunkMs
