package app.marmalade.stt.ui.screen

import app.marmalade.stt.data.SpeechPrefs
import app.marmalade.stt.install.InstallState
import app.marmalade.stt.install.SttEngineCatalog
import app.marmalade.stt.install.SttEngineDescriptor

// Engine badges ("Recommended for this phone", "Developer", "Streaming") and
// whether an engine can be picked, for the Engines cards and rows and the
// engine details dialog. [engineFacts] (EngineFacts.kt) turns the badges,
// being in use and the speed tier into what each surface prints; EnginePills.kt
// draws them. The decisions are plain functions so they are unit-tested.

/** A small label on an engine. Display order is the enum's. */
enum class EngineBadge {
    /** The engine the setup guide picked for this phone ([SpeechPrefs.recommendedEngineId], #27). */
    RECOMMENDED,

    /** Only listed while Settings → Developer engines is on (or it is the recommended one). */
    DEVELOPER,

    /** Decodes audio as it arrives (true streaming family). */
    STREAMING,
}

/**
 * The badges for [engine]; [recommendedId] is [SpeechPrefs.recommendedEngineId],
 * [showDeveloper] is [SpeechPrefs.developerEngines].
 */
fun engineBadges(engine: SttEngineDescriptor, recommendedId: String?, showDeveloper: Boolean): List<EngineBadge> = buildList {
    if (engine.id == recommendedId) add(EngineBadge.RECOMMENDED)
    if (engine.developer) add(EngineBadge.DEVELOPER)
    if (engine.family.streaming) add(EngineBadge.STREAMING)
}

/** Where an engine stands with respect to being the one that runs. */
enum class EngineUse {
    /** It is the active engine. */
    IN_USE,

    /** Installed and listed, but another engine runs: its card's Use button or its row picks it. */
    AVAILABLE,

    /** Not installed (or damaged, or hidden): it can't be picked yet. */
    UNAVAILABLE,
}

/**
 * Whether [engine] is in use, can be picked, or neither. Picking needs the
 * model installed and intact, and the engine [SttEngineCatalog.runnable]
 * under the current Developer engines setting; otherwise
 * [SttEngineCatalog.effective] would ignore the choice anyway.
 */
fun engineUse(engine: SttEngineDescriptor, state: InstallState, prefs: SpeechPrefs): EngineUse = when {
    prefs.activeEngine.id == engine.id -> EngineUse.IN_USE
    state == InstallState.Installed &&
        prefs.runnable(engine) ->
        EngineUse.AVAILABLE
    else -> EngineUse.UNAVAILABLE
}

/**
 * The selection to store after [removedId] is uninstalled, or null to leave
 * it: removing the selected engine falls back to the one recommended for this
 * phone, or the default, so nothing keeps pointing at an engine whose files
 * are gone.
 */
fun selectionAfterRemoving(removedId: String, prefs: SpeechPrefs): String? {
    if (prefs.selectedEngineId != removedId) return null
    val fallback = prefs.recommendedEngineId?.takeIf { it != removedId && SttEngineCatalog.byId(it) != null }
        ?: SttEngineCatalog.default.id
    return fallback.takeIf { it != removedId }
}
