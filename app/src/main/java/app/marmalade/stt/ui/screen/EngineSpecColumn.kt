package app.marmalade.stt.ui.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.marmalade.stt.R
import app.marmalade.stt.install.EngineLanguages
import app.marmalade.stt.install.EngineQuality
import app.marmalade.stt.install.LanguageOptions
import app.marmalade.stt.install.QualityRating
import app.marmalade.stt.install.SpeedTier
import app.marmalade.stt.install.SttEngineDescriptor
import app.marmalade.stt.ui.rememberUiLocale
import app.marmalade.stt.ui.theme.accent
import app.marmalade.stt.ui.theme.isDark
import java.text.Collator
import java.util.Locale

// -----------------------------------------------------------------------------
// The engine specs, after marmalade-tts-android's EngineSpecColumn: SPEED /
// QUALITY / LANGUAGES, the same three axes everywhere so engines compare
// straight down the list (#27; Max's lab picks, 2026-09-28).
//
//   SPEED     ◄── SpeechPrefs.speedTier(engine) (SpeedBudget.speedTier at this
//                 phone's budget and threads): a five-segment meter for THIS
//                 phone (5 = fastest, as many segments as QUALITY, Max
//                 2026-10-10); the tier's phrase is its
//                 contentDescription (speedMeterRes). SLOW is over the budget;
//                 the card and row say "May be slow on this phone"
//                 (EngineFact.MayBeSlow), so its meter says just "Slow".
//   QUALITY   ◄── EngineQuality.rating(engine, language): a five-segment
//                 meter, one per star (#67). `language` is the one the user
//                 dictates in (#76), passed in by the caller:
//                 ratingLanguage(prefs, system locale) = Settings →
//                 Recognition language, or on Auto the UI locale (the
//                 onboarding card: the recommendation's language, the same
//                 rule). Measured in that language when the engine is
//                 offered for it, else in English, else in the engine's main
//                 language (Parakeet Japanese: Japanese), with "Tested in
//                 English" / "Tested in Japanese" under it then; only
//                 languages the engine is offered for count (#50). "Not
//                 rated" for an engine measured in none. qualityLabel's word
//                 ("Great (English)") is the meter's contentDescription; the
//                 A3 rows show it after the meter (rows have no labels, so the
//                 word tells their two meters apart), the tiles the word under
//                 the meter with "Tested in English" below it.
//   LANGUAGES ◄── EngineLanguages.of(engine) → languagesLabel: the language's
//                 name, or a count ("See all 25" on the card, which opens a
//                 dialog of the names).
//
//   EngineSpecColumn  the A1 card's right-hand column (fixed width)
//   SpecTiles         the three as tiles: the B1 dialog and C1 onboarding card
//   At 150 % font size and up (stackSpecs, #68) the column becomes a
//   full-width row under the card's text and the tiles stack, so the
//   uppercase labels and the values don't break mid-word.
//   SpeedMeter / QualityMeter  the markers, also on the A3 rows
//
// Meter colour is a traffic light keyed to the share of segments lit
// (meterBand): all of them green, at least 3/5 lime, at least 2/5 amber, else
// red. Both meters have five segments: 5 green, 4 and 3 lime, 2 amber, 1 red.
// Quality has five because every star level is in use and the recommendation
// turns on neighbouring ones (Hindi: Nemotron 3.5 Good against turbo Basic;
// Spanish: Ultra Best against Canary Great); speed has five to match it (#80).
// Shades are picked per mode (the TTS app's values). It never
// uses the brand orange, which the Download CTA keeps.
// The label choices are plain functions so they are unit-tested.
// -----------------------------------------------------------------------------

/** A quality word ([word], e.g. "Great") and the language it was measured in when that isn't the user's. */
data class QualityLabel(@StringRes val word: Int, val measuredIn: String?)

/**
 * [rating] in words, for a user who dictates in [userLanguage] (a BCP-47 tag
 * or ISO 639 code, [ratingLanguage]): TalkBack's and the tiles' text.
 */
fun qualityLabel(rating: QualityRating?, userLanguage: String): QualityLabel {
    if (rating == null) return QualityLabel(R.string.engine_quality_not_rated, measuredIn = null)
    val word = when (rating.stars) {
        5 -> R.string.engine_quality_best
        4 -> R.string.engine_quality_great
        3 -> R.string.engine_quality_good
        2 -> R.string.engine_quality_fair
        else -> R.string.engine_quality_basic
    }
    return QualityLabel(word, rating.language.takeIf { it != EngineLanguages.normalize(userLanguage) })
}

/**
 * Where [rating] comes from, for the info dialog: [text] with the test
 * [sets] (when it names two) and then the [language] (null: none).
 */
data class QualitySource(@StringRes val text: Int, val language: String?, val sets: List<String> = emptyList())

fun qualitySource(rating: QualityRating?): QualitySource {
    if (rating == null) return QualitySource(R.string.engine_quality_source_none, null)
    if (rating.language == "en") return QualitySource(R.string.engine_quality_source_english, null)
    val sets = EngineQuality.SCORES[rating.language]?.sets.orEmpty()
    return if (sets.size == 2) {
        QualitySource(R.string.engine_quality_source_two_sets, rating.language, sets)
    } else {
        QualitySource(R.string.engine_quality_source_fleurs, rating.language)
    }
}

/** The LANGUAGES value: one language's name, or a count that opens the list. */
sealed interface LanguagesLabel {
    data class One(val code: String) : LanguagesLabel

    data class Many(val count: Int) : LanguagesLabel
}

fun languagesLabel(codes: Set<String>): LanguagesLabel =
    if (codes.size == 1) LanguagesLabel.One(codes.single()) else LanguagesLabel.Many(codes.size)

/**
 * [codes] ordered by their names in [locale], for Settings → Recognition
 * language and the info dialog's per-engine language ([sortedLanguageChoices]).
 * The [Collator] puts "Árabe" before "Vietnamita" and orders kana,
 * Devanagari and Han names, which a case-insensitive code-point compare
 * doesn't (#51).
 */
fun sortedLanguageCodes(codes: Collection<String>, locale: Locale): List<String> =
    codes.sortedWith(compareBy(Collator.getInstance(locale)) { languageName(it, locale) })

/**
 * An engine's language choices ([LanguageOptions.codes]) in picker order:
 * Auto-detect first, then the languages by name in [locale] (#89).
 */
fun sortedLanguageChoices(codes: List<String>, locale: Locale): List<String> {
    val (auto, languages) = codes.partition { it == LanguageOptions.AUTO }
    return auto + sortedLanguageCodes(languages, locale)
}

/** [codes] as names in [locale], in its alphabetical order, for the languages dialog and the info dialog. */
fun sortedLanguageNames(codes: Collection<String>, locale: Locale): List<String> =
    codes.map { languageName(it, locale) }.sortedWith(Collator.getInstance(locale))

/** The phrase for [tier] on this phone ("Fast on this phone"); for an unmeasured engine, "Not measured". */
@StringRes
fun speedTierRes(tier: SpeedTier?): Int = when (tier) {
    SpeedTier.FASTEST -> R.string.engine_speed_tier_fastest
    SpeedTier.VERY_FAST -> R.string.engine_speed_tier_very_fast
    SpeedTier.FAST -> R.string.engine_speed_tier_fast
    SpeedTier.MODERATE -> R.string.engine_speed_tier_moderate
    SpeedTier.SLOW -> R.string.engine_badge_may_be_slow_long
    null -> R.string.engine_speed_unmeasured
}


/**
 * The speed meter's TalkBack text: [speedTierRes]'s phrase, but just "Slow"
 * for [SpeedTier.SLOW], whose "May be slow on this phone" the pill or the
 * row's facts beside the meter already say (#68: it was read twice).
 */
@StringRes
fun speedMeterRes(tier: SpeedTier): Int = if (tier == SpeedTier.SLOW) speedWordRes(tier) else speedTierRes(tier)

/** Whether the font is large enough (150 % and up) to stack the specs rather than squeeze them (#68). */
@Composable
fun stackSpecs(): Boolean = LocalDensity.current.fontScale >= STACK_FONT_SCALE

/** The tier as one word for a tile ("Fastest"); for an unmeasured engine, "Not measured". */
@StringRes
fun speedWordRes(tier: SpeedTier?): Int = when (tier) {
    SpeedTier.FASTEST -> R.string.engine_speed_word_fastest
    SpeedTier.VERY_FAST -> R.string.engine_speed_word_very_fast
    SpeedTier.FAST -> R.string.engine_speed_word_fast
    SpeedTier.MODERATE -> R.string.engine_speed_word_moderate
    SpeedTier.SLOW -> R.string.engine_speed_word_slow
    null -> R.string.engine_speed_unmeasured
}

/**
 * The A1 card's SPEED / QUALITY / LANGUAGES for [engine], whose tier on this
 * phone is [speedTier] ([app.marmalade.stt.data.SpeechPrefs.speedTier]),
 * rated for [language], the one the user dictates in ([ratingLanguage]).
 * Fixed width so the values line up from card to card; at large font sizes
 * ([stackSpecs]) a full-width row that wraps, for under the card's text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EngineSpecColumn(
    engine: SttEngineDescriptor,
    speedTier: SpeedTier?,
    language: String,
    modifier: Modifier = Modifier,
) {
    if (stackSpecs()) {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            EngineSpecs(engine, speedTier, language)
        }
    } else {
        Column(modifier = modifier.width(SpecColumnWidth)) {
            EngineSpecs(engine, speedTier, language)
        }
    }
}

@Composable
private fun EngineSpecs(engine: SttEngineDescriptor, speedTier: SpeedTier?, language: String) {
    SpecRow(label = stringResource(R.string.engine_detail_speed)) {
        SpeedValue(speedTier, modifier = Modifier.padding(top = 2.dp))
    }
    SpecRow(label = stringResource(R.string.engine_detail_quality)) {
        QualityValue(EngineQuality.rating(engine, language), language, modifier = Modifier.padding(top = 2.dp))
    }
    SpecRow(label = stringResource(R.string.engine_detail_languages)) {
        LanguagesValue(EngineLanguages.of(engine))
    }
}

/**
 * SPEED / QUALITY / LANGUAGES as three tiles in [tileColor]: the details
 * dialog and the setup guide's recommended engine. Each tile is one TalkBack
 * stop ("Speed, Fastest on this phone"). Side by side; stacked at large font
 * sizes ([stackSpecs]), where a third of a dialog broke words mid-way.
 * QUALITY is rated for [language], as on the card ([EngineSpecColumn]).
 */
@Composable
fun SpecTiles(
    engine: SttEngineDescriptor,
    speedTier: SpeedTier?,
    language: String,
    tileColor: Color,
    modifier: Modifier = Modifier,
) {
    if (stackSpecs()) {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SpecTileSet(engine, speedTier, language, tileColor, tile = Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpecTileSet(
                engine,
                speedTier,
                language,
                tileColor,
                tile = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

/** The three tiles, each laid out with [tile]. */
@Composable
private fun SpecTileSet(
    engine: SttEngineDescriptor,
    speedTier: SpeedTier?,
    language: String,
    tileColor: Color,
    tile: Modifier,
) {
    SpecTile(label = stringResource(R.string.engine_detail_speed), color = tileColor, modifier = tile) {
        SpeedValue(speedTier)
        if (speedTier != null) {
            // The meter already told TalkBack the tier.
            SpecValueText(
                text = stringResource(speedWordRes(speedTier)),
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clearAndSetSemantics {},
            )
        }
    }
    SpecTile(label = stringResource(R.string.engine_detail_quality), color = tileColor, modifier = tile) {
        val rating = EngineQuality.rating(engine, language)
        if (rating == null) {
            SpecValueText(qualityText(null, language))
        } else {
            // The words under it say the same; TalkBack hears them as one.
            QualityMeter(stars = rating.stars, modifier = Modifier.clearAndSetSemantics {})
            val label = qualityLabel(rating, language)
            val spoken = qualityText(rating, language)
            Column(modifier = Modifier.clearAndSetSemantics { contentDescription = spoken }) {
                SpecValueText(text = stringResource(label.word), modifier = Modifier.padding(top = 2.dp))
                // On its own small line, as on the card: "Best (английский)" broke inside
                // the language's name in a tile a third of the dialog wide (#69).
                label.measuredIn?.let { language ->
                    Text(
                        text = stringResource(R.string.engine_quality_tested_in, languageNameInText(language)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    SpecTile(label = stringResource(R.string.engine_detail_languages), color = tileColor, modifier = tile) {
        val codes = EngineLanguages.of(engine)
        when (val label = languagesLabel(codes)) {
            is LanguagesLabel.One -> SpecValueText(languageName(label.code))
            is LanguagesLabel.Many -> {
                // "25" reads as "25 languages" for TalkBack.
                val spoken = pluralStringResource(R.plurals.engine_languages_count, label.count, label.count)
                SpecValueText(
                    // In the locale's digits, like the numbers in the strings around it ("٢٥" in Arabic, #69).
                    text = String.format(Locale.getDefault(), "%d", label.count),
                    modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
                )
            }
        }
    }
}

@Composable
private fun SpecTile(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    value: @Composable () -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.small, color = color, modifier = modifier) {
        Column(
            modifier = Modifier
                .semantics(mergeDescendants = true) {}
                .padding(10.dp),
        ) {
            SpecLabel(label)
            Spacer(Modifier.height(4.dp))
            value()
        }
    }
}

/**
 * "Great", "Great (English)" or "Not rated" for a user who dictates in
 * [language] ([ratingLanguage]): the tiles' text and the meter's contentDescription.
 */
@Composable
fun qualityText(rating: QualityRating?, language: String): String {
    val label = qualityLabel(rating, language)
    val word = stringResource(label.word)
    return label.measuredIn?.let { stringResource(R.string.engine_quality_in_language, word, languageNameInText(it)) } ?: word
}

/** The meter, with the tier's phrase for TalkBack; "Not measured" for an engine without a measurement. */
@Composable
fun SpeedValue(tier: SpeedTier?, modifier: Modifier = Modifier) {
    if (tier == null) {
        SpecValueText(stringResource(R.string.engine_speed_unmeasured), modifier = modifier)
    } else {
        val phrase = stringResource(speedMeterRes(tier))
        SpeedMeter(tier = tier, modifier = modifier.semantics { contentDescription = phrase })
    }
}

/**
 * The card's QUALITY: the meter (TalkBack hears "Great (English)"), with
 * "Tested in English" under it when the rating comes from another language
 * than the user's; "Not rated" as text, so no rating never looks like a bad one.
 */
@Composable
private fun QualityValue(rating: QualityRating?, language: String, modifier: Modifier = Modifier) {
    if (rating == null) {
        SpecValueText(qualityText(null, language), modifier = modifier)
        return
    }
    val spoken = qualityText(rating, language)
    Column(modifier = modifier.clearAndSetSemantics { contentDescription = spoken }) {
        QualityMeter(stars = rating.stars)
        qualityLabel(rating, language).measuredIn?.let { language ->
            Text(
                text = stringResource(R.string.engine_quality_tested_in, languageNameInText(language)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Overline label above its value, as in the TTS app's spec sheet. */
@Composable
private fun SpecRow(label: String, value: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        SpecLabel(label)
        Spacer(Modifier.height(2.dp))
        value()
    }
}

@Composable
private fun SpecLabel(label: String) {
    Text(
        text = label.uppercase(rememberUiLocale()),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SpecValueText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

/**
 * One language's name, or "See all 25" with a chevron in the link colour,
 * which opens [LanguagesDialog]. For TalkBack it is plain text ("25
 * languages") that merges into the card: the click semantics are cleared
 * (clearAndSetSemantics before clickable), and the full list is in the
 * details dialog, which TalkBack reaches through the card's "Show more".
 */
@Composable
private fun LanguagesValue(codes: Set<String>) {
    when (val label = languagesLabel(codes)) {
        is LanguagesLabel.One -> SpecValueText(languageName(label.code))
        is LanguagesLabel.Many -> {
            var showDialog by rememberSaveable { mutableStateOf(false) }
            val spoken = pluralStringResource(R.plurals.engine_languages_count, label.count, label.count)
            val openLabel = stringResource(R.string.engine_languages_open)
            val ink = MaterialTheme.accent.ink
            // A 48 dp tap target (#68) pulled up by its own padding, so the
            // text still sits tight under its label like the other values.
            Box(
                modifier = Modifier
                    .offset(y = -LanguagesTapPadding)
                    .clip(RoundedCornerShape(8.dp))
                    .clearAndSetSemantics { contentDescription = spoken }
                    .clickable(onClickLabel = openLabel, role = Role.Button) { showDialog = true }
                    .fillMaxWidth()
                    .padding(vertical = LanguagesTapPadding),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.engine_languages_see_all, label.count),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ink,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = ink,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (showDialog) LanguagesDialog(codes = codes, onDismiss = { showDialog = false })
        }
    }
}

/** The engine's languages by name, alphabetical. Scrolls: Whisper turbo has 99. */
@Composable
private fun LanguagesDialog(codes: Set<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.engine_detail_languages), modifier = Modifier.semantics { heading() }) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                sortedLanguageNames(codes, rememberUiLocale()).forEach { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            EngineTextButton(label = stringResource(R.string.engine_dialog_close), onClick = onDismiss)
        },
    )
}

/** The five-segment speed meter: [SpeedTier.meterFill] segments lit in the traffic-light colour, the rest neutral. */
@Composable
fun SpeedMeter(tier: SpeedTier, modifier: Modifier = Modifier) {
    SegmentMeter(fill = tier.meterFill, segments = SPEED_SEGMENTS, modifier = modifier)
}

/** The five-segment quality meter, one segment per star (1–5). Callers give it its TalkBack text. */
@Composable
fun QualityMeter(stars: Int, modifier: Modifier = Modifier) {
    SegmentMeter(fill = stars, segments = QUALITY_SEGMENTS, modifier = modifier)
}

@Composable
private fun SegmentMeter(fill: Int, segments: Int, modifier: Modifier = Modifier) {
    val on = meterBandColor(meterBand(fill, segments), MaterialTheme.colorScheme.isDark)
    // Unlit segments are outlines, so "3 of 5" reads by shape, not only by colour (#68).
    val off = meterUnlitColor(MaterialTheme.colorScheme.isDark)
    val shape = RoundedCornerShape(2.dp)
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(segments) { i ->
            Box(
                modifier = Modifier
                    .size(width = 6.dp, height = 12.dp)
                    .then(if (i < fill) Modifier.background(on, shape) else Modifier.border(1.dp, off, shape)),
            )
        }
    }
}

/** A meter's colour band, from the share of its segments lit. */
enum class MeterBand { GREEN, LIME, AMBER, RED }

/** All segments lit: green; at least 3/5: lime; at least 2/5: amber; else red (see the header). */
fun meterBand(fill: Int, segments: Int): MeterBand = when {
    fill >= segments -> MeterBand.GREEN
    fill * 5 >= segments * 3 -> MeterBand.LIME
    fill * 5 >= segments * 2 -> MeterBand.AMBER
    else -> MeterBand.RED
}

/** The meter's red, for the "May be slow on this phone" warning icon. */
@Composable
fun slowColor(): Color = meterBandColor(MeterBand.RED, MaterialTheme.colorScheme.isDark)

/**
 * Semantic green / lime / amber / red, per mode so the meter reads on the
 * cream light surface and the stone dark one, and independent of the accent
 * (the dark values as marmalade-tts-android). A segment needs 3:1 against
 * the card: light mode uses the 700 shades (the 600s were 2.7–3.0:1, #68).
 * The red was the scheme's error red until #68 darkened that for text.
 */
internal fun meterBandColor(band: MeterBand, isDark: Boolean): Color = when (band) {
    MeterBand.GREEN -> if (isDark) Color(0xFF4ADE80) else Color(0xFF15803D) // green 400 / 700
    MeterBand.LIME -> if (isDark) Color(0xFFA3E635) else Color(0xFF4D7C0F) // lime 400 / 700
    MeterBand.AMBER -> if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309) // amber 400 / 700
    MeterBand.RED -> if (isDark) Color(0xFFEF4444) else Color(0xFFDC2626) // red 500 / 600
}

/**
 * An unlit segment's outline: stone 500 light, stone 400 dark, 3:1 on every
 * card (#68). Not the scheme's outline, whose light stone 400 is 2.3:1 on
 * cream (the shared design library's, #87).
 */
internal fun meterUnlitColor(isDark: Boolean): Color = if (isDark) Color(0xFFA8A29E) else Color(0xFF78716C)

private const val SPEED_SEGMENTS = 5
private const val QUALITY_SEGMENTS = 5
private val SpecColumnWidth = 118.dp
private val LanguagesTapPadding = 14.dp
private const val STACK_FONT_SCALE = 1.5f
