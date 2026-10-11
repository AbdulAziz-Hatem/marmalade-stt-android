# Ukrainian (uk) glossary

## Register

Polite **ви**, lowercase, as Android's Ukrainian UI addresses the user.
Instructions use the ви imperative as Android does („Натисніть“, „Відкрийте“,
„Повторіть спробу“); buttons use the infinitive („Завантажити“, „Відкрити
налаштування“, „Скасувати“); errors start with „Не вдалося …“ as Android's do;
status lines use nouns („Підготовка моделі…“, „Завантаження · …“). The two
live status lines speak as the app in the first person („Слухаю“, „Чую
вас…“), as Google's „Говоріть“ prompt does. Quotation marks are «…» (also
`quoted_name`); labels of this app and of Android are quoted in running text
(„на вкладці «Рушії»“, „у розділі «Дозволи»“), menu paths with → are not. The
apostrophe is the typographic ’ (з’являться, пам’ять), as most of AOSP's
Ukrainian strings write it. Decimals in literal text use a comma („0,6–3
ГБ“); units are Ukrainian (с, мс, ГБ). The percent ring keeps `%1$d%%`
without a space.

"Phone" is **телефон**; "app" is **додаток**, as Android's Ukrainian UI says.

Plurals: `one`, `few`, `many`, `other`. `other` is the fraction form (1,5
мови) and takes the genitive singular: „%d мови“, „%1$d правила“, „%1$d
шаблону“, „%1$d рядка“, „до %1$d секунди“. `benchmark_no_take` uses „до“ +
genitive: one „секунди“, few/many „секунд“, other „секунди“.

## Terms

| English | Ukrainian | Notes |
|---|---|---|
| Dictate (tab, screen) | Диктування (вкладка / екран «Диктування») | |
| Engines (tab) | Рушії (вкладка «Рушії») | рушій, the standard Ukrainian word for a software engine |
| Settings (tab, title) | Налаштування | as Android |
| dictation | диктування | „Почати диктування“ |
| engine / speech engine | рушій / рушій розпізнавання | |
| model / speech model | модель / модель мовлення | „мовна модель“ is the language model (Qwen3 decoder) |
| download (verb / noun) | завантажити / завантаження | as Android |
| load / unload (a model into memory) | запуск, підготовка / вивантаження | „завантаження“ is taken by *download*: „Підготовка моделі…“, „Не вдалося запустити модель мовлення“, benchmark „Запуск 1,2 с“ |
| upload (privacy text) | вивантажувати | „Аудіо ніколи не зберігається й не вивантажується“ |
| offline | без інтернету (UI), офлайн (listing, compounds) | |
| voice keyboard | голосова клавіатура | |
| Marmalade voice typing (IME name) | Голосовий ввід Marmalade | mirrors Android's „Голосовий ввід“ and Google's „Голосовий ввід Google“; quoted «…» in running text |
| voice typing / voice input / speech input | голосовий ввід | one term for all three, as Android |
| speech (settings section, what is spoken) | Мовлення | Android's own section name |
| End of speech | Кінець мовлення | |
| Tap to stop | Зупинка натисканням | |
| stops (End of speech descriptions) | зупиняє запис | always with its object („перш ніж зупинити запис“) |
| auto-stop | автозупинка | „Автозупинка після паузи“, „Автозупинка зі Smart Turn“ |
| end word / Say an end word / End words | стоп-слово / Стоп-слово / Стоп-слова | |
| Pause length | Тривалість паузи | |
| Smart Turn patience: Quick / Normal / Relaxed | Очікування Smart Turn: Коротке / Звичайне / Довге | neuter, agrees with „очікування“ |
| lock / locked (hold the mic) | закріпити / Закріплено | „заблоковано“ would read as "disabled" |
| recognition language | мова розпізнавання | |
| text cleanup | очищення тексту | |
| filler words | слова-паразити | |
| voice commands | голосові команди | |
| punctuation words | розділові знаки голосом | |
| Write numbers as digits | Числа цифрами | |
| Capitalize sentences | Речення з великої літери | |
| replacement dictionary | словник замін | |
| rule | правило | Add rule = Додати правило |
| snippet | шаблон | Add snippet = Додати шаблон |
| trigger phrase | кодова фраза | |
| What's heard / Write instead | Що чути / Замінити на | |
| Edit | Редагувати | |
| setup guide | майстер налаштування | |
| Developer engines | Рушії для розробників | Developer (pill, section) = Для розробників |
| streaming | потоковий | |
| take (benchmark) | дубль | |
| benchmark | бенчмарк | A/B-бенчмарк |
| tab | вкладка | |
| Speak now / speak-now popup | Говоріть / вікно «Говоріть» | |
| Privacy policy | Політика конфіденційності | |
| analytics / tracking / accounts | аналітика / стеження / облікові записи | |
| Support / Support development / More Marmalade / Report a bug | Підтримка / Підтримати розробку / Більше від Marmalade / Повідомити про помилку | |
| About | Про додаток | „Про Marmalade STT“ for the row |
| Appearance | Вигляд | |
| Speed: Fastest / Very fast / Fast / Moderate / Slow | Найшвидший / Дуже швидкий / Швидкий / Середній / Повільний | masculine (рушій) |
| Quality: Best / Great / Good / Fair / Basic / Not rated | Найкраща / Чудова / Добра / Прийнятна / Базова / Без оцінки | feminine (якість) |
| Mode: System / Light / Dark | Режим: Як у системі / Світлий / Темний | |
| Color: Wallpaper / Midnight / Forest / Berry | Колір: Шпалери / Північ / Ліс / Ягоди | |
| high contrast (the colour preset, #68) | Високий контраст (switch: “Використовувати висококонтрастні кольори”) | matches AOSP `accessibility_toggle_high_text_contrast_preference_title` (Android 16 QPR2 source, not checked on a device) |
| mic key (a keyboard's) | клавіша мікрофона | |
| keyboard button in the navigation bar | кнопка клавіатури на панелі навігації | descriptive |
| speech recognition service (Android's) | служба розпізнавання мовлення | also the summary row |
| speech-to-text (store short description) | диктування | „розпізнавання мовлення“ didn't fit 80 characters (fr made the same choice) |

Language names in placeholders: Android's Ukrainian display names are
lowercase feminine adjectives in the nominative („англійська“, „японська“;
„гінді“ indeclinable) and can't be inflected. A verb's object would need the
accusative („англійську“), so names never follow a verb or a preposition
directly. They stand only after a colon („Не розпізнає мову: японська“, „Мова
тесту: японська“, „мова цього рушія: англійська“, lists) or in apposition in
parentheses („для вашої мови (японська)“, „Чудова (англійська)“, „Авто
(німецька)“). A placeholder never starts a sentence (it is lowercase).

## Kept untranslated

Marmalade, Marmalade STT, all engine and model names (Parakeet Ultra, Parakeet
110M, Parakeet Japanese, Canary 180M Flash, Nemotron 3.5, Whisper Large v3
Turbo, Granite Speech, Qwen3-ASR, and upstream names in descriptions such as
Parakeet TDT 0.6B v3 and large-v3-turbo), Smart Turn, FUTO Keyboard, GitHub,
GitHub Sponsors, Silero VAD, ONNX Runtime, CPU, LibriSpeech, FLEURS, AMI,
NVIDIA, OpenAI, IBM, Alibaba, Moondream, WER, RTF, RSS, CTC, PRIVACY.md,
Markdown, encoder-decoder, issue (GitHub), spinning (developer screen), Rich
Input (SwiftKey, as Microsoft's Ukrainian page keeps it). The English spoken
words in the cleanup and end-word descriptions („um“, „new line“, „period“,
„Twenty five percent“, „over“ …) stay English in «…», and the „teh“/„tehran“
dictionary example stays. The snippet example was adapted: «мій номер»
matches «Мій номер» but not «мій номерок».

## External labels named

| Label (English) | Ukrainian used | Status |
|---|---|---|
| Android: Settings | Налаштування | verified (AOSP 16 QPR2 `settings_label`) |
| Android: Apps | Додатки | verified (AOSP 16 QPR2 `apps_dashboard_title`) |
| Android: Permissions (app info) | Дозволи | verified (AOSP 16 QPR2 `permissions_label`) |
| Android: System | Система | verified (AOSP 16 QPR2 `header_category_system`) |
| Android: Keyboard | Клавіатура | verified in AOSP (`keyboard_settings`); Pixel overlay not checked on a device |
| Android: On-screen keyboard | Екранна клавіатура | verified in AOSP (`virtual_keyboard_category`); Pixel overlay not checked on a device |
| Android: keyboard settings (generic) | налаштування клавіатури Android | descriptive, not a label |
| Android: OK (direct-boot dialog) | OK (Latin) | verified (frameworks/base 16 QPR2 `ok`) |
| Android: home screen | головний екран | descriptive |
| Android: Google voice typing | Голосовий ввід Google | unverified (Gboard's label, not checked on a device) |
| Android: System → Languages (Android 14–16) | Мови | verified (AOSP 16 QPR2 `languages_settings`) |
| Android: Voice input (also the search term) | Голосовий ввід | verified (AOSP 16 QPR2, AOSP 13, AOSP 12 `voice_input_settings_title`) |
| Android: Languages & input (Android 13) | Мова та введення | verified (AOSP 13 `language_settings`; AOSP 16 says „Мова й введення“) |
| Android: Apps & notifications (Android 9–11) | Додатки та сповіщення | verified (AOSP 9, 10, 11 `app_and_notification_dashboard_title`) |
| Android: Advanced (the collapsed group on Apps & notifications, Android 9–11) | Додатково | verified in source (preference library `expand_button_title`: Android 9 tag for 9; AndroidX `androidx-preference-release` for 10–11, whose exact bundled version wasn't checked) |
| Android: Default apps (Android 9–12) | Додатки за умовчанням | verified (AOSP 9–12 Settings `app_default_dashboard_title`, the row tapped) |
| Android: Assist & voice input (Android 9) | Помічник і голосовий ввід | verified (AOSP 9 `assist_and_voice_input_title`) |
| Android: Assist app (Android 9–10) | Додаток-помічник (Android 9); Помічник (Android 10) | verified (AOSP 9 `default_assist_title`; Android 10 Default apps row: PermissionController `role_assistant_short_label`; Android 10 page row: AOSP 10 `default_assist_title`) |
| Android: Digital assistant app (Default apps row, Android 11–12) | Цифровий помічник | verified (PermissionController 11, 12 `role_assistant_short_label`) |
| Android: Default digital assistant app (Android 11) | Цифровий помічник за умовчанням | verified (AOSP 11 `default_digital_assistant_title`) |
| Android: None (assistant picker) | Немає | verified (AOSP 9 `app_list_preference_none`; PermissionController 10, 11 `default_app_none`) |
| Android: Language & region (Android 16 where present, 17) | Мова й регіон | verified (AOSP 17 `language_and_region_settings`) |
| Android: Speech (row of Language & region, Android 17) | Мовлення | verified (AOSP 17 `speech_preference_title`); on Android 13–16 Speech is a heading and isn't named |
| Android: Force stop (written as a verb phrase) | Зупинити („примусово зупинити“) | verified (AOSP 16 QPR2 `force_stop` is „Зупинити“); the string describes the action rather than quoting the button |
| FUTO Keyboard: Settings | Налаштування | verified (FUTO translations `keyboard_language_settings`, `core/values-uk`) |
| FUTO Keyboard: Voice Input | Голосовий ввід | verified (FUTO translations `voice_input_settings_title`); the installed 0.1.30 build not checked on a device |
| FUTO Keyboard: Disable built-in voice input | Вимкнути вбудований голосовий ввід | verified (same source, same caveat) |
| FUTO Keyboard: Use voice input provided by external app | Використовувати голосовий ввід, наданий зовнішнім додатком | verified (same source, same caveat) |
| Fossify Keyboard: Voice typing method | Метод голосового набору | verified (Fossify `voice_typing_method`, main) |
| SwiftKey: settings | налаштування SwiftKey | from Microsoft's support page; in-app label unverified |
| SwiftKey: Rich input | Rich Input | from Microsoft's support page (keeps the English name); in-app label unverified |
| SwiftKey: Multi-modal voice typing | Мультимодальний голосовий ввід | from Microsoft's support page; in-app label unverified |

Sources for the #79 rows: AOSP Settings `res/values-uk/strings.xml` on the `android-9.0.0_r1` tag and the `android10-release` … `android17-release` branches; PermissionController's strings (Android 10–11: `platform/packages/apps/PackageInstaller`, Android 12: `platform/packages/modules/Permission`), which label the Default apps rows and the assistant picker on 10–12; the preference library's `expand_button_title` (`platform/frameworks/support`). Not checked against a phone maker's overlay on a device.

Sources: AOSP Settings strings at
https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/android16-qpr2-release/res/values-uk/strings.xml
("AOSP 16 QPR2", the Android 14+ path), the same file on `android13-release`
("AOSP 13") and `android12-release` ("AOSP 12"); Android's OK from
frameworks/base `core/res/res/values-uk/strings.xml` (android16-qpr2-release);
FUTO Keyboard's labels from its public translations repository
https://gitlab.futo.org/keyboard/keyboard-translations-public (`core/values-uk`,
master; label strings read, no code used); Fossify Keyboard's
`voice_typing_method` at
https://github.com/FossifyOrg/Keyboard/blob/main/app/src/main/res/values-uk/strings.xml
(main branch; the installed release not checked on a device); SwiftKey's
labels from Microsoft's support page
https://support.microsoft.com/uk-ua/swiftkey-keyboard/how-to-disable-multimodal-voice-dictation-in-swiftkey
(machine-translated by Microsoft, so the in-app labels are not checked). AOSP
labels are not checked against a Pixel's overlay on a device.
