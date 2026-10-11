# Vietnamese (vi) glossary

## Register

**Bạn** for the user, as Android's and Google's Vietnamese UI do. Instructions
use "Hãy …" or a bare imperative ("Hãy thử lại", "Nhấn vào micrô"); errors use
"Không … được" / "Không thể …" ("Không dùng được micrô"); status lines use
"Đang …" ("Đang nghe", "Đang tải xuống"); buttons are short verbs ("Tải xuống",
"Thử lại", "Hủy"). Quotation marks “…” (also `quoted_name`); `list_separator`
stays ", ". App labels in running text are quoted when they name a setting
(“Thời gian ngừng”, “Thêm quy tắc”); tab names and Android menu paths
(Cài đặt → Hệ thống → …) are not, matching the English. Decimals use a comma in
literal text ("0,6 đến 3 GB"). "Phone" is **điện thoại**; the listing's "on
device" is "trên máy".

Plurals: `other` only (Vietnamese nouns don't inflect: "%d ngôn ngữ").

Vietnamese runs long: buttons and pills were kept to Android's short forms
("Tải lại", "Gỡ bỏ", "Đang dùng"); the step counter is "Bước %1$d/%2$d".

## Terms

| English | Vietnamese | Notes |
|---|---|---|
| Dictate (tab, screen) / dictation | Đọc chính tả | Google's and Microsoft's Vietnamese for dictation |
| Engines (tab) / engine | Công cụ / công cụ | Android vi's word for a speech engine ("Công cụ ưu tiên", AOSP 16 QPR2 `tts_engine_preference_title`) |
| Settings (tab, title) | Cài đặt | |
| tab | thẻ | "thẻ Công cụ" |
| transcribe | chép lời | Google Recorder's word |
| recognize / recognition | nhận dạng | |
| model / speech model | mô hình / mô hình giọng nói | |
| download (verb / noun) | tải xuống / bản tải xuống | |
| Remove (model files) / Remove download | Gỡ bỏ / Xóa bản tải xuống | |
| voice keyboard | bàn phím giọng nói | |
| Marmalade voice typing (IME name) | Nhập bằng giọng nói Marmalade | Mirrors Android's "Nhập bằng giọng nói của Google" |
| voice typing / voice input / speech input | nhập bằng giọng nói | Android vi's `voice_input_settings_title` |
| speech (settings section) | Giọng nói | |
| speak-now popup / Speak now | cửa sổ “Hãy nói” / Hãy nói | |
| microphone / mic key | micrô / phím micrô | Android vi spelling |
| tap / press and hold | nhấn / nhấn giữ | Pixel vi |
| End of speech (section) | Cách dừng | "How it stops", like zh-CN 停止方式 |
| Tap to stop | Nhấn để dừng | |
| auto-stop | tự dừng | |
| Auto-stop after a pause | Tự dừng khi ngừng nói | |
| pause (in speech) / Pause length | ngừng / Thời gian ngừng | "tạm dừng" kept for the keyboard panel and downloads |
| end word / End words | từ kết thúc | |
| Smart Turn patience: Quick / Normal / Relaxed | Độ kiên nhẫn của Smart Turn: Nhanh / Bình thường / Thong thả | |
| Recognition language | Ngôn ngữ nhận dạng | |
| Auto / Auto-detect | Tự động / Tự động phát hiện | |
| Text cleanup | Làm sạch văn bản | |
| filler words | từ đệm | |
| voice commands | lệnh thoại | |
| Punctuation words | Từ chỉ dấu câu | |
| Replacement dictionary | Từ điển thay thế | |
| rule / Add rule | quy tắc / Thêm quy tắc | |
| Snippets / Add snippet | Đoạn mẫu / Thêm đoạn mẫu | |
| trigger phrase | cụm từ kích hoạt | |
| phrase (dictionary) | cụm từ | |
| Setup guide | Hướng dẫn thiết lập | |
| Developer / Developer engines | Nhà phát triển / Công cụ cho nhà phát triển | Android vi "Tùy chọn cho nhà phát triển" |
| benchmark / A/B benchmark | đo điểm chuẩn / Đo điểm chuẩn A/B | |
| take (benchmark recording) | bản ghi | |
| load / unload | tải / giải phóng | |
| streaming | Streaming | Kept: "trực tiếp/trực tuyến" would read as "online" |
| Listening / Hearing you… | Đang nghe / Đang nghe bạn nói… | |
| Ready | Sẵn sàng | |
| Speed: Fastest / Very fast / Fast / Moderate / Slow | Nhanh nhất / Rất nhanh / Nhanh / Trung bình / Chậm | |
| Quality: Best / Great / Good / Fair / Basic | Tốt nhất / Rất tốt / Tốt / Khá / Cơ bản | Khá ranks below Tốt, as in Vietnamese grading |
| Support (section) / Support development | Ủng hộ / Ủng hộ phát triển | Donations |
| Report a bug / More Marmalade | Báo lỗi / Thêm từ Marmalade | |
| About | Giới thiệu | Android vi "Giới thiệu về điện thoại" |
| license / open-source | giấy phép / nguồn mở | |
| Privacy policy | Chính sách quyền riêng tư | Google vi |
| analytics | phân tích | |
| word error rate (WER) | tỷ lệ lỗi từ (WER) | |
| Light / Dark / System | Sáng / Tối / Theo hệ thống | |
| Wallpaper / Midnight / Forest / Berry | Hình nền / Nửa đêm / Rừng / Quả mọng | |
| high contrast (the colour preset, #68) | Tương phản cao (switch: “Dùng màu tương phản cao”) | AOSP's `accessibility_toggle_high_text_contrast_preference_title` says “độ tương phản cao”; “độ” is dropped in all three strings (shorter chip, reads naturally) |
| speech recognition service (Android's) | dịch vụ nhận dạng giọng nói | |

Language names in placeholders: ICU's Vietnamese names come capitalised
("Tiếng Nhật", "Tiếng Anh"), while Vietnamese writes "tiếng Nhật" in lower case
mid-sentence. So every string that takes a language name puts it at the start
of the sentence ("%1$s không được hỗ trợ", "%1$s: lựa chọn hàng đầu …"), after
a colon ("Ngôn ngữ kiểm tra: %1$s", the Canary language list) or in parentheses
("Tự động (%1$s)", "… trên FLEURS (%1$s)"). Fixed text writes "tiếng Anh" in
lower case as usual.

## Kept untranslated

Marmalade, Marmalade STT, every engine and model name (Parakeet Ultra, Parakeet
110M, Parakeet Japanese, Canary 180M Flash, Nemotron 3.5, Whisper Large v3
Turbo, Granite Speech, Qwen3-ASR, and upstream names in descriptions such as
Parakeet TDT 0.6B v3 and large-v3-turbo), Smart Turn, FUTO Keyboard, GitHub,
GitHub Sponsors, GitHub issue, Silero VAD, ONNX Runtime, CPU, LibriSpeech,
FLEURS, AMI, NVIDIA, OpenAI, IBM, Alibaba, Moondream, WER, RTF, RSS, CTC,
Markdown, PRIVACY.md, Android, Google, Microsoft, SwiftKey, Fossify Keyboard,
Internet. Also kept: streaming, spinning (developer screens), the English
spoken words in the cleanup and end word descriptions (“um”, “new line”,
“over”, “Twenty five percent” → “25%”…), the dictionary/snippet case examples
(“teh”, “Teh”, “my email”; the rule they show holds for Vietnamese too) and
Fossify's English “Voice typing method” (see below).

## External labels named

| Label (English) | Vietnamese used | Status |
|---|---|---|
| Android: Settings | Cài đặt | verified (AOSP 16 QPR2 `settings_label`) |
| Android: Apps | Ứng dụng | verified (AOSP 16 QPR2 `apps_dashboard_title`) |
| Android: Permissions (app info) | Quyền | verified (AOSP 16 QPR2 `permissions_label`) |
| Android: System | Hệ thống | verified (AOSP 16 QPR2 `header_category_system`) |
| Android: Settings → System → Keyboard → On-screen keyboard | Cài đặt → Hệ thống → Bàn phím → Bàn phím ảo | verified in AOSP 16 QPR2 (`keyboard_settings`, `virtual_keyboard_category`); the Pixel overlay not checked on a device |
| Android: OK (second keyboard dialog) | OK | verified (AOSP 16 QPR2 framework `ok`) |
| Android: home screen | màn hình chính | descriptive |
| Android: navigation bar | thanh điều hướng | verified (AOSP 16 QPR2 `accessibility_button_disabled_button_mode_summary`) |
| Android: System → Languages (Android 14–16) | Ngôn ngữ | verified (AOSP 16 QPR2 `languages_settings`) |
| Android: Voice input (also the search term) | Nhập bằng giọng nói | verified (AOSP 16 QPR2, AOSP 13, AOSP 12 `voice_input_settings_title`) |
| Android: Languages & input (Android 13) | Ngôn ngữ và nhập liệu | verified (AOSP 13 `language_settings`) |
| Android: Apps & notifications (Android 9–11) | Ứng dụng và thông báo | verified (AOSP 9, 10, 11 `app_and_notification_dashboard_title`) |
| Android: Advanced (the collapsed group on Apps & notifications, Android 9–11) | Nâng cao | verified in source (preference library `expand_button_title`: Android 9 tag for 9; AndroidX for 10–11: `androidx-preference-release` has no vi file, `androidx-main` also says Nâng cao) |
| Android: Default apps (Android 9–12) | Ứng dụng mặc định | verified (AOSP 9–12 Settings `app_default_dashboard_title`, the row tapped) |
| Android: Assist & voice input (Android 9) | Trợ lý và nhập bằng giọng nói | verified (AOSP 9 `assist_and_voice_input_title`) |
| Android: Assist app (Android 9–10) | Ứng dụng trợ lý | verified (AOSP 9 `default_assist_title`; Android 10 Default apps row: PermissionController `role_assistant_short_label`; Android 10 page row: AOSP 10 `default_assist_title`) |
| Android: Digital assistant app (Default apps row, Android 11–12) | Ứng dụng trợ lý kỹ thuật số | verified (PermissionController 11, 12 `role_assistant_short_label`) |
| Android: Default digital assistant app (Android 11) | Ứng dụng trợ lý kỹ thuật số mặc định | verified (AOSP 11 `default_digital_assistant_title`) |
| Android: None (assistant picker) | Không có | verified (AOSP 9 `app_list_preference_none`; PermissionController 10, 11 `default_app_none`) |
| Android: Language & region (Android 16 where present, 17) | Ngôn ngữ và khu vực | verified (AOSP 17 `language_and_region_settings`) |
| Android: Speech (row of Language & region, Android 17) | Lời nói | verified (AOSP 17 `speech_preference_title`); on Android 13–16 Speech is a heading and isn't named |
| Android: Force stop (written as a verb phrase) | Buộc dừng (“buộc dừng Marmalade STT”) | verified (AOSP 16 QPR2 `force_stop`) |
| FUTO Keyboard: Settings → Voice Input | Cài đặt → Nhập bằng giọng nói | Nhập bằng giọng nói verified (FUTO `voice_input_settings_title`); Cài đặt unverified |
| FUTO Keyboard: "Disable built-in voice input" | “Tắt nhập bằng giọng nói” | verified (FUTO `voice_input_settings_disable_builtin_voice_input`; FUTO's Vietnamese drops "built-in") |
| FUTO Keyboard: "Use voice input provided by external app" | “Sử dụng nhập bằng giọng nói được cung cấp bởi ứng dụng bên ngoài” | verified (FUTO `…_subtitle`) |
| Fossify Keyboard: Voice typing method | “Voice typing method” | verified as English: Fossify's `values-vi` has no `voice_typing_method`, so the app shows English |
| SwiftKey: Multi-modal voice typing | Nhập bằng giọng nói đa phương thức | from Microsoft's support page (the toggle step); in-app label unverified |
| SwiftKey: Rich input | Phương thức nhập phong phú | from Microsoft's support page (the tap target; the step heading says "Đầu vào phong phú"); in-app label unverified |

Sources for the #79 rows: AOSP Settings `res/values-vi/strings.xml` on the `android-9.0.0_r1` tag and the `android10-release` … `android17-release` branches; PermissionController's strings (Android 10–11: `platform/packages/apps/PackageInstaller`, Android 12: `platform/packages/modules/Permission`), which label the Default apps rows and the assistant picker on 10–12; the preference library's `expand_button_title` (`platform/frameworks/support`). Not checked against a phone maker's overlay on a device.

Sources: AOSP Settings strings at https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/android16-qpr2-release/res/values-vi/strings.xml ("AOSP 16 QPR2", the Android 14+ path), the same file on `android13-release` ("AOSP 13") and `android12-release` ("AOSP 12"); the framework's `ok` at https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-qpr2-release/core/res/res/values-vi/strings.xml; FUTO Keyboard's labels from its public translations repo https://gitlab.futo.org/keyboard/keyboard-translations-public (`core/values-vi/strings-uix.xml`, read for wording only); Fossify Keyboard at https://github.com/FossifyOrg/Keyboard/blob/main/app/src/main/res/values-vi/strings.xml (main branch; the installed release not checked on a device); SwiftKey's labels from https://support.microsoft.com/vi-vn/swiftkey-keyboard/how-to-disable-multimodal-voice-dictation-in-swiftkey (machine-translated by Microsoft). AOSP labels are not checked against a Pixel's overlay on a device.
