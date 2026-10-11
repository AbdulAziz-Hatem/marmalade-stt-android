# :textproc — transcript cleanup rules

Pure-Kotlin JVM module that cleans raw speech-recognition output with fixed
rules. It runs fully on the device, with no network and no model
(ARCHITECTURE.md D7). It has no Android dependencies.

## Using it

```kotlin
val pipeline = TextPipeline(TextProcConfig(replacements = listOf(Replacement("kubernetes", "Kubernetes"))))

// For each final utterance:
val edit = pipeline.process(utterance, before = textBeforeCursor, nearSilent = segmentWasQuiet, silent = segmentWasSilent)
// Input method:   deleteSurroundingText(edit.deleteBefore, 0); commitText(edit.insert, 1)
// Own buffer:     transcript = edit.applyTo(transcript)
```

- Build one `TextPipeline` per config and reuse it. Construction compiles the
  rules; `process` is cheap and thread-safe.
- `before` is the text already before the cursor. It is used for spacing,
  capitalisation and "scratch that". Pass `""` when there is none (for
  example a one-shot `RecognizerIntent` result).
- `nearSilent` is the engine's judgement that the utterance's audio was
  near-silent (low RMS or low VAD speech probability), and `silent` that it
  was quieter still: digital silence or the mic floor, far below any soft
  speech (the app uses −50 and −58 dBFS). The module can't tell this from
  text, so it doesn't guess.
- `TextProcConfig.recognizer` says which recogniser family wrote the text
  (Whisper, Qwen3, Canary or another): each has its own phantom phrases
  (rule 2). The default, `ANY`, checks them all.
- Live partials go through `pipeline.cleanPartial(partial)` instead. It runs
  only rule 1 and, unless the hallucination filter is off, rule 2's sound-tag
  removal and loop cut, so a decoder loop or "(buzzing)" doesn't flash on
  screen before the Final replaces it. It returns `""` when nothing is left.
  The stricter end-of-partial cut (`Phantoms.cutTailRepeats`, #28) runs in
  the engine's `PartialStabilizer` instead, before words become stable.
- All config types are plain data classes and an enum, so they serialise
  simply.

Speed on the dev machine (i7-8750H, JVM, warmed up), for a 150-character utterance with
numbers in it: about 75 µs with the default config, 100 µs with 50
replacements, 240 µs with 500. Number conversion is about 25 µs of that.
Big dictionaries cost more because every rule is part of one regex.

## Languages

`TextProcConfig.language` is the language being dictated (an ISO 639 code
or BCP-47 tag; the app passes the engine's language, see ARCHITECTURE.md).
The filler words (rule 5), numbers as digits (rule 7) and spoken commands
and punctuation (rule 8) are English. They run only when the language is
English ("en", "en-GB", …, the default), because in other languages they
damage text: the filler "er" is German for "he", "ah" is a Spanish word.
For any other language, or "" (unknown), they are skipped. Everything else
runs in every language, apart from the Japanese rules below.

French keeps its space before `!`, `?`, `;` and `:` ("Et après ?"); rule 6
removes it in every other language.

Japanese ("ja") gets two rules of its own (#60), in rule 6: the space
removal and 「。」 at the end. They run only on an utterance with a kana or
kanji in it: on a Japanese phone, Ultra (which takes no hint) or Whisper on
Auto may hear English, and that stays plain English. Parakeet ja's output
also gets acronyms in capitals (rule 4, `lowerCaseLatin`). In every
language, no joining space goes between two Chinese or Japanese characters,
and an utterance with one in it gets no sentence capital (rule 9). The app
drops the 「うん」 Parakeet ja writes for noise before speech (`FinishedUtterances`).

**Whole words without spaces** (#62). Snippet triggers and dictionary
entries match whole words, and what counts as a word edge depends on the
script of the trigger's first and last character, in any language:

- A change of script is always an edge, so a Latin trigger next to kana,
  kanji or Hangul is a whole word: "gh" matches in 「ghのページ」 and "gh에서",
  but still not in "ghost" or 「ghostのページ」. Full-width digits and Latin
  letters (「２」, 「ｇｈ」) count as Latin here, not as Japanese: "gh" doesn't
  match in 「gh２のページ」, and a trigger "ｇｈ" matches in 「ｇｈのページ」 but
  not in 「ｇｈｏｓｔ」.
- Kanji, hiragana and Chinese match anywhere: the text has no spaces and
  no way to tell words apart without a dictionary. 「東京」 matches in
  「私は東京に」 and also in 「東京都」.
- A run of katakana is one word (a loanword), so a katakana trigger never
  matches inside one: 「メール」 matches in 「連絡先はメールです」 but not in
  「メールアドレス」 or 「ボイスメール」. A Latin letter before it is a script
  change: 「Eメール」 matches.
- Korean separates words with spaces, but particles join the end of a
  word. A Hangul trigger needs a word start, and a word end after any run of
  these particles and endings (`Words.KOREAN_PARTICLES`): 이, 가, 을, 를, 은,
  는, 의, 에, 에서, 에게, 께, 한테, 께서, 으로, 로, 와, 과, 랑, 이랑, 하고, 도, 만,
  까지, 부터, 처럼, 보다, 이나, 나, 요, 이다, 이에요, 예요, 입니다, 이야, 야.
  「메일」 matches in 「메일을」, 「메일에서는」 and 「메일이에요」, but not in
  「이메일」 or 「메일함」, and 「내 주소」 not in 「내 주소록」.

## Rule reference

This section is the source for the settings screen's help text. The rules
run in this order.

### 1. Clean-up of the raw text (always)

Unicode is normalised to NFC, so an accent typed or produced two ways
matches the same rule. Tabs, line breaks and repeated spaces become single
spaces. Invisible control characters are removed. The literal token
`<unk>` is removed: Parakeet ja writes it for a character outside its
vocabulary ("骨<unk>う品" becomes "骨う品"), in live text too. For Qwen3 (and
`ANY`), the text is cut at a leaked `<|endoftext|>` or `<|im_end|>` and any
other `<|…|>` token is removed: Qwen3 sometimes goes on past its end token
("system<|endoftext|>#" becomes "system", #57).

### 2. Hallucination filter

Whisper sometimes "hears" a stock phrase in silence or noise, such as
"Thank you." or "Thanks for watching!", and in Japanese
「ご視聴ありがとうございました」. `phantomFilter` has three values.
The app always uses **Only when quiet** and has no setting for it (#42);
the others are for other callers and tests:

| Setting | Behaviour |
|---|---|
| **Only when quiet** (default) | Drops the utterance when the text is nothing but stock phrases **and** the audio was quiet enough for them (the two tiers below). If you really say "thank you", it stays. |
| **Always** | Drops utterances made only of stock phrases, whatever the audio was. |
| **Off** | Keeps everything. |

Each recogniser has its own stock phrases (`TextProcConfig.recognizer`,
#57): Whisper's, Qwen3's and Canary's, measured on noise with every
language picked (`docs/benchmarks/2026-10-phantoms.md` has the tables; each
phrase in `Phantoms.kt` says how often each model wrote it). Whisper's
phrases are never checked against Qwen3's text, and so on; Parakeet,
Nemotron and Granite have none. The whole utterance must consist of them
("Thank you. Bye."): "Thank you for the help." is never dropped. Case,
punctuation (full-width too) and the number in a phrase don't matter
("Untertitelung des ZDF, 2017"), Turkish dotted and dotless i count as
"i" ("ALTYAZI M.K."), and sentences split at 「。」 too.

There are two tiers, because the rule is never to silently delete
something a user might really say:

- **Distinctive** phrases nobody dictates as a whole message go when the
  audio was near-silent (`nearSilent`), in every language: subtitle
  credits and outros ("Untertitelung des ZDF, 2020", "Продолжение
  следует...", 「ご視聴ありがとうございました」, "다음 영상에서 만나요."),
  "thanks for watching" and "please subscribe" in English, Qwen3's
  "system" in each language, Canary's "T S F". Every language, because the
  text shows its own language: Whisper on Auto may write Japanese for
  noise on an English phone.
- **Ordinary** words and sentences a user may say on their own go only
  when the audio was silent (`silent`), and only in their own language:
  "Vielen Dank." in German, "Gracias." in Spanish, "E aí" in Portuguese,
  「我」 and 「你」 in Chinese. Whisper's English ones (thank you, thank you
  very much, thank you so much, thanks, subscribe, bye, bye bye, goodbye,
  you, okay, so) count in every language, because on Auto Whisper writes
  English for noise whatever the phone's language. A soft "Gracias" at
  −55 dBFS stays; the same word from the mic floor goes.

Yes/no answers and fillers (「はい」, "sí", "ja", 「嗯」) are on neither list,
so a quiet one-word reply always stays.

The app also holds some phrases back when they are a session's first
text, at any level, and drops them when speech follows
(`Phantoms.isPreSpeechPhantom`; ARCHITECTURE.md): Whisper's distinctive
ones and 「おわり」, Qwen3's 「システム」. They are what those engines write
for the breath before the first Japanese word.

Unless the filter is off, sound tags are always removed, wherever they are
in the utterance:

- tags in square brackets, such as `[BLANK_AUDIO]` and `[Music]`
- ♪ notes
- lone opening brackets, such as the `( ( ( (` Whisper writes for mains hum
- noise captions in round brackets or asterisks: all capitals (`(MUSIC)`,
  `(APPLAUSE)`), or up to four words that name a sound (`(buzzing)`,
  `(laughs)`, `(wind howling)`, `(soft music playing)`, `*coughs*`). A
  caption needs at least one sound word ("buzzing", "laughs", "music",
  "coughing", "static" and about 100 more), and its other words must be
  from a short list of sound sources and fillers ("dog", "wind",
  "background", "the").

Any other round-bracket group is kept, because real speech has
parentheticals: "(after five)", "(page 4)" and "(the music was loud)" stay.
Such a group is removed only when the utterance is nothing but bracketed
groups. Any other asterisk group stays even alone ("*important*"): Whisper
writes "* Musik *" and "*Klopfen*" for noise in German and "*cris*" in
French (#57), and those name a sound.

Unless the filter is off, **decoder loops** are cut too. Canary sometimes
gets stuck repeating a letter or word until its length limit ("E E E E
…", "S E S E …", about one utterance in 200), and Whisper and Nemotron
occasionally do the same ("the the the …", "Get Get Get …"). A word,
letter or block of up to three of them repeated 6 or more times in a row,
ignoring case and punctuation, counts as a loop; real speech in the
benchmark sets never repeats a word more than 4 times.

- A loop that runs to the end of the utterance is removed from its first
  word: "E E E … E" inserts nothing, "It was the T T T … T" inserts "It
  was the".
- A loop followed by more words is a stutter the recogniser recovered
  from, so one copy stays: "No the the the … the scrolling" becomes "No
  the scrolling". Single letters are not kept: "T T T … T Looks a bit
  like" becomes "Looks a bit like".
- A loop the recogniser's length limit cut off mid-word ("Uh, Uh, U")
  still counts as running to the end.
- Up to 5 repeats are left alone ("no no no no no"), and digits never
  count: "five five five five five five zero one four two" is a phone
  number.
- Loops with no spaces are cut inside a word, or inside a whole Chinese,
  Japanese or Thai sentence. A unit of 2 to 10 letters repeated 6 or more
  times back to back, alone or joined by `. , - /` or `，、`, keeps one copy, and
  the debris of the same letters after it goes too: Whisper's "It's an
  xlsx.xlsx.xlsx.….xxlsx.xx.xxxx" inserts "It's an xlsx". A run of 10 or
  more of one letter goes whole ("港神อออ…อ出道" inserts "港神出道"),
  unless it stretches a word in the same script, which keeps one letter
  ("Nooooooooooo" inserts "No"). Digits, punctuation and the Japanese
  long-vowel mark "ー" never count; "hahaha", "bye-bye", "哈哈哈哈" and
  "ㅋㅋㅋㅋ" stay.
- The live text shown while you speak (with the engines that re-read the
  utterance: Whisper, Parakeet, Canary) is stricter at its end: 3 repeats
  there keep one copy ("a test test test" shows "a test"), because a
  recogniser that hears a half-finished word often repeats it. The
  finished text still uses the 6-repeat rule.

### 3. Snippets

Say a trigger phrase and it is replaced by your saved text, exactly as
written, including line breaks. Triggers match whole words in any case:
"my email" matches "My email" but not "my emailing". In Chinese and
Japanese a trigger also matches inside a sentence (see Languages).

- No later rule changes the saved text. Fillers, commands and
  capitalisation all leave it alone.
- If you say the trigger on its own, only the saved text is inserted.
  Whisper's full stop after it is dropped.
- Inside a sentence, the punctuation around the trigger is kept.

### 4. Replacement dictionary

Each rule is `wrong -> right`. It fixes a word or phrase the recogniser
keeps getting wrong.

- Matches whole words in any case. "teh" matches "Teh" but not "tehran".
  A possessive still matches: "github's" matches "github". A contraction
  does not: "don" doesn't match "don't". In Chinese and Japanese an entry
  also matches inside a sentence (see Languages).
- Spaces in the left side match any spacing: `git hub -> GitHub`.
- **Capitalisation:** if the right side contains a capital letter, it is
  used exactly as written (`kubernetes -> Kubernetes`). Otherwise it follows
  what was heard: "teh" → "the", "Teh" → "The", "TEH" → "THE".
- **Overlapping rules:** the longest match wins. With `new york -> New York City`
  and `york -> Yorkshire`, "new york" becomes "New York City" and "york" on
  its own becomes "Yorkshire".
- Rules don't chain. With `x -> y` and `y -> z`, "x" becomes "y".
- If two rules have the same left side, the first one wins.
- An empty right side deletes the phrase: `basically ->`.
- Text format for import and export (`Replacement.parseList` /
  `formatList`): one rule per line, `wrong -> right`. Lines starting with
  `#` and blank lines are ignored.

**Acronyms in capitals** (Parakeet ja only: `lowerCaseLatin`, set from the
engine). Parakeet ja's vocabulary has only lower-case Latin letters, so it
writes "fbi", "f1", "m16". In an utterance with a kana or kanji, a run of
ASCII letters and digits that starts with a letter, has at most 4 letters
and no capital becomes upper case: "fbi" → "FBI", "ジョンfケネディ" →
"ジョンFケネディ". Longer words ("safari"), runs with a capital ("iPhone",
"pH") and runs starting with a digit ("11n") stay. Engines that write case
(Whisper, Qwen3) never get this, so their "web" or a romaji "desu" stays.
It runs on the text between the dictionary's matches, so a replacement
keeps its own case ("gh -> github" gives "github", not "GITHUB").

### 5. Filler words

When this is on, "um", "umm", "ummm", "uhm", "uh", "uhh", "uhhh", "er",
"erm", "ah", "ahh", "hm", "hmm" and "hmmm" are removed, along with the
commas Whisper puts around them:

- "So, um, I think" → "So I think"
- "Yes, um I agree" → "Yes, I agree"
- "Um, it works." → "It works." (the next word gets the capital)

Real words are kept:

- the filler inside a longer word ("umbrella", "summer")
- a hyphenated word ("uh-oh")
- all capitals ("ER")
- similar real words ("err")
- "mm", which means millimetres, is not in the list

The list is English and is used only for English (see Languages). It can
be replaced in the config (`fillerWords`).
Words like "like" or "you know" are not removed by default, because they are
often meant.

### 6. Punctuation and spacing tidy-up (always)

Removing words can leave stray punctuation, so these are fixed:

- a space before `, . ! ? ; :` is removed ("Hello , world" → "Hello, world"),
  but ".NET" keeps its dot. In French only the space before `, .` and `…`
  goes.
- a comma right before a full stop is removed
- punctuation left at the start of an utterance is removed

An utterance left with only punctuation inserts nothing.

In Japanese only, for an utterance with a kana or kanji in it:

- a single space between two Chinese or Japanese characters is removed
  (「静かに まを開けた」 → 「静かにまを開けた」); one next to Latin letters stays
- **Sentence end.** A finished utterance that ends in a letter or digit gets
  「。」. The Japanese models end only some utterances with a mark, and each
  utterance ends at a pause, so it is mostly a sentence. It raised Parakeet
  ja's punctuation F1 from 26 to 56 on FLEURS-ja and Whisper turbo's from 62
  to 74. Nothing is added after any mark or bracket (。、？！」）…), after a
  snippet, or to live text. Parakeet ja has no 「？」, so a question gets 「。」
  too (「明日来ますか。」), which is normal in formal Japanese.

### 7. Numbers as digits

When this is on (the default), numbers spoken as words become digits. It
matters for engines that write words: Nemotron, Canary and Qwen3 write
"twenty five percent", Whisper and Parakeet mostly write "25%" already. On
the #14 dictation set it cut Canary's raw word error rate from 57 % to 32 %
and Nemotron's from about 70 % to about 50 %
(`docs/benchmarks/2026-09-host-wer.md`). English only: it doesn't run for
other languages (see Languages).

| Heard | Written |
|---|---|
| twenty five, two hundred and forty nine, twelve thousand three hundred | 25, 249, 12,300 |
| one million downloads, two point five billion | 1 million downloads, 2.5 billion |
| twelve point five percent, one point thirteen point eight | 12.5%, 1.13.8 |
| forty nine dollars and ninety nine cents, eighteen hundred dollars, thirty five euros | $49.99, $1,800, €35 |
| nine thirty AM, at six forty five, four fifteen p.m. | 9:30 AM, at 6:45, 4:15 p.m. |
| twenty twenty six, nineteen oh five | 2026, 1905 |
| March fourteenth, twenty twenty seven; the second of October | March 14th, 2027; the 2nd of October |
| the fifteenth, twenty first century | the 15th, 21st century |
| four eight two one three, four oh four | 48213, 404 |
| minus four degrees, gate B twenty two, v two | -4 degrees, gate B22, v2 |

**Small numbers stay words.** Zero to nine and first to ninth are left as
words ("three boxes", "the first time", "one of them", "at one point"),
as most style guides write them. They become digits only when something
next to them makes them a quantity:

- a unit after them: percent, degrees, a currency, metric and imperial
  units, cups and spoons, bytes, volts ("two cups" → "2 cups"). Time spans
  are not units: "three weeks" stays.
- a time: "seven PM", "five o'clock"
- a month: "June third" → "June 3rd", "the second of October"
- a label word before them: page, chapter, step, room, gate, version,
  level, floor, flight, exit, Android and a few more ("page 7")
- "minus", or a single letter before them ("B2")
- a decimal point: "two point five" → "2.5"

"A couple", "a hundred", "hundreds", "a dozen" and "half" are never
converted. Numbers from ten up always are.

**Pairs.** Two numbers said back to back are read the way people say
them:

- an hour and minutes (1–12, then 10–59 or "oh" plus a digit) become a time
  when "am", "pm", "o'clock", "in the morning" or "at night" follows, or
  "at", "by", "from", "until" or "around" comes before. Ten, eleven and
  twelve become a time even without that ("ten thirty" → "10:30"), unless
  a currency follows.
- otherwise two two-digit numbers make a year or a four-digit number
  ("nineteen fifty dollars" → "$1,950"), and a digit plus a two-digit
  number makes a house or room number ("seven forty two" → "742").
- three or more single digits make one digit string ("nine one one" →
  "911"). Commas stop that: "one, two, three" stays.

**Digits already there** are left alone, apart from three fix-ups:
"9.30am" → "9:30am", "$1800" → "$1,800", and "to$5" → "to $5". Running the
rules on their own output changes nothing.

**Phone numbers.** A digit run gets US phone punctuation when the same
sentence says it is a phone number: "phone", "call", "text", "cell",
"mobile", "fax", "dial" or "voicemail" up to eight words before it (or
four after), or "number" after "my", "your", "cell", "home" and the like.

| Heard | Written |
|---|---|
| my phone number is five five five zero one four two | my phone number is 555-0142 |
| call me at five five five, five five five, zero one four two | call me at 555-555-0142 |
| call one five five five five five five zero one four two | call 1-555-555-0142 |
| my number is plus four four two zero seven nine four six zero one two three | my number is +442079460123 |
| call plus one five five five five five five zero one four two | call +1 555-555-0142 |

- Seven digits become `555-0142`, ten `555-555-0142`, eleven starting with
  1 `1-555-555-0142`. After "plus" or "+", 8 to 15 digits are written
  `+` and the digits with no grouping (the international E.164 form),
  except `+1` numbers, which get the US grouping.
- Groups the recogniser split are joined when their shape fits: "555 0142",
  "555, 0142", "1 555 555 0142".
- Without the context the digits stay as they are: "Order number
  5550142", "the number is 5550142", "Invoice 5555550142". A sentence end
  between the context word and the digits blocks it, and so does a
  quantity word after them ("5550142 dollars").
- Digits that already have punctuation ("555-0142") are left alone.

**Commas** go into numbers of 10,000 and up and into money from $1,000 up.
Years, digit strings and plain four-digit numbers get none.

Limitations:

- A snippet's text and a dictionary rule's left side see the words, not
  the digits, because they run first. `catch twenty two -> Catch-22`
  works.
- "Wait a second" and "the fifth" stay words, and so does "twenty second"
  unless a month is next to it, because it is as often a duration.
- Fractions ("half a teaspoon", "three quarters") are not handled.
- "may" and "march" count as months only with a capital letter.
- A digit before a two-digit number is read as one number: "two twenty
  dollar bills" becomes "$220 bills". With the hyphen ("two twenty-dollar
  bills") it stays as it is.

Turn it off in Settings → Text cleanup → Write numbers as digits
(`numbersAsDigits`).

### 8. Spoken commands

Commands are whole phrases in any case. Any punctuation Whisper put right
after a command ("New line.") is dropped. A command followed by an
apostrophe is ordinary speech: "scratch that's fine" and "the new line's
colour" are left alone.

| Say | Result |
|---|---|
| "new line", "newline", "next line" | a line break |
| "new paragraph", "next paragraph" | a blank line (never more than one) |
| "scratch that" | removes the last sentence (see below) |

**"Scratch that", exactly:**

1. If the text ends with a line break, it removes the line break(s) only.
2. Otherwise it removes the last sentence. That is everything after the
   last `.`, `!` or `?` followed by a space, or after the last line break.
3. If there is no such point, it removes all of the text.

It reaches back into text that is already in the field. That includes text
you typed, and text from an earlier utterance. Say it twice to remove two
sentences.

Limitations:

- "Dr. Smith" counts as a sentence end.
- The phrase also fires inside normal speech ("scratch that itch").

Commands can be turned off (`spokenCommands`). Commands and spoken
punctuation are English and run only for English (see Languages).

**Spoken punctuation** is off by default (`spokenPunctuation`), because
these are also normal words ("the trial period"). `:textproc` treats the two
switches separately; the app's Settings puts punctuation ("Punctuation
words") under Voice commands and applies it only while commands are on
(#42).

| Say | Result |
|---|---|
| "period", "full stop" | `.` |
| "comma" | `,` |
| "question mark" | `?` |
| "exclamation mark", "exclamation point" | `!` |
| "colon" | `:` |
| "semicolon" | `;` |

What you say wins over Whisper's own punctuation next to it. "I'm done,
period." gives "I'm done."

### 9. Joining and capitalisation

Each utterance is joined to the text before the cursor:

- **Spacing:** a single space is added between words. No space is added
  after a line break or an opening bracket, or before closing punctuation
  (`, . ! ? ; : … ) ] } ’ ”` and `%`). An existing trailing space isn't
  doubled. No space is added between two Chinese or Japanese characters
  (Han, kana, 「。、」 and full-width marks such as 「？」), in any language:
  「です。」 + 「明日も」 gives 「です。明日も」. A switch of script keeps it:
  「東京」 + "and it was great" gives 「東京 and it was great」. Korean keeps
  its space.
- **Capitalisation** (`capitalizeSentences`, on by default): the first letter
  is capitalised at the start of the text, after a line break, and after
  `.`, `!`, `?`, `。`, `！` or `？`. It is not capitalised after an ellipsis ("…" or "...").
  A digit or a snippet at the start is left alone. Nothing is ever
  lowercased, so a mid-sentence join keeps Whisper's capital ("I went to
  the" + "Store." stays "Store"), because proper nouns can't be told apart.
  Not in an utterance with a Chinese or Japanese character in it: those
  scripts have no capitals, so a Latin word there keeps its own case
  ("npm。", "iPhoneを買った。").
