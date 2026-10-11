# Glossary: Traditional Chinese, Taiwan (zh-rTW)

## Register and style

- Address the user as **你** throughout, as Android's and Google's Taiwan UI do.
- Taiwan vocabulary and Android zh-TW wording, not converted zh-CN: 設定, 應用程式,
  檔案, 下載, 網路, 記憶體, 儲存空間, 手機 (measure word 支: 這支手機), 麥克風,
  鍵盤, 輕觸 (tap), 略過 (skip), 儲存 (save), 匯入 (import), 辨識 (recognize),
  偵測 (detect), 預設 (default), 授權 (license), 開放原始碼 (open source),
  帳戶, 隱私權政策, 串流 (streaming), 執行緒 (thread), 分頁 (tab), 桌布, 品質.
- Full-width punctuation (，。：；？（）), corner quotes 「…」 (also
  `quoted_name`), ellipsis … in status lines; `list_separator` is 、.
- A space between Han characters and Latin letters or digits (`版本 %1$s`,
  `0.6 到 3 GB`), none next to full-width punctuation. No space around a
  placeholder filled with a Chinese language name (`無法轉錄%1$s`).
- Quoted labels in a row take no 、 between them (「um」「uh」), as in zh-CN.
- Menu paths sit inside one pair of quotes: 「設定 → 系統 → 鍵盤 → 螢幕小鍵盤」.
- Confirmation titles use Android zh-TW's 「要…嗎？」 form (要下載 %1$s 嗎？).
- Plurals: `other` only, with a measure word (項規則, 個片段, 種語言, 行).
- Units: seconds as 秒 (`%1$.1f 秒`); milliseconds stay `ms`.

## Terms

| English | zh-TW | Notes |
|---|---|---|
| Dictate (tab) / dictation | 聽寫 | |
| Engines (tab) / engine | 引擎 | |
| Settings (tab) | 設定 | |
| tab | 分頁 (「引擎」分頁) | |
| model / speech model | 模型 / 語音模型 | |
| download (verb / noun) | 下載 / 下載內容 | |
| Remove (model files) / Remove download | 移除 / 刪除下載內容 | |
| voice keyboard | 語音鍵盤 | |
| Marmalade voice typing (IME name) | Marmalade 語音輸入 | Mirrors Android's 「Google 語音輸入」 |
| voice typing / voice input / speech input | 語音輸入 | |
| speech | 語音 | |
| speak-now popup / Speak now | 「請說話」視窗 / 請說話 | |
| transcribe | 轉錄 | Taiwan usage (Google 「即時轉錄」) |
| recognize / recognition | 辨識 | zh-CN 识别 |
| Recognition language | 辨識語言 | |
| Auto / Auto-detect | 自動 / 自動偵測 | |
| Text cleanup | 文字整理 | |
| filler words | 贅詞 | |
| voice commands | 語音指令 | |
| Replacement dictionary | 替換字典 | |
| rule / Add rule | 規則 / 新增規則 | Measure word 項 |
| Snippets / snippet / Add snippet | 文字片段 / 片段 / 新增片段 | |
| trigger phrase / phrase | 觸發片語 / 片語 | 片語 is Taiwan's word for "phrase" (zh-CN 短语) |
| End of speech (section) | 停止方式 | As zh-CN |
| Tap to stop | 輕觸停止 | |
| auto-stop | 自動停止 | |
| Auto-stop after a pause | 停頓後自動停止 | |
| Pause length | 停頓長度 | |
| end word / End words | 結束詞 | |
| Smart Turn patience: Quick / Normal / Relaxed | Smart Turn 耐心程度：快速 / 一般 / 寬鬆 | |
| Setup guide | 設定指南 | |
| Developer / Developer engines | 開發人員 / 開發人員引擎 | Android zh-TW 「開發人員選項」 |
| benchmark / A/B benchmark | 基準測試 / A/B 基準測試 | |
| take (benchmark recording) | 錄音 | |
| unload | 卸載 | |
| Runtime tuning | 執行階段調整 | |
| streaming | 串流 | |
| tap / press and hold | 輕觸 / 按住 | Android zh-TW |
| microphone / mic key | 麥克風 / 麥克風鍵 | |
| microphone access | 麥克風存取權 | Android zh-TW |
| Listening | 正在聆聽 | |
| Ready | 已就緒 | |
| damaged (files) | 已損毀 | |
| Speed: Fastest / Very fast / Fast / Moderate / Slow | 最快 / 非常快 / 快速 / 中等 / 較慢 | |
| Quality: Best / Great / Good / Fair / Basic | 最佳 / 優秀 / 良好 / 普通 / 基本 | |
| Support (section) / Support development | 贊助 / 贊助開發 | Donations; 支援 would read as "tech support" |
| Report a bug | 回報問題 | |
| license | 授權 | |
| privacy policy | 隱私權政策 | Google Taiwan's wording |
| analytics | 分析 | |
| crash reporting (listing) | 當機回報 | |
| word error rate (WER) | 字詞錯誤率（WER） | |
| this phone | 這支手機 | |
| Light / Dark / System | 淺色 / 深色 / 跟隨系統 | |
| Wallpaper / Midnight / Forest / Berry | 桌布 / 午夜 / 森林 / 莓果 | |
| high contrast (the colour preset, #68) | 高對比 (switch: “使用高對比色彩”) | matches AOSP `accessibility_toggle_high_text_contrast_preference_title` (Android 16 QPR2 source, not checked on a device) |
| speech recognition service (Android's) | 語音辨識服務 | |

Language names in placeholders: ICU's zh-TW names are 英文, 日文, 中文 … (the
文 form, not 語). They attach directly to the surrounding Chinese with no
particle and no agreement (無法轉錄%1$s, %1$s測試結果, 最適合%1$s的引擎), so no
string needed restructuring.

## Kept untranslated

Marmalade, Marmalade STT, every engine and model name (Parakeet Ultra, Parakeet
110M, Parakeet Japanese, Canary 180M Flash, Nemotron 3.5, Whisper Large v3
Turbo, Granite Speech, Qwen3-ASR, and upstream names in descriptions such as
Parakeet TDT 0.6B v3 and large-v3-turbo), Smart Turn, FUTO Keyboard, GitHub,
GitHub Sponsors, GitHub issue, Silero VAD, ONNX Runtime, CPU, LibriSpeech,
FLEURS, AMI, NVIDIA, OpenAI, IBM, Alibaba, Moondream, WER, RTF, RSS, CTC,
Markdown, PRIVACY.md, Android, Google, Microsoft, SwiftKey, Fossify Keyboard.
The spoken English words in the cleanup and end word descriptions (「um」,
「new line」, 「over」, 「Twenty five percent」 → 「25%」…) and the
dictionary/snippet case examples (「teh」, 「Teh」, 「my email」), since Chinese
has no letter case. FUTO Keyboard's Voice Input labels stay in English
because FUTO shows them in English to Taiwan users (see below).

## External labels named

| Label as written | Where it is | Status |
|---|---|---|
| 設定 (Settings) | Android | verified (AOSP 16 QPR2 `settings_label`) |
| 應用程式 (Apps) | Android | verified (AOSP 16 QPR2 `apps_dashboard_title`) |
| 權限 (Permissions) | Android app info | verified (AOSP 16 QPR2 `permissions_label`) |
| 確定 (OK, the direct-boot warning dialog) | Android | verified (AOSP 16 QPR2 framework `ok`) |
| 設定 → 系統 → 鍵盤 → 螢幕小鍵盤 (Settings → System → Keyboard → On-screen keyboard) | Pixel | verified in AOSP 16 QPR2 (`header_category_system`, `keyboard_settings`, `virtual_keyboard_category`); the Pixel overlay not checked on a device |
| 主畫面 (home screen) | Android | descriptive |
| 導覽列 (navigation bar) | Android | verified (AOSP 16 QPR2 `accessibility_button_disabled_button_mode_summary` 「導覽列按鈕模式」) |
| Android 設定 → 應用程式 → Marmalade STT | Android | verified for 設定 and 應用程式 |
| 語言 | Android: System → Languages (Android 14–16) | verified (AOSP 16 QPR2 `languages_settings`) |
| 語音輸入 | Android: Voice input (also the search term) | verified (AOSP 16 QPR2, AOSP 13, AOSP 12 `voice_input_settings_title`) |
| 語言與輸入設定 | Android: Languages & input (Android 13) | verified (AOSP 13 `language_settings`) |
| 應用程式和通知 | Android: Apps & notifications (Android 9–11) | verified (AOSP 9, 10, 11 `app_and_notification_dashboard_title`) |
| 進階 | Android: Advanced (the collapsed group on Apps & notifications, Android 9–11) | verified in source (preference library `expand_button_title`: Android 9 tag for 9; AndroidX `androidx-preference-release` for 10–11, whose exact bundled version wasn't checked) |
| 預設應用程式 | Android: Default apps (Android 9–12) | verified (AOSP 9–12 Settings `app_default_dashboard_title`, the row tapped) |
| 小幫手和語音輸入 | Android: Assist & voice input (Android 9) | verified (AOSP 9 `assist_and_voice_input_title`) |
| 小幫手應用程式 | Android: Assist app (Android 9–10) | verified (AOSP 9 `default_assist_title`; Android 10 Default apps row: PermissionController `role_assistant_short_label`; Android 10 page row: AOSP 10 `default_assist_title`) |
| 數位助理應用程式 | Android: Digital assistant app (Default apps row, Android 11–12) | verified (PermissionController 11, 12 `role_assistant_short_label`) |
| 預設的數位助理應用程式 | Android: Default digital assistant app (Android 11) | verified (AOSP 11 `default_digital_assistant_title`) |
| 無 | Android: None (assistant picker) | verified (AOSP 9 `app_list_preference_none`; PermissionController 10, 11 `default_app_none`) |
| 語言和區域 | Android: Language & region (Android 16 where present, 17) | verified (AOSP 17 `language_and_region_settings`) |
| 語音 | Android: Speech (row of Language & region, Android 17) | verified (AOSP 17 `speech_preference_title`); on Android 13–16 Speech is a heading and isn't named |
| 強制停止 | Android: Force stop (written as a verb phrase) | verified (AOSP 16 QPR2 `force_stop`) |
| 設定 → 「Voice Input」 | FUTO Keyboard | verified as English: FUTO's public translations have no zh-rTW (or zh-rHK) `strings-uix.xml`, so these screens show English; the generic 設定 is descriptive |
| 「Disable built-in voice input」 | FUTO Keyboard | verified as English (same source) |
| 「Use voice input provided by external app」 | FUTO Keyboard | verified as English (same source) |
| 語音輸入方法 | Fossify Keyboard: Voice typing method | verified (Fossify `voice_typing_method`, main) |
| 多模式語音輸入 | SwiftKey: Multi-modal voice typing | from Microsoft's support page; in-app label unverified |
| 豐富輸入 | SwiftKey: Rich input | from Microsoft's support page; in-app label unverified |

Sources for the #79 rows: AOSP Settings `res/values-zh-rTW/strings.xml` on the `android-9.0.0_r1` tag and the `android10-release` … `android17-release` branches; PermissionController's strings (Android 10–11: `platform/packages/apps/PackageInstaller`, Android 12: `platform/packages/modules/Permission`), which label the Default apps rows and the assistant picker on 10–12; the preference library's `expand_button_title` (`platform/frameworks/support`). Not checked against a phone maker's overlay on a device.

Sources: AOSP Settings strings at https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/android16-qpr2-release/res/values-zh-rTW/strings.xml ("AOSP 16 QPR2", the Android 14+ path), the same file on `android13-release` ("AOSP 13") and `android12-release` ("AOSP 12"); the framework's `ok` at https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-qpr2-release/core/res/res/values-zh-rTW/strings.xml; FUTO Keyboard's translations at https://gitlab.futo.org/keyboard/keyboard-translations-public (`core/values-zh-rTW/` holds only `strings.xml`, `strings-action-keys.xml` and `strings-talkback-descriptions.xml`; read for wording only); Fossify Keyboard's `voice_typing_method` at https://github.com/FossifyOrg/Keyboard/blob/main/app/src/main/res/values-zh-rTW/strings.xml (main branch; the installed release not checked on a device); SwiftKey's labels from https://support.microsoft.com/zh-tw/swiftkey-keyboard/how-to-disable-multimodal-voice-dictation-in-swiftkey (machine-translated by Microsoft). AOSP labels are not checked against a Pixel's overlay on a device. If FUTO adds Traditional Chinese for its Voice Input screen, `onboarding_futo_manual_body` should switch to it.
