package app.marmalade.stt.ui.onboarding

// Android's speech recognition service setting (#61): apps and keyboards
// that use android.speech.SpeechRecognizer get the service picked in
// Settings' Voice input picker (Settings.Secure.VOICE_RECOGNITION_SERVICE).
// An app can read that setting but not write it, so the guide checks it and
// opens the nearest settings screen (system/SettingsLinks.kt, #79). Pure, so
// it is unit-tested. Sources: docs/RESEARCH-keyboards-2026-10.md §3.1.

object SpeechService {

    /** PackageManager feature without which Android has no Voice input picker (Android Go phones). */
    const val FEATURE_VOICE_RECOGNIZERS = "android.software.voice_recognizers"

    /**
     * Whether the setting's value names our service. The value is a
     * flattened component, short (`pkg/.Cls`) or long (`pkg/pkg.Cls`).
     */
    fun isOurs(value: String?, ourPackage: String, ourClass: String): Boolean {
        val pkg = value?.substringBefore('/', missingDelimiterValue = "") ?: return false
        if (pkg != ourPackage) return false
        val cls = value.substringAfter('/')
        return (if (cls.startsWith(".")) pkg + cls else cls) == ourClass
    }
}
