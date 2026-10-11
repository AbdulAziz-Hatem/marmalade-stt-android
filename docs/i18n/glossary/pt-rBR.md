# Glossary: Brazilian Portuguese (pt-rBR)

## Register

*Você*, friendly and plain, as in marmalade-tts-android's pt-BR (shared
terms: celular, configurações, motor, app, baixar, “…” quotes). Imperatives
in help text and dialogs ("Baixe…", "Toque em…"); infinitives on buttons
("Baixar", "Permitir microfone"). "Celular" for the phone; "dispositivo" is
avoided in UI text.

## Terms

| English | Portuguese |
|---|---|
| Dictate (tab) / dictation | Ditar (aba) / ditado |
| Engines (tab) / engine | Motores (aba) / motor |
| Settings (tab, also Android's) | Configurações |
| model / speech model | modelo / modelo de fala |
| download (verb / noun) | baixar / download |
| voice keyboard | teclado de voz |
| voice input | entrada de voz |
| voice typing (IME name) | digitação por voz (“Digitação por voz do Marmalade”) |
| speech (settings section) | Fala |
| speak-now popup | pop-up de fala |
| Speak now | Fale agora |
| Recognition language | Idioma de reconhecimento |
| Auto (language) / Auto-detect | Automático / Detectar automaticamente |
| Text cleanup | Limpeza do texto |
| filler words | hesitações (“Remover hesitações”) |
| Replacement dictionary | Dicionário de substituições |
| rule / Add rule | regra / Adicionar regra |
| snippet / Add snippet | trecho / Adicionar trecho |
| trigger phrase | frase-gatilho |
| End of speech | Fim da fala |
| end word / End words | palavra de encerramento / Palavras de encerramento |
| auto-stop | parada automática |
| Auto-stop after a pause | Parada automática após uma pausa |
| Auto-stop with Smart Turn | Parada automática com Smart Turn |
| Pause length | Duração da pausa |
| Smart Turn patience: Quick / Normal / Relaxed | Paciência do Smart Turn: Rápida / Normal / Tranquila |
| Tap to stop | Tocar para parar |
| Locked / Lock listening | Travado / Travar a escuta |
| Setup guide | Guia de configuração |
| Developer engines | Motores para desenvolvedores |
| benchmark / take | benchmark / gravação |
| Quality: Best / Great / Good / Fair / Basic | Excelente / Ótima / Boa / Razoável / Básica |
| Speed: Fastest / Very fast / Fast / Moderate / Slow | O mais rápido / Muito rápido / Rápido / Moderado / Lento |
| Report a bug | Relatar um problema |
| Support / Support development | Apoio / Apoie o desenvolvimento |
| Open-source licenses | Licenças de código aberto |
| mic key (a keyboard's) | tecla de microfone |
| speech recognition service (Android's) | serviço de reconhecimento de fala |
| high contrast (the colour preset, #68) | Alto contraste (switch: “Usar cores em alto contraste”); matches AOSP `accessibility_toggle_high_text_contrast_preference_title` (Android 16 QPR2 source, not checked on a device) |

## Kept untranslated

Marmalade, Marmalade STT, all engine and model names (Parakeet Ultra, Parakeet
110M, Parakeet Japanese, Canary 180M Flash, Nemotron 3.5, Whisper Large v3
Turbo, Granite Speech, Qwen3-ASR, and upstream names in descriptions such as
Parakeet TDT 0.6B v3 and large-v3-turbo), Smart Turn, FUTO Keyboard, GitHub,
GitHub Sponsors, Silero VAD, ONNX Runtime, CPU, LibriSpeech, FLEURS, AMI,
NVIDIA, OpenAI, IBM, Alibaba, Moondream, WER, RTF, RSS, CTC, PRIVACY.md,
Markdown, OK. Also kept as loanwords: app, download, offline, streaming,
benchmark, issue (GitHub), spinning (ONNX Runtime threads), runtime, pop-up. The
spoken English words (“um”, “new line”, “period”, “over”, “Twenty five
percent”…) stay in English.

Adapted examples: the dictionary help uses “pra” → “Para” / “praia”
instead of “teh” → “The” / “tehran”; the snippet help uses “meu endereço” /
“meu endereçamento” instead of “my email” / “my emailing”. Both illustrate
the same matching rules.

## Labels of Android and FUTO Keyboard

| Label as named | Where | Status |
|---|---|---|
| Configurações | Android Settings app | verified |
| Apps | Android Settings → Apps | verified |
| Permissões | App info → Permissions | verified |
| Sistema | Settings → System | verified |
| Teclado | Settings → System → Keyboard (Android 14+) | unverified |
| Teclado na tela | Settings → System → Keyboard → On-screen keyboard | unverified |
| OK | Android's keyboard-enable / direct-boot dialog button | verified |
| configurações de teclado do Android | generic description, not a literal label | n/a |
| Configurações → Entrada de voz | FUTO Keyboard: Settings → Voice Input | unverified |
| “Desativar a entrada de voz integrada” | FUTO Keyboard: "Disable built-in voice input" | unverified |
| “Usar a entrada de voz de um app externo” | FUTO Keyboard: "Use voice input provided by external app" | unverified |
| Idiomas | Android: System → Languages (Android 14–16) | verified (AOSP 16 QPR2 `languages_settings`) |
| Entrada de texto por voz | Android: Voice input (also the search term) | verified (AOSP 16 QPR2, AOSP 13, AOSP 12 `voice_input_settings_title`) |
| Idiomas e entrada | Android: Languages & input (Android 13) | verified (AOSP 13 `language_settings`) |
| Apps e notificações | Android: Apps & notifications (Android 9–11) | verified (AOSP 9, 10, 11 `app_and_notification_dashboard_title`) |
| Avançado | Android: Advanced (the collapsed group on Apps & notifications, Android 9–11) | verified in source (preference library `expand_button_title`: Android 9 tag for 9; AndroidX `androidx-preference-release` for 10–11, whose exact bundled version wasn't checked) |
| Apps padrão | Android: Default apps (Android 9–12) | verified (AOSP 9–12 Settings `app_default_dashboard_title`, the row tapped) |
| Assistivo e voz | Android: Assist & voice input (Android 9) | verified (AOSP 9 `assist_and_voice_input_title`) |
| App assistivo | Android: Assist app (Android 9–10) | verified (AOSP 9 `default_assist_title`; Android 10 Default apps row: PermissionController `role_assistant_short_label`; Android 10 page row: AOSP 10 `default_assist_title`) |
| App assistente digital | Android: Digital assistant app (Default apps row, Android 11–12) | verified (PermissionController 11, 12 `role_assistant_short_label`) |
| App assistente digital padrão | Android: Default digital assistant app (Android 11) | verified (AOSP 11 `default_digital_assistant_title`) |
| Nenhum | Android: None (assistant picker) | verified (AOSP 9 `app_list_preference_none`; PermissionController 10, 11 `default_app_none`) |
| Idioma e região | Android: Language & region (Android 16 where present, 17) | verified (AOSP 17 `language_and_region_settings`) |
| Voz | Android: Speech (row of Language & region, Android 17) | verified (AOSP 17 `speech_preference_title`); on Android 13–16 Speech is a heading and isn't named |
| Forçar fechamento (“forçar o fechamento”) | Android: Force stop (written as a verb phrase) | verified (AOSP 16 QPR2 `force_stop`) |
| Método de digitação por voz | Fossify Keyboard: Voice typing method | verified (Fossify `voice_typing_method`, main) |
| Digitação por voz multimodal | SwiftKey: Multi-modal voice typing | from Microsoft's support page; in-app label unverified |
| Rich Input | SwiftKey: Rich input | from Microsoft's support page; in-app label unverified; the page shows “Rich Input” as the item to tap and “Entrada Avançada” in a heading, the former used |

Sources for the #79 rows: AOSP Settings `res/values-pt-rBR/strings.xml` on the `android-9.0.0_r1` tag and the `android10-release` … `android17-release` branches; PermissionController's strings (Android 10–11: `platform/packages/apps/PackageInstaller`, Android 12: `platform/packages/modules/Permission`), which label the Default apps rows and the assistant picker on 10–12; the preference library's `expand_button_title` (`platform/frameworks/support`). Not checked against a phone maker's overlay on a device.

Sources for the #61 rows: AOSP Settings strings at https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/android16-qpr2-release/res/values-pt-rBR/strings.xml ("AOSP 16 QPR2", the Android 14+ path), the same file on `android13-release` ("AOSP 13") and `android12-release` ("AOSP 12"); Fossify Keyboard's `voice_typing_method` at https://github.com/FossifyOrg/Keyboard/blob/main/app/src/main/res/values-pt-rBR/strings.xml (main branch; the installed release not checked on a device); SwiftKey's labels from Microsoft's support page https://support.microsoft.com/pt-br/swiftkey-keyboard/how-to-disable-multimodal-voice-dictation-in-swiftkey (machine-translated by Microsoft, so the in-app label is not checked). AOSP labels are not checked against a Pixel's overlay on a device.
