package app.marmalade.stt.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.marmalade.stt.R
import app.marmalade.stt.textproc.TextProcConfig
import app.marmalade.stt.ui.components.SectionHeader
import app.marmalade.stt.ui.components.SettingsRow
import app.marmalade.stt.ui.components.SettingsSwitchRow

/**
 * Settings → Text cleanup: the `:textproc` switches and the rows into the
 * dictionary and snippet editors. Supporting lines condense
 * `textproc/README.md`. Each change is saved at once through the callbacks
 * (SettingsViewModel → TextProcSettings).
 *
 * #42: the phrases-in-silence filter is always on and has no row. Voice
 * commands and its Punctuation words sub-switch are one group; Punctuation
 * words is greyed out while Voice commands is off (it only applies with it,
 * `DataStoreTextProcSettings`). While the speech isn't English
 * ([TextProcConfig.isEnglish], the cleanup language) the English-only rules
 * are greyed out and say "English only."; they keep their setting.
 */
@Composable
internal fun CleanupSection(
    config: TextProcConfig,
    onRemoveFillersChange: (Boolean) -> Unit,
    onSpokenCommandsChange: (Boolean) -> Unit,
    onSpokenPunctuationChange: (Boolean) -> Unit,
    onNumbersAsDigitsChange: (Boolean) -> Unit,
    onCapitalizeSentencesChange: (Boolean) -> Unit,
    onOpenDictionary: () -> Unit,
    onOpenSnippets: () -> Unit,
) {
    val english = config.isEnglish

    @Composable
    fun englishRule(desc: Int): String =
        if (english) stringResource(desc) else stringResource(R.string.cleanup_english_only, stringResource(desc))

    SectionHeader(stringResource(R.string.cleanup_section))
    SettingsSwitchRow(
        label = stringResource(R.string.cleanup_fillers),
        supporting = englishRule(R.string.cleanup_fillers_desc),
        checked = config.removeFillers,
        onCheckedChange = onRemoveFillersChange,
        enabled = english,
    )
    SettingsSwitchRow(
        label = stringResource(R.string.cleanup_commands),
        supporting = englishRule(R.string.cleanup_commands_desc),
        checked = config.spokenCommands,
        onCheckedChange = onSpokenCommandsChange,
        enabled = english,
    )
    SettingsSwitchRow(
        label = stringResource(R.string.cleanup_punctuation),
        supporting = englishRule(R.string.cleanup_punctuation_desc),
        checked = config.spokenPunctuation,
        onCheckedChange = onSpokenPunctuationChange,
        enabled = english && config.spokenCommands,
        indent = true,
    )
    SettingsSwitchRow(
        label = stringResource(R.string.cleanup_itn),
        supporting = englishRule(R.string.cleanup_itn_desc),
        checked = config.numbersAsDigits,
        onCheckedChange = onNumbersAsDigitsChange,
        enabled = english,
    )
    SettingsSwitchRow(
        label = stringResource(R.string.cleanup_capitalize),
        supporting = stringResource(R.string.cleanup_capitalize_desc),
        checked = config.capitalizeSentences,
        onCheckedChange = onCapitalizeSentencesChange,
    )
    val rules = config.replacements.size
    SettingsRow(
        label = stringResource(R.string.cleanup_dictionary),
        supporting = if (rules == 0) {
            stringResource(R.string.cleanup_dictionary_desc_none)
        } else {
            pluralStringResource(R.plurals.cleanup_dictionary_desc, rules, rules)
        },
        modifier = Modifier.clickable(role = Role.Button, onClick = onOpenDictionary),
    )
    val snippets = config.snippets.size
    SettingsRow(
        label = stringResource(R.string.cleanup_snippets),
        supporting = if (snippets == 0) {
            stringResource(R.string.cleanup_snippets_desc_none)
        } else {
            pluralStringResource(R.plurals.cleanup_snippets_desc, snippets, snippets)
        },
        modifier = Modifier.clickable(role = Role.Button, onClick = onOpenSnippets),
    )
}
