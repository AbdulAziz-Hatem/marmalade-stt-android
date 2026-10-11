# Hindi (hi) glossary

## Register

Polite **आप** throughout, in everyday Hindi as Android and Gboard show it, not
Sanskritised officialese. Common English tech loanwords are written in
Devanagari as Indian Android users see them (ऐप, सेटिंग, डाउनलोड, माइक्रोफ़ोन,
इंजन, मॉडल, टैप, सेव), matching marmalade-tts-android's `values-hi`. Buttons
use the polite imperative (डाउनलोड करें, रद्द करें, माइक्रोफ़ोन की अनुमति दें).
Nukta spellings as in the TTS app (फ़ोन, ज़रूरत, फ़ाइल) and chandrabindu
endings (हटाएँ, भाषाएँ), except where an Android or FUTO label is quoted
verbatim (अनुमतियां, वॉयस इनपुट).

Sentences end with the Devanagari danda **।**, like the TTS app; `?` and `:`
stay. Quotation marks are “…” (also `quoted_name`); `list_separator` stays
", ". Labels of this app that are verb phrases or multi-word setting names are
quoted in running text (“बोलकर लिखें” स्क्रीन, “ठहराव की लंबाई”, “नियम जोड़ें”);
the Engines tab is just "इंजन टैब", and Android menu paths (सेटिंग → सिस्टम →
…) and अनुमतियां are not quoted, matching the English. Seconds in the pause
slider are "सेकंड"; the developer benchmark keeps "s" and "ms".

## Terms

| English | Hindi | Note |
|---|---|---|
| Dictate (tab) | बोलकर लिखें | “बोलकर लिखें” टैब / स्क्रीन |
| Engines (tab) | इंजन | इंजन टैब |
| Settings (tab) | सेटिंग | as Android and the TTS app |
| tab | टैब | |
| dictation / dictate | बोलकर लिखना / बोलकर लिखना | “बोलकर लिखना शुरू करें” |
| engine | इंजन | speech engine: स्पीच इंजन |
| model / speech model | मॉडल / स्पीच मॉडल | |
| language model (Qwen3 decoder) | लैंग्वेज-मॉडल | |
| download (verb / noun) | डाउनलोड करें / डाउनलोड | डाउनलोड साइज़ |
| voice keyboard | वॉइस कीबोर्ड | |
| voice input | वॉइस इनपुट | FUTO's own labels spell it वॉयस इनपुट; used only when quoting FUTO |
| voice typing (IME) | वॉइस टाइपिंग | IME name: Marmalade वॉइस टाइपिंग |
| speech input (RecognitionService) | स्पीच इनपुट | |
| speech (settings section) | स्पीच | |
| speech is recognized | स्पीच की पहचान … होती है | |
| transcribe | टेक्स्ट में बदलना | developer benchmark: ट्रांसक्राइब |
| recognition language | पहचान की भाषा | |
| End of speech (section) | बोलने का अंत | |
| auto-stop | अपने आप रुकें | “ठहराव के बाद अपने आप रुकें”, “Smart Turn से अपने आप रुकें” |
| pause (in speech) / Pause length | ठहराव / ठहराव की लंबाई | the Pause button is "रोकें" |
| end word | अंत-शब्द | |
| patience (Smart Turn) | धैर्य | Quick / Normal / Relaxed: जल्दी / सामान्य / आराम से |
| lock / locked (hold the mic) | लॉक करें / लॉक है | |
| speak-now popup | “अभी बोलें” पॉपअप | |
| text cleanup | टेक्स्ट की सफ़ाई | |
| filler words | फ़िलर शब्द | |
| voice commands | वॉइस कमांड | |
| punctuation words | विराम चिह्न वाले शब्द | |
| replacement dictionary | शब्द बदलने का शब्दकोश | |
| rule | नियम | नियम जोड़ें |
| snippet | स्निपेट | स्निपेट जोड़ें |
| trigger phrase | ट्रिगर वाक्यांश | |
| setup guide | सेटअप गाइड | |
| step | चरण | |
| Developer engines | डेवलपर इंजन | |
| speed | गति | as the TTS app's spec column |
| quality: Best / Great / Good / Fair / Basic | सबसे अच्छी / बहुत अच्छी / अच्छी / ठीक-ठाक / सामान्य | feminine, agreeing with गुणवत्ता |
| take (benchmark) | टेक | |
| memory / storage | मेमोरी / स्टोरेज | |
| privacy policy | गोपनीयता नीति | |
| analytics / tracking / account | एनालिटिक्स / ट्रैकिंग / खाता | |
| About | ऐप के बारे में | the row: Marmalade STT के बारे में |
| Report a bug | गड़बड़ी की शिकायत करें | GitHub "issue" kept in Latin, as the TTS app |
| Wallpaper / Midnight / Forest / Berry | वॉलपेपर / आधी रात / जंगल / बेरी | colour presets |
| high contrast (the colour preset, #68) | ज़्यादा कंट्रास्ट (switch: “ज़्यादा कंट्रास्ट वाले रंग इस्तेमाल करें”) | derived: Android says "High contrast text" with a verb phrase (टेक्स्ट का कंट्रास्ट बढ़ाएं); its contrast level "High" is ज़्यादा; not checked on a device |
| Expanded / Collapsed (TalkBack) | बड़ा किया गया / छोटा किया गया | Material 3's own Hindi |
| mic key (a keyboard's) | माइक बटन | #61 |
| speech recognition service (Android's) | स्पीच पहचान सेवा | #61; also the summary row |

## Untranslated

Marmalade, Marmalade STT, every engine and model name (Parakeet Ultra, Parakeet
110M, Parakeet Japanese, Canary 180M Flash, Nemotron 3.5, Whisper Large v3
Turbo, Granite Speech, Qwen3-ASR, and upstream names in descriptions such as
Parakeet TDT 0.6B v3 and large-v3-turbo), Smart Turn, FUTO Keyboard, GitHub,
GitHub Sponsors, Silero VAD, ONNX Runtime, CPU, LibriSpeech, FLEURS, AMI,
NVIDIA, OpenAI, IBM, Alibaba, Moondream, WER, RTF, RSS, CTC, PRIVACY.md,
Markdown, A/B, API, MB/GB/ms. The spoken English words in the cleanup and
end-word descriptions (“um”, “new line”, “period”, “Twenty five percent”, “over”
…) and the matching examples in the dictionary and snippet help (“teh”, “my
email”) stay English.

## Labels of Android and FUTO Keyboard

| Label (English) | Hindi used | Status |
|---|---|---|
| Android: Settings | सेटिंग | verified (AOSP Settings `settings_label`, main) |
| Android: Apps | ऐप्लिकेशन | verified (AOSP `apps_dashboard_title`) |
| Android: Permissions | अनुमतियां | verified (AOSP `permissions_label`) |
| Android: System | सिस्टम | verified (AOSP `header_category_system`; AOSP has a ZWJ inside, we write it plainly) |
| Android: Keyboard | कीबोर्ड | verified in AOSP (`keyboard_settings`); Pixel overlay not checked on a device |
| Android: On-screen keyboard | ऑन-स्क्रीन कीबोर्ड | verified in AOSP (`virtual_keyboard_category`); Pixel overlay not checked on a device |
| Android: OK (direct-boot dialog) | ठीक है | verified (framework `ok`, android-36) |
| FUTO: Settings | सेटिंग | unverified (FUTO's translations use both सेटिंग and सेटिंग्स) |
| FUTO: Voice Input | वॉयस इनपुट | verified (FUTO keyboard-translations-public `core/values-hi`, `voice_input_settings_title`); the installed build not checked on a device |
| FUTO: Disable built-in voice input | निर्मित वॉयस इनपुट निष्क्रिय करें | verified (same source, same caveat) |
| FUTO: Use voice input provided by external app | बाहरी ऐप द्वारा प्रदान किए गए वॉयस इनपुट का उपयोग करें | verified (same source, same caveat) |
| Android: System → Languages (Android 14–16) | भाषाएं | verified (AOSP 16 QPR2 `languages_settings`) |
| Android: Voice input (also the search term) | फ़ोन को बोलकर निर्देश देने से जुड़ी सेटिंग (Android 14+ path); बोलकर फ़ोन को निर्देश (Android 9–13 paths); search term “बोलकर निर्देश” | verified (AOSP 16 QPR2; Android 14–15 word it बोलकर फ़ोन को निर्देश देने से जुड़ी सेटिंग; AOSP 13, AOSP 12 बोलकर फ़ोन को निर्देश). The search term is a substring of the current label and of the Assist label; that Settings search finds it is unverified |
| Android: Languages & input (Android 13) | भाषाएं और इनपुट | verified (AOSP 13 `language_settings`) |
| Android: Apps & notifications (Android 9–11) | ऐप्लिकेशन और सूचनाएं | verified (AOSP 9, 10, 11 `app_and_notification_dashboard_title`) |
| Android: Advanced (the collapsed group on Apps & notifications, Android 9–11) | बेहतर सेटिंग | verified in source (preference library `expand_button_title`: Android 9 tag for 9; AndroidX `androidx-preference-release` for 10–11, whose exact bundled version wasn't checked) |
| Android: Default apps (Android 9–12) | डिफ़ॉल्ट ऐप्लिकेशन | verified (AOSP 9–12 Settings `app_default_dashboard_title`, the row tapped) |
| Android: Assist & voice input (Android 9) | सहायक और बोलकर निर्देश देना | verified (AOSP 9 `assist_and_voice_input_title`) |
| Android: Assist app (Android 9–10) | सहायक ऐप्लिकेशन (Android 9); सहायक ऐप (Android 10); the row on Android 10's page (set to None): सहायक ऐप्लिकेशन | verified (AOSP 9 `default_assist_title`; Android 10 Default apps row: PermissionController `role_assistant_short_label`; Android 10 page row: AOSP 10 `default_assist_title`) |
| Android: Digital assistant app (Default apps row, Android 11–12) | डिजिटल असिस्टेंट ऐप्लिकेशन | verified (PermissionController 11, 12 `role_assistant_short_label`) |
| Android: Default digital assistant app (Android 11) | डिफ़ॉल्ट डिजिटल असिस्टेंट ऐप | verified (AOSP 11 `default_digital_assistant_title`) |
| Android: None (assistant picker) | कोई नहीं | verified (AOSP 9 `app_list_preference_none`; PermissionController 10, 11 `default_app_none`) |
| Android: Language & region (Android 16 where present, 17) | भाषा और देश/इलाका | verified (AOSP 17 `language_and_region_settings`) |
| Android: Speech (row of Language & region, Android 17) | बोली | verified (AOSP 17 `speech_preference_title`); on Android 13–16 Speech is a heading and isn't named |
| Android: Force stop (written as a verb phrase) | ज़बरदस्ती रोकें (“ज़बरदस्ती रोकते हैं”) | verified (AOSP 16 QPR2 `force_stop`) |
| Fossify Keyboard: Voice typing method | वॉयस टाइपिंग विधि (Fossify's वॉयस spelling, quoted verbatim) | verified (Fossify `voice_typing_method`, main) |
| SwiftKey: Multi-modal voice typing | “Multi-modal voice typing” (English kept) | unverified (no Hindi source found) |
| SwiftKey: Rich input | “Rich input” (English kept) | unverified (no Hindi source found) |

Sources for the #79 rows: AOSP Settings `res/values-hi/strings.xml` on the `android-9.0.0_r1` tag and the `android10-release` … `android17-release` branches; PermissionController's strings (Android 10–11: `platform/packages/apps/PackageInstaller`, Android 12: `platform/packages/modules/Permission`), which label the Default apps rows and the assistant picker on 10–12; the preference library's `expand_button_title` (`platform/frameworks/support`). Not checked against a phone maker's overlay on a device.

Sources for the #61 rows: AOSP Settings strings at https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/android16-qpr2-release/res/values-hi/strings.xml ("AOSP 16 QPR2", the Android 14+ path), the same file on `android13-release` ("AOSP 13") and `android12-release` ("AOSP 12"); Fossify Keyboard's `voice_typing_method` at https://github.com/FossifyOrg/Keyboard/blob/main/app/src/main/res/values-hi/strings.xml (main branch; the installed release not checked on a device); SwiftKey: Microsoft has no Hindi version of its support page (hi-in redirects to en-us). AOSP labels are not checked against a Pixel's overlay on a device.

FUTO's Hindi spells वॉयस and is more formal (निर्मित, निष्क्रिय, उपयोग); this
app says वॉइस इनपुट in its own text and uses FUTO's wording only when quoting
FUTO's menu labels.
