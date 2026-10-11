## English WER (%, normalised; ± = 95 % bootstrap half-width)

| Engine | LS clean | LS other | FLEURS en | AMI ihm | Dictation (norm) | Dictation (raw) | RTF | Peak RSS MB |
|---|---|---|---|---|---|---|---|---|
| nemotron-3-5-streaming-560ms | 3.2 ±0.7 | 7.8 ±1.4 | 10.2 ±1.5 | 13.8 ±2.2 | – | – | 0.144 | 2088 |
| nemotron-3-5-streaming-1120ms | 3.2 ±0.7 | 7.8 ±1.3 | 9.8 ±1.5 | 14.5 ±2.0 | – | – | 0.091 | 2103 |

## Spanish, German, French WER (%, FLEURS, 50 utterances each, normalised)

Hint = the language set as a user would pick it; auto = no hint (the app's default for Whisper turbo, Nemotron 3.5 and Qwen3). Parakeet v3 always detects.

| Engine | es | de | fr | es auto | de auto | fr auto | en auto (FLEURS) |
|---|---|---|---|---|---|---|---|
| nemotron-3-5-streaming-560ms | 7.9 | 10.1 | 13.8 | – | – | – | – |
| nemotron-3-5-streaming-1120ms | 7.3 | 10.9 | 13.6 | – | – | – | – |

## English WER, variant @tail0.7s

WER, then head_drop (first word missing) in brackets: this run's default → this variant.

| Engine | ls-clean | ls-other | fleurs-en | ami-ihm | dictation |
|---|---|---|---|---|---|
| nemotron-3-5-streaming-560ms | 3.2 (0) → 3.2 (0) | 7.8 (0) → 7.8 (0) | 10.2 (0) → 10.2 (0) | 13.8 (17) → 13.4 (16) | – |

## English WER, variant @tail1.3s

WER, then head_drop (first word missing) in brackets: this run's default → this variant.

| Engine | ls-clean | ls-other | fleurs-en | ami-ihm | dictation |
|---|---|---|---|---|---|
| nemotron-3-5-streaming-1120ms | 3.2 (0) → 2.8 (0) | 7.8 (0) → 7.1 (0) | 9.8 (1) → 9.5 (1) | 14.5 (19) → 12.9 (16) | – |

## Failure counters (all hinted session runs)

empty / runaway / head_drop / tail_drop per set, as defined in score.py; noise = clips (of 6) with any text through the app's VAD path, and fed straight to the model.

| Engine | ls-clean | ls-other | fleurs-en | ami-ihm | dictation | fleurs-es | fleurs-de | fleurs-fr | flushed | noise (VAD) | noise (no VAD) |
|---|---|---|---|---|---|---|---|---|---|---|---|
| nemotron-3-5-streaming-560ms | 0/0/0/4 | 0/0/0/4 | 0/0/0/4 | 1/0/17/25 | – | 0/0/0/1 | 0/0/1/2 | 0/1/0/1 | 14 | 0 | 0 |
| nemotron-3-5-streaming-1120ms | 0/0/0/10 | 0/0/0/14 | 0/0/1/7 | 0/0/19/41 | – | 0/0/0/1 | 0/0/0/7 | 0/1/0/7 | 14 | 0 | 0 |

## Japanese, Chinese, Korean CER (%, normalised; ± = 95 % bootstrap half-width)

Language hint as a user would pick it. RTF over these runs, relative only.

| Engine | fleurs-ja | fleurs-zh | RTF | Peak RSS MB |
|---|---|---|---|---|
| nemotron-3-5-streaming-560ms | 14.7 ±1.5 | 22.7 ±3.3 | 0.109 | 1786 |
| nemotron-3-5-streaming-1120ms | 15.1 ±1.5 | 22.8 ±3.1 | 0.070 | 1763 |

### CER on utterances without digits (same utterances for every engine)

Drops every utterance whose reference or any engine's hinted hypothesis has a digit.

| Engine | fleurs-ja (n=125) | fleurs-zh (n=74) |
|---|---|---|
| nemotron-3-5-streaming-560ms | 12.0 ±1.6 | 19.9 ±3.6 |
| nemotron-3-5-streaming-1120ms | 12.8 ±1.7 | 20.3 ±3.2 |

### Punctuation (sets with punctuated references)

F1 % over all marks (class-aware) / commas / sentence ends; then marks per 100 characters, hypothesis vs reference; then utterances ending in a sentence mark, hypothesis vs reference.

| Engine | fleurs-ja | fleurs-zh |
|---|---|---|
| nemotron-3-5-streaming-560ms | 48 / 52 / 42; 2.0 vs 5.6; 40 vs 200 of 200 | 45 / 65 / 5; 3.4 vs 6.8; 0 vs 100 of 100 |
| nemotron-3-5-streaming-1120ms | 45 / 52 / 32; 1.8 vs 5.6; 22 vs 200 of 200 | 48 / 68 / 6; 3.7 vs 6.8; 0 vs 100 of 100 |

### CJK failure counters (hinted runs)

empty / runaway / head_drop / tail_drop / hypotheses with digits, per set.

| Engine | fleurs-ja | fleurs-zh |
|---|---|---|
| nemotron-3-5-streaming-560ms | 0/0/2/1/0 | 0/0/0/6/8 |
| nemotron-3-5-streaming-1120ms | 0/0/2/20/0 | 0/0/3/26/7 |

## Noise-clip outputs (non-empty only)

