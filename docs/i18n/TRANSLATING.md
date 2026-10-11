# Translating Marmalade STT

The app's UI ships in English plus **de, es, fr, hi, it, ja, pt-BR and zh-CN**
(#51) and **ar, cs, id, ko, nl, pl, ru, sv, tr, uk, vi and zh-TW** (#69).
Every locale has every string. `TranslationsTest`
(`app/src/test/java/app/marmalade/stt/i18n/`) and lint's MissingTranslation
fail the build when one is missing or broken.

## Files

- English source: `app/src/main/res/values/strings*.xml`.
- Each locale: `app/src/main/res/values-<qualifier>/` with the **same file
  names** and the same keys in the same order (`de`, `es`, `fr`, `hi`, `it`,
  `ja`, `pt-rBR`, `zh-rCN`, `ar`, `cs`, `in`, `ko`, `nl`, `pl`, `ru`, `sv`,
  `tr`, `uk`, `vi`, `zh-rTW`). Indonesian's qualifier is Android's legacy
  `in`; its BCP-47 tag (`ui_locale`, `locales_config.xml`) is `id`.
- Strings marked `translatable="false"` (brand names, the wordmark, a
  separator format) are **not** copied into locale files.
- One glossary per locale: `docs/i18n/glossary/<qualifier>.md`. It records the
  register, the fixed terms, and every label of another app or of Android
  that the translation names and couldn't be checked against that app.
- Second-opinion reviews (Fable, per locale; #51, the listing rounds and
  #69), with the rejected fixes and why: `docs/i18n/review-2026-10-fable.md`.
- Spot-check page: `docs/design/i18n-lab.html`, English and every locale per
  key. Rebuild it after a change: `python3 tools/i18n/build_lab.py`. Arabic's
  right-to-left screens: `docs/design/rtl-audit.html` (#69).

## Adding a locale

Add the qualifier to `androidResources.localeFilters`, `res/xml/locales_config.xml`
and `LOCALES` in `TranslationsTest` (with its CLDR plural categories), and
set `ui_locale` in the new `strings.xml` to the locale's BCP-47 tag (`pt-BR`
for `values-pt-rBR`, `id` for `values-in`): language names and their sorting
follow it. Also add it to `LOCALES`/`NAMES` in `tools/i18n/build_lab.py` and,
with its store folder, to `LOCALES` in `tools/release/check_listing.py`.

## Adding or changing an English string

Add or change it in every `values-<qualifier>` file too, in the same file
and position, then run `./gradlew :app:testFdroidDebugUnitTest --tests '*TranslationsTest*'`.
A changed meaning needs a new translation, not just the old one kept.

## Rules

**Placeholders.** Keep every `%1$s`, `%2$d`, `%1$.1f`, `%d` and `%%`
exactly; their order in the sentence may change. Locale number formatting
(decimal commas) comes from the formatter, not the string. A placeholder
that holds a size ("Download · %1$s") gets the number with the locale's
unit, as Android's storage screens write it (`ByteFormat`, from ICU:
"671 Mo", "671 МБ", "٦٧١ م.ب"); a size written into a sentence uses the
same unit (fr "3 Go", ru "3 ГБ").

**Plurals.** Use the language's CLDR cardinal categories, every item keeping
the number placeholder:
de, hi, nl, sv, tr: `one`, `other` · es, fr, it, pt-BR: `one`, `many`,
`other` · ru, uk, pl, cs: `one`, `few`, `many`, `other` · ar: `zero`, `one`,
`two`, `few`, `many`, `other` · ja, zh-CN, zh-TW, ko, vi, id: `other` only.
In es, fr, it and pt `many` is the form a language uses for a million of
something, written with digits (es "1000000 de idiomas", fr "1000000 de
règles", it "1000000 di regole", pt "1000000 de idiomas"), as in
marmalade-tts-android; in ru, uk and pl it is 5–20 ("5 языков") and
`other` the fraction form; in cs `many` is the fraction form.

**A number in a plain string.** A `<string>` with `%1$d` can't agree with its
number in ru, uk, pl, cs or ar ("%1$d percent" was removed in #69 for this).
Give a counted word a `<plurals>`, or write the number without a counted
word ("41%", cs "Všechny (%1$d)").

**Escaping.** An apostrophe is `\'` (or the typographic `’`), a straight
double quote `\"`. Prefer the language's typographic quotation marks: de
„…“, fr « … » (with `&#160;` inside), es/it/pt-BR “…” , ja 「…」, zh-CN “…”,
ru/uk/ar «…», pl „…”, cs „…“, sv ”…”, nl/ko/vi/tr/id “…”, zh-TW 「…」.
French puts `&#160;` before `:` `;` `?` `!` as in the existing strings.

**Right to left (ar).** Compose lays each string out as a right-to-left
paragraph. Don't start a string with a Latin name if a natural order avoids
it. Menu paths use ← (Settings ← System …). A quoted Latin run that mixes
digits or punctuation with letters reorders ("pm 9:30"): put a left-to-right
isolate inside the quotes, written `«&#x2066;9:30 pm&#x2069;»`; pure words
and names ("Nemotron 3.5") need none. Literal numbers in Arabic prose use
Arabic-Indic digits (٢٥, ٠٫٦), as the app's number formatting does on an
Arabic phone; names, versions, the quoted English examples and
placeholders keep theirs. Text that isn't the app's own words (transcripts,
text fields, the English policy and licences) gets its direction from its
own script in code (`ui/Bidi.kt`), not from the strings.

**Keep untranslated:** Marmalade, Marmalade STT, every engine and model name
(Parakeet Ultra, Parakeet 110M, Parakeet Japanese, Canary 180M Flash,
Nemotron 3.5, Whisper Large v3 Turbo, Granite Speech, Qwen3-ASR, and the
upstream names the descriptions use, such as Parakeet TDT 0.6B v3), Smart Turn, FUTO
Keyboard, GitHub, GitHub Sponsors, Silero VAD, ONNX Runtime, CPU, the test
sets (LibriSpeech, FLEURS, AMI), company names (NVIDIA, OpenAI, IBM, Alibaba,
Moondream), the abbreviations WER, RTF, RSS, CTC, and `PRIVACY.md`.

**Spoken words stay English.** Filler removal, voice commands, punctuation
words, numbers as digits and the end word only work on English speech; the
app prefixes those rules with "English only." when the speech isn't
English. So in `cleanup_fillers_desc`, `cleanup_commands_desc`,
`cleanup_punctuation_desc`, `cleanup_itn_desc` and `settings_end_word_desc`
the quoted words (“new line”, “um”, “over”, “Twenty five percent” → “25%”…)
stay exactly as in English, in the locale's quotation marks; only the
sentence around them is translated. The dictionary and snippet help texts
explain matching rules: their examples may be adapted, but the rule they
illustrate must stay true.

**Cross-references.** A string that names another label of this app (the
tabs Dictate, Engines and Settings; Pause length; Auto-stop after a pause;
Recognition language; Developer engines; Add rule; Add snippet; Marmalade
voice typing; Setup guide) uses that label's translation word for word.
Android's own settings (Apps, Permissions, Settings → System → Keyboard →
On-screen keyboard) use the wording a Pixel shows in that language. FUTO
Keyboard's settings (Voice Input, "Disable built-in voice input") use FUTO's
own translation where known; otherwise translate them and list them in the
glossary as unverified.

**Privacy.** `onboarding_privacy_body` says exactly what the English says,
no more and no less (it must match PRIVACY.md). `about_privacy_english_note`
is empty in English; each locale says: "This policy is in English. Summary:
speech is recognized on your phone, never by a cloud or online speech
service. Audio is never saved or uploaded. No analytics, tracking or
accounts. The internet is used only to download speech models." Nothing
beyond what PRIVACY.md states.

**Language names in sentences.** A placeholder holding a language name
gets ICU's bare dictionary form in the UI's language (`languageNameInText`):
ru "японский", pl "japoński", uk "японська", cs "japonština", tr "Japonca",
id "Jepang" (no "bahasa"), vi "Tiếng Nhật" (capital T), ar "اليابانية". It
can't be inflected, so put it where that form is grammatical: after a
colon, in parentheses, after the word for "language", before a postposition
or noun (ko, tr), never with a consonant-dependent Korean particle or a
Turkish suffix after it. Each glossary says how its locale does it.

**Click labels.** `a11y_open_in_browser` and `cleanup_edit_action` are
click labels: TalkBack slots them into its own hint, in the phone's
language ("Double-tap to %s"). Write the form that fits after that
template: pl "Kliknij dwukrotnie, aby …" takes an infinitive (Otworzyć,
Edytować, not Otwórz); de "Zum … doppeltippen" a noun (Öffnen im Browser,
Bearbeiten); ko "…하려면 두 번 탭하세요" a noun that takes 하다 (수정,
브라우저로 이동). ru/uk, fr, es, pt, sv, nl, vi and id take an infinitive or
bare verb; ja the dictionary form. TalkBack 15's templates are in its APK
(`template_custom_hint_for_actions`).

**Tone and length.** Plain, friendly and short, like the English. Buttons,
tabs, pills and status lines stay short; if a natural translation is much
longer than the English, find a shorter natural one. Register: de *du*, es
*tú*, fr *vous*, hi *आप*, it *tu*, ja です・ます, pt-BR *você*, zh-CN *你*
(matching marmalade-tts-android where it has the same locale), ru/uk *вы/ви*,
pl informal (*Kliknij*), cs *vy*, sv *du*, nl *je*, ko 합니다/하세요, zh-TW
*你*, vi *bạn*, ar Modern Standard Arabic, tr *siz*, id *Anda*.

## Store listing (fastlane)

The F-Droid listing lives in `fastlane/metadata/android/<locale>/`, in the
locale folders marmalade-tts-android uses: `en-US` plus `de-DE`, `es-ES`,
`fr-FR`, `hi-IN`, `it-IT`, `ja-JP`, `pt-BR` and `zh-CN` (glossaries
`de`, `es`, `fr`, `hi`, `it`, `ja`, `pt-rBR`, `zh-rCN`), and Google Play's
codes for the #69 locales: `ar`, `cs-CZ`, `id`, `ko-KR`, `nl-NL`, `pl-PL`,
`ru-RU`, `sv-SE`, `tr-TR`, `uk`, `vi`, `zh-TW`. F-Droid shows the English
text where a file is missing.

- **Only approved English is translated.** Store text is public copy: a
  file enters `en-US` once the maintainer has signed it off, and a later
  change to the English needs a new sign-off before it is re-translated.
- Each locale has the same files as `en-US`: `title.txt` stays
  "Marmalade STT"; `short_description.txt` (max 80 characters, one line);
  `changelogs/<versionCode>.txt` (max 500; one per APK versionCode, arm64
  and armv7 carry the same text); `full_description.txt` (max 4000, the
  same HTML tags in the same order as the English).
- The app's rules above apply: register, the glossary's terms, names kept
  in English, the app's own translated labels word for word (tabs,
  settings, "End of speech" options), nothing PRIVACY.md doesn't say.
- Keep the facts and their order; shorten the wording, not the content, when
  a language runs long. Spoken examples stay English (the end word
  "over").
- Translate, then have a second reviewer read the English, the translation,
  the glossary and the locale's `strings*.xml`, and propose fixes without
  editing; apply or reject each one with a reason, as in
  `docs/i18n/review-2026-10-fable.md`.
- Check with `python3 tools/release/check_listing.py`: same files in every
  locale, lengths, the title, HTML structure and protected names.
