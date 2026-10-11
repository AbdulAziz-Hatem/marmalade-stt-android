# Phantom phrases per language, October 2026 (#57)

Whisper "hears" stock phrases in silence: "Thank you.", "Thanks for watching!". The app's hallucination filter
dropped them in English only. #55 found Whisper turbo writing 「おわり」 and
「ご視聴ありがとうございました」 for the quiet before Japanese speech, and "Müzik" in Turkish. This study collects
what every app engine that writes free text invents for noise, per language, and turns it into
one list per recogniser: `Phantoms.WHISPER`, `Phantoms.QWEN3` and `Phantoms.CANARY`. Host runs on the benchmark host (CPU, nice 19, at most 8 threads, Max gaming on the box for
part of it); no phone.

## Bottom line

- **Each engine has a signature phrase per language, and writes it for almost any noise.** Whisper turbo writes
  YouTube subtitle leftovers: "Untertitelung des ZDF, 2020", "Sous-titrage ST' 501", "Продолжение следует...",
  "Teksting av Nicolai Winther", 「ご視聴ありがとうございました」, "다음 영상에서 만나요.", "E aí". ACFT turbo (#55)
  writes the same ones, more consistently (on all 50 clips for several languages). Qwen3 writes "system" in every
  language ("system", 「システム。」, 시스템., 系统。, "Le système."). Canary writes "T S F", short replies and loops.
  Nemotron 3.5 and the Parakeets (ja, Ultra, 110M) wrote nothing.
- **The app now drops them, in two tiers, so nothing a user might really say is lost.** Only an utterance that
  is nothing but listed phrases, and only the phrases of the engine that wrote it. *Distinctive* phrases (subtitle
  credits, "thanks for watching", Qwen3's "system", Canary's "T S F") go when the audio was near-silent (below
  −50 dBFS before any gain). *Ordinary* ones a user may say on their own ("Thank you.", "Gracias.", "E aí", 「我」)
  go only when it was silent, below −58 dBFS: every one of them from quiet noise came at −60 dBFS or below, and the
  quietest speech measured on the Pixel 8a is −49 (see "Tiers and gating").
- **The near-silence gate catches phantoms on quiet audio, not on loud noise.** On the noise clips, 14 % of all
  texts came from near-silent audio with the AGC stand-in, 29 % without it: digital silence and the −70 dBFS mic
  floor, not room tone, breaths, taps or hum. A phantom from a breath or a tap is as loud as quiet speech, so it
  can't be told apart by level.
- **The lead-in phantoms are Japanese, and mostly loud.** Only FLEURS-ja had a phrase before the speech
  (turbo 「おわり」 on 15 of 100 clips, Qwen3 「システム。」 on 14, Parakeet ja #60's 「うん」 on 14); no other language
  and no other engine. Those segments measure −30 to −36 dBFS: the speaker's breath or a click before the first
  word, not silence. With the AGC stand-in none of the 31 listed ones was near-silent. Without it, with the app's
  SoftAgc (a phone without a platform AGC, like the Pixel), 5 of 19 were: turbo's 「ご視聴ありがとうございました」 2
  of 2, Qwen3's 「システム。」 3 of 13, turbo's 「おわり」 0 of 4. So the level gate alone removes few of them. #60's pre-speech hold now covers them: a session's first Final
  that is only a pre-speech phrase is held, dropped when speech follows, and sent when the session ends without
  more. That is Whisper's distinctive phrases and 「おわり」, and Qwen3's 「システム」 (see "Tiers and gating"). A
  phrase before the speech is followed by speech by definition, so the hold catches it at any level. The cost is
  #60's: one of these said as the first utterance, then a pause, then more speech, is lost.

## Method

`tools/asr-bench/run_57.sh` (README "Phantom phrases per language"):

- **Noise.** The six 10 s `noise` clips and 21 `noise-short` clips (1, 2 and 4 s of digital silence, the
  harness's −70 dBFS mic floor, room tone, a breath, a tap, clicks, hum; the length of a VAD false trigger plus
  the 0.6 s pre-roll), each decoded whole (`--direct`, what a false trigger hands the engine), once with the AGC
  stand-in (RMS to −25 dBFS, at most +30 dB) and once without. Each engine got every language hint the app offers
  it: turbo Auto and 17 languages, Qwen3 Auto and its 12 developer languages, Canary en/es/de/fr, Nemotron 3.5
  1120 ms Auto/hi/ar/vi; Parakeet ja, Ultra and 110M take none. ACFT turbo (`models-55/v-acft-h8g8`, 256-frame
  window) ran turbo's hints too.
- **Lead-ins.** The first 100 clips of each FLEURS set an engine is offered for, with that language picked,
  through the app's VAD session (0.5 s of mic floor, then the clip). A Final before the clip's speech is what
  the engine writes for the quiet or the breath before speaking. Japanese again without the AGC stand-in and
  with `SoftAgc` (a phone without a platform AGC, the Pixel), because there the app's near-silence judgement
  sees the raw level.
- **Counting.** A clip counts once per text, engine and hint: digital silence is the same with and without gain,
  so it counts once. Texts are compared as `Phantoms.isPhantom` compares them: lower case, punctuation as
  spaces, digit runs as "0".

**Inclusion rule.** A text goes on a list when one engine wrote it alone on 3 or more clips with one hint, or 2
for subtitle credits, outros, thanks and sound captions. Left out: yes/no answers and fillers a user says on
their own (はい, ええ, sí, sì, ja, no, 好, 嗯, 아, 어, uh, "I don't know", "C'est vrai"), one-letter words, digits, and
ordinary short sentences seen on fewer than 10 clips ("Das war's", "Zobaczcie", "Hva er det?"). Qwen3's fluent
invented sentences (it wrote whole paragraphs for digital silence) vary from clip to clip and can't be listed.

## Tiers and gating

The first version (one list per language, all at −50 dBFS) failed review: it could delete real quiet speech, such
as a soft "Gracias" or Ultra's "The system.". The rule since: never silently delete something a user might really
say.

- **Per engine.** Each recogniser has its own list (`TextProcConfig.recognizer`, from the engine's family):
  Whisper's phrases count only for Whisper (turbo and the other Whisper models), Qwen3's only for Qwen3, Canary's
  only for Canary. Parakeet, Nemotron and Granite wrote nothing and have none. A caller that doesn't know the
  engine (`Recognizer.ANY`, the default) gets every list.
- **Distinctive tier**, dropped below −50 dBFS (`NearSilence.THRESHOLD_DB`), in every language: phrases nobody
  dictates as a whole message. Whisper's subtitle credits, channel outros and "thanks for watching" in each
  language, and English "thanks for watching"-style phrases; Qwen3's "system" forms; Canary's "T S F". Every
  language, because the text shows its own language: Whisper on Auto may write Japanese for noise on an English
  phone.
- **Strict tier**, dropped below −58 dBFS (`NearSilence.SILENT_THRESHOLD_DB`), in the phrase's own language only:
  words and sentences a user may say on their own: "Vielen Dank.", "Gracias.", "Grazie a tutti.", "E aí",
  「감사합니다」, 「我」, 「你」, 「おわり」. Every strict-tier text from quiet noise came at exactly −100 (digital
  silence), −70 (the mic floor without gain) or −60 dBFS (white noise without gain), so −58 catches all of them;
  the quietest speech measured on the Pixel 8a (#20) is −49, 9 dB above. Whisper's English strict list ("Thank
  you.", "Bye.", "you", "so") counts in every language: on Auto, Whisper wrote English for every noise clip,
  whatever the phone's language.
- **Pre-speech hold**, at any level: Whisper's distinctive phrases and 「おわり」 (turbo's lead-in on 15 of 100
  FLEURS-ja clips), and Qwen3's 「システム」 (14 of 100) and "the system is now running" (#14). Qwen3's other
  "system" forms and the strict tier never came before speech and are not held, so an opening "E aí, tudo bem?"
  or "Gracias." stays.

Asterisk groups are no longer removed when they are the whole utterance: only a caption with a sound word goes
("* Musik *", "*Klopfen*"), so "*important*" stays at any level. Sentences are compared with Turkish dotted and
dotless i folded to "i", so all-caps "ALTYAZI M.K." matches "Altyazı M.K.".

## Noise phrases

Every text an engine wrote alone on 2 or more noise clips with one hint. Clips: distinct clips, of 50 per hint
(27 with the AGC stand-in, 27 without, the 4 digital-silence clips counted once). "Turbo" is the shipped int8
turbo, "ACFT" #55's fine-tune. Texts longer than 80 characters (loops, Qwen3's invented sentences) are left out.

| Hint | Text (as compared) | Clips | Tier |
|---|---|---|---|
| ar | ترجمة نانسي قنقر | ACFT 44, turbo 25 | Whisper, distinctive |
| ar | اشتركوا في القناة | turbo 15, ACFT 5 | Whisper, distinctive |
| ar | شكرا | turbo 7 | Whisper, strict |
| ar | موسيقى | turbo 3, ACFT 1 | Whisper, strict |
| auto | you | ACFT 45, turbo 17 | Whisper, strict (every language) |
| auto | so | turbo 13, ACFT 3 | Whisper, strict (every language) |
| auto | thank you | turbo 14, ACFT 2 | Whisper, strict (every language) |
| de | t s f | Canary 30 | Canary only, distinctive |
| de | vielen dank | turbo 17, ACFT 11 | Whisper, strict |
| de | system | Qwen3 28 | Qwen3 only, distinctive |
| de | musik | ACFT 10, turbo 6 | Whisper, strict |
| de | ja | Canary 10 | no: yes/no answer |
| de | das war's | ACFT 6, turbo 1 | no: ordinary sentence |
| de | ich bin der fall | ACFT 5, turbo 2 | Whisper, strict |
| de | untertitelung des zdf 0 | turbo 6 | Whisper, distinctive |
| de | ich bin der karte | turbo 3, ACFT 2 | Whisper, strict |
| de | das war's für heute | ACFT 5 | Whisper, strict |
| de | klopfen | turbo 4, ACFT 1 | no: an asterisk caption, which stripNonSpeech removes |
| de | 0 | Canary 5 | no: a number |
| de | hiereräusch | turbo 2, ACFT 2 | no: an asterisk group without a known sound word, kept |
| de | ich bin ein | turbo 3 | Whisper, strict |
| de | systemus | Qwen3 3 | Qwen3 only, distinctive |
| de | systeme | Qwen3 3 | Qwen3 only, distinctive |
| de | ich bin jetzt hier | ACFT 2 | no: seen on fewer clips than the rule needs |
| de | klöse | ACFT 2 | no: an asterisk group without a known sound word, kept |
| de | klöckige musik | ACFT 2 | no: an asterisk group without a known sound word, kept |
| de | ich bin der | ACFT 2 | no: seen on fewer clips than the rule needs |
| de | groan | turbo 2 | no: an asterisk caption, which stripNonSpeech removes |
| de | die energie die in der energieumwandlung entsteht wird als energie umgewandelt | Qwen3 2 | no: seen on fewer clips than the rule needs |
| en | you | ACFT 45, turbo 17 | Whisper, strict (every language) |
| en | system | Qwen3 27 | Qwen3 only, distinctive |
| en | so | turbo 13, ACFT 3 | Whisper, strict (every language) |
| en | thank you | turbo 14, ACFT 2 | Whisper, strict (every language) |
| en | a system | Qwen3 8 | Qwen3 only, distinctive |
| en | the system | Qwen3 7 | Qwen3 only, distinctive |
| en | i don't know | Canary 5 | no: ordinary reply |
| en | okay | Qwen3 2, Canary 1 | no: ordinary reply (only Whisper's English list has it) |
| en | t s f | Canary 3 | Canary only, distinctive |
| es | gracias por ver el video | ACFT 34, turbo 24 | Whisper, distinctive |
| es | system | Qwen3 23 | Qwen3 only, distinctive |
| es | suscríbete al canal | turbo 11, ACFT 9 | Whisper, distinctive |
| es | sí | Canary 8, Qwen3 2 | no: yes/no answer |
| es | y | ACFT 5, turbo 4 | no: one-letter word |
| es | gracias | turbo 9 | Whisper, strict |
| es | el sistema | Qwen3 5 | Qwen3 only, distinctive |
| es | no | Qwen3 5 | no: yes/no answer |
| es | sistema | Qwen3 4 | Qwen3 only, distinctive |
| es | uh | Canary 4 | no: filler |
| es | systemus | Qwen3 3 | Qwen3 only, distinctive |
| es | 0 | Canary 2 | no: a number |
| es | que es el | Canary 2 | no: seen on fewer clips than the rule needs |
| es | que es el primero | Canary 2 | no: seen on fewer clips than the rule needs |
| fr | sous titrage st' 0 | turbo 8, ACFT 4 | Whisper, distinctive |
| fr | system | Qwen3 12 | Qwen3 only, distinctive |
| fr | le système | Qwen3 9 | Qwen3 only, distinctive |
| fr | systeme | Qwen3 9 | Qwen3 only, distinctive |
| fr | un système | Qwen3 5 | Qwen3 only, distinctive |
| fr | c'est vrai | Canary 5 | no: ordinary reply |
| fr | 0 | Canary 4 | no: a number |
| fr | tilde | Canary 4 | no: ordinary word |
| fr | tout à fait | Canary 3 | no: ordinary reply |
| fr | système | Qwen3 2 | Qwen3 only, distinctive |
| fr | d'accord | Canary 2 | no: ordinary reply |
| fr | à cette époque | Canary 2 | no: seen on fewer clips than the rule needs |
| fr | à | Canary 2 | no: one-letter word |
| fr | tilogue | Canary 2 | no: seen on fewer clips than the rule needs |
| hi | झाल | ACFT 50, turbo 32 | Whisper, strict |
| hi | प्रप्राइब दो | turbo 3 | Whisper, strict |
| hi | प र प प र प प र प प र प प र प प र प प र प प र प प र प प र प | turbo 2 | no: seen on fewer clips than the rule needs |
| hi | प रव द प रव द प रव द प रव द | turbo 2 | no: seen on fewer clips than the rule needs |
| hi | प रव द प रव द | turbo 2 | no: seen on fewer clips than the rule needs |
| it | grazie a tutti | ACFT 50, turbo 37 | Whisper, strict |
| it | system | Qwen3 19 | Qwen3 only, distinctive |
| it | grazie | turbo 8 | Whisper, strict |
| it | il nostro corso gratuito è www mesmerism info | turbo 5 | Whisper, distinctive |
| it | il sistema | Qwen3 5 | Qwen3 only, distinctive |
| it | sì | Qwen3 4 | no: yes/no answer |
| it | sistema | Qwen3 3 | Qwen3 only, distinctive |
| it | systeme | Qwen3 3 | Qwen3 only, distinctive |
| ja | ご視聴ありがとうございました | ACFT 48, turbo 43 | Whisper, distinctive |
| ja | システム | Qwen3 34 | Qwen3 only, distinctive |
| ja | はい | Qwen3 7, turbo 2 | no: yes/no answer |
| ja | 次回予告 | turbo 1, ACFT 1 | no: one clip per model |
| ja | bgm | turbo 1, ACFT 1 | no: one clip per model |
| ja | 音声認識で認識した言葉 | Qwen3 2 | no: seen on fewer clips than the rule needs |
| ja | ええ | Qwen3 2 | no: yes/no answer |
| ja | 日本語 | Qwen3 2 | no: ordinary word |
| ko | 다음 영상에서 만나요 | ACFT 45, turbo 37 | Whisper, distinctive |
| ko | 시스템 | Qwen3 24 | Qwen3 only, distinctive |
| ko | 감사합니다 | turbo 7 | Whisper, strict |
| ko | 아 | ACFT 4, turbo 2 | no: filler |
| ko | 어 | Qwen3 3 | no: filler |
| ko | 이 | Qwen3 2 | no: filler |
| ko | 이러면 | Qwen3 2 | no: seen on fewer clips than the rule needs |
| nb | teksting av nicolai winther | ACFT 50, turbo 46 | Whisper, distinctive |
| nb | hva er det | turbo 3 | no: ordinary sentence |
| nl | system | Qwen3 23 | Qwen3 only, distinctive |
| nl | systemus | Qwen3 3 | Qwen3 only, distinctive |
| nl | dit is een systeem | Qwen3 2 | no: seen on fewer clips than the rule needs |
| nl | hoe zit het met je | Qwen3 2 | no: seen on fewer clips than the rule needs |
| pl | dziękuje za oglądanie | ACFT 42, turbo 27 | Whisper, distinctive |
| pl | system | Qwen3 41 | Qwen3 only, distinctive |
| pl | dzięki za oglądanie | turbo 8, ACFT 1 | Whisper, distinctive |
| pl | zobaczcie | ACFT 4, turbo 3 | no: ordinary sentence |
| pl | dziękuję | turbo 4 | Whisper, strict |
| pl | koniec | ACFT 2, turbo 1 | Whisper, strict |
| pl | dzień dobry | turbo 2, ACFT 1 | no: ordinary sentence |
| pl | system endoftext | Qwen3 3 | Qwen3 only: the token goes, then "system" |
| pl | wielkie prawa zastrzeżone | turbo 2 | Whisper, distinctive |
| pt | e aí | ACFT 50, turbo 44 | Whisper, strict |
| pt | system | Qwen3 33 | Qwen3 only, distinctive |
| pt | o sistema | Qwen3 6 | Qwen3 only, distinctive |
| pt | obrigado | turbo 3 | Whisper, strict |
| pt | o que é um sistema | Qwen3 2 | no: seen on fewer clips than the rule needs |
| ru | продолжение следует | ACFT 46, turbo 36 | Whisper, distinctive |
| ru | system | Qwen3 17 | Qwen3 only, distinctive |
| ru | система | Qwen3 10 | Qwen3 only, distinctive |
| ru | субтитры сделал dimatorzok | turbo 4, ACFT 4 | Whisper, distinctive |
| ru | субтитры создавал dimatorzok | turbo 3 | Whisper, distinctive |
| ru | системы | Qwen3 3 | Qwen3 only, distinctive |
| ru | спасибо | turbo 2 | Whisper, strict |
| ru | встреча с волгой | turbo 2 | no: seen on fewer clips than the rule needs |
| ru | время | turbo 2 | no: ordinary word |
| ru | давайте посмотрим на это | Qwen3 2 | no: seen on fewer clips than the rule needs |
| ru | ты | Qwen3 2 | no: ordinary word |
| ru | для чего это | Qwen3 2 | no: seen on fewer clips than the rule needs |
| ru | да это очень важно | Qwen3 2 | no: seen on fewer clips than the rule needs |
| tr | altyazı m k | ACFT 45, turbo 33 | Whisper, distinctive |
| tr | izlediğiniz için teşekkür ederim | turbo 17, ACFT 5 | Whisper, distinctive |
| vi | hãy subscribe cho kênh ghiền mì gõ để không bỏ lỡ những video hấp dẫn | ACFT 38, turbo 17 | Whisper, distinctive |
| vi | hãy subscribe cho kênh la la school để không bỏ lỡ những video hấp dẫn | turbo 17, ACFT 1 | Whisper, distinctive |
| vi | cảm ơn các bạn đã theo dõi và hẹn gặp lại | turbo 10, ACFT 6 | Whisper, distinctive |
| vi | cảm ơn các bạn đã theo dõi | turbo 6, ACFT 5 | Whisper, distinctive |
| zh | 我 | ACFT 18, turbo 3 | Whisper, strict |
| zh | 我可以做的 | ACFT 11, turbo 10 | Whisper, strict |
| zh | 系统 | Qwen3 21 | Qwen3 only, distinctive |
| zh | 你 | ACFT 15, turbo 1 | Whisper, strict |
| zh | 嗯 | Qwen3 15 | no: filler |
| zh | 字幕志愿者 李宗盛 | turbo 8, ACFT 3 | Whisper, distinctive |
| zh | 好 | turbo 9, ACFT 1 | no: yes/no answer |
| zh | 请不吝点赞 订阅 转发 打赏支持明镜与点点栏目 | turbo 8 | Whisper, distinctive |
| zh | system | Qwen3 8 | Qwen3 only, distinctive |
| zh | 字幕志愿者 杨栋梁 | turbo 2 | Whisper, distinctive |
| zh | 我可以用手指的手指 | turbo 2 | no: seen on fewer clips than the rule needs |
| zh | 优优独播剧场 yoyo television series exclusive | turbo 1, ACFT 1 | no: one clip per model |
| zh | 嗯 嗯 | Qwen3 2 | no: filler |

Near-silent share: of all texts on noise clips, 192 of 1384 (14 %) came from near-silent audio with the AGC
stand-in, 401 of 1387 (29 %) without it.

## Lead-ins

The first Final of a FLEURS clip that recurs across clips before the speech, through the app's VAD session.
Parakeet ja's 「うん」 is #60's and already held back by `FinishedUtterances`. Lone first sentences the VAD split
off (common in Vietnamese) are speech and not shown.

| Engine | Set | Text before the speech | Clips of 100 | Near-silent | Levels (dBFS) |
|---|---|---|---|---|---|
| Parakeet ja | fleurs-ja | うん | 14 | 0 | -36 … -30 |
| Qwen3 | fleurs-ja | システム | 14 | 0 | -33 … -30 |
| Qwen3 | fleurs-ja (no AGC, SoftAgc) | システム | 13 | 3 | -54 … -31 |
| Turbo | fleurs-ja | おわり | 15 | 0 | -33 … -30 |
| Turbo | fleurs-ja | ご視聴ありがとうございました | 2 | 0 | -36 … -33 |
| Turbo | fleurs-ja (no AGC, SoftAgc) | ああああああ | 4 | 0 | -32 … -31 |
| Turbo | fleurs-ja (no AGC, SoftAgc) | おわり | 4 | 0 | -34 … -31 |
| Turbo | fleurs-ja (no AGC, SoftAgc) | ご視聴ありがとうございました | 2 | 2 | -54 … -53 |

No text recurred before the speech in: Canary fleurs-de, Canary fleurs-en, Canary fleurs-es, Canary fleurs-fr, Nemotron 3.5 fleurs-ar, Nemotron 3.5 fleurs-hi, Nemotron 3.5 fleurs-vi, Parakeet Ultra fleurs-de, Parakeet Ultra fleurs-en, Parakeet Ultra fleurs-es, Parakeet Ultra fleurs-fr, Qwen3 fleurs-ko, Qwen3 fleurs-zh, Turbo fleurs-ar, Turbo fleurs-de, Turbo fleurs-en, Turbo fleurs-es, Turbo fleurs-fr, Turbo fleurs-hi, Turbo fleurs-ko, Turbo fleurs-nb, Turbo fleurs-tr, Turbo fleurs-vi, Turbo fleurs-zh.

## Other findings

- **Asterisk captions.** Turbo writes noise captions in asterisks in German, Dutch and French ("* Musik *",
  "*Klopfen*", "*Gelach*", "*cris*"), and in English sometimes ("*throwing*", "*Bird sound*"). They passed through
  before. `Phantoms.stripNonSpeech` now removes one that names a sound (with "musik", "klopfen", "applaus",
  "gelach", "cris" and "groan" added to the sound words), anywhere. Any other asterisk group stays, alone or not:
  "*important*" and "That was *really* good." are real text. "*Hiereräusch*" and "*Klöse*" (2 clips each) stay too.
- **Qwen3 leaks its end token**: "system<|endoftext|>#" (Polish hint, 3 clips), "<|endoftext|>#system" and
  "```python" (Portuguese). `TextPipeline` now cuts Qwen3's text at `<|endoftext|>` or `<|im_end|>` and removes
  any other `<|…|>` token, for Qwen3 only (and `Recognizer.ANY`), in partials too. "system<|endoftext|>#" becomes
  "system", which the Qwen3 list then drops on near-silent audio.
- **Canary loops on digits**: "100000…0" for noise with the de hint. `Phantoms.cutLoops` never treats digits as a
  loop (phone numbers), so it stays. Left alone on purpose: it was 1 clip, a run of one digit can be a real
  number, and a cut would leave a stray "1".
- **Turbo writes 「ああああああ」 for a breath** before Japanese speech without the AGC stand-in (4 of 100 FLEURS-ja
  clips; −32 dBFS). Six letters is under the loop cutter's run of 10; left alone, a vocalisation.
- Turbo on Auto detected English for every noise clip here (you, so, Thank you.); ACFT on Auto wrote "you" on 45
  of 50.

## Reproduce

```sh
# on the benchmark host, from a harness copy one level under $WORK
cd $WORK/harness-57b
../venv-54/bin/python -m asrbench.make_synthetic --work .. noise-short
WORK=$HOME/coding/asr-bench-work RESULTS=results-57 PY=$HOME/coding/asr-bench-work/venv-54/bin/python \
  ACFT=$HOME/coding/asr-bench-work/models-55/v-acft-h8g8 ./run_57.sh
../venv-54/bin/python -m asrbench.phantoms --work .. --results results-57          # noise table
../venv-54/bin/python -m asrbench.phantoms --work .. --mine results-57/whisper-turbo  # lead-ins
```

Results: `$WORK/results-57` (`<set>@direct[@auto|@hint-xx][@noagc][…@acft].jsonl` for noise; `fleurs-*.jsonl`
and `fleurs-ja@noagc@softagc.jsonl` for lead-ins). Each row has `finals` and `rms_db` since #57.
