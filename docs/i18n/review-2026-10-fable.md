# Second-opinion review of the first translations (#51, 2026-10-02)

Opus translated every locale from `TRANSLATING.md`. A Fable reviewer per
locale then read the English, the translation, the glossary and
`PRIVACY.md`, and returned fixes without editing anything. Applied fixes are
in the commit "i18n: apply the Fable review fixes"; the rejected ones and
the reasons are below.

| Locale | Fixes proposed | Applied | Rejected | Main finds |
|---|---|---|---|---|
| de | 11 | 11 | 0 | "Wie lange eine Pause … beendet" misread as a duration; "kommt mit" for "keeps up"; "Feld" for the keyboard panel; "erkennt" for "matches" in both help texts; `100 %` overran the download ring |
| es | 21 | 21 | 0 | "Elige" read as an order to the user; "desactívalo" pointed at the mic, not the other keyboard; language names need the article ("No transcribe el japonés") |
| fr | 17 | 16 | 1 | "pas de 1120 ms" read as "no 1120 ms"; "-le" with no antecedent; "premier choix en français" ambiguous |
| hi | 8 | 8 | 0 | `list_separator` lost its trailing space (aapt trims it; also in de); "अलग से" for "on its own" |
| it | 15 | 14 | 1 | "Registra una volta" (record once, ever); "riconoscere" for language detection; "caricato" also means "loaded" in this app |
| ja | 8 | 8 | 0 | Basic = 標準 ranked above まずまず; two card labels too long for the spec column |
| pt-BR | 10 | 10 | 0 | "desative-o" pointed at the mic; "nem mesmo o do Google" added emphasis PRIVACY.md doesn't make |
| zh-CN | 10 | 10 | 0 | 保存 used for both "kept in memory" and "saved"; spaces around the quoted keyboard name |

Rejected:

- **fr `onboarding_mic_open_settings`** stays « Ouvrir les paramètres de
  l'appli ». The reviewer offered the full « application » only if it
  doesn't truncate on the button; the short form is common French UI
  wording and safer on a 412 dp screen.
- **it `many` plural forms** stay "%1$d di regole" etc. The reviewer read
  `TRANSLATING.md`'s old note ("`many` reads like `other`") literally. CLDR's
  `many` is the million form, and "1000000 di regole" is correct Italian,
  as "de" is in es, fr and pt, matching marmalade-tts-android. The note now
  says so.

Code and test changes from the reviews:

- zh-CN pointed out that `InstallState.Corrupt` carried an English sentence
  shown inside a translated one; it now carries a `CorruptProblem` and the UI
  words it.
- `TranslationsTest` now also fails on leading or trailing whitespace (which
  aapt2 trims, the `list_separator` bug) and on double-escaped entities
  (`&amp;#160;`, which would show as text).

## Store listing review (#66, 2026-10-04)

The approved English short description and 1.0.0 release notes were
translated into the 8 locales the same way, then reviewed by two Fable
reviewers (de/es/fr/it and hi/ja/pt-BR/zh-CN). Every proposed fix was
applied:

| Locale | Fixes | What changed |
|---|---|---|
| de | 2 | "eigens gewählte für" → "mit eigenen für" (stilted ellipsis); "App in 9 Sprachen" → "Oberfläche in 9 Sprachen" |
| es | 2 | "opciones propias" → "algunos específicos" (says *dedicated engines*); "La app está en 9 idiomas" → "Interfaz en 9 idiomas" |
| fr | 2 | short: "Saisie vocale … au clavier" read as *typed at the keyboard*, now "Dictée privée hors ligne pour votre clavier"; "Appli en 9 langues" → "Interface en 9 langues" (room made by dropping "choisis") |
| it | 2 | "scelte dedicate a giapponese" → "alcuni dedicati al giapponese"; "App in 9 lingue" → "Interfaccia in 9 lingue" |
| hi | 1 | short: डिवाइस → फ़ोन, the app's word everywhere; ही kept (the app's own construction, not a stronger claim) |
| zh-CN | 1 | short: 设备 → 手机, the app's word (the reviewer's alternative 不会离开手机 not taken: it changes the English's form) |
| ja, pt-BR | 0 | |

The "9 languages" fixes restore the English's "the app itself": right after
a line of recognition languages, "App in 9 languages" read as the number of
languages it recognises. The changelogs run close to the 500-character limit
in the Latin-script locales (fr is 496), so their style is compact.

## Engines screen review (#67, 2026-10-04)

Four engine descriptions lost their "with punctuation and capitals" selling
point (Granite's also its false "fastest"), and the new
`engine_quality_tested_in` ("Tested in English", under the quality meter)
was added in all 8 locales. One Fable reviewer read all of them:

| Locale | Fix | Applied? |
|---|---|---|
| fr | `engine_desc_granite`: "le plus précis de nos tests" read as *the most accurate of our tests* → "dans nos tests" | yes |
| de | optional: "in unseren englischen Tests" → "in unseren Tests auf Englisch", the source strings' wording | yes |
| hi | optional: "अंग्रेज़ी में हमारे टेस्ट में" stacks two में → "अंग्रेज़ी के हमारे टेस्ट में" | yes |
| zh-CN | optional: "基于%1$s测试" could read as *based on an English exam* → "%1$s测试结果", parallel to ja | yes |
| es, it, ja, pt-BR | none | |

## Full description, keyboard switcher and Italian language names (#67, 2026-10-05)

Max's approved T2 (`fastlane/metadata/android/en-US/full_description.txt`)
was translated into the 8 locales. Three setup-guide cards
(`onboarding_kb_own_engine`, `_google_only`, `_no_mic`) now point to
Android's keyboard switcher, and the Italian strings that said "la lingua
%1$s" were rewritten. Two Fable reviewers (de/es/fr/it and hi/ja/pt-BR/zh-CN)
read all of it and checked every string that takes a language name in their
locales. Every proposed fix was applied:

| Locale | Fixes | What changed |
|---|---|---|
| de | 5 | listing: "kleines Marmalade-Feld" → "kleine Marmalade-Sprachtastatur" (round 1 ruled out "Feld" for the panel); "mit Sprachtaste" → "mit Spracheingabe-Taste", the app's word; cards: sentence-initial "Ihre Mikrofontaste" read as formal "Your" in a *du* app → "Die Mikrofontaste dieser Tastatur", and "Spracherkennung" → "Spracheingabe" (voice typing); optional: "Tastatur-Schaltfläche" → "Tastatursymbol" |
| es | 2 | optional: listing "Sin manos." (an exclamation) → "Sin usar las manos."; the two cards with "instead" in English get "en su lugar" |
| it | 3 | the glossary still taught "la lingua %1$s", now "in %1$s"; optional: listing "nessun server nostro" → "nessun nostro server", "Cinese e coreano usano" → "Il cinese e il coreano usano" |
| pt-BR | 2 | optional: "a digitação por voz própria" → "a própria digitação por voz"; `onboarding_recommend_may_be_slow` "A melhor opção … pode ficar lento" → "lenta" (agrees with "opção") |
| fr, hi, ja, zh-CN | 0 | |

Language names: the reviewers found no agreement bug outside the Italian
strings already fixed. es "el %1$s" is right for every language (all
masculine); fr only uses "en %1$s" and "(%1$s)"; de takes the capitalised
noun in every slot; pt-BR reads naturally without an article ("Não
transcreve japonês"); hi, ja and zh attach postpositions or nothing.

## Twelve more locales (#69, 2026-10-05)

ar, cs, id (`values-in`), ko, nl, pl, ru, sv, tr, uk, vi and zh-TW were
translated from `TRANSLATING.md` by four Opus translators (ru/uk/pl,
cs/sv/nl, ko/zh-TW/vi, ar/tr/id), app strings and store listing (T1, T3,
T2) together. Four Fable reviewers, one per batch, read the English with
its developer comments, the translation, the glossary, the translator's
notes, `PRIVACY.md` and the listing, and checked every string that takes a
language name. The translators applied the reviews:

| Locale | Fixes proposed | Applied | Rejected | Main finds |
|---|---|---|---|---|
| ar | 12 | 11 | 1 | Arabic-Indic digits in prose (5 strings + listing); «9:30 pm» and «25%» reordered in the RTL paragraph (now in an LRI…PDI isolate); "Default (CPU)" used the Auto word تلقائي (→ افتراضي); "بخلاف" ambiguous (→ "على عكس") |
| cs | 15 | 15 | 0 | „máte domluveno“ reads as "you have an appointment" (→ „končíte“); privacy „nenahrává“ read as "not recorded" (→ „nikam neodesílá“); a keyboard name next to "Marmalade" left who sees whom unclear |
| id | 9 | 9 | 0 | "Menanyakan FUTO Keyboard" asked *about* FUTO (→ "Bertanya kepada"); "Kamus pengganti" = a substitute dictionary (→ "Kamus penggantian"); listing "cuplikan suara" read as audio clips; language names without "bahasa" read as countries |
| ko | 6 | 6 | 0 | `dictate_mic_title` read as an order to use the mic; "%2$s에서" made the engine a place (→ "%2$s 엔진은"); 저장 용량 is Android's phone-storage label (→ 설치 크기) |
| nl | 7 | 7 | 0 | snippet help „niets verandert de tekst“ (→ „er verandert niets aan“); „Privé door ontwerp“ calque (→ „Standaard privé“) |
| pl | 9 | 9 | 0 | „w Ustawienia“ undeclined (→ „w Ustawieniach“); „rozpoznają język“ reads as *transcribe* (→ „wykrywają“, the it round-1 class); „Nagraj jedno nagranie“ tautology |
| ru | 12 | 12 | 0 | „выбран службой“ read as "chosen by the service" (→ „выбран как служба“); comma inside „только после того, как“; „расставляет регистр“ isn't a collocation |
| sv | 14 | 14 | 0 | privacy „sparas eller laddas aldrig upp“ could negate only the upload (→ „varken sparas eller laddas upp“); „Slut på tal“ read as "out of speech" (→ „Talets slut“); „skriver ut“ = prints |
| tr | 3 | 3 | 0 | "%1$d ms adımlarla" needs -lik; "Say an end word" made parallel with the other End of speech options |
| uk | 13 | 13 | 0 | `benchmark_note` "the take is never recorded" right after "record one take" (→ never saved); the same comma and "case" finds as ru |
| vi | 9 | 9 | 0 | `settings_recognition_language_desc` began with the imperative "Chọn" (the es "Elige" class); the card's size line looked like a second Download button |
| zh-TW | 4 | 4 | 0 | 小數字 could read as "decimal digits" (→ 較小的數字); 釋出 → 釋放 (Taiwan's "free up space"); 首選 used as a verb |

Rejected:

- **ar `engine_progress_percent`** keeps `%%` rather than the Arabic ٪: the
  placeholder check wants the English `%%`, and switching the sign would need
  a code and test change. Worth a native speaker's opinion.

Three bug classes recurred across batches and were checked in all 12:
`settings_patience_quick_desc` / `_relaxed_desc` said "stops" with no object
(it stops listening), `engine_desc_granite` said the formatter "sets the
case" (it adds capitals and punctuation), and `onboarding_speech_on` / `_off`
could read "chosen by the service" (it *is* the service).

Language names: the placeholder is ICU's bare nominative, so cs, pl, uk and
(partly) ru put it after a colon, in parentheses or after the noun for
"language"; tr never suffixes it; ko never puts a consonant-dependent
particle after it; vi gets "Tiếng X" with a capital T, so it only starts a
sentence, follows a colon or sits in parentheses; id adds "bahasa"; ar uses
it only as an object, after a colon or in parentheses. The reviewers found
no remaining agreement bug.

Code and test changes from #69:

- `engine_progress_state` ("%1$d percent", TalkBack) is gone: Slavic
  languages and Arabic can't make "percent" agree with a placeholder, and
  ru/uk had written "41 из 100" around it. TalkBack reads the "41%" string.
- The engine details dialog's quality tile broke "(английский)" inside the
  word; the tile now shows the word, then "Tested in English" on its own
  small line, as the card does.
- `TranslationsTest` accepts the Swedish opening quote ” and an LRI/PDI
  isolate inside the quotes of the spoken-English examples.

## The eight #51 locales brought up to the #69 standard (#70, 2026-10-05)

Two Opus fixers (de/es/fr/it and hi/ja/pt-BR/zh-CN) read every string of
the eight #51 locales against the English, looking for the three classes
#69 found in all twelve new locales, the language-name placeholders and
the earlier rounds' classes. One Fable reviewer per batch then read the
fixes and the strings the fixers had judged fine. Only real problems were
fixed:

| Locale | Fixes | What changed |
|---|---|---|
| de | 0 | "Stoppt früher" kept: "stoppt" isn't crash wording in German, and "unterbricht dich" names who is cut off |
| es | 4 | patience: "Se detiene" is Android's crash wording ("se ha detenido") → "Deja de escuchar", the app's own `recognizer_mic_stop` (reviewer); `onboarding_kb_first` "Toca … Abre" read as a second order → "Esa tecla abre"; `onboarding_speech_force_stop` "Entonces vuelve a elegir" read as Android switching back → "tendrás que volver a elegir" |
| fr | 3 | patience: "S'arrête" is the crash wording → "Arrête l'écoute", the app's `recognizer_mic_stop` (reviewer); `engine_desc_granite`: "formateur" is a *trainer* in everyday French and "la casse" jargon → "Les majuscules et la ponctuation sont ajoutées par l'application elle-même" (reviewer) |
| it | 1 | `onboarding_privacy_body` "nemmeno quello di Google" (not even) → "compreso quello di Google", PRIVACY.md's "including" (the pt-BR #51 class) |
| hi | 3 | patience: bare रुकता है is Android's "has stopped" → सुनना बंद करता है; `engine_desc_granite` "अक्षरों का छोटा-बड़ा होना" (a calque of "case") → कैपिटल अक्षर, as `cleanup_capitalize` |
| ja | 2 | patience: 停止 got its object, 聞き取りを停止 (= `recognizer_mic_stop`); the glossary still listed Basic as 標準, the value round 1 rejected (the strings say 基本) |
| pt-BR | 5 + listing | sentence-initial "Para …" reads as the preposition "For": patience and the three `settings_end_*_desc` → "Para de ouvir"; changelogs 10000001/10000002 "App em 21 idiomas" → "Interface: 21 idiomas" (the #66 class, missed in pt-BR; "Interface em 21 idiomas" would be 502 characters) |
| zh-CN | 5 | patience: 停止聆听 as zh-TW; `engine_desc_granite` 大小写 → 大写字母 (添加大小写 isn't a collocation); `cleanup_itn_desc` 小数字 (decimal digits) → 较小的数字, the zh-TW fix; `onboarding_futo_crash_once` 它 pointed at the mic → 键盘 |

Checked and fine in all eight: `onboarding_speech_on` / `_off` say Marmalade
STT *is* / *isn't* the service; every language-name slot is grammatical
(de capitalised noun after auf/für/kein, es "el %s" as every Spanish
language name is masculine, fr and it only after "en"/"in", in
parentheses or after a colon, pt without an article, hi/ja/zh with a
postposition or nothing). The End-of-speech option descriptions keep a
bare "stops" in de/es/fr/it/hi/ja/zh-CN: the option title right above
each one says auto-stop. Only pt-BR's needed the object, for the "For"
reading. The approved store listing changed only in the pt-BR changelogs.

## Accessibility strings (#68, 2026-10-05)

The 12 TalkBack strings in `strings_accessibility.xml` (download
started/ready/failed, open in browser, rule/snippet deleted, end words
reverted, the named engine buttons, benchmark finished, the setup guide's
pane title and recommendation). Round 1: the 8 #51 locales, one Opus
translator, one Fable reviewer: all OK; one optional fix applied (zh-CN
`a11y_engine_action_named` → `%2$s：%1$s`: "删除下载内容 Parakeet 110M" is
unnatural spoken, "Parakeet 110M：删除下载内容" isn't). Round 2: the 12 #69
locales, three Opus translators (ar/ru/uk/pl, cs/nl/sv/tr, id/ko/vi/zh-TW)
and one Fable reviewer per batch.

| Locale | Fixes proposed | Applied | Rejected | Main finds |
|---|---|---|---|---|
| ar | 1 | 1 | 0 | download label: "تنزيل %1$s بحجم %2$s", as `engine_card_size_download` says, not a bare "،" between two Latin runs |
| ru, uk | 1 each | 1 each | 0 | "Модель %1$s готова" → the app's engine word (Движок … готов, Рушій … готовий); «модель» is for files |
| pl | 2 | 2 | 0 | `a11y_open_in_browser` imperative → infinitive "Otworzyć w przeglądarce" (TalkBack says "Kliknij dwukrotnie, aby …"); "modelu" → "silnika" |
| nl, tr | 1 each | 1 each | 0 | download label starts with the visible button word (Downloaden: / İndir:), as the English and the card's rule do |
| tr | 1 | 0 | 1 | see rejected |
| ko | 1 | 1 | 0 | rule/snippet deleted as 합니다 sentences (spoken messages), not the 삭제됨 status form |
| id | 1 | 1 | 0 | "%1$s siap" → "%1$s sudah siap" (an announcement, not a label) |
| cs, sv, vi, zh-TW | 0 | – | – | OK |

Rejected:

- **tr `a11y_onboarding_recommended`** stays "Bu telefon için önerilen:
  %1$s" rather than the badge's "Bu telefon için önerilir": a finite verb
  before a colon is awkward Turkish, and the participle has the same stem
  (recorded in `glossary/tr.md`).

Found on the way, checked in all 20 locales: a click label
(`a11y_open_in_browser`, `cleanup_edit_action`) is spoken inside TalkBack's
own template, `template_custom_hint_for_actions` in TalkBack 15 ("%1$s to
%2$s"; pl "%1$s, aby %2$s", de "Zum %2$s %1$s", ko "%2$s하려면 %1$s하세요").
Fixed: pl `cleanup_edit_action` "Edytuj" → "Edytować", de "Im Browser
öffnen" → "Öffnen im Browser", ko "브라우저에서 열기" → "브라우저로 이동".
The rule is in `TRANSLATING.md` ("Click labels"). TalkBack's own hi and it
templates are mistranslated ("from … to …"); nothing the app can do.

## High contrast strings (#68, 2026-10-06)

Five keys for the High contrast preset: the setup guide's switch and its
line (`onboarding_high_contrast`, `_desc`), the Appearance chip
(`settings_color_high_contrast`) and TalkBack's on/off lines
(`a11y_high_contrast_on`, `_off`). Four Opus translators (de/es/fr/it/pt-BR,
hi/ja/zh-CN/zh-TW/ko, ar/ru/uk/pl/cs, nl/sv/tr/id/vi) took the "high
contrast" word from Android's own Settings strings
(`accessibility_toggle_high_text_contrast_preference_title`, the Color
contrast page's "High") and the menu path from each locale's
`settings_title` / `settings_appearance`. Four Fable reviewers checked them
against AOSP 16 QPR2 and the glossaries.

| Locales | Fixes proposed | Applied | Notes |
|---|---|---|---|
| de es fr it pt-BR | 0 | – | pt-BR "em alto contraste" is Android's own preposition |
| hi ja zh-CN zh-TW ko | 0 | – | hi ज़्यादा कंट्रास्ट is derived (Android has only a verb phrase); ja on/off lines follow the locale's other a11y lines (…しました) |
| ar ru uk pl cs | 1 | 1 | cs switch "Použít" → "Používat": AOSP Czech titles persistent switches with the imperfective, "Použít" is for buttons. ru uses Android's newer "Высокая контрастность" (the chip wraps in the FlowRow) |
| nl sv tr id vi | 0 | – | vi drops "độ" in all three strings |

Each glossary now has a "high contrast" row with its source.


**Later (#68 fixes, 2026-10-06):** `a11y_high_contrast_on` / `_off` are
removed in every locale. TalkBack already says the switch's own On/Off, and
the extra line was read twice.

## Download paused (#68 fixes, 2026-10-06)

`a11y_download_paused` ("%1$s: download paused"): TalkBack's line when a
download pauses, beside `a11y_download_ready` / `_failed`. Opus translated
ar cs de es fr hi in it ja ko and Fable translated nl pl pt-BR ru sv tr uk
vi zh-CN zh-TW. Both had the same instructions: use each locale's
`engine_status_paused` word, and mirror the shape of its
`a11y_download_failed` (name and a colon, or a whole sentence). Not
reviewed separately: each is the locale's existing "paused" word in its
existing "failed" frame.

| Locale | Translation |
|---|---|
| ar | تنزيل %1$s متوقف مؤقتًا |
| cs | %1$s: stahování pozastaveno (glossary "stahování" for a download in progress) |
| de | %1$s: Download pausiert |
| es | %1$s: descarga en pausa |
| fr | %1$s&#160;: téléchargement en pause |
| hi | %1$s: डाउनलोड रुका हुआ है |
| in | Download %1$s dijeda |
| it | %1$s: download in pausa |
| ja | %1$s のダウンロードは一時停止中です |
| ko | %1$s 다운로드가 일시중지되었습니다 |
| nl | %1$s: download gepauzeerd |
| pl | Wstrzymano pobieranie silnika %1$s |
| pt-BR | %1$s: download pausado |
| ru | Скачивание %1$s приостановлено |
| sv | %1$s: nedladdningen är pausad |
| tr | %1$s: indirme duraklatıldı |
| uk | Завантаження %1$s призупинено |
| vi | Đã tạm dừng tải xuống %1$s |
| zh-CN | %1$s：下载已暂停 |
| zh-TW | %1$s：下載已暫停 |

## Required download and the download service (#74, 2026-10-08)

Two new strings: `onboarding_model_downloading_note` ("Setup continues once
the model is ready. The download keeps going if the screen turns off or you
leave the app.", replacing `onboarding_model_background`, whose "carry on
with the next steps" stopped being true) and `download_channel_name`
("Speech model downloads", the notification channel's name in Android's
settings). `onboarding_skip_setup` was removed. Opus translated all 20
locales; one Fable reviewer checked them against each file's terms and the
glossaries. 18 OK; two fixes applied:

- ar: "يتابع الإعداد" read as transitive with no subject; now "يتواصل الإعداد".
- nl: "Het instellen" → "De installatie", matching the guide's name
  ("Installatiehulp"); "instellen" is the file's word for configuring
  something yourself.

## 1.0.0 candidate review (#81, 2026-10-10)

One Fable reviewer read the strings added or changed since the #74 review in
all 20 locales: `engine_speed_tier_very_fast` / `engine_speed_word_very_fast`
and the es/it "Fastest" change (#80), the `settings_hint_*` toasts and the
per-release `onboarding_speech_path_*` directions (#79), and the
Accessibility mode card (`onboarding_high_contrast`, `_desc`). It checked them
against the glossaries (whose #79 rows record AOSP's labels per release) and
each locale's `ime_name`, `settings_color_high_contrast` and `settings_title`;
the whitespace, apostrophe and double-escape sweep was clean. 15 locales OK;
applied:

- es `onboarding_speech_path_search`: "ábrelo" had no antecedent; "ábrela"
  (the feminine "Entrada de voz").
- hi `settings_hint_app_details`: a bare plural before में needs the oblique;
  “ऐप्लिकेशन” and “अनुमतियां” are now quoted as Android's labels, as the
  locale's speech paths already quote theirs.
- zh-CN, zh-TW `engine_speed_*_very_fast`: 很快 didn't rank clearly above
  快速 and also reads as "soon"; now 非常快 (glossaries updated).
- pt-BR `engine_speed_word_fastest` (offered as optional, applied for the
  same reason as the es/it change): "Mais rápido" can read as the
  comparative; now "O mais rápido", as its tier sentence says (glossary
  updated).

Not changed: de "Barrierefreiheitsmodus" (Android's section is
"Bedienungshilfen", but "Bedienungshilfen-Modus" is clumsy and
"Barrierefreiheit" is the standard term); es/it "El más rápido" / "Il più
veloce" are correct and masculine like the rest of the ladder, and no longer
than the de/fr tiles.

## Qwen3-ASR description (#89, 2026-10-22)

`engine_desc_qwen3` changed from "the most accurate for Chinese" to
"recommended for Chinese and Korean" (Korean is recommended for its cost,
not its accuracy). One independent Opus reviewer read all 20 locales
against the glossaries, each locale's `engine_badge_recommended` and the
store listings' names for Chinese and Korean. 19 OK; applied:

- ko: "중국어와 한국어에 추천합니다" shared the model as its subject with
  감지하며, so it read as "the model recommends Chinese and Korean"; now
  "중국어와 한국어에 추천하는 모델입니다".

## Short commands use Auto-stop (#120, 2026-10-25)

New `settings_end_apps_commands`, the line under Settings → End of speech →
Other apps until the user chooses there. One Fable reviewer read all 20
locales against each one's `settings_end_pause` (the label it quotes) and
register. 16 OK; applied, all the same case problem (a quoted label as the
verb's object):

- pl: "używają „…”" needs the genitive; now "korzystają z trybu „…”", as
  `settings_speech_pause_desc`'s "w trybie „…”".
- ru, uk: a quoted name after "используют" / "використовують" would have to
  decline; now "режим «…»", as the existing "в режиме" / "у режимі".
- tr: a bare quoted object needs the accusative; now "“…” seçeneğini kullanır".

Not applied: the reviewer's optional English rewording (mode first); it
judged the English OK as written.
