package app.marmalade.stt.ui.screen

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.marmalade.stt.R
import app.marmalade.stt.install.EngineSpeed
import app.marmalade.stt.install.SpeedBudget
import app.marmalade.stt.install.SttEngineCatalog
import java.util.Locale

// What the app says about speed (#24): the engine info dialog's Speed fact,
// the Settings → Developer "Re-measure phone speed" row and its snackbar.
// The choices are plain functions so they are unit-tested; the composables
// only resolve the strings.

/** A string resource and its (already formatted) arguments. */
data class TextRes(@StringRes val id: Int, val args: List<Any> = emptyList())

@Composable
fun TextRes.resolve(): String = stringResource(id, *args.toTypedArray())

/** A speed or cost ratio as shown: one decimal ("1.7"). */
fun formatSpeed(value: Double, locale: Locale = Locale.getDefault()): String = String.format(locale, "%.1f", value)

/**
 * The engine info dialog's Speed fact for [engineId]: its cost against
 * Parakeet Ultra, the speed reference, then a line saying the phone hasn't
 * been measured yet while [deviceSpeedFactor] is null (the tiers then assume
 * the reference phone). Null for an engine without a reference measurement
 * (no row).
 */
fun speedFactLines(
    engineId: String,
    deviceSpeedFactor: Double?,
    locale: Locale = Locale.getDefault(),
    reference: Map<String, EngineSpeed> = SpeedBudget.REFERENCE,
): List<TextRes>? {
    val speed = reference[engineId] ?: return null
    val cost = if (engineId == SttEngineCatalog.PARAKEET_ULTRA) {
        TextRes(R.string.engine_speed_reference)
    } else {
        TextRes(R.string.engine_speed_cost, listOf(formatSpeed(speed.relativeCost, locale)))
    }
    return listOfNotNull(cost, TextRes(R.string.engine_speed_phone_unmeasured).takeIf { deviceSpeedFactor == null })
}

/** The "Re-measure phone speed" row's supporting text: measuring, the last result, or not measured yet. */
fun phoneSpeedSummary(
    running: Boolean,
    deviceSpeedFactor: Double?,
    locale: Locale = Locale.getDefault(),
): TextRes = when {
    running -> TextRes(R.string.settings_developer_speed_measuring)
    deviceSpeedFactor == null -> TextRes(R.string.settings_developer_speed_unmeasured)
    else -> TextRes(R.string.settings_developer_speed_measured, listOf(formatSpeed(deviceSpeedFactor, locale)))
}

/** The snackbar after a re-measure; [factor] is the new speed factor, null when the probe failed. */
fun remeasuredMessage(factor: Double?, locale: Locale = Locale.getDefault()): TextRes =
    if (factor == null) {
        TextRes(R.string.settings_developer_speed_failed)
    } else {
        TextRes(R.string.settings_developer_speed_done, listOf(formatSpeed(factor, locale)))
    }
