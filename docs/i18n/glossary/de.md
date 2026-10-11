# German (de) glossary

## Register

Informal **du**, as current Android and Pixel German use it (and as
TRANSLATING.md fixes for de). Buttons use the infinitive („Herunterladen“,
„Abbrechen“, „Mikrofon erlauben“); instructions in running text use the du
imperative („Tippe auf das Mikrofon“, „Versuche es noch einmal“). Quotation
marks are „…“ (also `quoted_name`); `list_separator` stays ", ". Labels of this app in running text are quoted when they name a
tab or a setting („Diktieren“, „Engines“, „Auto-Stopp nach einer Pause“);
Android's menu paths (Einstellungen → System → …) and Berechtigungen are not,
matching the English.

"Phone" is **Smartphone** throughout (Google's German wording), never
„Handy“ or „Telefon“.

## Terms

| English | German | Note |
|---|---|---|
| Dictate (tab) | Diktieren | |
| Engines (tab) | Engines | die Engine, as in Android's own German |
| Settings (tab) | Einstellungen | |
| tab | Tab | „im Tab „Engines““ |
| dictation / dictate | Diktat, Diktieren / diktieren | „Diktat starten“ |
| engine | Engine (f.) | speech engine: Sprach-Engine, Spracherkennungs-Engine |
| model / speech model | Modell / Sprachmodell | |
| language model (Qwen3 decoder) | LLM | avoids clashing with Sprachmodell |
| download (verb / noun) | herunterladen / Download | Downloadgröße, Downloadfortschritt |
| voice keyboard | Sprachtastatur | |
| voice input / voice typing / speech input | Spracheingabe | Android German uses one word for all three (Google-Spracheingabe) |
| Marmalade voice typing (IME name) | Marmalade-Spracheingabe | |
| speech (settings section) | Spracherkennung | „Sprache“ alone would read as "language" |
| recognition language | Erkennungssprache | |
| End of speech (section) | Ende des Sprechens | |
| text cleanup | Textbereinigung | |
| filler words | Füllwörter | |
| voice commands | Sprachbefehle | |
| replacement dictionary | Ersetzungswörterbuch | |
| rule | Regel | Regel hinzufügen |
| snippet | Textbaustein | Textbaustein hinzufügen |
| trigger phrase | Auslösephrase | |
| end word | Endwort | |
| auto-stop | Auto-Stopp | „Auto-Stopp nach einer Pause“, „Auto-Stopp mit Smart Turn“ |
| Pause length | Pausenlänge | |
| patience (Smart Turn) | Geduld | „Geduld von Smart Turn“ keeps the name intact |
| lock / locked (hold the mic) | fixieren / Fixiert | „gesperrt“ would read as "disabled" |
| setup guide | Einrichtungsassistent | |
| Developer engines | Entwickler-Engines | |
| speed | Tempo | short for the card's column label |
| quality: Best / Great / Good / Fair / Basic | Spitze / Sehr gut / Gut / Ordentlich / Einfach | |
| take (benchmark) | Aufnahme | |
| memory | Arbeitsspeicher | storage: Speicher / Speicherplatz |
| privacy policy | Datenschutzerklärung | |
| analytics | Nutzungsanalyse | |
| About | Info | „Über Marmalade STT“ for the row |
| Wallpaper (colour preset) | Hintergrund | as in Android's „Hintergrund & Stil“ |
| mic key (a keyboard's) | Mikrofontaste | #61 |
| speech recognition service (Android's) | Spracherkennungsdienst | #61; also the summary row |
| high contrast (the colour preset, #68) | Hoher Kontrast (switch: “Farben mit hohem Kontrast nutzen”) | matches AOSP `accessibility_toggle_high_text_contrast_preference_title` (Android 16 QPR2 source, not checked on a device) |

## Untranslated

Marmalade, Marmalade STT, every engine and model name (Parakeet Ultra, Parakeet
110M, Parakeet Japanese, Canary 180M Flash, Nemotron 3.5, Whisper Large v3
Turbo, Granite Speech, Qwen3-ASR, and upstream names in descriptions such as
Parakeet TDT 0.6B v3 and large-v3-turbo), Smart Turn, FUTO Keyboard, GitHub,
GitHub Sponsors, Silero VAD, ONNX Runtime, CPU, LibriSpeech, FLEURS, AMI,
NVIDIA, OpenAI, IBM, Alibaba, Moondream, WER, RTF, RSS, CTC, PRIVACY.md,
Benchmark, Streaming, Markdown. The spoken English words in the cleanup and
end-word descriptions („um“, „new line“, „period“, „Twenty five percent“, „over“
…) stay English.

## Labels of Android and FUTO Keyboard

| Label (English) | German used | Status |
|---|---|---|
| Android: Settings | Einstellungen | verified (AOSP Settings `settings_label`, main) |
| Android: Apps | Apps | verified (AOSP `apps_dashboard_title`) |
| Android: Permissions | Berechtigungen | verified (AOSP `permissions_label`) |
| Android: System | System | verified (AOSP `header_category_system`) |
| Android: Keyboard | Tastatur | verified in AOSP (`keyboard_settings`); Pixel overlay not checked on a device |
| Android: On-screen keyboard | Bildschirmtastatur | verified in AOSP (`virtual_keyboard_category`); Pixel overlay not checked on a device |
| Android: OK (direct-boot dialog) | OK | verified |
| FUTO: Settings | Einstellungen | verified (FUTO translations repo, `core/values-de`) |
| FUTO: Voice Input | Sprachsteuerung | verified (FUTO translations repo `voice_input_settings_title`); the installed 0.1.30 build not checked on a device |
| FUTO: Disable built-in voice input | Integrierte Sprachsteuerung deaktivieren | verified (same source, same caveat) |
| FUTO: Use voice input provided by external app | Verwenden Sie die Sprachsteuerung einer externen App | verified (same source, same caveat); FUTO uses *Sie* here, quoted verbatim |
| Android: System → Languages (Android 14–16) | Sprachen | verified (AOSP 16 QPR2 `languages_settings`) |
| Android: Voice input (also the search term) | Spracheingabe | verified (AOSP 16 QPR2, AOSP 13, AOSP 12 `voice_input_settings_title`) |
| Android: Languages & input (Android 13) | Sprachen und Eingabe | verified (AOSP 13 `language_settings`) |
| Android: Apps & notifications (Android 9–11) | Apps & Benachrichtigungen | verified (AOSP 9, 10, 11 `app_and_notification_dashboard_title`) |
| Android: Advanced (the collapsed group on Apps & notifications, Android 9–11) | Erweitert | verified in source (preference library `expand_button_title`: Android 9 tag for 9; AndroidX `androidx-preference-release` for 10–11, whose exact bundled version wasn't checked) |
| Android: Default apps (Android 9–12) | Standard-Apps | verified (AOSP 9–12 Settings `app_default_dashboard_title`, the row tapped) |
| Android: Assist & voice input (Android 9) | Assistent & Spracheingabe | verified (AOSP 9 `assist_and_voice_input_title`) |
| Android: Assist app (Android 9–10) | Assistent-App | verified (AOSP 9 `default_assist_title`; Android 10 Default apps row: PermissionController `role_assistant_short_label`; Android 10 page row: AOSP 10 `default_assist_title`) |
| Android: Digital assistant app (Default apps row, Android 11–12) | App für digitalen Assistenten | verified (PermissionController 11, 12 `role_assistant_short_label`) |
| Android: Default digital assistant app (Android 11) | Standardapp für digitalen Assistenten | verified (AOSP 11 `default_digital_assistant_title`) |
| Android: None (assistant picker) | Keine (Android 9); Keine App (Android 10–11) | verified (AOSP 9 `app_list_preference_none`; PermissionController 10, 11 `default_app_none`) |
| Android: Language & region (Android 16 where present, 17) | Sprache & Region | verified (AOSP 17 `language_and_region_settings`) |
| Android: Speech (row of Language & region, Android 17) | Sprache | verified (AOSP 17 `speech_preference_title`); on Android 13–16 Speech is a heading and isn't named |
| Android: Force stop (written as a verb phrase) | Beenden erzwingen („das Beenden … erzwingst“) | verified (AOSP 16 QPR2 `force_stop`) |
| Fossify Keyboard: Voice typing method | Spracheingabe-Methode | verified (Fossify `voice_typing_method`, main) |
| SwiftKey: Multi-modal voice typing | Multimodale Spracheingabe | from Microsoft's support page; in-app label unverified |
| SwiftKey: Rich input | Rich Input | from Microsoft's support page; in-app label unverified (the German page keeps the English name) |

Sources for the #79 rows: AOSP Settings `res/values-de/strings.xml` on the `android-9.0.0_r1` tag and the `android10-release` … `android17-release` branches; PermissionController's strings (Android 10–11: `platform/packages/apps/PackageInstaller`, Android 12: `platform/packages/modules/Permission`), which label the Default apps rows and the assistant picker on 10–12; the preference library's `expand_button_title` (`platform/frameworks/support`). Not checked against a phone maker's overlay on a device.

Sources for the #61 rows: AOSP Settings strings at https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/android16-qpr2-release/res/values-de/strings.xml ("AOSP 16 QPR2", the Android 14+ path), the same file on `android13-release` ("AOSP 13") and `android12-release` ("AOSP 12"); Fossify Keyboard's `voice_typing_method` at https://github.com/FossifyOrg/Keyboard/blob/main/app/src/main/res/values-de/strings.xml (main branch; the installed release not checked on a device); SwiftKey's labels from Microsoft's support page https://support.microsoft.com/de-de/swiftkey-keyboard/how-to-disable-multimodal-voice-dictation-in-swiftkey (machine-translated by Microsoft, so the in-app label is not checked). AOSP labels are not checked against a Pixel's overlay on a device.

FUTO calls voice input „Sprachsteuerung“; this app says „Spracheingabe“ in its
own text and uses FUTO's word only when quoting FUTO's menu labels.
