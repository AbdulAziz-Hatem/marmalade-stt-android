package app.marmalade.stt.ui.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.marmalade.stt.R
import app.marmalade.stt.data.SpeechPrefs
import app.marmalade.stt.speech.EndOfDictation
import app.marmalade.stt.speech.EndWords
import app.marmalade.stt.speech.ListenSurface
import app.marmalade.stt.speech.Patience
import app.marmalade.stt.speech.SessionOptions
import app.marmalade.stt.ui.components.MarmaladeTextButton
import app.marmalade.stt.ui.components.SectionHeader
import app.marmalade.stt.ui.components.SettingsRow
import app.marmalade.stt.ui.components.SettingsSliderRow
import app.marmalade.stt.ui.components.rememberAnnouncer
import app.marmalade.stt.ui.contentDirection
import kotlin.math.roundToInt

// Settings → End of speech (#43): a row per surface that has a choice (the
// voice keyboard and other apps each open a choice dialog; the Dictate
// screen's behaviour is fixed and not listed, #44), then the pause length
// (always: the Dictate screen uses it), Smart Turn's patience and the end
// words, each only while a surface uses that mode, and last the tip about
// holding the mic (#42).

@Composable
fun EndOfSpeechSection(
    prefs: SpeechPrefs,
    onKeyboardEndChange: (EndOfDictation) -> Unit,
    onAppsEndChange: (EndOfDictation) -> Unit,
    onMinSilenceChange: (Float) -> Unit,
    onPatienceChange: (Patience) -> Unit,
    onEndWordsChange: (String) -> Unit,
) {
    SectionHeader(stringResource(R.string.settings_end))
    EndChoiceRow(
        label = stringResource(R.string.settings_end_keyboard),
        choices = SpeechPrefs.KEYBOARD_ENDS,
        selected = prefs.keyboardEnd,
        onSelect = onKeyboardEndChange,
    )
    EndChoiceRow(
        label = stringResource(R.string.settings_end_apps),
        choices = SpeechPrefs.APPS_ENDS,
        selected = prefs.endFor(ListenSurface.APPS),
        onSelect = onAppsEndChange,
    )
    PauseSlider(prefs.minSilenceSeconds, onMinSilenceChange)
    if (prefs.usesSmartTurn) PatienceRow(prefs.patience, onPatienceChange)
    if (prefs.usesEndWord) EndWordsField(prefs.endWords, onEndWordsChange)
    Text(
        text = stringResource(R.string.settings_end_lock_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** A surface's end of speech: the chosen option, and a dialog of [choices]. */
@Composable
private fun EndChoiceRow(
    label: String,
    choices: List<EndOfDictation>,
    selected: EndOfDictation,
    onSelect: (EndOfDictation) -> Unit,
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    SettingsRow(
        label = label,
        supporting = stringResource(selected.labelRes()),
        modifier = Modifier.clickable(role = Role.Button) { choosing = true },
    )
    if (choosing) {
        ChoiceDialog(
            title = label,
            choices = choices,
            selected = selected,
            label = { stringResource(it.labelRes()) },
            description = { stringResource(it.descriptionRes()) },
            onSelect = {
                onSelect(it)
                choosing = false
            },
            onDismiss = { choosing = false },
        )
    }
}

@Composable
private fun PatienceRow(patience: Patience, onPatienceChange: (Patience) -> Unit) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(R.string.settings_patience)
    SettingsRow(
        label = title,
        supporting = stringResource(R.string.settings_patience_desc, stringResource(patience.labelRes())),
        modifier = Modifier.clickable(role = Role.Button) { choosing = true },
    )
    if (choosing) {
        ChoiceDialog(
            title = title,
            choices = Patience.entries,
            selected = patience,
            label = { stringResource(it.labelRes()) },
            description = { stringResource(it.descriptionRes()) },
            onSelect = {
                onPatienceChange(it)
                choosing = false
            },
            onDismiss = { choosing = false },
        )
    }
}

/** Single choice with a line of description under each option; tapping one saves it and closes the dialog. */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    choices: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    description: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, modifier = Modifier.semantics { heading() }) },
        text = {
            Column(
                modifier = Modifier
                    .selectableGroup()
                    .verticalScroll(rememberScrollState()),
            ) {
                for (choice in choices) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = choice == selected, role = Role.RadioButton, onClick = { onSelect(choice) })
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice == selected, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = label(choice), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = description(choice),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            MarmaladeTextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_dialog_cancel)) }
        },
    )
}

@StringRes
private fun EndOfDictation.labelRes(): Int = when (this) {
    EndOfDictation.TAP_STOP -> R.string.settings_end_tap
    EndOfDictation.SMART_TURN -> R.string.settings_end_smart_turn
    EndOfDictation.PAUSE -> R.string.settings_end_pause
    EndOfDictation.END_WORD -> R.string.settings_end_word
}

@StringRes
private fun EndOfDictation.descriptionRes(): Int = when (this) {
    EndOfDictation.TAP_STOP -> R.string.settings_end_tap_desc
    EndOfDictation.SMART_TURN -> R.string.settings_end_smart_turn_desc
    EndOfDictation.PAUSE -> R.string.settings_end_pause_desc
    EndOfDictation.END_WORD -> R.string.settings_end_word_desc
}

@StringRes
private fun Patience.labelRes(): Int = when (this) {
    Patience.QUICK -> R.string.settings_patience_quick
    Patience.NORMAL -> R.string.settings_patience_normal
    Patience.RELAXED -> R.string.settings_patience_relaxed
}

@StringRes
private fun Patience.descriptionRes(): Int = when (this) {
    Patience.QUICK -> R.string.settings_patience_quick_desc
    Patience.NORMAL -> R.string.settings_patience_normal_desc
    Patience.RELAXED -> R.string.settings_patience_relaxed_desc
}

@Composable
private fun PauseSlider(seconds: Float, onMinSilenceChange: (Float) -> Unit) {
    // The dragged value lives here until release, so a drag saves once.
    var minSilence by remember(seconds) { mutableFloatStateOf(seconds) }
    val shown = SettingsViewModel.snapToTenth(minSilence)
    SettingsSliderRow(
        label = stringResource(R.string.settings_speech_pause),
        supporting = stringResource(R.string.settings_speech_pause_desc),
        valueText = stringResource(R.string.settings_speech_pause_value, shown),
        valueDescription = stringResource(R.string.settings_speech_pause_state, shown),
        value = minSilence,
        onValueChange = { minSilence = it },
        // Saved only here, when the user lets go: someone who never moves it has no stored value and gets the default.
        onValueChangeFinished = { onMinSilenceChange(minSilence) },
        valueRange = SessionOptions.MIN_SILENCE_RANGE_START..SessionOptions.MIN_SILENCE_RANGE_END,
        steps = MIN_SILENCE_STEPS,
    )
}

/**
 * The end words, comma-separated. Saved when the field loses focus or on the
 * keyboard's Done. With nothing usable typed the stored words come back, and
 * TalkBack is told (#68): the field's text changing on its own says nothing.
 */
@Composable
private fun EndWordsField(words: List<String>, onEndWordsChange: (String) -> Unit) {
    val stored = EndWords.format(words)
    var text by remember(stored) { mutableStateOf(stored) }
    val focus = LocalFocusManager.current
    val announce = rememberAnnouncer()
    val revertedText = stringResource(R.string.a11y_end_words_reverted)
    fun save() {
        when {
            text == stored -> {}
            // Nothing usable typed: show the stored words again (the setting is never empty).
            EndWords.parse(text).isEmpty() -> {
                text = stored
                announce(revertedText)
            }
            else -> onEndWordsChange(text)
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(stringResource(R.string.settings_end_words)) },
        textStyle = LocalTextStyle.current.contentDirection(),
        supportingText = { Text(stringResource(R.string.settings_end_words_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .onFocusChanged { if (!it.isFocused) save() },
    )
}

/** 0.1 s steps between the ends of the range: 0.3, 0.4, … 1.5 has 11 stops in between. */
private val MIN_SILENCE_STEPS =
    ((SessionOptions.MIN_SILENCE_RANGE_END - SessionOptions.MIN_SILENCE_RANGE_START) * 10f).roundToInt() - 1
