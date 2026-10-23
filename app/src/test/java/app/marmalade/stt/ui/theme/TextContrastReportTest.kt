package app.marmalade.stt.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import app.marmalade.ui.theme.MarmaladeDarkColors
import app.marmalade.ui.theme.MarmaladeDepth
import app.marmalade.ui.theme.MarmaladeLightColors
import app.marmalade.ui.theme.MarmaladeToast
import app.marmalade.ui.theme.MarmaladeToastInk
import app.marmalade.ui.theme.buildPresetScheme
import app.marmalade.ui.theme.darkDepth
import app.marmalade.ui.theme.lightDepth
import java.io.File
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Test
import app.marmalade.ui.theme.ThemePreset as SharedPreset

/**
 * Every text style against every surface it sits on, this app next to
 * Marmalade TTS (the library at TTS's own theme, 260fc1d: 1.3.0 plus TTS
 * #89), per preset and mode (#87). Each row says where its colours come
 * from: "shared" rows draw both apps from the same token, and their
 * contrast must match exactly; "library" rows are library changes since
 * TTS's theme (error red, #87 round 3), which TTS gets when it moves its
 * pin; "stt" rows are this app's own tokens (links, pills, the mic, High
 * contrast). High contrast has no TTS twin; it is set against TTS's
 * Marmalade.
 *
 * Writes `build/reports/text-contrast.json`, which the #87 lab renders.
 */
class TextContrastReportTest {

    private class Ink(val name: String, val stt: Color, val tts: Color, val source: String, val surfaces: List<String>)

    @Test
    fun `shared text styles read exactly as in TTS, and the report is written`() {
        val rows = mutableListOf<String>()
        for (preset in ThemePreset.entries - ThemePreset.SYSTEM) {
            for (dark in listOf(false, true)) {
                val stt = presetScheme(preset, dark)
                val sttDepth = sttDepth(preset, stt, dark)
                val accent = accentColors(preset, dark, stt)
                val ttsPreset = preset.shared ?: SharedPreset.MARMALADE
                val base = if (dark) MarmaladeDarkColors else MarmaladeLightColors
                val tts = (if (ttsPreset == SharedPreset.MARMALADE) base else buildPresetScheme(base, ttsPreset, dark))
                    .copy(error = if (dark) TTS_DARK_ERROR else TTS_LIGHT_ERROR)
                val ttsDepth = if (dark) darkDepth(tts) else lightDepth(tts)
                val twin = preset != ThemePreset.HIGH_CONTRAST
                val shared = if (twin) "shared" else "stt"

                val text = listOf("page", "card", "raised", "sheet", "dialog")
                val inks = listOf(
                    Ink("Body text (onSurface)", stt.onSurface, tts.onSurface, shared, text),
                    Ink("Secondary text (onSurfaceVariant)", stt.onSurfaceVariant, tts.onSurfaceVariant, shared, text),
                    Ink("Quietest text (depth.dim)", sttDepth.dim, ttsDepth.dim, shared, text),
                    Ink("Icons and chevrons (depth.faint, 3:1)", sttDepth.faint, ttsDepth.faint, shared, text),
                    // Light Midnight, Forest and Berry take their dark ink (AA; TTS's primary isn't).
                    Ink(
                        "Section titles (accent.text / TTS primary)", accent.text, tts.primary,
                        if (!dark && preset != ThemePreset.MARMALADE) "stt" else shared, text,
                    ),
                    Ink("Links and text buttons (accent.ink / TTS primary)", accent.ink, tts.primary, "stt", text),
                    Ink("Error text (error)", stt.error, tts.error, if (twin) "library" else "stt", text),
                    Ink("Filled button label (onPrimary on primary)", stt.onPrimary, tts.onPrimary, shared, listOf("primary")),
                    Ink("Accent button label (onPrimaryContainer)", stt.onPrimaryContainer, tts.onPrimaryContainer, shared, listOf("primaryContainer")),
                    Ink("Nav bar selected label (onSurface)", stt.onSurface, tts.onSurface, shared, listOf("navBar")),
                    Ink("Pill text (accent.pillInk / TTS toast ink)", accent.pillInk, MarmaladeToastInk, "stt", listOf("pill")),
                    Ink("Mic icon (accent.onMic / no TTS mic)", accent.onMic, Color.Unspecified, "stt", listOf("mic")),
                )
                for (ink in inks) {
                    for (surface in ink.surfaces) {
                        val sBg = surfaceOf(surface, stt, sttDepth, accent.pill, accent.mic)
                        val tBg = surfaceOf(surface, tts, ttsDepth, MarmaladeToast, Color.Unspecified)
                        val sRatio = contrast(ink.stt, sBg)
                        val tRatio = if (ink.tts.isSpecified && tBg.isSpecified) contrast(ink.tts, tBg) else null
                        val name = "${preset.name} ${if (dark) "dark" else "light"} ${ink.name} on $surface"
                        if (ink.source == "shared") assertEquals(name, tRatio!!, sRatio, 0.0001f)
                        rows += """{"preset":"${preset.name}","mode":"${if (dark) "dark" else "light"}",""" +
                            """"style":"${ink.name}","surface":"$surface","source":"${ink.source}",""" +
                            """"stt":"${hex(ink.stt)}","sttBg":"${hex(sBg)}","sttRatio":${round(sRatio)},""" +
                            """"tts":${ink.tts.takeIf { it.isSpecified }?.let { "\"${hex(it)}\"" } ?: "null"},""" +
                            """"ttsBg":${tBg.takeIf { it.isSpecified }?.let { "\"${hex(it)}\"" } ?: "null"},""" +
                            """"ttsRatio":${tRatio?.let { round(it) } ?: "null"}}"""
                    }
                }
            }
        }
        File("build/reports").mkdirs()
        File("build/reports/text-contrast.json").writeText(rows.joinToString(",\n", "[\n", "\n]\n"))
    }

    /** The ramp the app's theme provides: High contrast keeps its full-strength quiet text there too. */
    private fun sttDepth(preset: ThemePreset, scheme: ColorScheme, dark: Boolean): MarmaladeDepth {
        val depth = if (dark) darkDepth(scheme) else lightDepth(scheme)
        return if (preset == ThemePreset.HIGH_CONTRAST) depth.copy(dim = scheme.onSurfaceVariant) else depth
    }

    private fun surfaceOf(name: String, s: ColorScheme, d: MarmaladeDepth, pill: Color, mic: Color): Color = when (name) {
        "page" -> d.page
        "card" -> d.card
        "raised" -> d.raised
        "sheet" -> d.sheet
        // AlertDialog's container.
        "dialog" -> s.surfaceContainerHigh
        "navBar" -> s.surfaceContainer
        "primary" -> s.primary
        "primaryContainer" -> s.primaryContainer
        "pill" -> pill
        "mic" -> mic
        else -> error(name)
    }

    /** WCAG contrast of [fg] drawn over [bg] (a translucent ink composited first). */
    private fun contrast(fg: Color, bg: Color): Float {
        val a = fg.compositeOver(bg).luminance() + 0.05f
        val b = bg.luminance() + 0.05f
        return max(a, b) / min(a, b)
    }

    private fun round(f: Float) = Math.round(f * 100) / 100.0

    private fun hex(c: Color) = "#%08X".format(c.value.toLong() ushr 32)

    private companion object {
        /** TTS's error reds (its theme, 260fc1d); the library's are AA since #87 round 3. */
        val TTS_LIGHT_ERROR = Color(0xFFDC2626)
        val TTS_DARK_ERROR = Color(0xFFEF4444)
    }
}
