# Changelog

All notable changes to **marmalade-stt-android** will be documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.1.0] - 2026-10-21

### Fixed
- Apps that use Android's speech recognizer, such as Duolingo's speaking
  exercises, failed at once ("could not recognize the input") whenever
  Marmalade STT was the phone's speech recognition service (#90, GitHub #1).
  Android refused the microphone because our app was in the background;
  Marmalade STT now opens it on behalf of the app that asked, as Android
  12 and later expect.
- Starting speech recognition again right after cancelling it no longer
  fails as "busy" while the speech model is still loading.
- The settings button next to Marmalade STT in Android's voice input and
  keyboard settings opens the app's Settings tab instead of Dictate.

### Added
- Apps that ask for a language get it: if your current engine doesn't
  recognize it, another downloaded engine that does is used for that
  request; if none does, the app is told the language isn't available
  (and the speak-now sheet says so). With "Keep engine ready" on, your own
  engine is loaded again a minute after the last such request.
- Apps can hand Marmalade STT their own audio to transcribe (Android 13 and
  later), for example a recording: any common PCM format, kept in memory
  only. Long sessions can return their text sentence by sentence.
- No app can keep the microphone or the speech engine busy indefinitely: a
  request stops after 10 minutes, and an app's own audio after 10 seconds
  without (almost) any or after an hour; what was heard is still
  transcribed.
- Your own use wins: starting the voice keyboard, Dictate or the speak-now
  sheet while another app is using Marmalade STT's speech recognizer ends
  that app's request (it is told the recognizer is busy) instead of
  showing you "busy".
- More of Android's speech-recognizer options are honoured: the number of
  results, raw text next to the formatted text, partial results without a
  trailing full stop, the pause that ends speech and a minimum listening
  time. Results report their confidence as unknown. Apps can ask which
  languages are installed.

### Changed
- Qwen3-ASR can be picked for all 30 of its languages, English included,
  not just Chinese and Korean (#88). Its card shows a rating for your
  language where we measured it, and its English one elsewhere. With a
  recognition language set, Qwen3-ASR is told that language. The setup
  guide still recommends Qwen3-ASR only for Chinese and Korean, so no
  recommendation changes.

## [1.0.1] - 2026-10-16

### Fixed
- The release build is reproducible on F-Droid's build server again, so
  F-Droid can publish the 32-bit ARM (armeabi-v7a) APK. F-Droid's build of
  1.0.0 for that ABI differed from ours only in the precompiled ART startup
  profile (`assets/dexopt/baseline.prof` and `baseline.profm`), which the
  Android build tools don't produce the same way on every machine; it
  depends on the number of CPU cores, for one. The F-Droid and GitHub APKs no
  longer include that profile. Android now learns which code to compile ahead
  of time on the phone itself, so the first few launches after installing or
  updating can be slightly slower. Nothing else changed.

## [1.0.0] - 2026-10-10

The first public release. Nothing was published before it, so this lists
the development builds' changes, newest first; a newer entry (higher up)
replaces an older one where they differ (the first builds' default engine was
Distil-Whisper Small, for example; 1.0.0's is Parakeet Ultra).

### Added
- New "High contrast" colours (Settings → Appearance): darker orange
  buttons, switches and mic, and dark brown text where the default theme
  uses orange, readable at WCAG AA in light and dark mode. The setup
  guide's first screen has an "Accessibility mode" switch to turn it on;
  turning it off brings back the colours you had, even after leaving the
  guide.
- Parakeet TDT_CTC 0.6B (Japanese), NVIDIA's Japanese Parakeet, is the
  engine for Japanese: the most accurate Japanese engine in our tests,
  and about ten times lighter to run than Whisper Large v3 Turbo, which it
  replaces as the recommendation. Phones set to Japanese get the new
  recommendation at the next start; an engine you have downloaded and use
  stays selected.
- Japanese text: a sentence without a final mark gets 「。」, and nothing
  puts a space between Japanese or Chinese sentences or capitalises a Latin
  word in them. With Parakeet Japanese, a stray 「うん」 heard in noise before
  speech is dropped (a 「うん」 reply on its own stays) and short Latin
  abbreviations are written in capitals (FBI). English spoken on a phone
  set to Japanese is left as English.
- The setup guide's keyboard step now covers every keyboard you have
  turned on, not just FUTO Keyboard: it says how each one's mic key gets to
  Marmalade, warns when another voice keyboard (such as Google voice
  typing) would win the mic keys, and has a box to try it in. The README's
  new "Works with your keyboard" table lists the keyboards tested:
  HeliBoard, Android's own keyboard, FlorisBoard, AnySoftKeyboard,
  Unexpected Keyboard, Fossify Keyboard, OpenBoard and Thumb-Key work; the
  mic keys of Gboard, Hacker's Keyboard and Simple Keyboard can't reach
  Marmalade, but Android's keyboard switcher can, and the guide says how
  (tested with Gboard on the emulator); Samsung Keyboard only offers
  Samsung's or Google's voice typing (not tested).
- A setup guide step for Android's speech recognition service: it shows
  whether Marmalade STT is the one apps get, and opens the right settings
  screen for the phone's Android version.
- The app is translated into 20 languages: German, Spanish, French, Hindi,
  Italian, Japanese, Brazilian Portuguese, Simplified and Traditional
  Chinese, Korean, Russian, Ukrainian, Polish, Czech, Dutch, Swedish,
  Turkish, Indonesian, Vietnamese and Arabic (laid out right to left),
  including the engine descriptions. On Android 13 and later each can be picked for this
  app alone in Android's app settings. The privacy policy stays in English,
  with a short translated summary above it. Download sizes use the
  language's own units and digits, as Android's storage screens do (671 Mo
  in French, 671 МБ in Russian, ٦٧١ م.ب in Arabic); English writes kB.
- With Keep engine ready on, the engine also decodes a moment of silence
  after it loads, so the first dictation's text appears a little sooner.
- Settings → Developer → Runtime tuning: ONNX Runtime's thread spinning and
  pinning to the phone's fast cores, for measuring. The default is
  unchanged; on a Pixel 8a neither made the engines faster.
- Settings → Support, as in Marmalade TTS, in both builds: Support
  development (GitHub Sponsors; voluntary and not tax-deductible), More
  Marmalade (the other apps on GitHub) and Report a bug, which moved there
  from About. Appearance is now the first section in Settings.
- Settings → Appearance, as in Marmalade TTS: light, dark or the system's
  mode, and a color (Marmalade, Midnight, Forest, Berry, or on Android 12
  and later your wallpaper's). The voice keyboard and the speak-now popup
  follow it.
- Engine cards show how fast each engine is on your phone, how good it is
  in our tests, and its languages; tap Use on an installed card to use it,
  and Show more for the details. The app measures the phone's speed once,
  in about a second, with nothing downloaded. Engines too slow for it, and
  other engines you have downloaded, are under "Other engines" as compact
  rows. The speed figures are desktop measurements scaled to a phone, each
  checked on a Pixel 8a.
- The setup guide recommends an engine for your phone: Parakeet Ultra when
  it knows your language, Parakeet TDT_CTC 0.6B for Japanese, Qwen3-ASR
  0.6B for Chinese and Korean (more accurate than Whisper Large v3 Turbo
  for Chinese, close for Korean, and about 40 % cheaper to run), Nemotron 3.5 Streaming for Hindi, Arabic
  and Vietnamese, otherwise Whisper Large v3 Turbo. On a phone short of
  memory or speed:
  Parakeet TDT_CTC 110M for English, Canary 180M Flash for Spanish, German
  and French. Whisper turbo needs about 6 GB of memory; on a smaller phone
  it is still the recommendation for the languages only it covers, with a
  warning that it may be slow or be closed by Android.
- Nemotron 3.5 Streaming is for Hindi, Arabic and Vietnamese, where it is
  as accurate as Whisper Large v3 Turbo or better and much lighter; for its
  other languages Whisper turbo was more accurate in our tests. It comes as
  one download, with text in steps of about a second.
- Settings → Recognition language: the language you speak, Auto by
  default (the phone's language). It picks the recommended engine and is
  passed to the engines that take a language hint; a card says when its
  engine doesn't know the language.
- An engine that can't keep up with your voice on this phone is marked
  slow on it from then on (Settings → Developer → Re-measure phone speed
  starts over).
- The Dictate screen says "Catching up…" when the speech engine falls
  behind your voice, and suggests a smaller engine if it can't keep up
  with this phone at all.
- Four more developer engines, all English-only NVIDIA Parakeet models picked
  from the Open ASR Leaderboard: Parakeet TDT_CTC 110M (a small, fast model,
  about 136 MB), Parakeet TDT 0.6B v2, and Parakeet Unified 0.6B in its
  offline form and as a 560 ms streaming engine. The streaming one decodes
  slower than real time even on a desktop, so it is marked slow.
- Parakeet Ultra as a developer engine: Moondream's post-trained Parakeet
  TDT 0.6B v3 (same 25 languages, lower error rates in our tests),
  converted for the app and downloaded from our own model repo,
  github.com/maxwhipw/marmalade-stt-engines.
- Developer engine: IBM Granite Speech 5.0 470M (English), also in the
  F-Droid build. It was the most accurate engine in our
  tests. Its own text is lowercase with no punctuation, so a small
  formatter we trained (7.5 MB, in the app) adds capitals, punctuation
  and apostrophes ("Björn's", "I'll"). With it, Granite's text, capitals
  and punctuation included, scored about as well as Parakeet's in our tests.
- Settings → End of speech chooses when listening stops, separately for
  the voice keyboard (Tap to stop by default: the mic stays on until you
  tap it) and for other apps and the speak-now popup (Auto-stop with Smart
  Turn by default). The auto-stop options are Smart Turn (waits until you
  sound finished, so a pause to think doesn't cut you off), a pause (the
  pause length), and Say an end word (default "over"; the word must be said
  on its own, after a pause or as its own sentence, and isn't typed). On
  the keyboard, the first end in an auto-stop mode types your words and
  closes it, as ✓ does.
- Smart Turn patience (Quick, Normal, Relaxed) sets how long Smart Turn
  waits before it decides you're done.
- Press and hold the mic on the voice keyboard or the speak-now popup to
  keep listening through every end until you tap it; the mic shows a lock.
- Smart Turn uses pipecat's smart-turn v3.2 model (8.7 MB), which comes
  with the app, so it works from the first launch with no download.

### Changed
- The speed meter on the Engines tab and in the setup guide has five
  segments, like the quality meter (#80). The top step splits in two:
  Fastest and Very fast. On a Pixel 8a, Parakeet 110M is the
  fastest and Parakeet Ultra very fast.
- An engine about as accurate as the best one we measured in a language
  gets at least three of five quality segments (Good), however hard the
  language (#80). Nemotron 3.5 now reads Good in Hindi, where it is the
  most accurate engine, and in Vietnamese, where it is within two points
  of Whisper Large v3 Turbo; Turbo's Vietnamese reads Good too.
- Parakeet 110M, Nemotron 3.5 and Qwen3-ASR download their model
  files in ONNX Runtime's pre-optimised format, which loads faster (#78):
  on a Pixel 6a, 31 to 37 % faster for Parakeet 110M, 24 to 50 % for
  Nemotron and 20 % for Qwen3-ASR, with the same transcripts. An engine you
  have already downloaded keeps its files and needs no new download.
- Whisper Large v3 Turbo, Qwen3-ASR and Canary load faster (#78): their
  model parts now load at the same time. On a Pixel 6a, Qwen3-ASR went
  from 4.9 to 4.1 s and Whisper Large v3 Turbo from 3.3 to 2.4 s.
- With Keep engine ready off, the speech engine now stays loaded while the
  voice keyboard panel, the speak-now popup or the Dictate screen is open,
  so tapping the mic again doesn't reload it (#78). The Dictate screen
  starts loading it as it opens, so its first tap doesn't wait either. The
  engine is freed once you leave.
- The setup guide needs a speech model before it moves on (#74): the steps
  after it try dictation, which can't work without one. The model step has
  no Skip, and its Next unlocks once an engine is installed (at once if one
  already is); "Skip setup" is gone. Every other step can still be
  skipped, and Back and leaving the app still work.
- Every speech engine now downloads from our own GitHub releases
  (`maxwhipw/marmalade-stt-engines`), not Hugging Face. The files are the
  same, byte for byte, so engines you already installed stay installed.
- Accessibility pass (#68). TalkBack now hears the setup guide's step on
  every page, when a download finishes, pauses or fails, and what the microphone
  permission did; buttons say whose engine they act on; screens, cards and
  dialogs have headings; the colour chips are radio buttons. Error red is
  darker (light) or lighter (dark), so it is readable. At the largest text sizes the Dictate screen, the speak-now
  popup and the engine cards scroll or stack instead of cutting off
  buttons and words. Keyboard focus is easier to see, "Show more" is
  easier to tap, and the mic's level ring holds still with Remove
  animations on. In Arabic at the largest text size, screen titles no
  longer lose their last letter. In dark mode the mic is a light toast
  disc instead of a brown one that blended into the page. The speed and
  quality meters use deeper colours in light mode and draw empty segments
  as outlines, and switches and text boxes have firmer edges. Copy and
  Clear on Dictate are dark brown instead of orange.
- Engines have shorter names: Parakeet 110M, Parakeet Japanese, Nemotron 3.5,
  Qwen3-ASR and (for developers) Granite Speech; the licenses screen still
  names the upstream models. Quality on the Engines screen is a five-step
  meter like Speed's, with "Tested in English" under it when it wasn't
  measured in your language; TalkBack reads the word ("Great"). The
  descriptions no longer advertise punctuation, and Granite's no longer
  claims to be the fastest.
- The Dictate screen's mic button is a little smaller, and the "Tap the
  microphone to start" hint is gone (the button still tells TalkBack what
  it does).
- Smart Turn's Normal patience now waits for a 1.2 s pause before it
  judges whether you're done: at 0.5 s it cut off about one sentence in
  five in our tests across 11 languages. Relaxed now waits 1.5 s.
- Whisper Large v3 Turbo and Qwen3-ASR prepare the audio for the model
  much faster: the spectrogram uses a real FFT instead of a slow direct
  transform. On a desktop CPU that makes each decode 9–16 % faster; on
  phones the gain is probably smaller. Transcripts are as accurate as
  before.
- Engine cards rate quality in your language where we tested it: Japanese,
  Chinese, Korean, Hindi, Arabic, Turkish, Vietnamese and Norwegian now
  too, besides English, Spanish, German and French. An engine is rated only
  in languages it is offered for.
- The recommendation weighs accuracy against speed: a slower engine is
  recommended only where it was clearly more accurate in our tests. After
  an update that changes the rules, the app recommends again; an engine
  you have downloaded and are using stays in use.
- Whisper Large v3 Turbo now takes your recognition language for Hindi,
  Arabic, Turkish, Vietnamese and Norwegian too, instead of guessing it. In
  Hindi that cut its errors by about a third in our tests.
- Phones with 4 GB of RAM are now recommended Parakeet Ultra. On a phone an
  engine wouldn't otherwise fit, it runs in a leaner mode: about a third
  less memory for long dictation (1.2 GB for Parakeet Ultra at the
  28-second limit instead of 1.7 GB), but slower (Parakeet Ultra about
  1.3×), with the same text. Phones with room for an engine run it as
  before.
- The engine memory and speed figures behind the recommendation are now
  measured on a Pixel 8a for every engine, at the longest utterance the app
  decodes. Canary turned out faster than estimated, Granite slower.
- Recommendations that changed with these figures: 4 GB phones get
  Parakeet Ultra (above); 2 GB phones now fit Parakeet 110M, and Canary for
  German, French and Spanish; slower phones get Canary without the "may be
  slow" mark more often. On a 6 GB phone, languages only Whisper Large v3
  Turbo covers still get it, now marked low on memory.
- An engine that fell behind on your phone no longer gives way to one
  estimated to be several times slower: the setup guide now recommends
  another engine only if it is estimated to cost no more.
- The setup guide's memory check uses what the engines really need on a
  phone, measured on a Pixel 8a, instead of desktop figures up to twice as
  high. Phones with 6 GB of RAM are now recommended Parakeet Ultra.
- Settings is simpler. The hallucination filter is no longer a setting:
  phrases an engine "hears" in silence, like "Thank you.", are removed automatically when the mic heard only silence,
  and a "Thank you." you really say stays. Spoken commands and
  spoken punctuation are one Voice commands group, with Punctuation words
  under it (it works only while Voice commands is on). Filler words, voice
  commands and numbers as digits are greyed out and marked "English only"
  while you speak another language. Recognition language says what it
  does. Smart Turn patience and the end words show only while something
  uses them, the hold-the-mic tip moved to the end of End of speech, and
  the Developer section moved to the bottom, where Re-measure phone speed
  shows only with Developer engines on. Plainer wording throughout.
- Keep model loaded is now Keep engine ready, and off by default: the
  engine is loaded when you start listening and freed afterwards, which
  saves about 0.6 to 3 GB of memory, depending on the engine (the setting
  used to say about 300 MB). Turn it on if you use voice input often.
  If you had turned it on or off yourself, your choice is kept.
- Patient mode is gone: the voice keyboard now keeps its mic on until you
  tap by default. The Dictate screen stops listening only when you tap the
  mic and adds your words at each pause.
- The pause length defaults to 1.2 seconds (it was 0.9), which cut people
  off mid-sentence less than half as often in our tests. A length you set
  yourself is kept.
- A shorter engine list: Parakeet Ultra is the default engine, with
  Parakeet TDT_CTC 110M (the light one), Canary 180M Flash, Nemotron 3.5
  Streaming and Whisper Large v3 Turbo (for the languages the others don't
  know). Canary's card says when it needs you to pick a recognition
  language.
- The A/B benchmark shows each engine's text after the same cleanup as
  dictation (filler words removed, numbers as digits, your dictionary), with
  the raw text below it, and scores the word error rate on the cleaned text.
- Speed details no longer compare the phone with a particular reference
  phone: the cards say how fast an engine is on this phone, and the details
  compare its decoding time with Parakeet Ultra's.
- The per-engine language setting is now for developer engines only;
  Settings → Recognition language replaces it.
- New app icon: the Marmalade jar with a microphone, on a deeper amber
  background than Marmalade TTS so the two apps are easy to tell apart.
- The privacy policy and the setup guide say plainly that recognition
  never uses a cloud or online speech service, Google's included, and that
  there are no API keys or accounts. The policy also covers the Support
  links, which open in your browser.

### Removed
- Engines that didn't make the cut: Distil-Whisper Small and Whisper Tiny,
  Base and Small (English); Parakeet TDT 0.6B v3 (Parakeet Ultra replaces
  it), Parakeet TDT 0.6B v2, Parakeet Unified 0.6B and its streaming form;
  Nemotron Speech Streaming (English) and Nemotron 3.5 Streaming at 320 ms
  and 560 ms, with Settings → Developer → Nemotron 3.5 chunk size.
  When you update, their downloaded files are deleted, and if you used one
  the app switches to the engine recommended for your phone.

### Fixed
- The first words are no longer lost while the speech model loads (#78):
  the mic opens as soon as you tap it, in the voice keyboard, the speak-now
  popup, other apps' speech requests and the Dictate screen, and keeps what
  you say in memory until the model is ready. "Listening" and the level
  meter show at once, and a tap on stop before the model is ready still
  gives you the text.
- "Open settings" on the setup guide's speech step no longer closes the app
  on Android 17: Settings crashed on that page and took the app with it.
  Settings now opens in its own task, and if a page closes straight away,
  Settings' home opens instead.
- The setup guide no longer says "Couldn't check" for FUTO Keyboard when
  FUTO can't report its voice input setting; it shows just the steps to set
  it yourself.
- Every link from the app into Android's Settings (speech recognition
  service, keyboards, the app's own page) checks that the page exists before
  opening it, falls back to the nearest page, and says where to go next when
  only Settings' home opened (#79). The setup guide's "Open settings" on the
  speech recognition step opens Android 17's Language & region page (it went
  to Settings' home there), Android 16's System page when Language & region
  has no page of its own, and highlights the row to tap on Android 9 to 17
  (before, the highlight only worked on Android 17). On Android 14 and
  later it no longer tries the old Languages & input page, which crashes
  Settings on Android 15 (#80); it goes to the System page instead when
  Languages can't open. The step's directions
  name the pages of your Android version: Android 13 to 16 have no separate
  Speech page, Android 11 and 12 say Digital assistant app, and on Android 9
  to 11 they say to set the assistant to None first, because Android hides
  Voice input while an assistant like Google's is set. Android doesn't let
  apps open the Voice input picker itself.
- The Engines tab and the setup guide rate an engine's quality in the
  language you set under Settings → Recognition language, not the phone's
  language (#76). Parakeet Japanese, Qwen3-ASR and Nemotron 3.5 said "Not
  rated" on an English phone even with Japanese, Chinese or Hindi chosen
  there. An engine that doesn't transcribe your language now shows its
  rating in its own main language, marked "Tested in Japanese" (or Chinese,
  or Hindi), instead of "Not rated".
- Model downloads no longer stop when the screen turns off or you switch
  apps (#74). Android 15 and later cut the network of an app in the
  background a few seconds later, so a large download on mobile data
  failed with "The download was interrupted", and so did Retry. A download
  now runs in a foreground service, and a dropped connection, such as a cell
  handover or a VPN reconnecting, is retried on its own for about 40 s
  before the guide or the Engines tab shows Retry. A Wi-Fi login page
  answering instead of the server can no longer damage a half-finished
  download.
- The setup guide's top bar no longer slides under the status bar when a
  tall keyboard, such as FUTO Keyboard, opens over its "Try it here" box.
- Italian: the engine note and the setup guide's recommendation name the
  language without "la lingua" (it read "la lingua italiano"): "Non
  trascrive in giapponese", "La nostra prima scelta per dettare in
  italiano".
- The stock phrases engines "hear" in silence are now removed in other
  languages too, not just English: Whisper's 「ご視聴ありがとうございました」,
  "Untertitelung des ZDF", "Продолжение следует...", "Gracias por ver el
  video." and their like in 15 languages, Qwen3's "system" in each of its
  languages, and Canary's "T S F". Each engine's phrases are checked only
  against that engine's text. Only an utterance that is nothing but such a
  phrase is dropped, and only from near-silent audio. Words you might
  really say on their own ("Thank you.", "Gracias.", "E aí", 「我」) go only
  from true silence (the mic's floor), so a softly spoken one stays, and
  short replies such as 「はい」 or "sí" are never dropped. Before Japanese
  speech, Whisper's 「おわり」 and Qwen3's 「システム」 are dropped like
  Parakeet's 「うん」. Sound captions in asterisks ("* Musik *") go like
  ones in brackets; other words in asterisks stay.
- Qwen3 no longer shows its internal `<|endoftext|>` marker or the text
  it sometimes writes after it.
- The setup guide and the Dictate screen no longer keep the processor
  busy while you read them. The jar mascot redrew itself on every frame,
  which held the app at 50–75 % CPU on an emulator. It now breathes twice
  and then only moves to blink: about 1 % CPU. While it listens it draws
  at half the frame rate, with the same look, and with Android's "Remove
  animations" on it stands still.
- Dictionary entries and snippets now work in Japanese and Chinese, which
  have no spaces between words: 「メール」 is found in 「連絡先はメールです」,
  and a Latin entry such as "gh" is found next to Japanese text
  (「ghのページ」). A katakana entry is still not found inside a longer
  katakana word (「メールアドレス」). In Korean, an entry is found before a
  particle (「메일을」) but not inside a longer word (「메일함」).
- The setup guide's warning about another voice keyboard being on could
  miss it on Android 11 and later: Android hides other apps' keyboards from
  an app that doesn't ask to see them.
- Whisper Large v3 Turbo no longer loses the end of Hindi sentences longer
  than about 13 seconds. In our tests on 11 to 35 second Hindi recordings,
  the word error rate fell from 39 % to 32 %.
- When Whisper Large v3 Turbo gets stuck repeating itself in English or
  Japanese, the text after you tap stop comes much sooner: it now stops at
  about as much text as that much speech can hold, instead of running on
  for up to half a minute on a phone.
- Speaking for more than about 28 seconds without a pause no longer loses
  the end with Whisper, or uses more memory than the engine's figures: the
  app now splits such speech itself, at the quietest moment just before 28
  seconds, and keeps joining the text in Tap to stop and End word.
- Cancelling dictation while Whisper, Canary or Qwen3-ASR is still working
  out the text now stops it within a moment, so the mic is ready again at
  once instead of answering that another session is running. This now
  includes the first part of the work, where the engine reads the whole
  recording: a cancel there used to wait for it, several seconds for a long
  utterance on a phone.
- Repeated text with no spaces in it, such as "xlsx.xlsx.xlsx…" or a long
  run of one letter in Chinese or Thai, is now removed like other repeats
  a recognizer gets stuck on.
- Whisper turbo no longer drops Chinese, Japanese, Korean and other
  characters that its tokenizer splits in two: one test sentence came out
  with "学方法" for "学习方法" and "电程" for "电脑程序".
  The build applies sherpa-onnx's upstream fix (#3965), which isn't in a
  release yet.
- Whisper turbo no longer stops mid-sentence in Hindi, Japanese, Arabic
  and other languages whose words take many tokens: sherpa-onnx capped its
  output at 6 tokens per second of audio, and the build now removes that
  cap.
- Picking another engine just as dictation started, or while the old one
  was still loading, could leave the old engine's model in memory until
  the app closed.
- A Pixel 8a measured its own speed as 1.7× a Pixel 8a, so every phone
  looked faster than it is and could be offered engines too slow for it.
  The reference is now the app's own measurement on a Pixel 8a.
- Text cleanup no longer damages other languages. Filler removal, spoken
  commands and punctuation, and numbers as digits are English rules, and
  now run only when you dictate English (the engine's language, else
  Settings → Recognition language, else the phone's). German "er" ("he")
  is no longer deleted as a filler. French keeps its space before
  "?", "!", ":" and ";".
- Apps that use Android's speech recognizer now see the languages of the
  engine you use, instead of US English only.
- A language an app asks for (with the "speak now" sheet or Android's
  speech recognizer) is used for that request: engines that take a
  language hint get it, and text cleanup follows it. Your own settings
  don't change.
- Patient mode no longer keeps the microphone open in other apps: the
  "speak now" sheet and Android's speech recognizer answer at the first
  end (Smart Turn, a pause or the end word).
- The live text while you speak is steadier. Words the recogniser has
  settled on stay put until the sentence is finished, and a word repeated
  at the end ("test test test", which Whisper tiny produced on half-heard
  words) shows once. With Whisper tiny about a third fewer words flicker
  in and out; the finished text is unchanged.
- On a phone too slow for the chosen engine, the text no longer falls
  further and further behind your speech: while the engine is behind, the
  live text waits (or, for the streaming engines, is worked out in one go)
  so it can catch up.
- The Parakeet developer engines no longer drift into English partway
  through a German or Spanish sentence. Each utterance now gets a moment of
  silence after it before it is transcribed, as Whisper already did. On the
  German test set this took the word error rate from 16 % to 7 %.
- After a long sentence, the finished text no longer lags far behind: the
  live text shown while you speak never holds up the final result, and
  on long sentences it only re-reads the last few seconds. On a Pixel 8a
  a 22 s sentence took 14.7 s to finish; in the simulation of that phone
  the slowest long sentence now takes 8 s, and a typical one 5.5 s.
- On phones without built-in microphone gain control (the Pixel 8a), the
  hallucination filter again recognises near-silent audio: it now judges
  the level the microphone delivered, not the level after the app's own
  gain control boosted it.
- Live text while you speak no longer flashes a recogniser loop ("E E E E
  …") or a noise caption ("(buzzing)", "[BLANK_AUDIO]") before the
  finished sentence replaces it, in the app, the voice keyboard, the
  speech-input sheet and partial results sent to other apps.
- The near-silence level that lets the hallucination filter drop stock
  phrases ("Thank you.") is now -50 dBFS, measured on a Pixel 8a. At the
  old -45, quiet but real sentences counted as near-silent.
- The first word is no longer clipped when you start talking as the mic
  opens. Every utterance now keeps 0.6 s of audio from before the moment
  speech was detected, and 1 s for the streaming (Nemotron) engines, which
  used to drop the first word of one utterance in six (padded with silence
  when the mic opened less than a second before). The A/B benchmark
  cuts takes the same way.
- Quiet speech on phones without built-in microphone gain control is
  recognised much better: the app now levels the input itself there, so
  the first word isn't lost and fewer words are misheard.
  The developer A/B benchmark levels its take the same way, and its
  results say whether it did.
- The setup guide picks up on the step it was on when the app was closed
  or killed during a first run, instead of starting over.
- The setup guide's FUTO Keyboard instructions use FUTO's real labels
  (Voice Input → "Disable built-in voice input"), and the keyboard step
  names the keyboard as Android lists it ("Marmalade voice typing").
  When another voice keyboard (Google voice typing, say) is on, the FUTO
  step says to turn it off, since FUTO's mic key may open that one instead.
- The setup guide's keyboard step warns that some phones (the Pixel)
  show a second message when you turn the keyboard on, about the app not
  starting after a reboot until you unlock, and that it's fine to tap OK.
- The setup guide's FUTO Keyboard instructions say to tap the mic again
  if FUTO closes the first time (FUTO Keyboard 0.1.30 does that once
  after Google voice typing is turned off).
- The model download bar's track is visible in dark mode, so a download
  that has barely started no longer looks almost finished.
- Dark mode no longer flashes white while the app starts.
- Dictionary rules and snippet triggers typed with a separate accent mark
  (e.g. "e" + "´") now match, for any caller of `:textproc`.
- Reproducible builds: the native library no longer embeds a build-directory
  hash that depended on the checkout path.

### Changed (early development)
- Speech recognition uses up to 4 of the phone's fast CPU cores instead of
  always 2 (never the slow efficiency cores), so text arrives sooner on
  phones that have them.

### Added (early development)
- Numbers as digits (Settings → Text cleanup → Write numbers as digits, on
  by default): spoken numbers become digits, so "twenty five percent" is
  written "25%", "nine thirty AM" "9:30 AM", "eighteen hundred dollars"
  "$1,800" and "June third" "June 3rd". Small numbers stay words ("three
  boxes", "one of them") unless a unit, time, month or label word makes
  them a quantity. Rules only, on the device, English. It matters most for
  Nemotron, Canary and Qwen3, which write numbers as words.
- Phone numbers get their dashes when you say they are one: "my phone
  number is five five five zero one four two" is written "555-0142", and
  ten digits "555-555-0142". Other digit runs ("order number 5550142")
  stay as they are.
- When a speech model gets stuck repeating one letter or word ("E E E E
  …"), the repeats are removed instead of typed. Canary does this about
  once in 200 sentences.
- Noise captions such as "(buzzing)", "(wind howling)" or "(MUSIC)" are
  removed even when they come with speech, as are the lone "( ( (" Whisper
  writes for hum. Real parentheticals like "(after five)" stay.
- Developer engines (Settings → Developer engines, off by default): the
  speech models being considered for the next default, each downloadable
  and selectable on the Engines tab. Whisper tiny.en, base.en, small.en and
  large-v3-turbo; NVIDIA Parakeet TDT 0.6B v3 and canary-180m-flash; NVIDIA
  Nemotron 3.5 Streaming (320 ms and 560 ms) and Nemotron Speech Streaming
  (English), which show text as you speak; and Qwen3-ASR 0.6B. Engines
  that take a language hint get a language setting. Distil-Whisper Small
  stays the default. Every model has an ethics review and a license entry.
- A/B benchmark (Settings → Developer → A/B benchmark): record up to 30 s
  once (kept in memory only, never saved), run it through every installed
  engine one at a time, and compare transcripts, load and decode time, RTF,
  peak memory and, with a reference text, word error rate. Copy the results
  as a Markdown table.
- Voice keyboard ("Marmalade voice typing"): FUTO Keyboard's mic key (with
  its built-in voice input turned off), and any LatinIME-style keyboard's
  voice key, open a Marmalade
  panel that listens straight away. The words in progress are shown
  underlined in the text field, and each finished sentence is typed through
  your text cleanup settings. ✓ or ✕ (or Back) returns you to your keyboard.
  The mic is on only while the panel is showing, and it pauses by itself
  after 30 s without speech (tap the mic to go on).
- Speech input for other apps: a "speak now" sheet for apps that ask
  Android for speech input (ACTION_RECOGNIZE_SPEECH), and a speech
  recognition service for apps that use Android's SpeechRecognizer. Both
  return one sentence and stop listening after 6 s of silence.
- Setup guide on first launch, and again from Settings → Setup guide. It
  walks through the microphone permission, the speech-model download, turning
  on the Marmalade voice keyboard and, when FUTO Keyboard is installed, making
  Marmalade STT FUTO's voice engine (FUTO's own confirm dialog, with a status
  check and manual steps as the fallback). Every step can be skipped
  (until #74, which made the speech model required).
- Text cleanup settings: filler-word removal, the hallucination filter (only
  when quiet / always / off), spoken commands, spoken punctuation and
  sentence capitals, with short help from the rule reference. A replacement
  dictionary editor (add, edit, delete, paste-import of `wrong -> right`
  lines, copy all) and a snippets editor. Settings are saved on the phone and
  the Dictate screen uses them from the next sentence.
- App scaffold (`app.marmalade.stt`): the Marmalade theme in light and dark,
  and a bottom-navigation shell with Dictate, Engines and Settings tabs.
- Dictate screen: tap the mic and talk. Finished sentences appear as text,
  with the words in progress shown in grey until they're final; a ring
  around the mic follows your voice. Copy and Clear buttons. Text goes
  through `:textproc` before it's added, so "new line" and "scratch that"
  work. The text is kept in memory only, and dictation stops when you leave
  the screen. Without a speech model, the screen points you to Engines.
- Speech engine: on-device streaming recognition, ported from the Marmalade
  client. Whisper runs over Silero VAD with a 100 Hz high-pass filter and
  the platform's noise suppression and gain control. Partials are re-decoded
  every 200 ms, and each pause ends an utterance. A very long utterance is
  split at 28 s, because Whisper can't decode more than 30 s at once. Each
  utterance is marked near-silent from its loudness, so `:textproc` can drop
  phantom "Thank you."s. The model loads when the app starts.
- Engines screen: download Distil-Whisper Small (English, 299 MB) from
  HuggingFace, pinned to a fixed revision and checked file by file against
  SHA-256 hashes. Progress is shown, and a download can be cancelled and
  resumed, even after the app is closed. Damaged files are flagged and can
  be downloaded again. "Check files" re-verifies an installed model, and
  the model can be removed.
- Licenses screen and `NOTICE.md` list the Distil-Whisper and OpenAI Whisper
  weights (MIT). The ethics review is `docs/model-reviews/distil-small-en.md`.
- Speech settings: how long a pause ends a sentence (0.3–1.5 s), patient
  mode (keep listening through pauses, deliver everything at the end), and
  keeping the model loaded between sessions.
- About screen with the app version, a bug-report link and a GitHub Sponsors
  link.
- Open-source licenses screen listing every component in the APK, with the
  full license texts bundled.
- In-app privacy policy, rendered from `PRIVACY.md`.
- `fdroid` and `play` build flavors, reproducible-build settings and a
  dependency lockfile.
- One APK per ABI, and compressed native libraries in the fdroid flavor. The
  unsigned arm64 release APK drops from 58.9 MB to 15.5 MB (fdroid) or
  35.8 MB (play). The Play bundle stays at 40.2 MB.
- Backup is turned off. The app asks for two permissions: `INTERNET`, used
  only to download speech models, and the microphone, which feeds the
  on-device recognizer only.
- Native speech runtime: sherpa-onnx v1.13.8 built from source with TTS off
  (no espeak-ng or piper, so no GPL code), linked against ONNX Runtime 1.28.0
  from Maven. A build check fails if any packaged native library contains
  espeak or piper code.
- Silero VAD (v4, MIT) bundled in the app for voice-activity detection.
- `:textproc`, a pure-Kotlin module for rule-based transcript cleanup:
  - a hallucination filter
  - filler-word removal
  - spoken commands ("new line", "new paragraph", "scratch that")
  - optional spoken punctuation
  - a replacement dictionary
  - snippets
  - spacing and capitalisation when utterances are joined

  It runs on-device, with no network and no model. The rule reference is in
  `textproc/README.md`.
