# Spanish (es) glossary

## Register and style

- *Tú*, neutral international Spanish, as in marmalade-tts-android's
  `values-es`. Imperatives in the *tú* form ("Toca", "Descarga", "Inténtalo de
  nuevo"); button labels in the infinitive ("Descargar", "Permitir el
  micrófono").
- Errors use the preterite: "No se pudo …" (as the TTS app does).
- Quotation marks: “…”. Decimal comma in prose numbers ("0,6 y 3 GB").
- Plurals: `one`, `many`, `other`. `many` (1 000 000 and similar) uses
  "de" before the noun, as the TTS app does: "%d de idiomas".
- "Ajustes" for Settings, as the TTS app does and as Pixels in Spain show.
  Latin American Pixels say "Configuración"; `values-es` serves both.

## Terms

| English | Spanish |
|---|---|
| app | app |
| dictation / dictate | dictado / dictar |
| Dictate (tab, screen) | Dictar |
| Engines (tab) | Motores |
| Settings (tab, screen) | Ajustes |
| engine | motor |
| model / speech model | modelo / modelo de voz |
| download (verb / noun) | descargar / descarga |
| unload (an engine from memory) | liberar (not "descargar", which means download) |
| remove (model files) | eliminar |
| voice keyboard | teclado de voz |
| Marmalade voice typing (IME name) | Dictado por voz de Marmalade |
| Voice typing (IME subtype) | Dictado por voz |
| voice input / speech input | entrada de voz |
| speak-now popup | ventana para hablar |
| Speak now | Habla ahora |
| speech (settings section, "speech is recognized") | voz |
| End of speech (settings section) | Fin del dictado |
| recognition language | idioma de reconocimiento |
| Auto-detect | Detección automática |
| text cleanup | limpieza del texto |
| filler words | muletillas |
| voice commands | comandos de voz |
| replacement dictionary | diccionario de sustituciones |
| rule | regla |
| snippet | fragmento |
| trigger phrase | frase de activación |
| end word | palabra de cierre |
| auto-stop | parada automática |
| Auto-stop after a pause | Parada automática tras una pausa |
| Pause length | Duración de la pausa |
| Smart Turn patience: Quick / Normal / Relaxed | Paciencia de Smart Turn: Rápida / Normal / Relajada |
| lock listening | bloquear la escucha |
| Developer engines | Motores para desarrolladores |
| benchmark | banco de pruebas |
| take (a recording) | toma |
| streaming (engine type) | streaming (kept) |
| chunk / step | bloque / paso |
| setup guide | guía de configuración |
| tab | pestaña |
| Support / Support development | Apoyo / Apoyar el desarrollo |
| Report a bug | Informar de un error |
| issue (GitHub) | incidencia |
| About | Acerca de |
| Open-source licenses | Licencias de código abierto |
| Privacy policy | Política de privacidad |
| offline | sin conexión |
| quality words: Best / Great / Good / Fair / Basic | Óptima / Muy buena / Buena / Aceptable / Básica |
| speed words: Fastest / Very fast / Fast / Moderate / Slow | El más rápido / Muy rápido / Rápido / Moderado / Lento |
| Appearance colours: Wallpaper / Midnight / Forest / Berry | Fondo de pantalla / Medianoche / Bosque / Frutos rojos |
| high contrast (the colour preset, #68) | Alto contraste (switch: “Usar colores de alto contraste”); matches AOSP `accessibility_toggle_high_text_contrast_preference_title` (Android 16 QPR2 source, not checked on a device) |
| mic key (a keyboard's) | tecla de micrófono |
| speech recognition service (Android's) | servicio de reconocimiento de voz |

## Untranslated

Marmalade, Marmalade STT, all engine and model names (Parakeet Ultra, Parakeet
110M, Parakeet Japanese, Canary 180M Flash, Nemotron 3.5, Whisper Large v3
Turbo, Granite Speech, Qwen3-ASR, and upstream names in descriptions such as
Parakeet TDT 0.6B v3 and large-v3-turbo), Smart Turn, FUTO Keyboard, GitHub,
GitHub Sponsors, Silero VAD, ONNX Runtime, CPU, LibriSpeech, FLEURS, AMI,
NVIDIA, OpenAI, IBM, Alibaba, Moondream, WER, RTF, RSS, CTC, Markdown,
`PRIVACY.md`, "streaming". The spoken English words in the cleanup and end-word
descriptions ("over", "um", "new line", "period", "Twenty five percent"…) stay
in English.

Adapted examples: the dictionary help uses “haber” → “A ver” (a real Spanish
mishearing) instead of “teh”; the snippet help uses “mi casa” / “mi
casamiento” instead of “my email” / “my emailing”. Both keep the rule they
illustrate (whole-word matching, case following what was heard).

## Labels of Android and FUTO Keyboard

| Label in our text | Where | Status |
|---|---|---|
| Ajustes (Android Settings) | Android | verified (Pixel, es-ES; es-419 shows "Configuración") |
| Aplicaciones | Android Settings | verified (Pixel, es-ES) |
| Permisos | Android app info | verified (Pixel, es-ES) |
| Ajustes → Sistema → Teclado → Teclado en pantalla | Android Settings | unverified ("Sistema" is sure; "Teclado" and "Teclado en pantalla" not checked on a Pixel) |
| Aceptar (the OK button of the direct-boot dialog) | Android system dialog | unverified |
| Ajustes → Entrada de voz | FUTO Keyboard settings | unverified (FUTO's Spanish not checked) |
| “Desactivar la entrada de voz integrada” | FUTO Keyboard, Voice Input | unverified |
| “Usar la entrada de voz de una app externa” | FUTO Keyboard, Voice Input (subtitle) | unverified |
| Idiomas | Android: System → Languages (Android 14–16) | verified (AOSP 16 QPR2 `languages_settings`) |
| Entrada de voz | Android: Voice input (also the search term) | verified (AOSP 16 QPR2, AOSP 13, AOSP 12 `voice_input_settings_title`) |
| Idiomas e introducción de texto | Android: Languages & input (Android 13) | verified (AOSP 13 `language_settings`) |
| Aplicaciones y notificaciones | Android: Apps & notifications (Android 9–11) | verified (AOSP 9, 10, 11 `app_and_notification_dashboard_title`) |
| Avanzada (Android 9); Ajustes avanzados (Android 10–11) | Android: Advanced (the collapsed group on Apps & notifications, Android 9–11) | verified in source (preference library `expand_button_title`: Android 9 tag for 9; AndroidX `androidx-preference-release` for 10–11, whose exact bundled version wasn't checked) |
| Aplicaciones predeter. (Android 9); Aplicaciones predeterminadas (Android 10–12) | Android: Default apps (Android 9–12) | verified (AOSP 9–12 Settings `app_default_dashboard_title`, the row tapped) |
| Asist. y entrada de voz | Android: Assist & voice input (Android 9) | verified (AOSP 9 `assist_and_voice_input_title`) |
| Aplicación de asistencia | Android: Assist app (Android 9–10) | verified (AOSP 9 `default_assist_title`; Android 10 Default apps row: PermissionController `role_assistant_short_label`; Android 10 page row: AOSP 10 `default_assist_title`) |
| Asistente digital | Android: Digital assistant app (Default apps row, Android 11–12) | verified (PermissionController 11, 12 `role_assistant_short_label`) |
| Asistente digital predeterminado | Android: Default digital assistant app (Android 11) | verified (AOSP 11 `default_digital_assistant_title`) |
| Ninguna (Android 9); Ninguno (Android 10); Ninguna (Android 11) | Android: None (assistant picker) | verified (AOSP 9 `app_list_preference_none`; PermissionController 10, 11 `default_app_none`) |
| Idioma y región | Android: Language & region (Android 16 where present, 17) | verified (AOSP 17 `language_and_region_settings`) |
| Voz | Android: Speech (row of Language & region, Android 17) | verified (AOSP 17 `speech_preference_title`); on Android 13–16 Speech is a heading and isn't named |
| Forzar detención (“fuerzas la detención”) | Android: Force stop (written as a verb phrase) | verified (AOSP 16 QPR2 `force_stop`) |
| Método de escritura por voz | Fossify Keyboard: Voice typing method | verified (Fossify `voice_typing_method`, main) |
| Escritura por voz multimodal | SwiftKey: Multi-modal voice typing | from Microsoft's support page; in-app label unverified |
| Entrada enriquecida | SwiftKey: Rich input | from Microsoft's support page; in-app label unverified |

Sources for the #79 rows: AOSP Settings `res/values-es/strings.xml` on the `android-9.0.0_r1` tag and the `android10-release` … `android17-release` branches; PermissionController's strings (Android 10–11: `platform/packages/apps/PackageInstaller`, Android 12: `platform/packages/modules/Permission`), which label the Default apps rows and the assistant picker on 10–12; the preference library's `expand_button_title` (`platform/frameworks/support`). Not checked against a phone maker's overlay on a device.

Sources for the #61 rows: AOSP Settings strings at https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/android16-qpr2-release/res/values-es/strings.xml ("AOSP 16 QPR2", the Android 14+ path), the same file on `android13-release` ("AOSP 13") and `android12-release` ("AOSP 12"); Fossify Keyboard's `voice_typing_method` at https://github.com/FossifyOrg/Keyboard/blob/main/app/src/main/res/values-es/strings.xml (main branch; the installed release not checked on a device); SwiftKey's labels from Microsoft's support page https://support.microsoft.com/es-es/swiftkey-keyboard/how-to-disable-multimodal-voice-dictation-in-swiftkey (machine-translated by Microsoft, so the in-app label is not checked). AOSP labels are not checked against a Pixel's overlay on a device.
