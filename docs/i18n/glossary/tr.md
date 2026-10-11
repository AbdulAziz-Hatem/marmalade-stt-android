# Turkish (tr) glossary

## Register

Polite *siz* in running text, as Android's Turkish UI does ("mikrofona
dokunun", "tekrar deneyin", "seçin"). Buttons and menu actions use the bare
imperative, as Android's buttons do ("İndir", "Tekrar dene", "Kaldır", "Ayarları
aç", "İptal", "Bitti"). Errors use the passive "… -ılamadı" ("Model
yüklenemedi"), progress lines "… -ılıyor" ("İndiriliyor"). Typographic quotes
“…” (also `quoted_name`); `list_separator` stays `,\u0020`. Apostrophes written
`\'`, for suffixes after proper names ("Android\'in", "Marmalade STT\'yi") and
after this app's and Android's labels used as names ("Ayarlar\'da"). Decimals in
literal text use a comma ("0,6 GB"). The percent sign goes first, as Turkish
writes it: `engine_progress_percent` is `%%%1$d` ("%41").

Plurals: `one`, `other`. Turkish keeps the noun singular after a number, so both
forms are the same ("1 dil", "25 dil").

## Terms

| English | Turkish | Notes |
|---|---|---|
| Dictate (tab, screen) | Dikte (Dikte sekmesi / ekranı) | |
| Engines (tab) | Motorlar (Motorlar sekmesi) | |
| Settings (tab, title) | Ayarlar | Android's word |
| tab | sekme | |
| dictation / dictate | dikte / dikte etmek | |
| engine / speech engine | motor / konuşma motoru | |
| model / speech model | model / konuşma modeli | |
| language model (Qwen3 decoder) | dil modeli | "küçük bir dil modeli kod çözücüsü" |
| download (verb / noun) | indirmek / indirme | Button "İndir"; status "İndiriliyor" |
| install (engine) | kurmak / kurulu | Kept apart from "yüklemek" (load) |
| load / unload (engine) | yükleme / bellekten kaldırma | |
| upload | karşıya yüklemek | Google's Turkish word |
| voice keyboard | sesli klavye | |
| voice typing (IME name) | Marmalade Sesle Yazma | Mirrors "Google Sesle Yazma" |
| voice input / speech input | ses girişi | Android's label "Ses girişi" |
| speech recognition service | konuşma tanıma hizmeti | |
| speech (settings section) | Konuşma | AOSP `speech_category_title` |
| End of speech | Konuşmanın sonu | |
| recognition language | tanıma dili | |
| Speak now / speak-now popup | Şimdi konuşun / “Şimdi konuşun” penceresi | |
| auto-stop | otomatik durdurma | Auto-stop after a pause = Duraklamadan sonra otomatik durdur |
| pause (in speech) | duraklama, ara | Pause length = Duraklama süresi; Pause listening = Dinlemeyi duraklat; Paused = Duraklatıldı |
| Tap to stop | Durdurmak için dokunun | |
| end word | bitiş kelimesi | Say an end word = Bitiş kelimesiyle durdur (the End-of-speech options name what the app does) |
| capital letters and punctuation (Granite) | büyük harfler ve noktalama | The formatter *adds* them ("ekler") |
| patience (Smart Turn) | Smart Turn sabrı: Hızlı / Normal / Rahat | The descriptions say what stops: "Dinlemeyi daha erken bırakır" |
| tap / touch & hold | dokunun / dokunup basılı tutun | Android's words |
| default | varsayılan | Android's word |
| text cleanup | metin temizleme | |
| filler words | dolgu kelimeleri | |
| voice commands | sesli komutlar | |
| punctuation words | noktalama kelimeleri | |
| replacement dictionary | değiştirme sözlüğü | |
| rule | kural | Add rule = Kural ekle |
| snippet | hazır metin | Add snippet = Hazır metin ekle |
| trigger phrase | tetikleyici ifade | |
| phrase (dictionary) | ifade | |
| setup guide | kurulum rehberi | |
| Developer engines | Geliştirici motorları | |
| streaming (engine badge) | akışlı | "Akışlı · %1$d ms\'lik adımlarla": a unit before a noun takes -lik |
| Recommended for this phone (TalkBack, `a11y_onboarding_recommended`) | Bu telefon için önerilen: %1$s | the badge says "önerilir"; before a colon the participle reads better (#68 review) |
| benchmark / A/B benchmark | karşılaştırma testi / A/B karşılaştırması | |
| take (benchmark) | kayıt | Record button = "Kayıt yap" (not "Kaydet", which is Save); "saved" = "saklanmaz", so the take is never "kaydedilmez" next to "kayıt" |
| Privacy policy | Gizlilik politikası | |
| private (the app's selling point) | gizli / gizliliğinizi koruyan | "gizli" as in Chrome's "Gizli pencere" |
| analytics | analiz | |
| Open-source licenses | Açık kaynak lisansları | |
| Support / Report a bug / More Marmalade | Destek / Hata bildir / Daha fazla Marmalade | |
| Quality: Best / Great / Good / Fair / Basic | En iyi / Çok iyi / İyi / Orta / Temel | |
| Speed: Fastest / Very fast / Fast / Moderate / Slow | En hızlı / Çok hızlı / Hızlı / Orta / Yavaş | |
| Light / Dark / System | Açık / Koyu / Sistem | |
| Wallpaper / Midnight / Forest / Berry | Duvar kağıdı / Gece yarısı / Orman / Böğürtlen | |
| high contrast (the colour preset, #68) | Yüksek kontrast (switch: “Yüksek kontrastlı renkleri kullan”) | matches AOSP `accessibility_toggle_high_text_contrast_preference_title` (Android 16 QPR2 source, not checked on a device) |
| mic key (a keyboard's) | mikrofon tuşu | |
| phone | telefon | |

Language names in placeholders: Java's Turkish names are capitalised and can't
take a suffix in the string ("%1$s'yi" is impossible: İngilizce'yi but
Almanca'yı). Strings put them where no suffix is needed: before the
postposition "için" ("%1$s için ilk tercihimiz"), as the bare first noun of a
compound whose head carries the suffix ("%1$s desteği yok", "%1$s
testlerimizden"), after a colon ("Test dili: %1$s") or in parentheses
("Otomatik (%1$s)").

## Kept untranslated

Marmalade, Marmalade STT, all engine and model names (Parakeet Ultra, Parakeet
110M, Parakeet Japanese, Canary 180M Flash, Nemotron 3.5, Whisper Large v3
Turbo, Granite Speech, Qwen3-ASR, and upstream names in descriptions such as
Parakeet TDT 0.6B v3 and large-v3-turbo), Smart Turn, FUTO Keyboard, GitHub,
GitHub Sponsors, Silero VAD, ONNX Runtime, CPU, LibriSpeech, FLEURS, AMI,
NVIDIA, OpenAI, IBM, Alibaba, Moondream, WER, RTF, RSS, CTC, PRIVACY.md, API,
Markdown, and "spinning"/"pinning" in parentheses on the developer screen. Also
kept: the English spoken words in the cleanup and end-word descriptions and the
"teh"/"tehran" dictionary example (an English misspelling; the rule it shows
holds in any language). The snippet example was adapted: “ev adresim” matches
“Ev adresim” but not “ev adresimde”.

## External labels named

| Label (English) | Turkish used | Status |
|---|---|---|
| Android: Settings | Ayarlar | verified (AOSP 16 QPR2 `settings_label`) |
| Android: Apps | Uygulamalar | verified (AOSP 16 QPR2, AOSP 12 `apps_dashboard_title`) |
| Android: Permissions (app info) | İzinler | verified (AOSP 16 QPR2 `permissions_label`) |
| Android: System | Sistem | verified (AOSP 16 QPR2 `header_category_system`) |
| Android: Settings → System → Keyboard → On-screen keyboard | Ayarlar → Sistem → Klavye → Dokunmatik klavye | verified (AOSP 16 QPR2 `keyboard_settings`, `virtual_keyboard_category`; Android 13 said "Ekran klavyesi") |
| Android: keyboard settings (generic) | Android'in klavye ayarları | descriptive, not a label |
| Android: OK (second keyboard dialog) | Tamam | verified (AOSP 16 QPR2 `dlg_ok`) |
| Android: home screen | ana ekran | verified (AOSP 16 QPR2 `preview_pager_home_title`) |
| Android: System → Languages (Android 14–16) | Diller | verified (AOSP 16 QPR2 `languages_settings`) |
| Android: Voice input (also the search term) | Ses girişi | verified (AOSP 16 QPR2, 13, 12 `voice_input_settings_title`) |
| Android: Languages & input (Android 13) | Diller ve giriş | verified (AOSP 13 `language_settings`) |
| Android: Apps & notifications (Android 9–11) | Uygulamalar ve bildirimler (Android 9); Uygulama ve bildirimler (Android 10–11) | verified (AOSP 9, 10, 11 `app_and_notification_dashboard_title`) |
| Android: Advanced (the collapsed group on Apps & notifications, Android 9–11) | Gelişmiş | verified in source (preference library `expand_button_title`: Android 9 tag for 9; AndroidX `androidx-preference-release` for 10–11, whose exact bundled version wasn't checked) |
| Android: Default apps (Android 9–12) | Varsayılan uygulamalar | verified (AOSP 9–12 Settings `app_default_dashboard_title`, the row tapped) |
| Android: Assist & voice input (Android 9) | Yardım ve ses girişi | verified (AOSP 9 `assist_and_voice_input_title`) |
| Android: Assist app (Android 9–10) | Yardım uygulaması; the row on Android 10's page (set to None): Asistan uygulaması | verified (AOSP 9 `default_assist_title`; Android 10 Default apps row: PermissionController `role_assistant_short_label`; Android 10 page row: AOSP 10 `default_assist_title`) |
| Android: Digital assistant app (Default apps row, Android 11–12) | Dijital asistan uygulaması | verified (PermissionController 11, 12 `role_assistant_short_label`) |
| Android: Default digital assistant app (Android 11) | Varsayılan dijital asistan uygulaması | verified (AOSP 11 `default_digital_assistant_title`) |
| Android: None (assistant picker) | Yok | verified (AOSP 9 `app_list_preference_none`; PermissionController 10, 11 `default_app_none`) |
| Android: Language & region (Android 16 where present, 17) | Dil ve bölge | verified (AOSP 17 `language_and_region_settings`) |
| Android: Speech (row of Language & region, Android 17) | Konuşma | verified (AOSP 17 `speech_preference_title`); on Android 13–16 Speech is a heading and isn't named |
| Android: Force stop (written as a verb phrase) | Durmaya zorla ("durmaya zorlarsanız") | verified (AOSP 16 QPR2 `force_stop`) |
| Google voice typing | Google Sesle Yazma | unverified (Gboard's Turkish name not checked here) |
| FUTO: Settings | Ayarlar | verified (FUTO translations repo `core/values-tr`, `keyboard_language_settings`); the installed build not checked on a device |
| FUTO: Voice Input | Ses Girişi | verified (same repo, `voice_input_settings_title`; same caveat) |
| FUTO: Disable built-in voice input | “Yerleşik ses girişini devre dışı bırak” | verified (same repo; same caveat) |
| FUTO: Use voice input provided by external app | “Dış uygulama tarafından sağlanan ses girişini kullanın” | verified (same repo; same caveat), quoted verbatim |
| Fossify Keyboard: Voice typing method | “Sesli yazı yazma” | verified (Fossify `voice_typing_method`, main); Fossify's own wording, quoted as is |
| SwiftKey: Multi-modal voice typing | “Multimodal Sesle Yazma” | from Microsoft's support page (which also says "Çok Modlu Sesle Yazma"); in-app label unverified |
| SwiftKey: Rich input | Zengin Giriş | from Microsoft's support page; in-app label unverified |

Sources for the #79 rows: AOSP Settings `res/values-tr/strings.xml` on the `android-9.0.0_r1` tag and the `android10-release` … `android17-release` branches; PermissionController's strings (Android 10–11: `platform/packages/apps/PackageInstaller`, Android 12: `platform/packages/modules/Permission`), which label the Default apps rows and the assistant picker on 10–12; the preference library's `expand_button_title` (`platform/frameworks/support`). Not checked against a phone maker's overlay on a device.

Sources: AOSP Settings strings at https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/android16-qpr2-release/res/values-tr/strings.xml ("AOSP 16 QPR2", the Android 14+ path), the same file on `android13-release` ("AOSP 13") and `android12-release` ("AOSP 12"); FUTO's labels from https://gitlab.futo.org/keyboard/keyboard-translations-public (`core/values-tr/strings-uix.xml`, master); Fossify Keyboard's `voice_typing_method` at https://github.com/FossifyOrg/Keyboard/blob/main/app/src/main/res/values-tr/strings.xml (main branch; the installed release not checked on a device); SwiftKey's labels from Microsoft's support page https://support.microsoft.com/tr-tr/swiftkey-keyboard/how-to-disable-multimodal-voice-dictation-in-swiftkey (machine-translated by Microsoft, so the in-app label is not checked). AOSP labels are not checked against a Pixel's overlay on a device.
