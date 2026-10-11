# Japanese (ja) glossary

## Register and conventions

- です・ます throughout, plain and friendly; labels, buttons, tabs and pills are
  noun phrases or short verb phrases (「マイクを許可」「ルールを追加」).
- Follows marmalade-tts-android's `values-ja`: 設定, 外観, 支援, ライセンス,
  エンジン, ダウンロード, このアプリについて, 開発者向け, 再試行, 配布元, 当方,
  「…をお試しください」, 端末 for "phone".
- Half-width space between Japanese and Latin words or numbers
  (「Marmalade STT を開く」「約 0.6〜3 GB」). No space next to a placeholder that
  is a language name, because it is filled with Japanese (「%1$sには非対応」 →
  「日本語には非対応」); engine names, sizes and numbers keep the space.
- Full-width （）, ：, ？ and 「」 for quotes and for naming other labels.
  Menu paths: 「設定」→「システム」.
- Spoken English (filler words, voice commands, punctuation words, number
  examples, the end word) stays English inside 「」.
- The dictionary and snippet help keep their Latin examples (「teh」「Teh」,
  「my email」): Japanese has no letter case, so a Japanese example couldn't
  show the rule.

## Terms

| English | Japanese |
|---|---|
| Dictate (tab, screen), dictation | 音声入力 |
| voice input (FUTO, other apps), speech input | 音声入力 |
| voice typing (keyboard name) | 音声入力 (「Marmalade 音声入力」, like 「Google 音声入力」) |
| voice keyboard | 音声キーボード |
| engine | エンジン |
| model / speech model | モデル / 音声認識モデル |
| download / downloaded | ダウンロード / ダウンロード済み (未ダウンロード) |
| speech (settings section) | 音声 |
| transcribe | 文字起こし |
| Recognition language | 認識言語 |
| Auto-detect | 自動検出 |
| Text cleanup | テキストの整形 |
| filler words | つなぎ言葉 |
| voice commands | 音声コマンド |
| Replacement dictionary | 置換辞書 |
| rule | ルール |
| snippet | 定型文 |
| trigger phrase | 呼び出しフレーズ |
| End of speech (section) | 話し終わりの判定 |
| end word | 終了ワード |
| auto-stop | 自動停止 |
| Auto-stop after a pause | 無音で自動停止 |
| Pause length | 無音の長さ |
| pause (to think) | 間（を置く） |
| Smart Turn patience: Quick / Normal / Relaxed | Smart Turn の待ち時間：短め / 標準 / 長め |
| Keep engine ready | エンジンを保持する (marmalade-tts-android's term) |
| Setup guide | セットアップガイド |
| Developer engines | 開発者向けエンジン |
| Speak now | お話しください |
| Listening / Hearing you… | 聞き取り中 / 聞こえています… |
| Ready | 準備完了 |
| Take (benchmark) | 録音 |
| Size on disk | 保存サイズ |
| Speed tiers: Fastest / Very fast / Fast / Moderate / Slow | 最速 / 非常に高速 / 高速 / 中速 / 低速 |
| Quality: Best / Great / Good / Fair / Basic | 最高 / 非常に良い / 良い / まずまず / 基本 (not 標準, which reads above まずまず; 標準 is the Normal patience) |
| Copy all / Import | すべてコピー / インポート |
| mic key (a keyboard's) | マイクキー |
| speech recognition service (Android's) | 音声認識サービス |
| high contrast (the colour preset, #68) | 高コントラスト (switch: “高コントラストの配色を使う”); matches AOSP `accessibility_toggle_high_text_contrast_preference_title` (Android 16 QPR2 source, not checked on a device) |

## Untranslated

Marmalade, Marmalade STT, every engine and model name, Smart Turn, FUTO
Keyboard (and "FUTO"), GitHub, GitHub Sponsors, GitHub Issue, Silero VAD,
ONNX Runtime, CPU, LibriSpeech, FLEURS, AMI, NVIDIA, OpenAI, IBM, Alibaba,
Moondream, WER, RTF, RSS, CTC, PRIVACY.md, Markdown, API キー, A/B, ms.

## Labels of Android and FUTO Keyboard

| Label | Japanese used | Status |
|---|---|---|
| Android: Settings | 設定 | verified |
| Android: Apps | アプリ | verified |
| Android: Permissions (app info) | 権限 | verified |
| Android: System | システム | verified |
| Android: System → Keyboard (Android 15+) | キーボード | unverified |
| Android: On-screen keyboard | 画面キーボード | unverified (high confidence) |
| Android: the direct-boot dialog's button | OK | verified |
| FUTO Keyboard: Settings | 設定 | unverified |
| FUTO Keyboard: Voice Input | 音声入力 | unverified |
| FUTO Keyboard: "Disable built-in voice input" | 内蔵の音声入力を無効にする | unverified |
| FUTO Keyboard: "Use voice input provided by external app" | 外部アプリが提供する音声入力を使用する | unverified |
| Android: System → Languages (Android 14–16) | 言語 | verified (AOSP 16 QPR2 `languages_settings`) |
| Android: Voice input (also the search term) | 音声入力 | verified (AOSP 16 QPR2, AOSP 13, AOSP 12 `voice_input_settings_title`) |
| Android: Languages & input (Android 13) | 言語と入力 | verified (AOSP 13 `language_settings`) |
| Android: Apps & notifications (Android 9–11) | アプリと通知 | verified (AOSP 9, 10, 11 `app_and_notification_dashboard_title`) |
| Android: Advanced (the collapsed group on Apps & notifications, Android 9–11) | 詳細設定 | verified in source (preference library `expand_button_title`: Android 9 tag for 9; AndroidX `androidx-preference-release` for 10–11, whose exact bundled version wasn't checked) |
| Android: Default apps (Android 9–12) | デフォルト アプリ (Android 9–10); デフォルトのアプリ (Android 11–12) | verified (AOSP 9–12 Settings `app_default_dashboard_title`, the row tapped) |
| Android: Assist & voice input (Android 9) | アシストと音声入力 | verified (AOSP 9 `assist_and_voice_input_title`) |
| Android: Assist app (Android 9–10) | アシストアプリ | verified (AOSP 9 `default_assist_title`; Android 10 Default apps row: PermissionController `role_assistant_short_label`; Android 10 page row: AOSP 10 `default_assist_title`) |
| Android: Digital assistant app (Default apps row, Android 11–12) | デジタル アシスタント アプリ | verified (PermissionController 11, 12 `role_assistant_short_label`) |
| Android: Default digital assistant app (Android 11) | デフォルトのデジタル アシスタント アプリ | verified (AOSP 11 `default_digital_assistant_title`) |
| Android: None (assistant picker) | なし | verified (AOSP 9 `app_list_preference_none`; PermissionController 10, 11 `default_app_none`) |
| Android: Language & region (Android 16 where present, 17) | 言語と地域 | verified (AOSP 17 `language_and_region_settings`) |
| Android: Speech (row of Language & region, Android 17) | 音声 | verified (AOSP 17 `speech_preference_title`); on Android 13–16 Speech is a heading and isn't named |
| Android: Force stop (written as a verb phrase) | 強制停止 | verified (AOSP 16 QPR2 `force_stop`) |
| Fossify Keyboard: Voice typing method | 音声入力方式 | verified (Fossify `voice_typing_method`, main) |
| SwiftKey: Multi-modal voice typing | マルチモーダル音声入力 | from Microsoft's support page; in-app label unverified |
| SwiftKey: Rich input | リッチ入力 | from Microsoft's support page; in-app label unverified |

Sources for the #79 rows: AOSP Settings `res/values-ja/strings.xml` on the `android-9.0.0_r1` tag and the `android10-release` … `android17-release` branches; PermissionController's strings (Android 10–11: `platform/packages/apps/PackageInstaller`, Android 12: `platform/packages/modules/Permission`), which label the Default apps rows and the assistant picker on 10–12; the preference library's `expand_button_title` (`platform/frameworks/support`). Not checked against a phone maker's overlay on a device.

Sources for the #61 rows: AOSP Settings strings at https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/android16-qpr2-release/res/values-ja/strings.xml ("AOSP 16 QPR2", the Android 14+ path), the same file on `android13-release` ("AOSP 13") and `android12-release` ("AOSP 12"); Fossify Keyboard's `voice_typing_method` at https://github.com/FossifyOrg/Keyboard/blob/main/app/src/main/res/values-ja/strings.xml (main branch; the installed release not checked on a device); SwiftKey's labels from Microsoft's support page https://support.microsoft.com/ja-jp/swiftkey-keyboard/how-to-disable-multimodal-voice-dictation-in-swiftkey (machine-translated by Microsoft, so the in-app label is not checked). AOSP labels are not checked against a Pixel's overlay on a device.
