# Arabic (ar) glossary

## Register

Modern Standard Arabic, addressing the user in the masculine singular as
Android's Arabic UI does ("انقر على…", "اختَر…", "يمكنك…"). Buttons and menu
actions use the verbal noun, as Android does ("تنزيل", "إلغاء", "إعادة
المحاولة", "فتح الإعدادات"); instructions in running text use the imperative
("نزِّل", "فعِّل", "حاوِل مرة أخرى"). Errors start with "تعذّر …" ("couldn't"),
progress lines with "جارٍ …" ("جارٍ التنزيل"), as in AOSP Settings.

Punctuation is Arabic: comma ، (also `list_separator`, `،\u0020`), semicolon ؛,
question mark ؟. Quotation marks are «…» (also `quoted_name`); the English
spoken words in the cleanup and end-word descriptions sit inside «…». Standalone
numbers in Arabic prose use Arabic-Indic digits ("٠٫٦ إلى ٣ غيغابايت", "٢٥", "١٠٠",
in the store listing too); names, versions and quoted English examples keep
their Western digits ("Parakeet TDT 0.6B v3", "Smart Turn v3", "SHA-256",
"1.0.0", «Twenty five percent» → «25%»), and placeholders are formatted by the
app. Units in literal text are Arabic ("غيغابايت", "ث" for
seconds, "ملي ثانية"); the app's byte sizes ("640 MB") come from code in Latin.
Menu paths use ← (pointing in the reading direction): "الإعدادات ← النظام".

Plurals: all six CLDR categories. Every item keeps its digit placeholder, so
`one` and `two` are written "%d لغة" / "%d لغتان" (the number stays, though
prose would say "لغة واحدة" / "لغتان"); `few` (3–10) takes the plural ("%d
لغات", "%d ثوانٍ"), `many` (11–99) the accusative singular ("%d مقتطفًا", "%d
لغة"), `zero` and `other` (100, 101…) the genitive singular, as AOSP Settings
does.

Right-to-left: sentences are ordered so they start with an Arabic word wherever
a natural order allows, and a Latin name never stands right next to another
Latin name or placeholder without an Arabic word between them ("تستخدم FUTO
Keyboard تطبيق Marmalade STT"), so the bidi algorithm keeps them apart. A quoted
English example that mixes digits and Latin or symbols gets a left-to-right
isolate, written as XML character references: «&#x2066;9:30 pm&#x2069;»,
«&#x2066;25%&#x2069;» (without it "9:30 pm" renders "pm 9:30" and "25%" renders
"%25"). No other bidi control characters are written into the strings.

## Terms

| English | Arabic | Notes |
|---|---|---|
| Dictate (tab, screen) | الإملاء (علامة التبويب «الإملاء» / شاشة «الإملاء») | |
| Engines (tab) | المحرّكات | Android ar calls a TTS engine "المحرّك" |
| Settings (tab, title) | الإعدادات | Android's word |
| tab | علامة التبويب | Google's word; tab names in running text in «…» |
| dictation / dictate | الإملاء / أملى (تمليه) | |
| engine / speech engine | محرّك / محرّك كلام | |
| model / speech model | نموذج / نموذج كلام | |
| language model (Qwen3 decoder) | نموذج لغوي | "مفكِّك ترميز صغير قائم على نموذج لغوي" |
| download (verb / noun) | نزِّل / تنزيل | Never "تحميل", which here means loading |
| load / unload (engine) | تحميل / إلغاء التحميل | "جارٍ تحميل النموذج…" |
| upload | رفع | "لا يتم حفظ الصوت أو رفعه"; "تحميل" avoided (many readers take it as download) |
| voice keyboard | لوحة المفاتيح الصوتية | |
| voice typing (IME name) | الكتابة الصوتية من Marmalade | Mirrors "الكتابة الصوتية من Google"; "voice typing" = الكتابة الصوتية, as Fossify and Microsoft write it |
| voice input / speech input | الإدخال الصوتي | Android's label for both |
| speech recognition service | خدمة التعرّف على الكلام | |
| speech (settings section) | الكلام | AOSP `speech_category_title` |
| End of speech | نهاية الكلام | |
| recognition language | لغة التعرّف | |
| transcribe (a language) | يتعرّف على | "لا يتعرّف على %1$s" |
| capital letters and punctuation (Granite) | الأحرف الكبيرة وعلامات الترقيم | The formatter *adds* them ("تضيفها") |
| speak-now popup / sheet | نافذة «تحدّث الآن» | |
| Speak now | تحدّث الآن | |
| auto-stop | إيقاف تلقائي | Auto-stop after a pause = إيقاف تلقائي بعد وقفة |
| pause (in speech) | وقفة | Pause length = مدة الوقفة; Pause listening = إيقاف الاستماع مؤقتًا; Paused = متوقف مؤقتًا |
| Tap to stop | النقر للإيقاف | |
| end word | كلمة الإنهاء | |
| patience (Smart Turn) | مهلة Smart Turn: سريع / عادي / متمهّل | "مهلة" (the wait before acting), not "صبر" (a person's patience); the options describe Smart Turn, so masculine |
| tap / touch & hold | انقر / انقر مع الاستمرار | Android's words |
| default (adjective) | افتراضي | Clearer than Android's "تلقائي", which also means "automatic" and is this app's word for *Auto* ("تلقائي (%1$s)"); "Default (CPU)" = "افتراضي (CPU)". Android's label "التطبيقات التلقائية" is quoted as is |
| accessibility | تسهيل الاستخدام | Android ar: "أدوات تسهيل الاستخدام" (AOSP 16 QPR2 `accessibility_settings`) |
| Skip (button) | تخطّي | As "تخطّي الإعداد" |
| unlike (engine card) | على عكس | Not "بخلاف", which also means "apart from" |
| text cleanup | تنظيف النص | |
| filler words | كلمات الحشو | |
| voice commands | الأوامر الصوتية | |
| punctuation words | كلمات الترقيم | |
| replacement dictionary | قاموس الاستبدال | |
| rule | قاعدة | Add rule = إضافة قاعدة |
| right side (of a rule) | النص البديل | Not "الجانب الأيمن": in a right-to-left layout the replacement is on the left |
| snippet | مقتطف | Add snippet = إضافة مقتطف |
| trigger phrase | عبارة التشغيل | |
| phrase (dictionary) | عبارة | |
| capital letter / case | حرف كبير / حالة الأحرف | Arabic has no case; the feature affects Latin text |
| setup guide | دليل الإعداد | |
| Developer engines | محرّكات المطوّرين | |
| streaming (engine badge) | فوري | Text appears as you speak; "بث" would read as broadcasting |
| take (benchmark) | تسجيل | segments = مقاطع |
| benchmark | قياس الأداء | A/B benchmark = مقارنة A/B |
| keep in memory / save | يُبقي في الذاكرة / يحفظ | Different roots, so "kept in memory" never reads as "saved" |
| Privacy policy | سياسة الخصوصية | |
| analytics | تحليلات | |
| Open-source licenses | تراخيص البرامج المفتوحة المصدر | Android's wording |
| Support / Report a bug / More Marmalade | الدعم / الإبلاغ عن خطأ / المزيد من Marmalade | |
| Quality: Best / Great / Good / Fair / Basic | الأفضل / ممتازة / جيدة / مقبولة / أساسية | Feminine (الجودة); "الأفضل" is invariable |
| Speed: Fastest / Very fast / Fast / Moderate / Slow | الأسرع / سريع جدًا / سريع / متوسط / بطيء | Masculine (المحرّك) |
| Recommended for this phone | موصى به لهذا الهاتف | |
| Light / Dark / System | فاتح / داكن / النظام | |
| Wallpaper / Midnight / Forest / Berry | الخلفية / منتصف الليل / الغابة / التوت | |
| high contrast (the colour preset, #68) | التباين العالي (switch: “استخدام ألوان عالية التباين”) | matches AOSP `accessibility_toggle_high_text_contrast_preference_title` (Android 16 QPR2 source, not checked on a device) |
| mic key (a keyboard's) | مفتاح الميكروفون | |
| phone | هاتف | Never "جهاز" in the app's own text |

Language names in placeholders: Java's Arabic display names are definite nouns
("الإنجليزية", "اليابانية"; some are not nisba adjectives, e.g. "الإسبرانتو"
or names starting with "لغة"). The strings therefore use them only where a bare
definite noun is grammatical whatever its form: as a direct object ("لا يتعرّف
على %1$s", "إذا كنت تتحدث %1$s"), after a colon ("لغة الاختبار: %1$s"), or in
parentheses ("تلقائي (%1$s)"). Never after a prefixed particle (بـ، لـ، كـ),
never as an adjective of "اللغة", and never referred back to with a gendered
pronoun.

## Kept untranslated

Marmalade, Marmalade STT, all engine and model names (Parakeet Ultra, Parakeet
110M, Parakeet Japanese, Canary 180M Flash, Nemotron 3.5, Whisper Large v3
Turbo, Granite Speech, Qwen3-ASR, and upstream names in descriptions such as
Parakeet TDT 0.6B v3 and large-v3-turbo), Smart Turn, FUTO Keyboard, GitHub,
GitHub Sponsors, Silero VAD, ONNX Runtime, CPU, LibriSpeech, FLEURS, AMI,
NVIDIA, OpenAI, IBM, Alibaba, Moondream, WER, RTF, RSS, CTC, PRIVACY.md, API,
A/B, Markdown, MB/GB from the app's byte formatter. Also kept: the English spoken
words in the cleanup and end-word descriptions, the "teh"/"tehran" dictionary
example and the "my email" snippet example (both show case-insensitive
matching, which Arabic script has no way to show; the rules they illustrate hold
in any language), and the Korean "메일" example.

## External labels named

| Label (English) | Arabic used | Status |
|---|---|---|
| Android: Settings | الإعدادات | verified (AOSP 16 QPR2 `settings_label`) |
| Android: Apps | التطبيقات | verified (AOSP 16 QPR2, AOSP 12 `apps_dashboard_title`) |
| Android: Permissions (app info) | الأذونات | verified (AOSP 16 QPR2 `permissions_label`) |
| Android: System | النظام | verified (AOSP 16 QPR2 `header_category_system`) |
| Android: Settings → System → Keyboard → On-screen keyboard | الإعدادات ← النظام ← لوحة المفاتيح ← لوحة المفاتيح على الشاشة | verified (AOSP 16 QPR2 `keyboard_settings`, `virtual_keyboard_category`) |
| Android: keyboard settings (generic) | إعدادات لوحة المفاتيح في Android | descriptive, not a label |
| Android: OK (second keyboard dialog) | حسنًا | verified (AOSP 16 QPR2 `dlg_ok`, `okay`) |
| Android: home screen | الشاشة الرئيسية | verified (AOSP 16 QPR2 `preview_pager_home_title`) |
| Android: System → Languages (Android 14–16) | اللغات | verified (AOSP 16 QPR2 `languages_settings`) |
| Android: Voice input (also the search term) | الإدخال الصوتي | verified (AOSP 16 QPR2, 13, 12 `voice_input_settings_title`) |
| Android: Languages & input (Android 13) | اللغات والإدخال | verified (AOSP 13 `language_settings`) |
| Android: Apps & notifications (Android 9–11) | التطبيقات والإشعارات | verified (AOSP 9, 10, 11 `app_and_notification_dashboard_title`) |
| Android: Advanced (the collapsed group on Apps & notifications, Android 9–11) | إعدادات متقدمة | verified in source (preference library `expand_button_title`: Android 9 tag for 9; AndroidX `androidx-preference-release` for 10–11, whose exact bundled version wasn't checked) |
| Android: Default apps (Android 9–12) | التطبيقات التلقائية | verified (AOSP 9–12 Settings `app_default_dashboard_title`, the row tapped) |
| Android: Assist & voice input (Android 9) | المساعد والإدخال الصوتي | verified (AOSP 9 `assist_and_voice_input_title`) |
| Android: Assist app (Android 9–10) | تطبيق المساعد; the row on Android 10's page (set to None): التطبيق المساعد | verified (AOSP 9 `default_assist_title`; Android 10 Default apps row: PermissionController `role_assistant_short_label`; Android 10 page row: AOSP 10 `default_assist_title`) |
| Android: Digital assistant app (Default apps row, Android 11–12) | تطبيق المساعد الرقمي | verified (PermissionController 11, 12 `role_assistant_short_label`) |
| Android: Default digital assistant app (Android 11) | تطبيق المساعد الرقمي التلقائي | verified (AOSP 11 `default_digital_assistant_title`) |
| Android: None (assistant picker) | بدون (Android 9); غير محدَّد (Android 10–11) | verified (AOSP 9 `app_list_preference_none`; PermissionController 10, 11 `default_app_none`) |
| Android: Language & region (Android 16 where present, 17) | اللغة والمنطقة | verified (AOSP 17 `language_and_region_settings`) |
| Android: Speech (row of Language & region, Android 17) | الكلام | verified (AOSP 17 `speech_preference_title`); on Android 13–16 Speech is a heading and isn't named |
| Android: Force stop (written as a verb phrase) | فرض الإيقاف ("إذا فرضتَ إيقاف …") | verified (AOSP 16 QPR2 `force_stop`) |
| Google voice typing | «الكتابة الصوتية من Google» | unverified (Gboard's Arabic name not checked) |
| FUTO: Settings | الإعدادات | verified (FUTO translations repo `core/values-ar`, `keyboard_language_settings`); the installed build not checked on a device |
| FUTO: Voice Input | الإدخال الصوتي | verified (same repo, `voice_input_settings_title`; same caveat) |
| FUTO: Disable built-in voice input | «تعطيل إدخال الصوت المدمج» | verified (same repo; same caveat), quoted verbatim |
| FUTO: Use voice input provided by external app | «استخدام الإدخال الصوتي الذي يوفره التطبيق الخارجي» | verified (same repo; same caveat), quoted verbatim |
| Fossify Keyboard: Voice typing method | «طريقة الكتابة الصوتية» | verified (Fossify `voice_typing_method`, main) |
| SwiftKey: Multi-modal voice typing | «الكتابة الصوتية متعددة الوسائط» | from Microsoft's support page; in-app label unverified |
| SwiftKey: Rich input | الإدخال الغني | from Microsoft's support page, which also says "إدخال منسق" for the same item; in-app label unverified |

Sources for the #79 rows: AOSP Settings `res/values-ar/strings.xml` on the `android-9.0.0_r1` tag and the `android10-release` … `android17-release` branches; PermissionController's strings (Android 10–11: `platform/packages/apps/PackageInstaller`, Android 12: `platform/packages/modules/Permission`), which label the Default apps rows and the assistant picker on 10–12; the preference library's `expand_button_title` (`platform/frameworks/support`). Not checked against a phone maker's overlay on a device.

Sources: AOSP Settings strings at https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/android16-qpr2-release/res/values-ar/strings.xml ("AOSP 16 QPR2", the Android 14+ path), the same file on `android13-release` ("AOSP 13") and `android12-release` ("AOSP 12"); FUTO's labels from https://gitlab.futo.org/keyboard/keyboard-translations-public (`core/values-ar/strings-uix.xml`, master); Fossify Keyboard's `voice_typing_method` at https://github.com/FossifyOrg/Keyboard/blob/main/app/src/main/res/values-ar/strings.xml (main branch; the installed release not checked on a device); SwiftKey's labels from Microsoft's support page https://support.microsoft.com/ar-sa/swiftkey-keyboard/how-to-disable-multimodal-voice-dictation-in-swiftkey (machine-translated by Microsoft, so the in-app label is not checked). AOSP labels are not checked against a Pixel's overlay on a device.
