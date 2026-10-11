# Glossary: Simplified Chinese (zh-rCN)

## Register and style

- Address the user as **你** throughout (as marmalade-tts-android does; its one
  stray 您 in `settings_privacy_desc` was not copied).
- Mainland Simplified Chinese UI style. Full-width punctuation (，。：；？（）),
  quotes “…”, ellipsis … in status lines.
- A space between Han characters and Latin letters or digits (`版本 %1$s`,
  `0.6 到 3 GB`), none next to full-width punctuation. No space around a
  placeholder that is filled with a Chinese language name (`无法转写%1$s`).
- Quoted labels in a row take no 顿号 between them (“um”“uh”), per GB/T 15834.
- Menu paths use “A → B → C” inside one pair of quotes, as the sibling app does.
- Plurals: `other` only, with a measure word (条规则, 个片段, 种语言, 行).
- Units: seconds as 秒 (`%1$.1f 秒`); milliseconds stay `ms`.

## Terms

| English | Chinese |
|---|---|
| Dictate (tab) / dictation | 听写 |
| Engines (tab) / engine | 引擎 |
| Settings (tab) | 设置 |
| tab | 标签页 (“引擎”标签页) |
| model / speech model | 模型 / 语音模型 |
| download (verb / noun) | 下载 / 下载内容 |
| Remove (model files) | 移除 |
| Remove download | 删除下载内容 |
| voice keyboard | 语音键盘 |
| Marmalade voice typing (IME name) | Marmalade 语音输入 |
| voice typing / voice input / speech input | 语音输入 |
| speech | 语音 |
| speak-now popup / Speak now | “请说话”弹窗 / 请说话 |
| transcribe | 转写 |
| recognize / recognition | 识别 |
| Recognition language | 识别语言 |
| Auto / Auto-detect | 自动 / 自动检测 |
| Text cleanup | 文本整理 |
| filler words | 语气词 |
| voice commands | 语音指令 |
| Replacement dictionary | 替换词典 |
| rule / Add rule | 规则 / 添加规则 |
| Snippets / snippet / Add snippet | 文本片段 / 片段 / 添加片段 |
| trigger phrase | 触发短语 |
| End of speech (section) | 停止方式 |
| Tap to stop | 点按停止 |
| auto-stop | 自动停止 |
| Auto-stop after a pause | 停顿后自动停止 |
| Pause length | 停顿时长 |
| end word / End words | 结束词 |
| Smart Turn patience: Quick / Normal / Relaxed | Smart Turn 耐心：快速 / 正常 / 宽松 |
| Setup guide | 设置向导 |
| Developer / Developer engines | 开发者 / 开发者引擎 |
| benchmark / A/B benchmark | 基准测试 / A/B 基准测试 |
| take (benchmark recording) | 录音 |
| streaming | 流式 |
| tap | 点按 |
| microphone / mic | 麦克风 |
| Listening | 正在聆听 |
| Ready | 已就绪 |
| Speed: Fastest / Very fast / Fast / Moderate / Slow | 最快 / 非常快 / 快速 / 中等 / 较慢 |
| Quality: Best / Great / Good / Fair / Basic | 最佳 / 优秀 / 良好 / 一般 / 基础 |
| license | 许可 |
| privacy policy | 隐私政策 |
| word error rate (WER) | 词错误率（WER） |
| this phone | 这部手机 |
| mic key (a keyboard's) | 麦克风键 |
| speech recognition service (Android's) | 语音识别服务 |
| high contrast (the colour preset, #68) | 高对比度 (switch: “使用高对比度配色”); matches AOSP `accessibility_toggle_high_text_contrast_preference_title` (Android 16 QPR2 source, not checked on a device) |

## Kept untranslated

Marmalade, Marmalade STT, every engine and model name (Parakeet Ultra, Parakeet
110M, Parakeet Japanese, Canary 180M Flash, Nemotron 3.5, Whisper Large v3
Turbo, Granite Speech, Qwen3-ASR, and upstream names in descriptions such as
Parakeet TDT 0.6B v3 and large-v3-turbo), Smart Turn, FUTO Keyboard, GitHub,
GitHub Sponsors, GitHub issue, Silero VAD, ONNX Runtime, CPU, LibriSpeech,
FLEURS, AMI, NVIDIA, OpenAI, IBM, Alibaba, Moondream, WER, RTF, RSS, CTC,
Markdown, PRIVACY.md, Android, Google. The spoken English words in the cleanup
and end word descriptions (“um”, “new line”, “over”, “Twenty five percent” →
“25%”…) and the dictionary/snippet case examples (“teh”, “Teh”, “my email”),
since Chinese has no letter case.

## Labels of Android and FUTO Keyboard

| Label as written | Where it is | Status |
|---|---|---|
| 设置 (Settings) | Android | verified |
| 应用 (Apps) | Android | verified |
| 权限 (Permissions) | Android app info | verified |
| 确定 (OK, the direct-boot warning dialog) | Android | verified |
| 设置 → 系统 → 键盘 → 屏幕键盘 (Settings → System → Keyboard → On-screen keyboard) | Pixel | unverified (系统 and 屏幕键盘 are AOSP wording; the Android 15 “键盘” level is not checked) |
| Android 设置 → 应用 → Marmalade STT | Android | verified for 设置 and 应用 |
| 设置 → 语音输入 (Settings → Voice Input) | FUTO Keyboard | unverified |
| “停用内置语音输入” (Disable built-in voice input) | FUTO Keyboard | unverified |
| “使用外部应用提供的语音输入” (Use voice input provided by external app) | FUTO Keyboard | unverified |
| 语言 | Android: System → Languages (Android 14–16) | verified (AOSP 16 QPR2 `languages_settings`) |
| 语音输入 | Android: Voice input (also the search term) | verified (AOSP 16 QPR2, AOSP 13, AOSP 12 `voice_input_settings_title`) |
| 语言和输入法 | Android: Languages & input (Android 13) | verified (AOSP 13 `language_settings`) |
| 应用和通知 | Android: Apps & notifications (Android 9–11) | verified (AOSP 9, 10, 11 `app_and_notification_dashboard_title`) |
| 高级 | Android: Advanced (the collapsed group on Apps & notifications, Android 9–11) | verified in source (preference library `expand_button_title`: Android 9 tag for 9; AndroidX `androidx-preference-release` for 10–11, whose exact bundled version wasn't checked) |
| 默认应用 | Android: Default apps (Android 9–12) | verified (AOSP 9–12 Settings `app_default_dashboard_title`, the row tapped) |
| 助手和语音输入 | Android: Assist & voice input (Android 9) | verified (AOSP 9 `assist_and_voice_input_title`) |
| 助手应用 (Android 9); 辅助应用 (Android 10); the row on Android 10's page (set to None): 助手应用 | Android: Assist app (Android 9–10) | verified (AOSP 9 `default_assist_title`; Android 10 Default apps row: PermissionController `role_assistant_short_label`; Android 10 page row: AOSP 10 `default_assist_title`) |
| 数字助理应用 | Android: Digital assistant app (Default apps row, Android 11–12) | verified (PermissionController 11, 12 `role_assistant_short_label`) |
| 默认数字助理应用 | Android: Default digital assistant app (Android 11) | verified (AOSP 11 `default_digital_assistant_title`) |
| 无 | Android: None (assistant picker) | verified (AOSP 9 `app_list_preference_none`; PermissionController 10, 11 `default_app_none`) |
| 语言和区域 | Android: Language & region (Android 16 where present, 17) | verified (AOSP 17 `language_and_region_settings`) |
| 语音 | Android: Speech (row of Language & region, Android 17) | verified (AOSP 17 `speech_preference_title`); on Android 13–16 Speech is a heading and isn't named |
| 强行停止 | Android: Force stop (written as a verb phrase) | verified (AOSP 16 QPR2 `force_stop`) |
| 语音输入方式 | Fossify Keyboard: Voice typing method | verified (Fossify `voice_typing_method`, main) |
| 多模式语音键入 | SwiftKey: Multi-modal voice typing | from Microsoft's support page; in-app label unverified; the page says 多模式语音输入 in its intro and 多模式语音键入 for the toggle, the latter used |
| 丰富输入 | SwiftKey: Rich input | from Microsoft's support page; in-app label unverified |

Sources for the #79 rows: AOSP Settings `res/values-zh-rCN/strings.xml` on the `android-9.0.0_r1` tag and the `android10-release` … `android17-release` branches; PermissionController's strings (Android 10–11: `platform/packages/apps/PackageInstaller`, Android 12: `platform/packages/modules/Permission`), which label the Default apps rows and the assistant picker on 10–12; the preference library's `expand_button_title` (`platform/frameworks/support`). Not checked against a phone maker's overlay on a device.

Sources for the #61 rows: AOSP Settings strings at https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/android16-qpr2-release/res/values-zh-rCN/strings.xml ("AOSP 16 QPR2", the Android 14+ path), the same file on `android13-release` ("AOSP 13") and `android12-release` ("AOSP 12"); Fossify Keyboard's `voice_typing_method` at https://github.com/FossifyOrg/Keyboard/blob/main/app/src/main/res/values-zh-rCN/strings.xml (main branch; the installed release not checked on a device); SwiftKey's labels from Microsoft's support page https://support.microsoft.com/zh-cn/swiftkey-keyboard/how-to-disable-multimodal-voice-dictation-in-swiftkey (machine-translated by Microsoft, so the in-app label is not checked). AOSP labels are not checked against a Pixel's overlay on a device.
