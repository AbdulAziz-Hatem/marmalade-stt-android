# Italian (it) glossary

## Register

Informal *tu* throughout ("Tocca il microfono", "Scarica un modello"), matching
marmalade-tts-android's `values-it` and Android's Italian UI. Buttons use the
imperative ("Scarica", "Riprova", "Annulla"), errors use "Impossibile …" as
Android does, and status lines use nouns ("Caricamento…", "Download in corso").
Typographic quotes “…”. Apostrophes written `\'`. Decimals in literal text use a
comma ("0,6 GB").

Plurals: `one`, `many`, `other`. `many` (millions) follows the TTS app's
pattern with "di": "%1$d di regole".

## Terms

| English | Italian | Notes |
|---|---|---|
| Dictate (tab, screen) | Dettatura (scheda / schermata Dettatura) | Noun, not the imperative "Detta" (reads like the participle "said") |
| Engines (tab) | Motori (scheda Motori) | As in the TTS app |
| Settings (tab, title) | Impostazioni | TTS app's nav says "Opzioni" (5 tabs); 3 tabs here, so the full word |
| dictation | dettatura | |
| engine / speech engine | motore / motore vocale | |
| model / speech model | modello / modello vocale | |
| download (verb / noun) | scaricare / download | Button "Scarica"; status "Download in corso" |
| voice keyboard | tastiera vocale | |
| voice typing (IME name) | Digitazione vocale (Marmalade) | Mirrors Android's "Digitazione vocale Google" |
| voice input | input vocale | |
| speech (settings section) | Voce | |
| speech (what was spoken) | parlato | "Fine del parlato", "nessun parlato riconosciuto" |
| End of speech | Fine del parlato | |
| recognition language | lingua di riconoscimento | |
| text cleanup | pulizia del testo | |
| filler words | intercalari | |
| voice commands | comandi vocali | |
| replacement dictionary | dizionario di sostituzione | |
| rule | regola | Add rule = Aggiungi regola |
| snippet | frammento | Add snippet = Aggiungi frammento |
| trigger phrase | frase di attivazione | |
| phrase (dictionary) | espressione | "frase" means sentence |
| end word | parola di chiusura | |
| auto-stop | stop automatico | Auto-stop after a pause = Stop automatico dopo una pausa |
| Tap to stop | Tocca per fermare | |
| Pause length | Durata della pausa | |
| patience (Smart Turn) | pazienza: Rapida / Normale / Rilassata | Feminine, agrees with "pazienza" |
| setup guide | guida alla configurazione | |
| Developer engines | Motori per sviluppatori | As in the TTS app |
| take (benchmark) | registrazione | |
| unload | rilascio | Avoids "scaricare" (= download) |
| tab | scheda | |
| Speak now | Parla ora | |
| Privacy policy | Informativa sulla privacy | As in the TTS app |
| analytics | statistiche d'uso | |
| Support / Report a bug / More Marmalade | Sostegno / Segnala un problema / Altro da Marmalade | As in the TTS app |
| Quality: Best / Great / Good / Fair / Basic | Eccellente / Ottima / Buona / Discreta / Di base | Feminine (qualità) |
| Speed: Fastest / Very fast / Fast / Moderate / Slow | Il più veloce / Molto veloce / Veloce / Medio / Lento | Masculine (motore) |
| Light / Dark / System | Chiara / Scura / Sistema | As in the TTS app |
| Wallpaper / Midnight / Forest / Berry | Sfondo / Mezzanotte / Foresta / Frutti di bosco | |
| high contrast (the colour preset, #68) | Alto contrasto (switch: “Usa colori ad alto contrasto”) | matches AOSP `accessibility_toggle_high_text_contrast_preference_title` (Android 16 QPR2 source, not checked on a device) |
| mic key (a keyboard's) | tasto del microfono | #61 |
| speech recognition service (Android's) | servizio di riconoscimento vocale | #61; also the summary row |

Language names in placeholders: Android's Italian display names are lowercase
adjectives ("tedesco"), so strings put them after "in" ("Non trascrive in
inglese", "per dettare in giapponese"), never after an article or "la
lingua", which would need agreement ("la lingua italiano" is wrong; #67).

## Kept untranslated

Marmalade, Marmalade STT, all engine and model names (Parakeet Ultra, Parakeet
110M, Parakeet Japanese, Canary 180M Flash, Nemotron 3.5, Whisper Large v3
Turbo, Granite Speech, Qwen3-ASR, and upstream names in descriptions such as
Parakeet TDT 0.6B v3 and large-v3-turbo), Smart Turn, FUTO Keyboard, GitHub,
GitHub Sponsors, Silero VAD, ONNX Runtime, CPU, LibriSpeech, FLEURS, AMI,
NVIDIA, OpenAI, IBM, Alibaba, Moondream, WER, RTF, RSS, CTC, PRIVACY.md. Also
kept: streaming, benchmark, encoder-decoder, Markdown, spinning/pinning
(developer screens), the English spoken words in the cleanup and end-word
descriptions, and the "teh"/"tehran" dictionary example (an English misspelling;
the rule it shows holds in any language). The snippet example was adapted: “il
mio indirizzo” matches “Il mio indirizzo” but not “il mio indirizzario”.

## External labels named

| Label (English) | Italian used | Status |
|---|---|---|
| Android: Settings | Impostazioni | verified |
| Android: Apps | App | verified |
| Android: Permissions (app info) | Autorizzazioni | verified |
| Android: System | Sistema | verified |
| Android: Settings → System → Keyboard → On-screen keyboard | Impostazioni → Sistema → Tastiera → Tastiera sullo schermo | unverified ("Tastiera" and "Tastiera sullo schermo" on Android 14+ Pixel) |
| Android: keyboard settings (generic) | impostazioni della tastiera di Android | descriptive, not a label |
| Android: OK (second keyboard dialog) | OK | verified |
| Android: home screen | schermata Home | verified |
| FUTO Keyboard: Settings → Voice Input | Impostazioni → Input vocale | unverified |
| FUTO Keyboard: "Disable built-in voice input" | “Disattiva input vocale integrato” | unverified |
| FUTO Keyboard: "Use voice input provided by external app" | “Usa l'input vocale fornito da un'app esterna” | unverified |
| Android: System → Languages (Android 14–16) | Lingue | verified (AOSP 16 QPR2 `languages_settings`) |
| Android: Voice input (also the search term) | Input vocale | verified (AOSP 16 QPR2, AOSP 13, AOSP 12 `voice_input_settings_title`) |
| Android: Languages & input (Android 13) | Lingue e immissione | verified (AOSP 13 `language_settings`) |
| Android: Apps & notifications (Android 9–11) | App e notifiche | verified (AOSP 9, 10, 11 `app_and_notification_dashboard_title`) |
| Android: Advanced (the collapsed group on Apps & notifications, Android 9–11) | Avanzate | verified in source (preference library `expand_button_title`: Android 9 tag for 9; AndroidX `androidx-preference-release` for 10–11, whose exact bundled version wasn't checked) |
| Android: Default apps (Android 9–12) | App predefinite | verified (AOSP 9–12 Settings `app_default_dashboard_title`, the row tapped) |
| Android: Assist & voice input (Android 9) | Assistenza e input vocale | verified (AOSP 9 `assist_and_voice_input_title`) |
| Android: Assist app (Android 9–10) | App di assistenza | verified (AOSP 9 `default_assist_title`; Android 10 Default apps row: PermissionController `role_assistant_short_label`; Android 10 page row: AOSP 10 `default_assist_title`) |
| Android: Digital assistant app (Default apps row, Android 11–12) | App assistente digitale | verified (PermissionController 11, 12 `role_assistant_short_label`) |
| Android: Default digital assistant app (Android 11) | App assistente digitale predefinita | verified (AOSP 11 `default_digital_assistant_title`) |
| Android: None (assistant picker) | Nessuna | verified (AOSP 9 `app_list_preference_none`; PermissionController 10, 11 `default_app_none`) |
| Android: Language & region (Android 16 where present, 17) | Lingua e regione | verified (AOSP 17 `language_and_region_settings`) |
| Android: Speech (row of Language & region, Android 17) | Voce | verified (AOSP 17 `speech_preference_title`); on Android 13–16 Speech is a heading and isn't named |
| Android: Force stop (written as a verb phrase) | Forza interruzione (“forzi l'interruzione”) | verified (AOSP 16 QPR2 `force_stop`) |
| Fossify Keyboard: Voice typing method | Metodo immissione voce | verified (Fossify `voice_typing_method`, main) |
| SwiftKey: Multi-modal voice typing | Digitazione vocale multimodale | from Microsoft's support page; in-app label unverified |
| SwiftKey: Rich input | Rich Input | from Microsoft's support page; in-app label unverified (the Italian page keeps the English name) |

Sources for the #79 rows: AOSP Settings `res/values-it/strings.xml` on the `android-9.0.0_r1` tag and the `android10-release` … `android17-release` branches; PermissionController's strings (Android 10–11: `platform/packages/apps/PackageInstaller`, Android 12: `platform/packages/modules/Permission`), which label the Default apps rows and the assistant picker on 10–12; the preference library's `expand_button_title` (`platform/frameworks/support`). Not checked against a phone maker's overlay on a device.

Sources for the #61 rows: AOSP Settings strings at https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/android16-qpr2-release/res/values-it/strings.xml ("AOSP 16 QPR2", the Android 14+ path), the same file on `android13-release` ("AOSP 13") and `android12-release` ("AOSP 12"); Fossify Keyboard's `voice_typing_method` at https://github.com/FossifyOrg/Keyboard/blob/main/app/src/main/res/values-it/strings.xml (main branch; the installed release not checked on a device); SwiftKey's labels from Microsoft's support page https://support.microsoft.com/it-it/swiftkey-keyboard/how-to-disable-multimodal-voice-dictation-in-swiftkey (machine-translated by Microsoft, so the in-app label is not checked). AOSP labels are not checked against a Pixel's overlay on a device.
