package app.marmalade.stt.ui.theme

/** Settings → Appearance → Mode (#35): follow the system's dark theme, or always light or dark. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    /** Whether the app is dark in this mode while the system is [systemDark]. */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        /** The mode stored under [name], [SYSTEM] (the default) for none or an unknown one. */
        fun fromString(name: String?): ThemeMode = entries.find { it.name.equals(name, ignoreCase = true) } ?: SYSTEM
    }
}

/**
 * Settings → Appearance (#35), as marmalade-tts-android has it: the colour
 * [preset] and the light/dark [mode], independent of each other ("Forest,
 * always dark"). Stored by [app.marmalade.stt.data.AppearanceSettings];
 * every screen of the app, the voice keyboard and the speech sheet apply it
 * ([MarmaladeSttTheme]).
 */
data class Appearance(
    val preset: ThemePreset = ThemePreset.MARMALADE,
    val mode: ThemeMode = ThemeMode.SYSTEM,
)
