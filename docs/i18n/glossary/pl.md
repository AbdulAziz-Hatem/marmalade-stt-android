# Polish (pl) glossary

## Register

Informal 2nd person singular, as Android's and Google's Polish UI address the
user, with the pronoun capitalised as Google writes it (**Twój**, Twoje,
Twojego, **Cię**, **Ci**). Instructions use the informal imperative
(„Kliknij“, „Otwórz“, „Spróbuj ponownie“): for tapping, AOSP 16's Polish
mostly says „Kliknij“ (38 strings, against 12 „Dotknij“), so this app does
too. Buttons use the imperative as Android pl does („Pobierz“, „Anuluj“,
„Otwórz ustawienia“); errors start with „Nie udało się …“; status lines use
nouns („Wczytywanie modelu…“, „Pobieranie · …“). The two live status lines
speak as the app in the first person („Słucham“, „Słyszę Cię…“). Gendered
past forms of "you" (skończyłeś/skończyłaś) are avoided by rephrasing („zanim
uzna wypowiedź za zakończoną“, „Twoja wypowiedź“).

Quotation marks are „…” (also `quoted_name`); this app's tab and setting labels
are quoted in running text („na karcie „Silniki””, „w opcji „Długość
pauzy””); Android's labels and menu paths with → are not („w sekcji
Uprawnienia“), matching the English. "Android" is declined („ustawienia
Androida“, „przez Androida“) as Google's Polish does. Decimals in literal text
use a comma („0,6–3 GB“). The percent ring keeps `%1$d%%` without a space.

"Phone" is **telefon**; "app" is **aplikacja**.

Plurals: `one`, `few`, `many`, `other`. `other` is the fraction form (1,5
języka) and takes the genitive singular: „%d języka“, „%1$d reguły“, „%1$d
szablonu“, „do %1$d sekundy“. With `many` numerals the verb is singular
neuter („5 wierszy nie jest regułami i zostanie pominiętych“).

## Terms

| English | Polish | Notes |
|---|---|---|
| Dictate (tab, screen) | Dyktowanie (karta / ekran „Dyktowanie”) | |
| Engines (tab) | Silniki (karta „Silniki”) | |
| Settings (tab, title) | Ustawienia | as Android |
| dictation / dictate | dyktowanie / dyktować | |
| engine / speech engine | silnik / silnik mowy | |
| model / speech model | model / model mowy | language model (Qwen3 decoder) = model językowy |
| download (verb / noun) | pobrać / pobieranie | as Android |
| load / unload (model into memory) | wczytywanie, wczytanie / zwolnienie | |
| offline | bez internetu (UI), offline (listing, descriptions) | |
| voice keyboard | klawiatura głosowa | |
| Marmalade voice typing (IME name) | Pisanie głosowe Marmalade | mirrors Google's „Pisanie głosowe Google“; quoted „…” in running text |
| voice typing (keyboards) | pisanie głosowe | |
| voice input / speech input | wprowadzanie głosowe | FUTO's own term; Android's settings page is „Rozpoznawanie mowy“ |
| speech (settings section) | Mowa | Android's own section name |
| End of speech | Koniec wypowiedzi | |
| Tap to stop | Zatrzymanie kliknięciem | |
| stops (End of speech descriptions) | zatrzymuje nasłuchiwanie | always with its object; bare „zatrzymuje“ dangles |
| auto-stop | automatyczne zatrzymanie | „Automatyczne zatrzymanie po pauzie“, „Automatyczne zatrzymanie ze Smart Turn“; not „autostop“ (hitchhiking) |
| end word / Say an end word / End words | słowo końcowe / Słowo końcowe / Słowa końcowe | |
| Pause length | Długość pauzy | |
| Smart Turn patience: Quick / Normal / Relaxed | Oczekiwanie Smart Turn: Krótkie / Normalne / Długie | neuter, agrees with „oczekiwanie“ |
| lock / locked (hold the mic) | przypiąć / Przypięte | „zablokowane“ would read as "disabled" |
| recognition language | język rozpoznawania | |
| detect (the language) | wykrywać | „Wykrywaj automatycznie“, „Nie wykrywa języka“; never „rozpoznawać język“, which reads as transcribing it |
| open source (listing heading, licenses) | open source | „otwarte źródło“ is a calque Polish doesn't use for software |
| text cleanup | porządkowanie tekstu | |
| filler words | wypełniacze | |
| voice commands | polecenia głosowe | |
| punctuation words | interpunkcja głosem | |
| Write numbers as digits | Liczby cyframi | |
| Capitalize sentences | Wielka litera na początku zdania | |
| replacement dictionary | słownik zamian | |
| rule | reguła | Add rule = Dodaj regułę |
| snippet | szablon | Add snippet = Dodaj szablon |
| trigger phrase | fraza wywołująca | |
| What's heard / Write instead | Co słychać / Zamień na | „Wpisz zamiast“ read as an unfinished imperative |
| setup guide | kreator konfiguracji | |
| Developer engines | Silniki dla programistów | Developer (pill, section) = Dla programistów |
| streaming | strumieniowy | |
| take (benchmark) | nagranie | |
| benchmark | benchmark | Benchmark A/B |
| tab | karta | |
| Speak now / speak-now popup | Mów teraz / okno „Mów teraz” | Google's Polish prompt |
| Privacy policy | Polityka prywatności | |
| analytics / tracking / accounts | analityka / śledzenie / konta | |
| Support / Support development / More Marmalade / Report a bug | Wsparcie / Wesprzyj rozwój / Więcej od Marmalade / Zgłoś błąd | |
| About | Informacje | „O Marmalade STT“ for the row |
| Appearance | Wygląd | |
| Speed: Fastest / Very fast / Fast / Moderate / Slow | Najszybszy / Bardzo szybki / Szybki / Średni / Wolny | masculine (silnik) |
| Quality: Best / Great / Good / Fair / Basic / Not rated | Najlepsza / Świetna / Dobra / Przyzwoita / Podstawowa / Bez oceny | feminine (jakość) |
| Mode: System / Light / Dark | Tryb: Systemowy / Jasny / Ciemny | |
| Color: Wallpaper / Midnight / Forest / Berry | Kolor: Tapeta / Północ / Las / Jagody | „Tapeta“ as Android's wallpaper setting |
| high contrast (the colour preset, #68) | Duży kontrast (switch: “Używaj kolorów o dużym kontraście”) | matches AOSP `accessibility_toggle_high_text_contrast_preference_title` (Android 16 QPR2 source, not checked on a device) |
| mic key (a keyboard's) | klawisz mikrofonu | |
| keyboard button in the navigation bar | przycisk klawiatury na pasku nawigacji | descriptive |
| speech recognition service (Android's) | usługa rozpoznawania mowy | also the summary row |
| speech-to-text (store short description) | dyktowanie | „zamiana mowy na tekst“ didn't fit 80 characters (fr, uk made the same choice) |

Language names in placeholders: Android's Polish display names are lowercase
masculine adjectives in the nominative („angielski“, „japoński“; „hindi“
indeclinable) and can't be inflected. Negation and most prepositions need the
genitive („nie obsługuje japońskiego“), so names stand only where the
nominative or the identical inanimate accusative is right: after a colon
(„Nie obsługuje języka: japoński“, „Język testu: japoński“, lists), after the
noun „język“ in the nominative or accusative („(język japoński)“, „ten
obsługuje język angielski“), or in apposition in parentheses („dla Twojego
języka (japoński)“, „Świetna (angielski)“, „Automatycznie (niemiecki)“). A
placeholder never starts a sentence (it is lowercase).

## Kept untranslated

Marmalade, Marmalade STT, all engine and model names (Parakeet Ultra, Parakeet
110M, Parakeet Japanese, Canary 180M Flash, Nemotron 3.5, Whisper Large v3
Turbo, Granite Speech, Qwen3-ASR, and upstream names in descriptions such as
Parakeet TDT 0.6B v3 and large-v3-turbo), Smart Turn, FUTO Keyboard, GitHub
(declined „na GitHubie“), GitHub Sponsors, Silero VAD, ONNX Runtime, CPU,
LibriSpeech, FLEURS, AMI, NVIDIA, OpenAI, IBM, Alibaba, Moondream, WER, RTF,
RSS, CTC, PRIVACY.md, Markdown, encoder-decoder, issue (GitHub), spinning
(developer screen), Rich Input (SwiftKey). The English spoken words in the
cleanup and end-word descriptions („um“, „new line“, „period“, „Twenty five
percent“, „over“ …) stay English in „…”, and the „teh“/„tehran“ dictionary
example stays. The snippet example was adapted: „mój adres” matches „Mój
adres” but not „mój adresat”.

## External labels named

| Label (English) | Polish used | Status |
|---|---|---|
| Android: Settings | Ustawienia | verified (AOSP 16 QPR2 `settings_label`) |
| Android: Apps | Aplikacje | verified (AOSP 16 QPR2 `apps_dashboard_title`) |
| Android: Permissions (app info) | Uprawnienia | verified (AOSP 16 QPR2 `permissions_label`) |
| Android: System | System | verified (AOSP 16 QPR2 `header_category_system`) |
| Android: Keyboard | Klawiatura | verified in AOSP (`keyboard_settings`); Pixel overlay not checked on a device |
| Android: On-screen keyboard | Klawiatura ekranowa | verified in AOSP (`virtual_keyboard_category`); Pixel overlay not checked on a device |
| Android: keyboard settings (generic) | ustawienia klawiatury Androida | descriptive, not a label |
| Android: OK (direct-boot dialog) | OK | verified (frameworks/base 16 QPR2 `ok`) |
| Android: home screen | ekran główny | AOSP wording („na ekranie głównym“) |
| Android: Google voice typing | Pisanie głosowe Google | unverified (Gboard's label, not checked on a device) |
| Android: System → Languages (Android 14–16) | Języki | verified (AOSP 16 QPR2 `languages_settings`) |
| Android: Voice input (also the search term) | Rozpoznawanie mowy | verified (AOSP 16 QPR2, AOSP 13, AOSP 12 `voice_input_settings_title`); Polish Android names this page „Rozpoznawanie mowy“, not "voice input" |
| Android: Languages & input (Android 13) | Języki i metody wprowadzania | verified (AOSP 13 `language_settings`) |
| Android: Apps & notifications (Android 9–11) | Aplikacje i powiadomienia | verified (AOSP 9, 10, 11 `app_and_notification_dashboard_title`) |
| Android: Advanced (the collapsed group on Apps & notifications, Android 9–11) | Zaawansowane | verified in source (preference library `expand_button_title`: Android 9 tag for 9; AndroidX `androidx-preference-release` for 10–11, whose exact bundled version wasn't checked) |
| Android: Default apps (Android 9–12) | Domyślne aplikacje (Android 9); Aplikacje domyślne (Android 10–12) | verified (AOSP 9–12 Settings `app_default_dashboard_title`, the row tapped) |
| Android: Assist & voice input (Android 9) | Asystent i rozpoznawanie mowy | verified (AOSP 9 `assist_and_voice_input_title`) |
| Android: Assist app (Android 9–10) | Aplikacja asystująca | verified (AOSP 9 `default_assist_title`; Android 10 Default apps row: PermissionController `role_assistant_short_label`; Android 10 page row: AOSP 10 `default_assist_title`) |
| Android: Digital assistant app (Default apps row, Android 11–12) | Asystent cyfrowy | verified (PermissionController 11, 12 `role_assistant_short_label`) |
| Android: Default digital assistant app (Android 11) | Domyślny asystent cyfrowy | verified (AOSP 11 `default_digital_assistant_title`) |
| Android: None (assistant picker) | Brak | verified (AOSP 9 `app_list_preference_none`; PermissionController 10, 11 `default_app_none`) |
| Android: Language & region (Android 16 where present, 17) | Język i region | verified (AOSP 17 `language_and_region_settings`) |
| Android: Speech (row of Language & region, Android 17) | Mowa | verified (AOSP 17 `speech_preference_title`); on Android 13–16 Speech is a heading and isn't named |
| Android: Force stop (written as a verb phrase) | Wymuś zatrzymanie („wymusisz zatrzymanie“) | verified (AOSP 16 QPR2 `force_stop`) |
| FUTO Keyboard: Settings | Ustawienia | verified (FUTO translations `keyboard_language_settings`, `core/values-pl`) |
| FUTO Keyboard: Voice Input | Wprowadzanie głosowe | verified (FUTO translations `voice_input_settings_title`); the installed 0.1.30 build not checked on a device |
| FUTO Keyboard: Disable built-in voice input | Wyłącz wbudowane wprowadzanie głosowe | verified (same source, same caveat) |
| FUTO Keyboard: Use voice input provided by external app | Użyj wprowadzania głosowego dostarczonego przez zewnętrzną aplikację | verified (same source, same caveat) |
| Fossify Keyboard: Voice typing method | Sposób wpisywania głosowego | verified (Fossify `voice_typing_method`, main) |
| SwiftKey: settings | ustawienia SwiftKey | from Microsoft's support page; in-app label unverified |
| SwiftKey: Rich input | Rich Input | from Microsoft's support page (it names the section both „Zaawansowane dane wejściowe“ and „Rich Input“; the tap target is „Rich Input“); in-app label unverified |
| SwiftKey: Multi-modal voice typing | Multimodalne wpisywanie głosowe | from Microsoft's support page; in-app label unverified |

Sources for the #79 rows: AOSP Settings `res/values-pl/strings.xml` on the `android-9.0.0_r1` tag and the `android10-release` … `android17-release` branches; PermissionController's strings (Android 10–11: `platform/packages/apps/PackageInstaller`, Android 12: `platform/packages/modules/Permission`), which label the Default apps rows and the assistant picker on 10–12; the preference library's `expand_button_title` (`platform/frameworks/support`). Not checked against a phone maker's overlay on a device.

Sources: AOSP Settings strings at
https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/android16-qpr2-release/res/values-pl/strings.xml
("AOSP 16 QPR2", the Android 14+ path), the same file on `android13-release`
("AOSP 13") and `android12-release` ("AOSP 12"); Android's OK from
frameworks/base `core/res/res/values-pl/strings.xml` (android16-qpr2-release);
FUTO Keyboard's labels from its public translations repository
https://gitlab.futo.org/keyboard/keyboard-translations-public (`core/values-pl`,
master; label strings read, no code used); Fossify Keyboard's
`voice_typing_method` at
https://github.com/FossifyOrg/Keyboard/blob/main/app/src/main/res/values-pl/strings.xml
(main branch; the installed release not checked on a device); SwiftKey's
labels from Microsoft's support page
https://support.microsoft.com/pl-pl/swiftkey-keyboard/how-to-disable-multimodal-voice-dictation-in-swiftkey
(machine-translated by Microsoft, so the in-app labels are not checked). AOSP
labels are not checked against a Pixel's overlay on a device.
