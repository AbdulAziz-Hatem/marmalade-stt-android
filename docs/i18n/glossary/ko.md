# Korean (ko) glossary

## Register

Polite **합니다/하세요** style, as Android's Korean UI uses it: instructions
end in 하세요 ("마이크를 탭하세요", "다시 시도하세요"), descriptions and
messages in 합니다 ("받아쓰는 동안에만 듣습니다"), errors as "~할 수 없습니다"
("마이크를 사용할 수 없습니다"). Buttons, chips and status lines are noun
phrases ("다운로드", "다시 시도", "듣는 중", "준비됨", "켜짐"). No 해요체, no
pronoun for "you": Korean UI leaves the user implicit. Quotation marks “…”
(also `quoted_name`); `list_separator` stays ", ". App labels in running text
are quoted when they name a setting (“멈춤 시간”, “규칙 추가”); tab names and
Android menu paths (설정 → 시스템 → …) are not, matching the English.

"Phone" is **휴대전화** throughout (Google's Korean wording), never 폰 or 기기.
Numbers and units: "0.6~3GB", "%1$.1f초", "100ms" (no space before a unit, as
Korean UI writes them); counters after the noun ("규칙 %1$d개", "%d개 언어").

Plurals: `other` only.

## Terms

| English | Korean | Notes |
|---|---|---|
| Dictate (tab, screen) | 받아쓰기 (받아쓰기 탭 / 화면) | |
| Engines (tab) | 엔진 | |
| Settings (tab, title) | 설정 | |
| tab | 탭 | |
| dictation / dictate | 받아쓰기 / 받아쓰다 | |
| transcribe | (텍스트로) 변환하다; 받아쓰다 in the language-name strings | |
| engine / speech engine | 엔진 / 음성 엔진 | |
| model / speech model | 모델 / 음성 모델 | |
| download (verb / noun) | 다운로드하다 / 다운로드 | |
| Remove (model files) / Remove download | 제거 / 다운로드 삭제 | |
| Size on disk / Download size (engine details) | 설치 크기 / 다운로드 크기 | 저장 용량 is Android's label for the phone's storage |
| voice keyboard | 음성 키보드 | |
| Marmalade voice typing (IME name) | Marmalade 음성 입력 | Mirrors Android's "Google 음성 입력" |
| voice typing / voice input / speech input | 음성 입력 | Android Korean uses one term for all three |
| speech (settings section) | 음성 | |
| speak-now popup / Speak now | “말하세요” 팝업 / 말하세요 | Google's Korean prompt |
| End of speech (section) | 중지 방식 | "How it stops"; 음성 종료 reads as "voice ends" |
| Tap to stop | 탭하여 중지 | |
| auto-stop | 자동 중지 | |
| Auto-stop after a pause | 말을 멈추면 자동 중지 | |
| pause (in speech) / Pause length | 멈춤 / 멈춤 시간 | 일시중지 is kept for media-style pausing (keyboard panel, download) |
| end word / End words | 종료 단어 | |
| Smart Turn patience: Quick / Normal / Relaxed | Smart Turn 대기 시간: 빠름 / 보통 / 여유 | 인내심 reads oddly for a setting |
| Recognition language | 인식 언어 | |
| Auto / Auto-detect | 자동 / 자동 감지 | |
| Text cleanup | 텍스트 정리 | |
| filler words | 군말 | |
| voice commands | 음성 명령 | |
| Punctuation words | 문장부호 단어 | |
| Replacement dictionary | 대체 사전 | |
| rule / Add rule | 규칙 / 규칙 추가 | |
| Snippets / Add snippet | 상용구 / 상용구 추가 | The usual Korean word for saved phrases (Samsung Keyboard uses it) |
| trigger phrase | 트리거 문구 | |
| phrase (dictionary) | 구문 | |
| Setup guide | 설정 가이드 | |
| Developer / Developer engines | 개발자 / 개발자 엔진 | |
| benchmark / A/B benchmark | 벤치마크 / A/B 벤치마크 | |
| take (benchmark recording) | 녹음 | |
| unload | 언로드 | Paired with 로드 |
| streaming | 스트리밍 | |
| tap / press and hold | 탭하다 / 길게 누르다 | Android Korean |
| microphone / mic key | 마이크 / 마이크 키 | |
| Listening / Hearing you… | 듣는 중 / 음성 감지 중… | |
| Ready | 준비됨 | |
| Speed: Fastest / Very fast / Fast / Moderate / Slow | 가장 빠름 / 매우 빠름 / 빠름 / 보통 / 느림 | |
| Quality: Best / Great / Good / Fair / Basic | 최고 / 우수 / 좋음 / 보통 / 기본 | |
| Support (section) / Support development | 후원 / 개발 후원 | Donations, not help |
| Report a bug / More Marmalade | 버그 신고 / Marmalade 앱 더보기 | |
| About | 정보 | Android Korean ("휴대전화 정보") |
| license | 라이선스 | |
| Privacy policy | 개인정보처리방침 | |
| analytics | 분석 | |
| word error rate (WER) | 단어 오류율(WER) | |
| Light / Dark / System | 밝게 / 어둡게 / 시스템 | |
| Wallpaper / Midnight / Forest / Berry | 배경화면 / 한밤 / 숲 / 베리 | |
| high contrast (the colour preset, #68) | 고대비 (switch: “고대비 색상 사용”) | matches AOSP `accessibility_toggle_high_text_contrast_preference_title` (Android 16 QPR2 source, not checked on a device) |
| speech recognition service (Android's) | 음성 인식 서비스 | |

Language names in placeholders: Android's Korean display names are bare nouns
(영어, 일본어, 중국어 …). A particle can't follow a placeholder, because 을/를,
이/가, 은/는, 와/과 and (으)로 depend on the last sound. So the strings put the
name before a noun ("%1$s 받아쓰기 미지원", "%1$s 테스트 기준", "%1$s용 …")
or before a particle that never changes (에, 용), or after a colon. The same
holds for engine names (`%1$s 엔진을 다운로드할까요?`) and file names
(`%1$s 파일이 없습니다.`). Quoted English words in fixed text do take the
particle their Korean reading needs (“tehran”과, “over”처럼).

## Kept untranslated

Marmalade, Marmalade STT, every engine and model name (Parakeet Ultra, Parakeet
110M, Parakeet Japanese, Canary 180M Flash, Nemotron 3.5, Whisper Large v3
Turbo, Granite Speech, Qwen3-ASR, and upstream names in descriptions such as
Parakeet TDT 0.6B v3 and large-v3-turbo), Smart Turn, FUTO Keyboard, GitHub,
GitHub Sponsors, Silero VAD, ONNX Runtime, CPU, LibriSpeech, FLEURS, AMI,
NVIDIA, OpenAI, IBM, Alibaba, Moondream, WER, RTF, RSS, CTC, Markdown,
PRIVACY.md, Android, Google, Microsoft, SwiftKey, Fossify Keyboard. The spoken
English words in the cleanup and end word descriptions (“um”, “new line”,
“over”, “Twenty five percent” → “25%”…) and the dictionary/snippet case
examples (“teh”, “Teh”, “my email”), since Korean has no letter case. The
Korean particle example (“메일” matches “메일을”) is the English's own.

## External labels named

| Label (English) | Korean used | Status |
|---|---|---|
| Android: Settings | 설정 | verified (AOSP 16 QPR2 `settings_label`) |
| Android: Apps | 앱 | verified (AOSP 16 QPR2 `apps_dashboard_title`) |
| Android: Permissions (app info) | 권한 | verified (AOSP 16 QPR2 `permissions_label`) |
| Android: System | 시스템 | verified (AOSP 16 QPR2 `header_category_system`) |
| Android: Settings → System → Keyboard → On-screen keyboard | 설정 → 시스템 → 키보드 → 터치 키보드 | verified in AOSP 16 QPR2 (`keyboard_settings`, `virtual_keyboard_category`); the Pixel overlay not checked on a device |
| Android: OK (second keyboard dialog) | 확인 | verified (AOSP 16 QPR2 framework `ok`) |
| Android: home screen | 홈 화면 | descriptive |
| Android: navigation bar (keyboard button) | 탐색 메뉴 | verified (AOSP 16 QPR2 `accessibility_button_disabled_button_mode_summary` "탐색 메뉴 버튼 모드") |
| Android: System → Languages (Android 14–16) | 언어 | verified (AOSP 16 QPR2 `languages_settings`) |
| Android: Voice input (also the search term) | 음성 입력 | verified (AOSP 16 QPR2, AOSP 13, AOSP 12 `voice_input_settings_title`) |
| Android: Languages & input (Android 13) | 언어 및 입력 | verified (AOSP 13 `language_settings`) |
| Android: Apps & notifications (Android 9–11) | 앱 및 알림 | verified (AOSP 9, 10, 11 `app_and_notification_dashboard_title`) |
| Android: Advanced (the collapsed group on Apps & notifications, Android 9–11) | 고급 | verified in source (preference library `expand_button_title`: Android 9 tag for 9; AndroidX `androidx-preference-release` for 10–11, whose exact bundled version wasn't checked) |
| Android: Default apps (Android 9–12) | 기본 앱 | verified (AOSP 9–12 Settings `app_default_dashboard_title`, the row tapped) |
| Android: Assist & voice input (Android 9) | 지원 및 음성 입력 | verified (AOSP 9 `assist_and_voice_input_title`) |
| Android: Assist app (Android 9–10) | 지원 앱 | verified (AOSP 9 `default_assist_title`; Android 10 Default apps row: PermissionController `role_assistant_short_label`; Android 10 page row: AOSP 10 `default_assist_title`) |
| Android: Digital assistant app (Default apps row, Android 11–12) | 디지털 어시스턴트 앱 | verified (PermissionController 11, 12 `role_assistant_short_label`) |
| Android: Default digital assistant app (Android 11) | 기본 디지털 어시스턴트 앱 | verified (AOSP 11 `default_digital_assistant_title`) |
| Android: None (assistant picker) | 없음 | verified (AOSP 9 `app_list_preference_none`; PermissionController 10, 11 `default_app_none`) |
| Android: Language & region (Android 16 where present, 17) | 언어 및 지역 | verified (AOSP 17 `language_and_region_settings`) |
| Android: Speech (row of Language & region, Android 17) | 음성 | verified (AOSP 17 `speech_preference_title`); on Android 13–16 Speech is a heading and isn't named |
| Android: Force stop (written as a verb phrase) | 강제 종료 (“강제 종료하면”) | verified (AOSP 16 QPR2 `force_stop`) |
| FUTO Keyboard: Settings → Voice Input | 설정 → 음성 입력 | 음성 입력 verified (FUTO `voice_input_settings_title`); 설정 unverified |
| FUTO Keyboard: "Disable built-in voice input" | “내장 음성 입력 비활성화” | verified (FUTO `voice_input_settings_disable_builtin_voice_input`) |
| FUTO Keyboard: "Use voice input provided by external app" | “외부 앱에서 제공하는 음성 입력 사용” | verified (FUTO `…_subtitle`) |
| Fossify Keyboard: Voice typing method | 음성 입력 | verified (Fossify `voice_typing_method`, main) |
| SwiftKey: Multi-modal voice typing | 멀티모달 음성 입력 | from Microsoft's support page; in-app label unverified (the page's step heading says "멀티 모드 음성 입력", the toggle line "멀티모달 음성 입력"; the latter used) |
| SwiftKey: Rich input | 풍부한 입력 | from Microsoft's support page; in-app label unverified (heading "서식 있는 입력", the tap target "풍부한 입력"; the latter used) |

Sources for the #79 rows: AOSP Settings `res/values-ko/strings.xml` on the `android-9.0.0_r1` tag and the `android10-release` … `android17-release` branches; PermissionController's strings (Android 10–11: `platform/packages/apps/PackageInstaller`, Android 12: `platform/packages/modules/Permission`), which label the Default apps rows and the assistant picker on 10–12; the preference library's `expand_button_title` (`platform/frameworks/support`). Not checked against a phone maker's overlay on a device.

Sources: AOSP Settings strings at https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/android16-qpr2-release/res/values-ko/strings.xml ("AOSP 16 QPR2", the Android 14+ path), the same file on `android13-release` ("AOSP 13") and `android12-release` ("AOSP 12"); the framework's `ok` at https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-qpr2-release/core/res/res/values-ko/strings.xml; FUTO Keyboard's labels from its public translations repo https://gitlab.futo.org/keyboard/keyboard-translations-public (`core/values-ko/strings-uix.xml`, read for wording only); Fossify Keyboard's `voice_typing_method` at https://github.com/FossifyOrg/Keyboard/blob/main/app/src/main/res/values-ko/strings.xml (main branch; the installed release not checked on a device); SwiftKey's labels from https://support.microsoft.com/ko-kr/swiftkey-keyboard/how-to-disable-multimodal-voice-dictation-in-swiftkey (machine-translated by Microsoft). AOSP labels are not checked against a Pixel's overlay on a device.
