# Dutch (nl) glossary

## Register

Informal **je/jij**, as Google's and Android's Dutch UI address the user ("Tik
op OK", "Probeer het opnieuw"). Buttons use the infinitive ("Downloaden",
"Annuleren", "Microfoon toestaan"); instructions in running text use the
imperative ("Tik op de microfoon", "Kies Marmalade STT"); errors start with
"Kan … niet …" as Android does ("Kan de instellingen niet openen"); status lines
use the third person or infinitives ("Luistert", "Model laden…",
"Downloaden"). Quotation marks are “…” (also `quoted_name`); `list_separator`
stays ", ". Apostrophes are written `\'` ("komma\'s"). Decimals in literal text
use a comma ("0,6 tot 3 GB").

"Phone" is **telefoon** throughout; "device" (toestel) only where the English
says device (store listing).

Plurals: `one`, `other`.

## Terms

| English | Dutch | Notes |
|---|---|---|
| Dictate (tab, screen) | Dicteren (tabblad Dicteren, scherm Dicteren) | |
| Engines (tab) | Engines (tabblad Engines) | de engine, as Android's Dutch ("Voorkeursengine") |
| Settings (tab, title) | Instellingen | |
| tab | tabblad | |
| dictation / dictate | dicteren | |
| engine / speech engine | engine / spraakengine | |
| model / speech model | model / spraakmodel | |
| language model (Qwen3 decoder) | taalmodel ("taalmodeldecoder") | |
| download (verb / noun) | downloaden / download | |
| voice keyboard | spraaktoetsenbord | |
| voice typing | spraakgestuurd typen | as Google's "Spraakgestuurd typen van Google" and Fossify's Dutch |
| Marmalade voice typing (IME name) | Spraakgestuurd typen van Marmalade | mirrors Google's IME name |
| voice input | spraakinvoer | Android's word ("Spraakinvoer") |
| speech input (apps asking Android) | spraakherkenning | |
| speech recognition service | spraakherkenningsservice | #61; also the summary row |
| speech (settings section) | Spraak | |
| End of speech | Einde van het spreken | |
| recognition language | herkenningstaal | |
| speak-now popup | het venster “Nu spreken” | "Nu spreken" = `ime_speak_now` / `recognizer_speak_now` |
| text cleanup | tekst opschonen | |
| filler words | stopwoordjes | everyday Dutch for "um", "eh" |
| voice commands | spraakopdrachten | |
| punctuation words | leestekens uitspreken | |
| replacement dictionary | vervangingswoordenboek | |
| rule (dictionary) | vervanging | Add rule = Vervanging toevoegen. "Regel" also means a line of text, which the import help needs ("één vervanging per regel") |
| phrase (dictionary) | woordgroep | "zin" means sentence |
| snippet | tekstfragment | Add snippet = Tekstfragment toevoegen |
| trigger phrase | triggerzin | |
| end word | eindwoord | |
| auto-stop | automatisch stoppen | Auto-stop after a pause = Automatisch stoppen na een pauze; Auto-stop with Smart Turn = Automatisch stoppen met Smart Turn |
| Tap to stop | Tik om te stoppen | |
| Pause length | Pauzeduur | |
| patience (Smart Turn): Quick / Normal / Relaxed | Geduld van Smart Turn: Snel / Normaal / Ontspannen | |
| lock / locked (hold the mic) | vergrendelen / Vergrendeld | |
| setup guide | installatiehulp | |
| Developer engines | Ontwikkelaarsengines | |
| speed: Fastest / Very fast / Fast / Moderate / Slow | Snelst / Zeer snel / Snel / Gemiddeld / Traag | |
| quality: Best / Great / Good / Fair / Basic | Beste / Uitstekend / Goed / Redelijk / Basis | |
| take (benchmark) | opname | |
| unload | vrijgeven | |
| mic key (a keyboard's) | microfoontoets | #61 |
| privacy policy | privacybeleid | |
| analytics | analyse | |
| About | Over | "Over Marmalade STT" for the row |
| Support / Report a bug / More Marmalade | Ondersteuning / Een bug melden / Meer van Marmalade | |
| Light / Dark / System | Licht / Donker / Systeem | |
| Wallpaper / Midnight / Forest / Berry | Achtergrond / Middernacht / Bos / Bessen | |
| high contrast (the colour preset, #68) | Hoog contrast (switch: “Kleuren met hoog contrast gebruiken”) | matches AOSP `accessibility_toggle_high_text_contrast_preference_title` (Android 16 QPR2 source, not checked on a device) |

Language names in placeholders: Android's Dutch display names ("Engels",
"Japans", "Hindi") need no inflection and take any preposition: "Transcribeert
geen %1$s", "voor %1$s", "(%1$s)". After "in" Dutch uses the neuter article,
which every language name takes: "Getest in het %1$s", "in het %3$s".

## Kept untranslated

Marmalade, Marmalade STT, all engine and model names (Parakeet Ultra, Parakeet
110M, Parakeet Japanese, Canary 180M Flash, Nemotron 3.5, Whisper Large v3
Turbo, Granite Speech, Qwen3-ASR, and upstream names in descriptions such as
Parakeet TDT 0.6B v3 and large-v3-turbo), Smart Turn, FUTO Keyboard, GitHub,
GitHub Sponsors, Silero VAD, ONNX Runtime, CPU, LibriSpeech, FLEURS, AMI,
NVIDIA, OpenAI, IBM, Alibaba, Moondream, WER, RTF, RSS, CTC, PRIVACY.md. Also
kept: engine, benchmark, streaming, spinning/pinning (developer screen),
encoder-decoder, Markdown, offline, the English spoken words in the cleanup and
end-word descriptions, and the "teh"/"tehran" dictionary example (an English
misspelling; the rule it shows holds in any language). The snippet example was
adapted: “mijn e-mail” matches “Mijn e-mail” but not “mijn e-mailadres”.

## External labels named

| Label (English) | Dutch used | Status |
|---|---|---|
| Android: Settings | Instellingen | verified (AOSP 16 QPR2 `settings_label`) |
| Android: Apps | Apps | verified (AOSP `apps_dashboard_title`) |
| Android: Permissions (app info) | Rechten | verified (AOSP `permissions_label`) |
| Android: System | Systeem | verified (AOSP `header_category_system`) |
| Android: Keyboard (Android 14+) | Toetsenbord | verified in AOSP 16 QPR2 (`keyboard_settings`); Pixel overlay not checked on a device |
| Android: On-screen keyboard | Schermtoetsenbord | verified in AOSP (`virtual_keyboard_category`); Pixel overlay not checked on a device |
| Android: keyboard settings (generic) | toetsenbordinstellingen van Android | descriptive, not a label |
| Android: OK (second keyboard dialog) | OK | verified (AOSP `dlg_ok`) |
| Android: home screen | startscherm | unverified (common Android Dutch wording; not looked up) |
| Android: navigation bar keyboard button | toetsenbordknop in de navigatiebalk | descriptive, not a label |
| Google: "Google voice typing" | Spraakgestuurd typen van Google | unverified (Gboard's Dutch label not checked) |
| FUTO Keyboard: Settings | Instellingen | verified loosely (FUTO translations `core/values-nl`, `keyboard_language_settings`) |
| FUTO Keyboard: Voice Input | Steminvoer | verified (FUTO keyboard-translations-public `core/values-nl/strings-uix.xml`, `voice_input_settings_title`); the installed build not checked on a device |
| FUTO Keyboard: "Disable built-in voice input" | “Schakel ingebouwde steminvoer uit” | verified (same source, `voice_input_settings_disable_builtin_voice_input`, same caveat) |
| FUTO Keyboard: "Use voice input provided by external app" | “Gebruik steminvoer van een externe app” | verified (same source, `…_subtitle`, same caveat) |
| Android: System → Languages (Android 14–16) | Talen | verified (AOSP 16 QPR2 `languages_settings`) |
| Android: Voice input (also the search term) | Spraakinvoer | verified (AOSP 16 QPR2, AOSP 13, AOSP 12 `voice_input_settings_title`) |
| Android: Languages & input (Android 13) | Talen en invoer | verified (AOSP 13 `language_settings`) |
| Android: Apps & notifications (Android 9–11) | Apps en meldingen | verified (AOSP 9, 10, 11 `app_and_notification_dashboard_title`) |
| Android: Advanced (the collapsed group on Apps & notifications, Android 9–11) | Geavanceerd | verified in source (preference library `expand_button_title`: Android 9 tag for 9; AndroidX `androidx-preference-release` for 10–11, whose exact bundled version wasn't checked) |
| Android: Default apps (Android 9–12) | Standaard-apps (Android 9); Standaard apps (Android 10–12) | verified (AOSP 9–12 Settings `app_default_dashboard_title`, the row tapped) |
| Android: Assist & voice input (Android 9) | Assistentie en spraakinvoer | verified (AOSP 9 `assist_and_voice_input_title`) |
| Android: Assist app (Android 9–10) | App voor assistentie | verified (AOSP 9 `default_assist_title`; Android 10 Default apps row: PermissionController `role_assistant_short_label`; Android 10 page row: AOSP 10 `default_assist_title`) |
| Android: Digital assistant app (Default apps row, Android 11–12) | Digitale-assistent-app | verified (PermissionController 11, 12 `role_assistant_short_label`) |
| Android: Default digital assistant app (Android 11) | Standaard digitale-assistent-app | verified (AOSP 11 `default_digital_assistant_title`) |
| Android: None (assistant picker) | Geen | verified (AOSP 9 `app_list_preference_none`; PermissionController 10, 11 `default_app_none`) |
| Android: Language & region (Android 16 where present, 17) | Taal en regio | verified (AOSP 17 `language_and_region_settings`) |
| Android: Speech (row of Language & region, Android 17) | Spraak | verified (AOSP 17 `speech_preference_title`); on Android 13–16 Speech is a heading and isn't named |
| Android: Force stop (written as a verb phrase) | Gedwongen stoppen ("Marmalade STT gedwongen stopt") | verified (AOSP 16 QPR2 `force_stop`) |
| Fossify Keyboard: Voice typing method | Methode voor spraakgestuurd typen | verified (Fossify `voice_typing_method`, main) |
| SwiftKey: Multi-modal voice typing | Multimodaal typen via stem | from Microsoft's support page; in-app label unverified |
| SwiftKey: Rich input | Uitgebreide invoer | from Microsoft's support page, which also says "Rijke invoer" in the same steps; in-app label unverified |

Sources for the #79 rows: AOSP Settings `res/values-nl/strings.xml` on the `android-9.0.0_r1` tag and the `android10-release` … `android17-release` branches; PermissionController's strings (Android 10–11: `platform/packages/apps/PackageInstaller`, Android 12: `platform/packages/modules/Permission`), which label the Default apps rows and the assistant picker on 10–12; the preference library's `expand_button_title` (`platform/frameworks/support`). Not checked against a phone maker's overlay on a device.

Sources: AOSP Settings strings at https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/android16-qpr2-release/res/values-nl/strings.xml ("AOSP 16 QPR2", the Android 14+ path), the same file on `android13-release` ("AOSP 13") and `android12-release` ("AOSP 12"); FUTO's labels at https://gitlab.futo.org/keyboard/keyboard-translations-public/-/blob/master/core/values-nl/strings-uix.xml (master); Fossify Keyboard's `voice_typing_method` at https://github.com/FossifyOrg/Keyboard/blob/main/app/src/main/res/values-nl/strings.xml (main branch; the installed release not checked on a device); SwiftKey's labels from Microsoft's support page https://support.microsoft.com/nl-nl/swiftkey-keyboard/how-to-disable-multimodal-voice-dictation-in-swiftkey (machine-translated by Microsoft, so the in-app label is not checked). AOSP labels are not checked against a Pixel's overlay on a device.

FUTO calls voice input "Steminvoer"; this app says "spraakinvoer" (Android's
word) in its own text and uses FUTO's word only when naming FUTO's menu labels.
