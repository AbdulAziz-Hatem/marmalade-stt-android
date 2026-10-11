package app.marmalade.stt.ui.onboarding

import android.content.Intent

/**
 * Talking to FUTO Keyboard about its voice input engine.
 *
 * FUTO Keyboard exposes an activity (action [ACTION_VOICE_INPUT_SWITCH]) that
 * another app can start for a result: in `popup` mode it asks the user to
 * switch FUTO's voice input to that app; in `check` mode it only reports
 * whether it already uses it. Older FUTO versions don't have the activity;
 * then launching throws ActivityNotFoundException and all we can offer is
 * the manual instructions. The protocol is documented from FUTO's source,
 * which is reference-only for this repo (FUTO Source First License): nothing
 * here is copied from it.
 */
object FutoVoiceInput {

    /** FUTO Keyboard's packages, in the order we prefer them when several are installed. */
    val PACKAGES: List<String> = listOf(
        "org.futo.inputmethod.latin",
        "org.futo.inputmethod.latin.unstable",
        "org.futo.inputmethod.latin.playstore",
    )

    const val ACTION_VOICE_INPUT_SWITCH = "org.futo.inputmethod.latin.action.VoiceInputSwitch"
    const val EXTRA_TARGET_PACKAGE = "targetPackage"
    const val EXTRA_MODE = "mode"

    /** `popup` result: the user said yes and FUTO now uses the target app. */
    const val RESULT_SWITCHED = 65

    /** `check` result: FUTO's voice input is the target app. */
    const val RESULT_SELECTED = 67

    /** `check` result: FUTO's voice input is something else. */
    const val RESULT_NOT_SELECTED = 68

    /** Android's RESULT_CANCELED, spelled out so this file's logic stays JVM-testable. */
    private const val RESULT_CANCELED = 0

    /**
     * Which installed FUTO package to talk to. The one that is the current
     * keyboard wins (someone with both stable and unstable installed types
     * with one of them); otherwise the first of [PACKAGES] that is installed.
     *
     * @param installed package names found on the device (any, not only FUTO's).
     * @param defaultImePackage package of the current default keyboard, if known.
     * @return null when no FUTO Keyboard is installed.
     */
    fun pickPackage(installed: Set<String>, defaultImePackage: String?): String? {
        if (defaultImePackage != null && defaultImePackage in PACKAGES && defaultImePackage in installed) {
            return defaultImePackage
        }
        return PACKAGES.firstOrNull { it in installed }
    }

    /** The result code of a [switchIntent] round trip, as a [FutoStatus]. */
    fun resultFromCode(mode: FutoMode, resultCode: Int): FutoStatus = when (mode) {
        FutoMode.POPUP -> when (resultCode) {
            RESULT_SWITCHED -> FutoStatus.SELECTED
            // FUTO answers "no" (or a failed switch) with a plain cancel.
            RESULT_CANCELED -> FutoStatus.NOT_SELECTED
            else -> FutoStatus.UNKNOWN
        }
        FutoMode.CHECK -> when (resultCode) {
            RESULT_SELECTED -> FutoStatus.SELECTED
            RESULT_NOT_SELECTED -> FutoStatus.NOT_SELECTED
            // Includes 0: an activity that returned without answering.
            else -> FutoStatus.UNKNOWN
        }
    }

    /**
     * The intent for [mode], aimed at [futoPackage], asking about
     * [ourPackage]. Start it for a result; ActivityNotFoundException means
     * this FUTO version can't be asked ([FutoStatus.UNKNOWN]).
     */
    fun switchIntent(futoPackage: String, ourPackage: String, mode: FutoMode): Intent =
        Intent(ACTION_VOICE_INPUT_SWITCH)
            .setPackage(futoPackage)
            .putExtra(EXTRA_TARGET_PACKAGE, ourPackage)
            .putExtra(EXTRA_MODE, mode.extra)
}

/** The two things FUTO's switch activity can be asked to do. */
enum class FutoMode(val extra: String) {
    /** Show FUTO's "use this app for voice input?" dialog. */
    POPUP("popup"),

    /** Only report whether FUTO already uses this app. */
    CHECK("check"),
}

/** What FUTO Keyboard said about its voice input engine. */
enum class FutoStatus {
    /** FUTO uses Marmalade STT for voice input. */
    SELECTED,

    /** FUTO uses something else (or the user said no). */
    NOT_SELECTED,

    /** FUTO couldn't be asked (older version) or gave an answer we don't know. */
    UNKNOWN,
}
