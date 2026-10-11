# French (fr) glossary

## Register and style

- *vous*, polite and plain, as in marmalade-tts-android's `values-fr`.
- Buttons and actions in the infinitive (« Télécharger », « Annuler »,
  « Autoriser le micro »); instructions in the imperative (« Appuyez sur… »).
- Typography: « … » with `&#160;` inside; `&#160;` before `:` `;` `?` `!`;
  `&#160;` before `%` in the percent label (`%1$d&#160;%%`). Apostrophes are
  `\'`. Decimal commas come from the formatter, except in prose written out
  in the string (« 0,6 à 3 Go »).
- Errors start with « Impossible de… » (sibling app's pattern).
- The app's own Settings tab is **Réglages**; Android's Settings app is
  **Paramètres** (as in the sibling app).
- Language names filled in by `%1$s` are lowercase French names
  (« japonais »), so sentences avoid an article before them: « en %1$s ».

## Terms

| English | French |
|---|---|
| Dictate (tab) | Dicter |
| Engines (tab) | Moteurs |
| Settings (tab, this app) | Réglages |
| tab | onglet |
| dictation | dictée |
| dictate | dicter |
| engine | moteur |
| model / speech model | modèle / modèle vocal |
| download (verb / noun) | télécharger / téléchargement |
| download again | retélécharger |
| remove | supprimer |
| voice keyboard | clavier vocal |
| voice typing, voice input, speech input | saisie vocale |
| Marmalade voice typing (IME name) | Saisie vocale Marmalade |
| Speak now | Parlez maintenant |
| speak-now popup | fenêtre « Parlez maintenant » |
| speech (settings section) | Parole |
| End of speech (settings section) | Fin de la dictée |
| recognition language | langue de reconnaissance |
| text cleanup | nettoyage du texte |
| filler words | hésitations |
| voice commands | commandes vocales |
| replacement dictionary | dictionnaire de remplacement |
| rule | règle |
| snippet | extrait |
| trigger phrase | phrase-clé |
| end word | mot de fin |
| auto-stop | arrêt automatique |
| Auto-stop after a pause | Arrêt automatique après une pause |
| Pause length | Durée de pause |
| setup guide | guide de configuration |
| microphone / mic | micro |
| privacy policy | politique de confidentialité |
| analytics | mesure d'audience |
| tracking | suivi |
| open-source licenses | licences open source |
| Support (section) | Soutien |
| About | À propos |
| Appearance | Apparence |
| Developer engines | Moteurs pour développeurs |
| benchmark | benchmark |
| take (benchmark recording) | prise |
| streaming | streaming |
| offline | hors ligne |
| save (a setting / an edit) | enregistrer |
| saved (audio, never) | sauvegardé (not « enregistré », which also means "recorded") |
| mic key (a keyboard's) | touche micro |
| speech recognition service (Android's) | service de reconnaissance vocale |
| high contrast (the colour preset, #68) | Contraste élevé (switch: “Utiliser des couleurs à contraste élevé”); matches AOSP `accessibility_toggle_high_text_contrast_preference_title` (Android 16 QPR2 source, not checked on a device) |

## Untranslated

Marmalade, Marmalade STT, every engine and model name, Smart Turn, FUTO
Keyboard, GitHub, GitHub Sponsors, Silero VAD, ONNX Runtime, CPU,
LibriSpeech, FLEURS, AMI, NVIDIA, OpenAI, IBM, Alibaba, Moondream, WER, RTF,
RSS, CTC, PRIVACY.md, Markdown. The English spoken words in the cleanup and
end-word descriptions stay English inside « ».

## External labels named

| Label | French used | Status |
|---|---|---|
| Android Settings app | Paramètres | verified |
| Android → Apps | Applications | verified |
| Android → Permissions (app info) | Autorisations | verified |
| Android Settings → System → Keyboard → On-screen keyboard | Paramètres → Système → Clavier → Clavier à l'écran | unverified (Pixel, Android 14+) |
| Android keyboard-settings dialog button "OK" | OK | unverified |
| FUTO Keyboard → Settings | Paramètres | unverified |
| FUTO Keyboard → Voice Input | Saisie vocale | unverified |
| FUTO "Disable built-in voice input" | Désactiver la saisie vocale intégrée | unverified |
| FUTO "Use voice input provided by external app" | Utiliser la saisie vocale fournie par une application externe | unverified |
| Android: System → Languages (Android 14–16) | Langues | verified (AOSP 16 QPR2 `languages_settings`) |
| Android: Voice input (also the search term) | Saisie vocale | verified (AOSP 16 QPR2, AOSP 13, AOSP 12 `voice_input_settings_title`) |
| Android: Languages & input (Android 13) | Langues et saisie | verified (AOSP 13 `language_settings`) |
| Android: Apps & notifications (Android 9–11) | Applis et notifications | verified (AOSP 9, 10, 11 `app_and_notification_dashboard_title`) |
| Android: Advanced (the collapsed group on Apps & notifications, Android 9–11) | Préférences avancées (Android 9); Paramètres avancés (Android 10–11) | verified in source (preference library `expand_button_title`: Android 9 tag for 9; AndroidX `androidx-preference-release` for 10–11, whose exact bundled version wasn't checked) |
| Android: Default apps (Android 9–12) | Applications par défaut (Android 9–11); Applis par défaut (Android 12) | verified (AOSP 9–12 Settings `app_default_dashboard_title`, the row tapped) |
| Android: Assist & voice input (Android 9) | Assist. et saisie vocale | verified (AOSP 9 `assist_and_voice_input_title`) |
| Android: Assist app (Android 9–10) | Application d'assistance | verified (AOSP 9 `default_assist_title`; Android 10 Default apps row: PermissionController `role_assistant_short_label`; Android 10 page row: AOSP 10 `default_assist_title`) |
| Android: Digital assistant app (Default apps row, Android 11–12) | Assistant numérique (Android 11); Appli d'assistant numérique (Android 12) | verified (PermissionController 11, 12 `role_assistant_short_label`) |
| Android: Default digital assistant app (Android 11) | Assistant numérique par défaut | verified (AOSP 11 `default_digital_assistant_title`) |
| Android: None (assistant picker) | Aucune | verified (AOSP 9 `app_list_preference_none`; PermissionController 10, 11 `default_app_none`) |
| Android: Language & region (Android 16 where present, 17) | Langue et région | verified (AOSP 17 `language_and_region_settings`) |
| Android: Speech (row of Language & region, Android 17) | Voix | verified (AOSP 17 `speech_preference_title`); on Android 13–16 Speech is a heading and isn't named |
| Android: Force stop (written as a verb phrase) | Forcer l'arrêt (« forcez l'arrêt ») | verified (AOSP 16 QPR2 `force_stop`) |
| Fossify Keyboard: Voice typing method | Méthode de saisie vocale | verified (Fossify `voice_typing_method`, main) |
| SwiftKey: Multi-modal voice typing | Saisie vocale multimodale | from Microsoft's support page; in-app label unverified |
| SwiftKey: Rich input | Entrée enrichie | from Microsoft's support page; in-app label unverified; the page says « Saisie enrichie » in a heading and « Entrée enrichie » for the item to tap, the latter used |

Sources for the #79 rows: AOSP Settings `res/values-fr/strings.xml` on the `android-9.0.0_r1` tag and the `android10-release` … `android17-release` branches; PermissionController's strings (Android 10–11: `platform/packages/apps/PackageInstaller`, Android 12: `platform/packages/modules/Permission`), which label the Default apps rows and the assistant picker on 10–12; the preference library's `expand_button_title` (`platform/frameworks/support`). Not checked against a phone maker's overlay on a device.

Sources for the #61 rows: AOSP Settings strings at https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/android16-qpr2-release/res/values-fr/strings.xml ("AOSP 16 QPR2", the Android 14+ path), the same file on `android13-release` ("AOSP 13") and `android12-release` ("AOSP 12"); Fossify Keyboard's `voice_typing_method` at https://github.com/FossifyOrg/Keyboard/blob/main/app/src/main/res/values-fr/strings.xml (main branch; the installed release not checked on a device); SwiftKey's labels from Microsoft's support page https://support.microsoft.com/fr-fr/swiftkey-keyboard/how-to-disable-multimodal-voice-dictation-in-swiftkey (machine-translated by Microsoft, so the in-app label is not checked). AOSP labels are not checked against a Pixel's overlay on a device.
