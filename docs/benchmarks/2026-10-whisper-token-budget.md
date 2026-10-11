# Whisper's token budget: Hindi past 224 tokens, shorter runaways (#54, 2026-10-02)

**Question.** Whisper stops a decode at 224 tokens (OpenAI's `sample_len`,
n_text_ctx / 2), the only limit left since patch 0002 (#37). Hindi on
Whisper turbo needs 12–17 tokens a second, so a Hindi utterance longer than
about 13–18 s lost its end (#49 saw a 16.6 s FLEURS Final stop at exactly
224 tokens; its reference needs 267). Should Hindi utterances be cut
shorter, at a quiet point, so each decode fits? And can a per-language
budget shorten the 224 steps a looping decode costs English and Japanese?

**Answer.** Raise the limit for Hindi rather than cut. The 224 is a
convention: the decoder's self-attention cache holds 448 positions, which
sherpa-onnx already guards. Patch 0005 makes the stream option
`max_new_tokens` replace it, and the app sets ceil(k·s) + c per language
(`SherpaBackends.kt` `WHISPER_TOKEN_BUDGETS`): Hindi 20/s + 10, English
6/s + 10, Japanese 10/s + 10, the others keep 224. Hindi decodes are cut at
21 s (`UtteranceCap.maxDecodeSamples`), where its budget would pass the 443
tokens the cache holds. On FLEURS Hindi that is 31.0 → 30.8 WER, and on
11–35 s Hindi 39.3 → 31.5. Cutting at 12 s with the 224 limit was worse on
FLEURS Hindi (+1.9) and no better on the long set: a cut costs words.

## Which languages reach 224

Whisper turbo's byte-level BPE tokens per second of speech, from the FLEURS
reference texts (re-encoded with turbo's tokenizer; seconds from the first to
the last sample above 5 % of the clip's peak), for every language the app
offers turbo a hint for:

| Language | p99 tokens/s | max | 224 tokens at the p99 rate |
|---|---|---|---|
| Hindi | 18.6 | 19.7 | 12.0 s |
| Chinese | 9.1 | 9.5 | 24.5 s |
| Arabic | 8.5 | 8.7 | 26.3 s |
| Vietnamese | 7.4 | 8.8 | 30.1 s |
| French, German, Korean, Norwegian, Turkish, Japanese, Spanish, English | 5.0–7.1 | 5.4–7.2 | over 31 s |

(Through the app's session, turbo's own Hindi Finals peaked at 17.2 a
second, `2026-10-whisper-runaway.md`.) Only Hindi reaches 224 well inside
the 28.6 s cap. Chinese and Arabic could at their densest in a 24–28 s
utterance of continuous speech; not changed here.

## Hindi: cut, or decode further?

Host harness (`tools/asr-bench`, the app's offline session: Silero, 0.6 s
pre-roll, harness AGC, 4 threads, `nice -n 19`). Two sets: FLEURS Hindi
(100 clips, 4–21 s) and **FLEURS hi pairs**, 50 pairs of those clips, each
trimmed to its speech and joined with no gap (11–35 s, median 20 s; 34 of
the 50 references are over 224 tokens), as #48 joined LibriSpeech.
Normalised WER (`normalise_world`), with a paired bootstrap (2000 resamples)
against the first row.

**Cut shorter (shared venv, patches 0001–0003, Whisper's 224):**

| Cap, pre-roll included | FLEURS hi | Δ (95 % CI) | hi pairs | Δ (95 % CI) | pairs ending in a deletion |
|---|---|---|---|---|---|
| 28.6 s (before) | 31.7 | | 39.7 | | 18 |
| 14 s | 31.9 | +0.2 (−0.3 to +0.8) | 37.2 | −2.5 (−6.2 to +1.4) | 0 |
| 12 s | 32.2 | +0.5 (−0.8 to +2.4) | 35.6 | −4.2 (−8.3 to −0.2) | 1 |
| 10 s | 42.5 | +10.8 (+3.5 to +20.8) | 36.8 | −3.0 (−7.4 to +1.0) | 0 |

Cutting brings the long set's endings back (deletions 286 → 77 at 12 s) but
adds insertions (65 → 121): the quietest window in the last 1.5 s of
continuous speech is often mid-phrase, and Whisper then tends to repeat the
sentence before it. One 22 s clip came out of its 10.6 s first part as
"कोई सुनामी की चतावनी जारी नहीं की गई है … के अनुसार कोई सुनामी की चतावनी
चतावनी जारी नहीं की गई है … के अनुसार कोई सुनामी की चतावनी" (+18 errors);
another repeated "अक्सर बेतरती व्याकरण". At 10 s nearly every clip is cut
and the repeats dominate.

**Decode further (venv-54: patches 0001–0005, kissfft knf as the app):**

| Config | FLEURS hi | Δ (95 % CI) | hi pairs | Δ (95 % CI) | del / ins / sub (pairs) | pairs ending in a deletion |
|---|---|---|---|---|---|---|
| 224 tokens, 28.6 s cap (before) | 31.0 | | 39.3 | | 272 / 62 / 615 | 18 |
| 224 tokens, 12 s cap | 32.9 | +1.9 (+0.1 to +4.7) | 39.3 | +0.0 (−6.7 to +8.9) | 63 / 241 / 646 | 0 |
| **budget 20/s + 10, 21 s cap (app)** | **30.8** | −0.2 (−0.6 to +0.1) | **31.5** | **−7.8 (−11.9 to −4.4)** | 34 / 71 / 655 | 0 |
| budget 20/s + 10, 28.6 s cap | 30.8 | −0.2 (−0.6 to +0.0) | 34.7 | −4.6 (−6.7 to −2.6) | 130 / 71 / 638 | 2 |

Past 224, turbo keeps transcribing: 20 single-segment pairs came out over
224 tokens (up to 375), with no new insertions. Without the 21 s cap the
longest pairs (25–35 s) run into the cache's 443 and lose their ends again.
The two venvs' "before" rows differ by float rounding in the log-mel
(kissfft knf, #53), which flips some int8 transcripts; compare within a
table.

Why 20/s + 10: the densest Hindi Final measured was 17.2 tokens a second
(`2026-10-whisper-runaway.md`), and #49's budget table had k = 20 cutting no
Hindi Final. It reaches 443 at 21.65 s; the cap is 21 s.

## English and Japanese: shorter runaways

A Whisper decode that falls into a loop runs to its limit: 224 steps, 8–10 s
on the host and roughly 20–40 s on the Pixel 8a, which the user waits
through after tapping stop (#49; a cancel now stops it at once). #49's
FLEURS data had English Finals at most 4.8 tokens a second and Japanese 8.1;
a budget of 6/s + 10 (English) and 10/s + 10 (Japanese) cut none of them,
and stops the 6.9 s Japanese runaway at 79 steps instead of 224.

Checked on more sets: turbo through the app's session (shared venv, so
nothing was cut), each Final's token count against its budget. A Final is
cut only if it needs more tokens than its budget; greedy decoding is
deterministic, so every other Final's text is unchanged.

| Set | Finals | most tokens/s (no loops, ≥ 2 s) | least slack, tokens | cut |
|---|---|---|---|---|
| FLEURS en | 202 | 4.8 | 16 | 0 |
| LibriSpeech other | 202 | 6.1 | 10 | 0 |
| AMI ihm | 211 | 5.5 | 11 | 1: a 224-token "... ... ..." loop on 2.8 s |
| AMI long (≥ 8 s) | 225 | 4.9 | 12 | 0 |
| dictation (ours) | 40 | 4.6 | 15 | 0 |
| FLEURS ja | 233 | 8.0 | 13 | 1: #49's 224-token runaway |
| Kokoro Speech ja | 209 | 9.7 (a 2.6 s phrase its reference says twice) | 11 | 0 |
| homophones ja | 152 | 6.7 | 18 | 0 |

LibriSpeech other also had a 2.2 s Final at exactly its budget (24 tokens):
"Herman listened to the pair in silence." said three times for one. With
c = 10, the densest real Final needs k = 3.95 in English (a 10.9 s AMI
turn) and 6.82 in Japanese (an 8.7 s FLEURS clip), so 6 and 10 leave about
half again. In venv-54 the FLEURS ja runaway's decode took 2.3 s instead of
6.2 s (host, 4 threads).

## Scripts and raw outputs

On the benchmark host, under `$WORK/`: `results-54/` (cut sweep, shared venv),
`results-54v/` (venv-54), `data/fleurs-hi-pairs/` and `raw/fleurs-hi-pairs/`
(`scratch-54/mkpairs.py`), `scratch-54/score54.py` (the tables),
`scratch-54/budget_check.py` and `budget-*.jsonl` (per-Final token counts).
The venv: `VENV=venv-54 PATCH_ROOT=<repo>/third_party/patches setup.sh`.
