# Czech (cs) glossary

## Register

Formal **vy** with lowercase *vy / váš*, as Android's Czech UI addresses the
user ("Klepněte na OK", "Zkuste to znovu"). Buttons use the infinitive
("Stáhnout", "Zrušit", "Povolit mikrofon"); instructions in running text use the
vy imperative ("Klepněte na mikrofon", "Vyberte Marmalade STT"); errors use
"… se nepodařilo …" / "… nelze …" as Android does; status lines use nouns or
the app speaking in the first person ("Načítání modelu…", "Poslouchám",
"Slyším vás…"), like Google's Czech voice typing.

**No gendered second-person past tense.** Formal Czech marks the user's gender
in the past participle ("jste domluvil / domluvila"). Strings avoid it with
present or future tense or impersonal forms: "že končíte", "kdy domluvíte", "až s ní
něco napíšete", "na to, co máte na mysli", "Vyslovený text". The app as
subject is written "Aplikace Marmalade STT … slyšela" or in tenses without
gender ("Marmalade STT vás neuslyší").

Typographic quotes „…“ (also `quoted_name`); `list_separator` stays ", ".
Decimals in literal text use a comma ("0,6 až 3 GB"). No non-breaking spaces
after one-letter prepositions (Android's Czech strings don't use them either).

Plurals: `one` (1 jazyk), `few` (2–4 jazyky), `many` (fractions, genitive
singular: 1,5 jazyka; never shown for whole counts but required by CLDR),
`other` (0, 5+: jazyků). Plural sentences are phrased so the verb works for
every form ("Přeskočí se 2 řádky / 5 řádků").

## Terms

| English | Czech | Notes |
|---|---|---|
| Dictate (tab, screen) | Diktování (karta / obrazovka Diktování) | Noun |
| Engines (tab) | Moduly (karta Moduly) | Android Czech calls a TTS engine "modul" (Preferovaný modul); short for a tab |
| Settings (tab, title) | Nastavení | |
| tab | karta | |
| dictation / dictate | diktování / diktovat | |
| engine / speech engine | modul / řečový modul | |
| model / speech model | model / řečový model | |
| language model (Qwen3 decoder) | jazykový model | distinct from "řečový model" |
| download (verb / noun) | stáhnout / stahování | "Velikost stahování", "Stažení selhalo" |
| voice keyboard | hlasová klávesnice | |
| voice input / voice typing | hlasové zadávání | Android Czech uses one term ("Hlasové zadávání Google"); FUTO's Czech too |
| Marmalade voice typing (IME name) | Hlasové zadávání Marmalade | mirrors "Hlasové zadávání Google" |
| speech input (apps asking Android) | rozpoznávání řeči | |
| speech recognition service | služba rozpoznávání řeči | #61; also the summary row |
| speech (settings section) | Řeč | |
| End of speech | Konec promluvy | |
| recognition language | jazyk rozpoznávání | |
| speak-now popup | okno „Mluvte“ | "Mluvte" = `ime_speak_now` / `recognizer_speak_now` |
| text cleanup | úprava textu | |
| filler words | výplňková slova | |
| voice commands | hlasové příkazy | |
| punctuation words | interpunkce slovy | |
| replacement dictionary | slovník náhrad | |
| rule | pravidlo | Add rule = Přidat pravidlo |
| snippet | úryvek | Add snippet = Přidat úryvek |
| trigger phrase | spouštěcí fráze | |
| end word | koncové slovo | |
| stops (End of speech descriptions) | přestane poslouchat | never a bare transitive "zastaví" without an object |
| auto-stop | automaticky zastavit | Auto-stop after a pause = Automaticky zastavit po pauze; Auto-stop with Smart Turn = Automaticky zastavit pomocí Smart Turn |
| Tap to stop / Say an end word | Zastavit klepnutím / Vyslovit koncové slovo | infinitives, like the other options |
| Pause length | Délka pauzy | |
| patience (Smart Turn): Quick / Normal / Relaxed | trpělivost: Rychlá / Normální / Klidná | feminine, agrees with "trpělivost" |
| lock / locked (hold the mic) | zamknout / Zamknuto | as messaging apps word a locked voice recording |
| setup guide | průvodce nastavením | |
| Developer engines | Moduly pro vývojáře | |
| speed tiles: Fastest / Very fast / Fast / Moderate / Slow | Nejvyšší / Velmi vysoká / Vysoká / Střední / Nízká | feminine, under the tile header "Rychlost"; the full phrases stay masculine ("Rychlý na tomto telefonu", the engine) |
| quality: Best / Great / Good / Fair / Basic | Nejlepší / Výborná / Dobrá / Ucházející / Základní | feminine (kvalita) |
| take (benchmark) | záznam | |
| unload | uvolnit / uvolnění | |
| benchmark | benchmark | developer screen |
| mic key (a keyboard's) | tlačítko mikrofonu | #61 |
| privacy policy | zásady ochrany soukromí | |
| analytics | analytika | |
| About | O aplikaci | |
| Support / Report a bug / More Marmalade | Podpora / Nahlásit chybu / Další od Marmalade | |
| Light / Dark / System | Světlý / Tmavý / Systém | masculine (režim) |
| Wallpaper / Midnight / Forest / Berry | Tapeta / Půlnoc / Les / Lesní plody | |
| high contrast (the colour preset, #68) | Vysoký kontrast (switch: “Používat barvy s vysokým kontrastem”) | matches AOSP `accessibility_toggle_high_text_contrast_preference_title` (Android 16 QPR2 source, not checked on a device) |

Language names in placeholders: Android's Czech display names are lowercase
nominative nouns ("angličtina", "japonština") and can't be inflected, while
Czech needs the accusative or genitive after almost every verb and preposition
("nepřepisuje japonštinu", "pro japonštinu"). So every slot puts the name after
a colon or in parentheses: "Nepřepisuje jazyk: %1$s", "Jazyk testu: %1$s",
"…, jazyk: %3$s", "pro váš jazyk (%1$s)", "Automaticky (%1$s)". Never "pro
%1$s", "v %1$s" or "nepřepisuje %1$s" (#67's bug class).

## Kept untranslated

Marmalade, Marmalade STT, all engine and model names (Parakeet Ultra, Parakeet
110M, Parakeet Japanese, Canary 180M Flash, Nemotron 3.5, Whisper Large v3
Turbo, Granite Speech, Qwen3-ASR, and upstream names in descriptions such as
Parakeet TDT 0.6B v3 and large-v3-turbo), Smart Turn, FUTO Keyboard, GitHub
(declined as "na GitHubu"), GitHub Sponsors, Silero VAD, ONNX Runtime, CPU,
LibriSpeech, FLEURS, AMI, NVIDIA, OpenAI, IBM, Alibaba (written "od společnosti
Alibaba" to keep the name intact), Moondream, WER, RTF, RSS, CTC, PRIVACY.md.
Also kept: benchmark, encoder-decoder, Markdown, spinning (developer screen),
offline, open source, the English spoken words in the cleanup and end-word
descriptions, and the "teh"/"tehran" dictionary example (an English misspelling;
the rule it shows holds in any language). The snippet example was adapted:
„můj e-mail“ matches „Můj e-mail“ but not „můj e-mailový“.

## External labels named

| Label (English) | Czech used | Status |
|---|---|---|
| Android: Settings | Nastavení | verified (AOSP 16 QPR2 `settings_label`) |
| Android: Apps | Aplikace | verified (AOSP `apps_dashboard_title`) |
| Android: Permissions (app info) | Oprávnění | verified (AOSP `permissions_label`) |
| Android: System | Systém | verified (AOSP `header_category_system`) |
| Android: Keyboard (Android 14+) | Klávesnice | verified in AOSP 16 QPR2 (`keyboard_settings`); Pixel overlay not checked on a device |
| Android: On-screen keyboard | Softwarová klávesnice | verified in AOSP (`virtual_keyboard_category`); Pixel overlay not checked on a device |
| Android: keyboard settings (generic) | nastavení klávesnic Androidu | descriptive, not a label |
| Android: OK (second keyboard dialog) | OK | verified (AOSP `dlg_ok`) |
| Android: home screen | plocha | unverified (common Pixel/Android Czech wording; not looked up) |
| Android: navigation bar keyboard button | tlačítko klávesnice na navigační liště | descriptive, not a label |
| Google: "Google voice typing" | Hlasové zadávání Google | unverified (Gboard's Czech label not checked) |
| FUTO Keyboard: Settings | Nastavení | verified loosely (FUTO translations `core/values-cs`, `keyboard_language_settings`) |
| FUTO Keyboard: Voice Input | Hlasové zadávání | verified (FUTO keyboard-translations-public `core/values-cs/strings-uix.xml`, `voice_input_settings_title`); the installed build not checked on a device |
| FUTO Keyboard: "Disable built-in voice input" | „Vypnout zabudované hlasové zadávání“ | verified (same source, `voice_input_settings_disable_builtin_voice_input`, same caveat) |
| FUTO Keyboard: "Use voice input provided by external app" | „Použít hlasové zadávání poskytnuté externí aplikací“ | verified (same source, `…_subtitle`, same caveat) |
| Android: System → Languages (Android 14–16) | Jazyky | verified (AOSP 16 QPR2 `languages_settings`) |
| Android: Voice input (also the search term) | Hlasové zadávání | verified (AOSP 16 QPR2, AOSP 13, AOSP 12 `voice_input_settings_title`) |
| Android: Languages & input (Android 13) | Jazyky a zadávání | verified (AOSP 13 `language_settings`) |
| Android: Apps & notifications (Android 9–11) | Aplikace a oznámení | verified (AOSP 9, 10, 11 `app_and_notification_dashboard_title`) |
| Android: Advanced (the collapsed group on Apps & notifications, Android 9–11) | Rozšířená nastavení | verified in source (preference library `expand_button_title`: Android 9 tag for 9; AndroidX `androidx-preference-release` for 10–11, whose exact bundled version wasn't checked) |
| Android: Default apps (Android 9–12) | Výchozí aplikace | verified (AOSP 9–12 Settings `app_default_dashboard_title`, the row tapped) |
| Android: Assist & voice input (Android 9) | Asistence a hlasové zadávání | verified (AOSP 9 `assist_and_voice_input_title`) |
| Android: Assist app (Android 9–10) | Asistenční aplikace | verified (AOSP 9 `default_assist_title`; Android 10 Default apps row: PermissionController `role_assistant_short_label`; Android 10 page row: AOSP 10 `default_assist_title`) |
| Android: Digital assistant app (Default apps row, Android 11–12) | Aplikace digitálního asistenta | verified (PermissionController 11, 12 `role_assistant_short_label`) |
| Android: Default digital assistant app (Android 11) | Výchozí aplikace dig. asistenta | verified (AOSP 11 `default_digital_assistant_title`) |
| Android: None (assistant picker) | Žádné | verified (AOSP 9 `app_list_preference_none`; PermissionController 10, 11 `default_app_none`) |
| Android: Language & region (Android 16 where present, 17) | Jazyk a oblast | verified (AOSP 17 `language_and_region_settings`) |
| Android: Speech (row of Language & region, Android 17) | Řeč | verified (AOSP 17 `speech_preference_title`); on Android 13–16 Speech is a heading and isn't named |
| Android: Force stop (written as a verb phrase) | Vynutit ukončení („vynutíte ukončení“) | verified (AOSP 16 QPR2 `force_stop`) |
| Fossify Keyboard: Voice typing method | Způsob psaní hlasem | verified (Fossify `voice_typing_method`, main) |
| SwiftKey: Multi-modal voice typing | Multimodální psaní hlasem | from Microsoft's support page; in-app label unverified |
| SwiftKey: Rich input | Rich Input | from Microsoft's support page; in-app label unverified (the Czech page keeps the English name) |

Sources for the #79 rows: AOSP Settings `res/values-cs/strings.xml` on the `android-9.0.0_r1` tag and the `android10-release` … `android17-release` branches; PermissionController's strings (Android 10–11: `platform/packages/apps/PackageInstaller`, Android 12: `platform/packages/modules/Permission`), which label the Default apps rows and the assistant picker on 10–12; the preference library's `expand_button_title` (`platform/frameworks/support`). Not checked against a phone maker's overlay on a device.

Sources: AOSP Settings strings at https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/android16-qpr2-release/res/values-cs/strings.xml ("AOSP 16 QPR2", the Android 14+ path), the same file on `android13-release` ("AOSP 13") and `android12-release` ("AOSP 12"); FUTO's labels at https://gitlab.futo.org/keyboard/keyboard-translations-public/-/blob/master/core/values-cs/strings-uix.xml (master); Fossify Keyboard's `voice_typing_method` at https://github.com/FossifyOrg/Keyboard/blob/main/app/src/main/res/values-cs/strings.xml (main branch; the installed release not checked on a device); SwiftKey's labels from Microsoft's support page https://support.microsoft.com/cs-cz/swiftkey-keyboard/how-to-disable-multimodal-voice-dictation-in-swiftkey (machine-translated by Microsoft, so the in-app label is not checked). AOSP labels are not checked against a Pixel's overlay on a device.

Android, FUTO and this app all say „Hlasové zadávání“ for voice input, so the
Android menu path, FUTO's menu and the IME name share the word.
