package app.marmalade.stt.ui.onboarding

// Which keyboards the setup guide knows, and how each one's mic key reaches
// Marmalade STT (#61). Pure: OnboardingViewModel reads Android's enabled
// keyboards into [EnabledKeyboard]s, and this file decides what to show.
// The per-keyboard facts come from each keyboard's source or docs and from
// emulator tests; see docs/RESEARCH-keyboards-2026-10.md and the README's
// "Works with" table. Copyleft keyboards' code was read, never copied.

/** How a keyboard's mic key gets to a voice engine, as far as the guide's advice goes. */
enum class KeyboardKind {
    /** FUTO Keyboard: its own voice input setting, and a switch activity we can ask (FutoVoiceInput). */
    FUTO,

    /**
     * Opens the first voice keyboard that is on: Android's LatinIME and its
     * forks (HeliBoard, Indic), FlorisBoard and AnySoftKeyboard. Android
     * lists preinstalled voice keyboards first, so Google voice typing wins
     * while it is on.
     */
    FIRST_VOICE_KEYBOARD,

    /**
     * As [FIRST_VOICE_KEYBOARD], but the keyboard declares no package
     * visibility for keyboards (OpenBoard, Thumb-Key): Android shows it
     * Marmalade only after it has typed into Marmalade STT, until the next
     * restart.
     */
    FIRST_VOICE_KEYBOARD_HIDDEN,

    /** Unexpected Keyboard: asks which voice keyboard to use the first time, then remembers. */
    CHOOSER,

    /** Fossify Keyboard: a "Voice typing method" setting; same visibility problem as [FIRST_VOICE_KEYBOARD_HIDDEN]. */
    FOSSIFY,

    /** SwiftKey: its own voice typing unless turned off; then it asks Android for speech input. */
    SWIFTKEY,

    /** Gboard, Samsung Keyboard, Yandex: the mic key always uses the keyboard's own recognizer. */
    OWN_ENGINE,

    /** Hacker's Keyboard: its mic key only opens Google's voice typing. */
    GOOGLE_ONLY,

    /** Simple Keyboard (both of them): no mic key. */
    NO_MIC_KEY,

    /** A keyboard we haven't checked. */
    UNKNOWN,
}

/**
 * An enabled keyboard, reduced to what the guide needs.
 *
 * @property id the input method's component, flattened (`package/class`).
 * @property subtypeModes the modes of its declared subtypes (`keyboard`,
 *   `voice`…); empty when it declares none, as some keyboards do.
 */
data class EnabledKeyboard(
    val id: String,
    val label: String,
    val subtypeModes: List<String> = emptyList(),
) {
    val packageName: String get() = id.substringBefore('/')

    /**
     * Whether it declares a voice subtype: a keyboard's voice key (FUTO's
     * with its built-in voice input off) opens such a keyboard.
     */
    val hasVoiceSubtype: Boolean get() = VOICE_MODE in subtypeModes

    /** A voice-only input method (ours, Google voice typing): something to dictate with, not to type with. */
    val isVoiceOnly: Boolean get() = subtypeModes.isNotEmpty() && subtypeModes.all { it == VOICE_MODE }

    /**
     * A voice keyboard as the guide warns about it: a voice subtype and no
     * keyboard subtype. A typing keyboard that also declares a voice
     * subtype is something the user types with, not a rival engine to turn
     * off.
     */
    val isVoiceKeyboard: Boolean get() = hasVoiceSubtype && KEYBOARD_MODE !in subtypeModes

    companion object {
        /** InputMethodSubtype mode of a voice keyboard (as in our voice_input_method.xml). */
        const val VOICE_MODE = "voice"

        /** InputMethodSubtype mode of a typing keyboard. */
        const val KEYBOARD_MODE = "keyboard"
    }
}

/** A keyboard the guide gives advice for. */
data class GuidedKeyboard(val label: String, val kind: KeyboardKind, val isDefault: Boolean)

object Keyboards {

    /**
     * Package names (or a package prefix followed by `.`, for debug and beta
     * builds) per kind. FUTO's come from [FutoVoiceInput.PACKAGES].
     */
    private val KNOWN: Map<String, KeyboardKind> = buildMap {
        FutoVoiceInput.PACKAGES.forEach { put(it, KeyboardKind.FUTO) }
        put("com.android.inputmethod.latin", KeyboardKind.FIRST_VOICE_KEYBOARD) // AOSP, LineageOS, GrapheneOS…
        put("helium314.keyboard", KeyboardKind.FIRST_VOICE_KEYBOARD)
        put("org.smc.inputmethod.indic", KeyboardKind.FIRST_VOICE_KEYBOARD)
        put("dev.patrickgold.florisboard", KeyboardKind.FIRST_VOICE_KEYBOARD)
        put("com.menny.android.anysoftkeyboard", KeyboardKind.FIRST_VOICE_KEYBOARD)
        put("org.dslul.openboard.inputmethod.latin", KeyboardKind.FIRST_VOICE_KEYBOARD_HIDDEN)
        put("com.dessalines.thumbkey", KeyboardKind.FIRST_VOICE_KEYBOARD_HIDDEN)
        put("juloo.keyboard2", KeyboardKind.CHOOSER)
        put("org.fossify.keyboard", KeyboardKind.FOSSIFY)
        put("com.touchtype.swiftkey", KeyboardKind.SWIFTKEY)
        put("com.google.android.inputmethod.latin", KeyboardKind.OWN_ENGINE) // Gboard
        put("com.samsung.android.honeyboard", KeyboardKind.OWN_ENGINE)
        put("ru.yandex.androidkeyboard", KeyboardKind.OWN_ENGINE)
        put("org.pocketworkstation.pckeyboard", KeyboardKind.GOOGLE_ONLY)
        put("rkr.simplekeyboard.inputmethod", KeyboardKind.NO_MIC_KEY)
        put("com.simplemobiletools.keyboard", KeyboardKind.NO_MIC_KEY)
    }

    /** The kind of the keyboard in [packageName]; a known package's `.debug`/`.beta`… builds count as it. */
    fun kindOf(packageName: String): KeyboardKind =
        KNOWN[packageName]
            ?: KNOWN.entries.firstOrNull { packageName.startsWith(it.key + ".") }?.value
            ?: KeyboardKind.UNKNOWN

    /**
     * The keyboards to give advice for: every enabled one you type with
     * (voice-only input methods are left out, ours included), the current
     * default first, then in Android's order.
     *
     * @param defaultId the default input method's flattened component, if known.
     */
    fun toGuide(enabled: List<EnabledKeyboard>, defaultId: String?): List<GuidedKeyboard> =
        enabled
            .filter { !it.isVoiceOnly }
            .distinctBy { it.id }
            .sortedByDescending { it.id == defaultId } // stable: the rest keep Android's order
            .map { GuidedKeyboard(label = it.label, kind = kindOf(it.packageName), isDefault = it.id == defaultId) }

    /**
     * The labels of enabled voice keyboards ([EnabledKeyboard.isVoiceKeyboard])
     * other than [ourId] (Google voice typing, say). Most keyboards' mic keys
     * open the first voice keyboard that is on, and Android lists
     * preinstalled ones first, so the guide says to turn these off. Never
     * the keyboard in use ([defaultId]): the user types with it.
     */
    fun otherVoiceKeyboards(enabled: List<EnabledKeyboard>, ourId: String, defaultId: String?): List<String> =
        enabled.filter { it.isVoiceKeyboard && it.id != ourId && it.id != defaultId }.map { it.label }.distinct()
}
