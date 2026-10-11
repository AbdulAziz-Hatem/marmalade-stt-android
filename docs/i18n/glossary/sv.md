# Swedish (sv) glossary

## Register

Informal **du**, as Android's and Google's Swedish UI address the user
("Tryck på OK", "Försök igen"). Buttons use the imperative ("Ladda ned",
"Avbryt", "Tillåt mikrofonen"); errors use "Det gick inte att …" as Android
does; status lines use present-tense verbs ("Lyssnar", "Läser in modellen…",
"Laddar ned"). Quotation marks are ”…” (the same mark on both sides, Swedish
style; also `quoted_name`); `list_separator` stays ", ". Decimals in literal
text use a comma ("0,6 till 3 GB"). Genitive of acronyms takes a colon
(NVIDIA:s, IBM:s, FUTO:s).

"Phone" is **telefon** throughout; "device" (enhet) only where the English
says device.

Plurals: `one`, `other`. "språk" is the same in both ("1 språk", "25 språk").

## Terms

| English | Swedish | Notes |
|---|---|---|
| Dictate (tab, screen) | Diktera (fliken Diktera, Diktera-skärmen) | |
| Engines (tab) | Motorer (fliken Motorer) | |
| Settings (tab, title) | Inställningar | |
| tab | flik | |
| dictation / dictate | diktering / diktera | |
| engine / speech engine | motor / taligenkänningsmotor | |
| model / speech model | modell / talmodell | |
| language model (Qwen3 decoder) | språkmodell ("språkmodellsavkodare") | |
| download (verb / noun) | ladda ned / nedladdning | Android Swedish writes "ladda ned" |
| voice keyboard | rösttangentbord | |
| voice input / voice typing | röstinmatning | Android Swedish uses one word ("Röstinmatning") |
| Marmalade voice typing (IME name) | Marmalade-röstinmatning | compound with a proper noun takes a hyphen |
| speech input (apps asking Android) | taligenkänning | |
| speech recognition service | taligenkänningstjänst | #61; also the summary row |
| speech (settings section) | Tal | as Android's "Tal" |
| End of speech | Talets slut | "Slut på tal" reads as "out of speech" |
| recognition language | igenkänningsspråk | |
| speak-now popup | fönstret ”Tala nu” | "Tala nu" = `ime_speak_now` / `recognizer_speak_now` |
| text cleanup | textrensning | |
| filler words | utfyllnadsord | |
| voice commands | röstkommandon | |
| punctuation words | uttalade skiljetecken | |
| replacement dictionary | ersättningsordlista | |
| rule | regel | Add rule = Lägg till regel |
| snippet | textsnutt | Add snippet = Lägg till textsnutt |
| trigger phrase | utlösarfras | |
| end word | slutord | |
| auto-stop | autostopp | Auto-stop after a pause = Autostopp efter paus; Auto-stop with Smart Turn = Autostopp med Smart Turn |
| Tap to stop | Tryck för att stoppa | |
| Pause length | Pauslängd | |
| patience (Smart Turn): Quick / Normal / Relaxed | tålamod: Snabbt / Normalt / Avslappnat | neuter, agrees with "tålamod" |
| lock / locked (hold the mic) | låsa / Låst | |
| setup guide | konfigurationsguide | |
| Developer engines | Utvecklarmotorer | |
| speed: Fastest / Very fast / Fast / Moderate / Slow | Snabbast / Mycket snabb / Snabb / Måttlig / Långsam | |
| quality: Best / Great / Good / Fair / Basic | Bäst / Mycket bra / Bra / Hyfsad / Enkel | |
| take (benchmark) | tagning | |
| unload | frigöra | avoids "ladda ur" next to "ladda ned" |
| mic key (a keyboard's) | mikrofontangent | #61 |
| privacy policy | integritetspolicy | |
| never saved or uploaded | varken sparas eller laddas upp | "varken … eller" negates both verbs; "sparas eller laddas aldrig upp" is ambiguous |
| speech-to-text (noun) | tal-till-text | hyphenated |
| analytics | analys | |
| About | Om | "Om Marmalade STT" for the row |
| Support / Report a bug / More Marmalade | Stöd / Rapportera ett fel / Mer från Marmalade | |
| Light / Dark / System | Ljust / Mörkt / System | neuter (läge) |
| Wallpaper / Midnight / Forest / Berry | Bakgrund / Midnatt / Skog / Bär | |
| high contrast (the colour preset, #68) | Hög kontrast (switch: “Använd färger med hög kontrast”) | matches AOSP `accessibility_toggle_high_text_contrast_preference_title` (Android 16 QPR2 source, not checked on a device) |

Language names in placeholders: Android's Swedish display names are lowercase
nouns ("engelska", "japanska") that take no inflection, so they work after any
verb or preposition: "Transkriberar inte %1$s", "för %1$s", "på %1$s",
"Testad på %1$s". No restructuring needed.

## Kept untranslated

Marmalade, Marmalade STT, all engine and model names (Parakeet Ultra, Parakeet
110M, Parakeet Japanese, Canary 180M Flash, Nemotron 3.5, Whisper Large v3
Turbo, Granite Speech, Qwen3-ASR, and upstream names in descriptions such as
Parakeet TDT 0.6B v3 and large-v3-turbo), Smart Turn, FUTO Keyboard, GitHub,
GitHub Sponsors, Silero VAD, ONNX Runtime, CPU, LibriSpeech, FLEURS, AMI,
NVIDIA, OpenAI, IBM, Alibaba, Moondream (Swedish genitive "Moondreams",
"Alibabas"), WER, RTF, RSS, CTC, PRIVACY.md. Also kept: benchmark, spinning
(developer screen), encoder-decoder, Markdown, offline, the English spoken
words in the cleanup and end-word descriptions, the "teh"/"tehran" dictionary
example (an English misspelling; the rule it shows holds in any language), and
FUTO Keyboard's menu labels (see below). The snippet example was adapted:
”min e-post” matches ”Min e-post” but not ”min e-postlista”.

## External labels named

| Label (English) | Swedish used | Status |
|---|---|---|
| Android: Settings | Inställningar | verified (AOSP 16 QPR2 `settings_label`) |
| Android: Apps | Appar | verified (AOSP `apps_dashboard_title`) |
| Android: Permissions (app info) | Behörigheter | verified (AOSP `permissions_label`) |
| Android: System | System | verified (AOSP `header_category_system`) |
| Android: Keyboard (Android 14+) | Tangentbord | verified in AOSP 16 QPR2 (`keyboard_settings`); Pixel overlay not checked on a device |
| Android: On-screen keyboard | Skärmtangentbord | verified in AOSP (`virtual_keyboard_category`); Pixel overlay not checked on a device |
| Android: keyboard settings (generic) | Androids tangentbordsinställningar | descriptive, not a label |
| Android: OK (second keyboard dialog) | OK | verified (AOSP `dlg_ok`) |
| Android: home screen | startskärmen | unverified (common Android Swedish wording; not looked up) |
| Android: navigation bar keyboard button | tangentbordsknappen i navigeringsfältet | descriptive, not a label |
| Google: "Google voice typing" | Googles röstinmatning | unverified (Gboard's Swedish label not checked) |
| FUTO Keyboard: Settings → Voice Input | FUTO Keyboards inställningar → Voice Input | verified that FUTO has no Swedish for these screens: keyboard-translations-public `core/values-sv/` has no `strings-uix.xml` (master), so FUTO shows the English labels; quoted in English |
| FUTO Keyboard: "Disable built-in voice input" | ”Disable built-in voice input” | same source; kept English as FUTO shows it |
| FUTO Keyboard: "Use voice input provided by external app" | ”Use voice input provided by external app” | same source; kept English as FUTO shows it |
| Android: System → Languages (Android 14–16) | Språk | verified (AOSP 16 QPR2 `languages_settings`) |
| Android: Voice input (also the search term) | Röstinmatning | verified (AOSP 16 QPR2, AOSP 13, AOSP 12 `voice_input_settings_title`) |
| Android: Languages & input (Android 13) | Språk och inmatning | verified (AOSP 13 `language_settings`) |
| Android: Apps & notifications (Android 9–11) | Appar och aviseringar | verified (AOSP 9, 10, 11 `app_and_notification_dashboard_title`) |
| Android: Advanced (the collapsed group on Apps & notifications, Android 9–11) | Avancerat | verified in source (preference library `expand_button_title`: Android 9 tag for 9; AndroidX `androidx-preference-release` for 10–11, whose exact bundled version wasn't checked) |
| Android: Default apps (Android 9–12) | Standardappar | verified (AOSP 9–12 Settings `app_default_dashboard_title`, the row tapped) |
| Android: Assist & voice input (Android 9) | Assistent och röstinmatning | verified (AOSP 9 `assist_and_voice_input_title`) |
| Android: Assist app (Android 9–10) | Assistentapp | verified (AOSP 9 `default_assist_title`; Android 10 Default apps row: PermissionController `role_assistant_short_label`; Android 10 page row: AOSP 10 `default_assist_title`) |
| Android: Digital assistant app (Default apps row, Android 11–12) | Digital assistentapp | verified (PermissionController 11, 12 `role_assistant_short_label`) |
| Android: Default digital assistant app (Android 11) | Digital assistentapp, standard | verified (AOSP 11 `default_digital_assistant_title`) |
| Android: None (assistant picker) | Ingen | verified (AOSP 9 `app_list_preference_none`; PermissionController 10, 11 `default_app_none`) |
| Android: Language & region (Android 16 where present, 17) | Språk och region | verified (AOSP 17 `language_and_region_settings`) |
| Android: Speech (row of Language & region, Android 17) | Tal | verified (AOSP 17 `speech_preference_title`); on Android 13–16 Speech is a heading and isn't named |
| Android: Force stop (written as a verb phrase) | Tvinga att avsluta ("tvingar Marmalade STT att avsluta") | verified (AOSP 16 QPR2 `force_stop`) |
| Fossify Keyboard: Voice typing method | Röstinmatningsmetod | verified (Fossify `voice_typing_method`, main) |
| SwiftKey: Multi-modal voice typing | Multimodal röstinmatning | from Microsoft's support page; in-app label unverified |
| SwiftKey: Rich input | Rich Input | from Microsoft's support page; in-app label unverified (the Swedish page keeps the English name) |

Sources for the #79 rows: AOSP Settings `res/values-sv/strings.xml` on the `android-9.0.0_r1` tag and the `android10-release` … `android17-release` branches; PermissionController's strings (Android 10–11: `platform/packages/apps/PackageInstaller`, Android 12: `platform/packages/modules/Permission`), which label the Default apps rows and the assistant picker on 10–12; the preference library's `expand_button_title` (`platform/frameworks/support`). Not checked against a phone maker's overlay on a device.

Sources: AOSP Settings strings at https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/android16-qpr2-release/res/values-sv/strings.xml ("AOSP 16 QPR2", the Android 14+ path), the same file on `android13-release` ("AOSP 13") and `android12-release` ("AOSP 12"); FUTO's translations at https://gitlab.futo.org/keyboard/keyboard-translations-public/-/tree/master/core/values-sv (only `strings.xml`, `strings-action-keys.xml` and `strings-talkback-descriptions.xml`, none with the voice-input settings); Fossify Keyboard's `voice_typing_method` at https://github.com/FossifyOrg/Keyboard/blob/main/app/src/main/res/values-sv/strings.xml (main branch; the installed release not checked on a device); SwiftKey's labels from Microsoft's support page https://support.microsoft.com/sv-se/swiftkey-keyboard/how-to-disable-multimodal-voice-dictation-in-swiftkey (machine-translated by Microsoft, so the in-app label is not checked). AOSP labels are not checked against a Pixel's overlay on a device. If FUTO adds Swedish, `onboarding_futo_manual_body` needs its labels.
