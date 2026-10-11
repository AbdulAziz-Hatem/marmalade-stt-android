# Russian (ru) glossary

## Register

Polite **вы**, lowercase, as Android's Russian UI addresses the user.
Instructions use the вы imperative as Android does („Нажмите“, „Откройте“,
„Повторите попытку“); buttons use the infinitive („Скачать“, „Открыть
настройки“, „Отмена“); errors start with „Не удалось …“ as Android's do;
status lines use nouns („Загрузка модели…“, „Скачивание · …“). The two live
status lines speak as the app in the first person („Слушаю“, „Слышу вас…“), as
Google's „Говорите“ popup does. Quotation marks are «…» (also `quoted_name`);
labels of this app and of Android are quoted in running text („на вкладке
«Движки»“, „в разделе «Разрешения»“), menu paths with → are not. ё is written.
Decimals in literal text use a comma („0,6–3 ГБ“); units are Russian (с, мс,
ГБ). The percent ring keeps `%1$d%%` without a space (the de review: a space
overran the ring).

"Phone" is **телефон** throughout.

Plurals: `one`, `few`, `many`, `other`. `other` is the fraction form (1,5
языка), so it takes the genitive singular, like `few`'s noun: „%d языка“,
„%1$d правила“, „до %1$d секунды“. `benchmark_no_take` uses „до“ + genitive:
one „секунды“, few/many „секунд“, other „секунды“.

## Terms

| English | Russian | Notes |
|---|---|---|
| Dictate (tab, screen) | Диктовка (вкладка / экран «Диктовка») | noun, as a tab label |
| Engines (tab) | Движки (вкладка «Движки») | движок is the everyday word for a speech engine |
| Settings (tab, title) | Настройки | as Android |
| dictation / dictate | диктовка / диктовать | „Начать диктовку“ |
| engine / speech engine | движок / движок распознавания | |
| model / speech model | модель / речевая модель | |
| language model (Qwen3 decoder) | декодер на основе языковой модели | |
| download (verb / noun) | скачать / скачивание, загрузка | buttons „Скачать“; status „Скачивание“; „Размер загрузки“, „Удалить загрузку“ |
| offline | без интернета (UI), офлайн (listing, compounds) | |
| voice keyboard | голосовая клавиатура | |
| Marmalade voice typing (IME name) | Голосовой ввод Marmalade | mirrors Android's „Голосовой ввод Google“; quoted «…» in running text |
| voice typing / voice input / speech input | голосовой ввод | Android Russian uses one term for all three |
| speech (settings section) | Речь | Android's own section name |
| End of speech | Конец речи | |
| Tap to stop | Остановка нажатием | |
| stops (End of speech descriptions) | останавливает запись | always with its object („прежде чем остановить запись“); „остановить“ alone dangles |
| auto-stop | автоостановка | „Автоостановка после паузы“, „Автоостановка со Smart Turn“; not „автостоп“ (hitchhiking) |
| end word / Say an end word / End words | стоп-слово / Стоп-слово / Стоп-слова | |
| Pause length | Длительность паузы | |
| Smart Turn patience: Quick / Normal / Relaxed | Ожидание Smart Turn: Короткое / Обычное / Долгое | neuter, agrees with „ожидание“; „терпение“ doesn't take these adjectives naturally |
| lock / locked (hold the mic) | закрепить / Закреплено | „заблокировано“ would read as "disabled" |
| recognition language | язык распознавания | |
| text cleanup | очистка текста | |
| filler words | слова-паразиты | |
| voice commands | голосовые команды | |
| punctuation words | знаки препинания голосом | |
| Write numbers as digits | Числа цифрами | |
| Capitalize sentences | Предложения с заглавной буквы | |
| replacement dictionary | словарь замен | |
| rule | правило | Add rule = Добавить правило |
| snippet | шаблон | Add snippet = Добавить шаблон; listing „голосовые шаблоны“ |
| trigger phrase | кодовая фраза | |
| What's heard / Write instead | Что слышно / Заменить на | |
| setup guide | мастер настройки | |
| Developer engines | Движки для разработчиков | Developer (pill, section) = Для разработчиков |
| streaming | потоковый | |
| take (benchmark) | дубль | |
| unload | выгрузка / выгрузить | |
| benchmark | бенчмарк | A/B-бенчмарк |
| tab | вкладка | |
| Speak now / speak-now popup | Говорите / окно «Говорите» | Google's Russian prompt |
| Privacy policy | Политика конфиденциальности | |
| analytics | аналитика | |
| Support / Support development / More Marmalade / Report a bug | Поддержка / Поддержать разработку / Ещё от Marmalade / Сообщить об ошибке | |
| About | О приложении | „О Marmalade STT“ for the row |
| Speed: Fastest / Very fast / Fast / Moderate / Slow | Быстрейший / Очень быстрый / Быстрый / Средний / Медленный | masculine (движок); one word for the tile |
| Quality: Best / Great / Good / Fair / Basic / Not rated | Лучшее / Отличное / Хорошее / Приемлемое / Базовое / Без оценки | neuter (качество) |
| Mode: System / Light / Dark | Режим: Как в системе / Светлый / Тёмный | |
| Color: Wallpaper / Midnight / Forest / Berry | Цвет: Обои / Полночь / Лес / Ягоды | |
| high contrast (the colour preset, #68) | Высокая контрастность (switch: “Использовать высокую контрастность”) | Android's Color contrast page ("Цветовая контрастность", level "Высокая"); «контрастные цвета» would read as vivid colours; not checked on a device |
| mic key (a keyboard's) | клавиша микрофона | |
| keyboard button in the navigation bar | кнопка клавиатуры на панели навигации | descriptive |
| speech recognition service (Android's) | служба распознавания речи | also the summary row |

Language names in placeholders: Android's Russian display names are lowercase
masculine adjectives in the nominative („английский“, „японский“; „хинди“ is
indeclinable) and can't be inflected. They only stand where the nominative is
grammatical: as the direct object of „распознаёт“ (inanimate accusative =
nominative: „Не распознаёт японский“), after a colon („Язык теста: японский“,
„язык: японский“), in apposition in parentheses („для вашего языка
(японский)“, „Отличное (английский)“, „Авто (немецкий)“), and in lists after a
colon. Never after „на“, „для“, „в“ or „языка“ („на японский“ ≠ „на
японском“). A placeholder never starts a sentence (it is lowercase).

## Kept untranslated

Marmalade, Marmalade STT, all engine and model names (Parakeet Ultra, Parakeet
110M, Parakeet Japanese, Canary 180M Flash, Nemotron 3.5, Whisper Large v3
Turbo, Granite Speech, Qwen3-ASR, and upstream names in descriptions such as
Parakeet TDT 0.6B v3 and large-v3-turbo), Smart Turn, FUTO Keyboard, GitHub,
GitHub Sponsors, Silero VAD, ONNX Runtime, CPU, LibriSpeech, FLEURS, AMI,
NVIDIA, OpenAI, IBM, Alibaba, Moondream, WER, RTF, RSS, CTC, PRIVACY.md,
Markdown, encoder-decoder, issue (GitHub), spinning (developer screen). The
English spoken words in the cleanup and end-word descriptions („um“, „new
line“, „period“, „Twenty five percent“, „over“ …) stay English in «…», and the
„teh“/„tehran“ dictionary example stays (an English misspelling; the rule it
shows holds in any language). The snippet example was adapted: «мой адрес»
matches «Мой адрес» but not «мой адресат».

## External labels named

| Label (English) | Russian used | Status |
|---|---|---|
| Android: Settings | Настройки | verified (AOSP 16 QPR2 `settings_label`) |
| Android: Apps | Приложения | verified (AOSP 16 QPR2 `apps_dashboard_title`) |
| Android: Permissions (app info) | Разрешения | verified (AOSP 16 QPR2 `permissions_label`) |
| Android: System | Система | verified (AOSP 16 QPR2 `header_category_system`) |
| Android: Keyboard | Клавиатура | verified in AOSP (`keyboard_settings`); Pixel overlay not checked on a device |
| Android: On-screen keyboard | Экранная клавиатура | verified in AOSP (`virtual_keyboard_category`); Pixel overlay not checked on a device |
| Android: keyboard settings (generic) | настройки клавиатуры Android | descriptive, not a label |
| Android: OK (direct-boot dialog) | ОК (Cyrillic) | verified (frameworks/base 16 QPR2 `ok`) |
| Android: home screen | главный экран | descriptive |
| Android: Google voice typing | Голосовой ввод Google | unverified (Gboard's label, not checked on a device) |
| Android: System → Languages (Android 14–16) | Языки | verified (AOSP 16 QPR2 `languages_settings`) |
| Android: Voice input (also the search term) | Голосовой ввод | verified (AOSP 16 QPR2, AOSP 13, AOSP 12 `voice_input_settings_title`) |
| Android: Languages & input (Android 13) | Язык и ввод | verified (AOSP 13 `language_settings`) |
| Android: Apps & notifications (Android 9–11) | Приложения и уведомления | verified (AOSP 9, 10, 11 `app_and_notification_dashboard_title`) |
| Android: Advanced (the collapsed group on Apps & notifications, Android 9–11) | Дополнительно | verified in source (preference library `expand_button_title`: Android 9 tag for 9; AndroidX `androidx-preference-release` for 10–11, whose exact bundled version wasn't checked) |
| Android: Default apps (Android 9–12) | Приложения по умолчанию | verified (AOSP 9–12 Settings `app_default_dashboard_title`, the row tapped) |
| Android: Assist & voice input (Android 9) | Помощник и голосовой ввод | verified (AOSP 9 `assist_and_voice_input_title`) |
| Android: Assist app (Android 9–10) | Помощник | verified (AOSP 9 `default_assist_title`; Android 10 Default apps row: PermissionController `role_assistant_short_label`; Android 10 page row: AOSP 10 `default_assist_title`) |
| Android: Digital assistant app (Default apps row, Android 11–12) | Цифровой помощник | verified (PermissionController 11, 12 `role_assistant_short_label`) |
| Android: Default digital assistant app (Android 11) | Цифровой помощник по умолчанию | verified (AOSP 11 `default_digital_assistant_title`) |
| Android: None (assistant picker) | Нет | verified (AOSP 9 `app_list_preference_none`; PermissionController 10, 11 `default_app_none`) |
| Android: Language & region (Android 16 where present, 17) | Язык и регион | verified (AOSP 17 `language_and_region_settings`) |
| Android: Speech (row of Language & region, Android 17) | Речь | verified (AOSP 17 `speech_preference_title`); on Android 13–16 Speech is a heading and isn't named |
| Android: Force stop (written as a verb phrase) | Остановить („принудительно остановить“) | verified (AOSP 16 QPR2 `force_stop` is „Остановить“); the string describes the action rather than quoting the button |
| FUTO Keyboard: Settings | Настройки | verified (FUTO translations `keyboard_language_settings`, `core/values-ru`) |
| FUTO Keyboard: Voice Input | Голосовой ввод | verified (FUTO translations `voice_input_settings_title`); the installed 0.1.30 build not checked on a device |
| FUTO Keyboard: Disable built-in voice input | Отключить встроенный голосовой ввод | verified (same source, same caveat) |
| FUTO Keyboard: Use voice input provided by external app | Использовать голосовой ввод от внешнего приложения | verified (same source, same caveat) |
| Fossify Keyboard: Voice typing method | Метод голосового ввода | verified (Fossify `voice_typing_method`, main) |
| SwiftKey: settings | Параметры SwiftKey | from Microsoft's support page; in-app label unverified |
| SwiftKey: Rich input | Расширенный ввод | from Microsoft's support page; in-app label unverified |
| SwiftKey: Multi-modal voice typing | Мультимодальный голосовой ввод | from Microsoft's support page; in-app label unverified |

Sources for the #79 rows: AOSP Settings `res/values-ru/strings.xml` on the `android-9.0.0_r1` tag and the `android10-release` … `android17-release` branches; PermissionController's strings (Android 10–11: `platform/packages/apps/PackageInstaller`, Android 12: `platform/packages/modules/Permission`), which label the Default apps rows and the assistant picker on 10–12; the preference library's `expand_button_title` (`platform/frameworks/support`). Not checked against a phone maker's overlay on a device.

Sources: AOSP Settings strings at
https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/android16-qpr2-release/res/values-ru/strings.xml
("AOSP 16 QPR2", the Android 14+ path), the same file on `android13-release`
("AOSP 13") and `android12-release` ("AOSP 12"); Android's OK from
frameworks/base `core/res/res/values-ru/strings.xml` (android16-qpr2-release);
FUTO Keyboard's labels from its public translations repository
https://gitlab.futo.org/keyboard/keyboard-translations-public (`core/values-ru`,
master; label strings read, no code used); Fossify Keyboard's
`voice_typing_method` at
https://github.com/FossifyOrg/Keyboard/blob/main/app/src/main/res/values-ru/strings.xml
(main branch; the installed release not checked on a device); SwiftKey's
labels from Microsoft's support page
https://support.microsoft.com/ru-ru/swiftkey-keyboard/how-to-disable-multimodal-voice-dictation-in-swiftkey
(machine-translated by Microsoft, so the in-app labels are not checked). AOSP
labels are not checked against a Pixel's overlay on a device.
