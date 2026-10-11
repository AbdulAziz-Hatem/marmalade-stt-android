# Indonesian (in, tag id) glossary

## Register

*Anda*, as Google's Indonesian UI does ("Ketuk mikrofon", "Coba lagi", "Anda
dapat…"). Buttons use the base verb, as Android does ("Download", "Coba lagi",
"Batal", "Buka setelan"); errors start with "Tidak dapat …"; progress lines use
the me- form ("Mendownload", "Memuat…"). Google's Indonesian keeps several
English loanwords and so does this translation: download (didownload,
mendownload), upload (diupload), offline, online, cloud, default, keyboard,
toolbar, streaming, benchmark, developer, issue, link. Typographic quotes “…”
(also `quoted_name`); `list_separator` stays `,\u0020`. Decimals in literal text
use a comma ("0,6 GB").

Plurals: `other` only. Indonesian keeps the noun singular after a number ("25
bahasa").

## Terms

| English | Indonesian | Notes |
|---|---|---|
| Dictate (tab, screen) | Dikte (tab Dikte / layar Dikte) | |
| Engines (tab) | Mesin (tab Mesin) | |
| Settings (tab, title) | Setelan | Android's word |
| tab | tab | |
| dictation / dictate | dikte / mendikte | |
| engine / speech engine | mesin / mesin ucapan | |
| model / speech model | model / model ucapan | |
| language model (Qwen3 decoder) | model bahasa | "decoder model bahasa kecil" |
| download (verb / noun) | download / mendownload / didownload | Google's Indonesian, not "unduh" |
| install | instal / terinstal | |
| load / unload (engine) | memuat / mengeluarkan dari memori | |
| voice keyboard | keyboard suara | |
| voice typing (IME name) | Pengetikan suara Marmalade | Mirrors "Pengetikan suara Google"; Fossify and Microsoft say "pengetikan suara" |
| voice input / speech input | input suara | Android's label "Input suara" |
| speech recognition service | layanan pengenalan ucapan | AOSP "Pengenalan ucapan di perangkat" |
| speech (settings section) | Ucapan | AOSP `speech_category_title` |
| End of speech | Akhir ucapan | |
| recognition language | bahasa pengenalan | |
| Speak now / speak-now popup | Bicaralah sekarang / pop-up “Bicaralah sekarang” | |
| auto-stop | berhenti otomatis | Auto-stop after a pause = Berhenti otomatis setelah jeda |
| pause (in speech) | jeda (noun) / berhenti sejenak, diam sejenak (verb) | Pause length = Durasi jeda; Pause listening = Jeda mendengarkan; Paused = Dijeda; "when you pause" = "saat Anda berhenti sejenak", never "saat Anda jeda" |
| Tap to stop | Ketuk untuk berhenti | |
| end word | kata penutup | |
| patience (Smart Turn) | Kesabaran Smart Turn: Cepat / Normal / Santai | The descriptions say what stops: "Berhenti mendengarkan lebih cepat" |
| tap / touch & hold | ketuk / sentuh lama | Android's words |
| text cleanup | pembersihan teks | |
| filler words | kata pengisi | |
| voice commands | perintah suara | |
| punctuation words | kata tanda baca | |
| replacement dictionary | kamus penggantian | "Kamus pengganti" would be a *substitute* dictionary; "penggantian" = replacing, as the listing says |
| rule | aturan | Add rule = Tambah aturan |
| snippet | cuplikan | Add snippet = Tambah cuplikan |
| trigger phrase | frasa pemicu | |
| setup guide | panduan penyiapan | Android says "penyiapan" for setup |
| Developer engines | Mesin developer | As Android's "Opsi developer" |
| take (benchmark) | rekaman | "kept in memory" = "berada di memori", so it never reads like "disimpan" (saved) |
| Privacy policy | Kebijakan privasi | |
| analytics | analitik | |
| Open-source licenses | Lisensi open source | Google's wording |
| Support / Report a bug / More Marmalade | Dukungan / Laporkan bug / Marmalade lainnya | |
| Quality: Best / Great / Good / Fair / Basic | Terbaik / Sangat baik / Baik / Cukup / Dasar | |
| Speed: Fastest / Very fast / Fast / Moderate / Slow | Tercepat / Sangat cepat / Cepat / Sedang / Lambat | |
| Light / Dark / System | Terang / Gelap / Sistem | |
| Wallpaper / Midnight / Forest / Berry | Wallpaper / Tengah malam / Hutan / Beri | |
| high contrast (the colour preset, #68) | Kontras tinggi (switch: “Gunakan warna kontras tinggi”) | matches AOSP `accessibility_toggle_high_text_contrast_preference_title` (Android 16 QPR2 source, not checked on a device) |
| mic key (a keyboard's) | tombol mikrofon | |
| navigation bar | menu navigasi | As AOSP 16 QPR2 id writes it ("keyboard atau menu navigasi", "mode tombol menu navigasi") |
| Expanded / Collapsed (TalkBack) | Diperluas / Diciutkan | |
| capital letters and punctuation (Granite) | huruf kapital dan tanda baca | The formatter *adds* them ("ditambahkan") |
| phone | ponsel | |
| Chinese (in prose) | bahasa Tionghoa | The name Java gives the app's language list |

Language names in placeholders: Java's Indonesian names come without "bahasa"
("Inggris", "Jepang"), and on their own read as country names. Every string
that takes one puts lowercase "bahasa" in front ("Tidak mendukung bahasa %1$s",
"Otomatis (bahasa %1$s)", "Diuji dalam bahasa %1$s", "%1$s (bahasa %2$s)").
The one exception is the list after a colon in `engine_card_language_choose`,
which follows "Pilih salah satu … Bahasa pengenalan:".

## Kept untranslated

Marmalade, Marmalade STT, all engine and model names (Parakeet Ultra, Parakeet
110M, Parakeet Japanese, Canary 180M Flash, Nemotron 3.5, Whisper Large v3
Turbo, Granite Speech, Qwen3-ASR, and upstream names in descriptions such as
Parakeet TDT 0.6B v3 and large-v3-turbo), Smart Turn, FUTO Keyboard, GitHub,
GitHub Sponsors, Silero VAD, ONNX Runtime, CPU, LibriSpeech, FLEURS, AMI,
NVIDIA, OpenAI, IBM, Alibaba, Moondream, WER, RTF, RSS, CTC, PRIVACY.md, API,
Markdown, encoder-decoder, decoding, spinning/pinning (developer screen). Also
kept: the English spoken words in the cleanup and end-word descriptions and the
"teh"/"tehran" dictionary example (an English misspelling; the rule it shows
holds in any language, even though "teh" is also Indonesian for tea). The
snippet example was adapted: “alamat rumah” matches “Alamat rumah” but not
“alamat rumahnya”.

## External labels named

| Label (English) | Indonesian used | Status |
|---|---|---|
| Android: Settings | Setelan | verified (AOSP 16 QPR2 `settings_label`) |
| Android: Apps | Aplikasi | verified (AOSP 16 QPR2, AOSP 12 `apps_dashboard_title`) |
| Android: Permissions (app info) | Izin | verified (AOSP 16 QPR2 `permissions_label`) |
| Android: System | Sistem | verified (AOSP 16 QPR2 `header_category_system`) |
| Android: Settings → System → Keyboard → On-screen keyboard | Setelan → Sistem → Keyboard → Keyboard virtual | verified (AOSP 16 QPR2 `keyboard_settings`, `virtual_keyboard_category`) |
| Android: keyboard settings (generic) | setelan keyboard Android | descriptive, not a label |
| Android: OK (second keyboard dialog) | Oke | verified (AOSP 16 QPR2 `dlg_ok`) |
| Android: home screen | layar utama | verified (AOSP 16 QPR2 `preview_pager_home_title`) |
| Android: System → Languages (Android 14–16) | Bahasa | verified (AOSP 16 QPR2 `languages_settings`) |
| Android: Voice input (also the search term) | Input suara (Android 12+); Masukan suara (Android 9–11 paths) | verified (AOSP 16 QPR2, 13, 12 `voice_input_settings_title`; AOSP 9, 10, 11 say "Masukan suara") |
| Android: Languages & input (Android 13) | Bahasa & input | verified (AOSP 13 `language_settings`; AOSP 12 says "Bahasa & masukan") |
| Android: Apps & notifications (Android 9–11) | Aplikasi & notifikasi | verified (AOSP 9, 10, 11 `app_and_notification_dashboard_title`) |
| Android: Advanced (the collapsed group on Apps & notifications, Android 9–11) | Lanjutan | verified in source (preference library `expand_button_title`: Android 9 tag for 9; AndroidX `androidx-preference-release` for 10–11, whose exact bundled version wasn't checked) |
| Android: Default apps (Android 9–12) | Aplikasi default | verified (AOSP 9–12 Settings `app_default_dashboard_title`, the row tapped) |
| Android: Assist & voice input (Android 9) | Bantuan & masukan suara | verified (AOSP 9 `assist_and_voice_input_title`) |
| Android: Assist app (Android 9–10) | Aplikasi bantuan | verified (AOSP 9 `default_assist_title`; Android 10 Default apps row: PermissionController `role_assistant_short_label`; Android 10 page row: AOSP 10 `default_assist_title`) |
| Android: Digital assistant app (Default apps row, Android 11–12) | Aplikasi asisten digital | verified (PermissionController 11, 12 `role_assistant_short_label`) |
| Android: Default digital assistant app (Android 11) | Aplikasi asisten digital default | verified (AOSP 11 `default_digital_assistant_title`) |
| Android: None (assistant picker) | Tidak ada (Android 9); Tidak Ada (Android 10–11) | verified (AOSP 9 `app_list_preference_none`; PermissionController 10, 11 `default_app_none`) |
| Android: Language & region (Android 16 where present, 17) | Bahasa & wilayah | verified (AOSP 17 `language_and_region_settings`) |
| Android: Speech (row of Language & region, Android 17) | Ucapan | verified (AOSP 17 `speech_preference_title`); on Android 13–16 Speech is a heading and isn't named |
| Android: Force stop (written as a verb phrase) | Paksa berhenti ("memaksa Marmalade STT berhenti") | verified (AOSP 16 QPR2 `force_stop`) |
| Google voice typing | Pengetikan suara Google | unverified (Gboard's Indonesian name not checked) |
| FUTO: Settings | setelan (generic, lowercase) | unverified (FUTO's Indonesian file has no `keyboard_language_settings`) |
| FUTO: Voice Input | Voice Input (English kept) | verified: FUTO's Indonesian translation (`core/values-in/strings-uix.xml`) has none of the voice-input keys, so FUTO shows English; the installed build not checked on a device |
| FUTO: Disable built-in voice input | “Disable built-in voice input” (English kept) | verified (same reason, same caveat) |
| FUTO: Use voice input provided by external app | “Use voice input provided by external app” (English kept) | verified (same reason, same caveat) |
| Fossify Keyboard: Voice typing method | “Metode pengetikan suara” | verified (Fossify `voice_typing_method`, main) |
| SwiftKey: Multi-modal voice typing | “Pengetikan Suara Multimodal” | from Microsoft's support page; in-app label unverified |
| SwiftKey: Rich input | Rich Input | from Microsoft's support page, whose tap target keeps the English name; in-app label unverified |

Sources for the #79 rows: AOSP Settings `res/values-in/strings.xml` on the `android-9.0.0_r1` tag and the `android10-release` … `android17-release` branches; PermissionController's strings (Android 10–11: `platform/packages/apps/PackageInstaller`, Android 12: `platform/packages/modules/Permission`), which label the Default apps rows and the assistant picker on 10–12; the preference library's `expand_button_title` (`platform/frameworks/support`). Not checked against a phone maker's overlay on a device.

Sources: AOSP Settings strings at https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/android16-qpr2-release/res/values-in/strings.xml ("AOSP 16 QPR2", the Android 14+ path), the same file on `android13-release` ("AOSP 13") and `android12-release` ("AOSP 12"); FUTO's labels from https://gitlab.futo.org/keyboard/keyboard-translations-public (`core/values-in/strings-uix.xml`, master); Fossify Keyboard's `voice_typing_method` at https://github.com/FossifyOrg/Keyboard/blob/main/app/src/main/res/values-in/strings.xml (main branch; the installed release not checked on a device); SwiftKey's labels from Microsoft's support page https://support.microsoft.com/id-id/swiftkey-keyboard/how-to-disable-multimodal-voice-dictation-in-swiftkey (machine-translated by Microsoft, so the in-app label is not checked). AOSP labels are not checked against a Pixel's overlay on a device.
