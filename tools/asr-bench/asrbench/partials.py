"""Live-partial quality (#28): the app's partial text cleanup and stabilizer, and a metric over what is shown.

    python -m asrbench.partials --work $WORK --engine whisper-tiny-en --limit 5 dictation
    python -m asrbench.partials --work $WORK --engine distil-small-en --rescore ls-clean dictation

The text functions are ports of the Kotlin and must stay in lockstep with it:
- `strip_trailing_punctuation`: SttTextUtils.stripTrailingPunctuation
- `normalize`, `clean_partial`: TextPipeline.normalize / cleanPartial (default config, phantom filter on)
- `strip_non_speech`, `cut_loops`, `cut_tail_repeats`: Phantoms
- `PartialStabilizer`, `aligned_end`: PartialStabilizer.kt

Kotlin semantics kept: `isLetterOrDigit` is `isalpha() or isdecimal()` (Unicode L* or Nd),
`lowercase(Locale.ROOT)` is `lower()`, and Java's `\\s` is ASCII whitespace only, so
`WHITESPACE` below is spelled out. Kotlin works on UTF-16 chars, so a letter outside the BMP
counts as a letter here but not in the app's `isLetterOrDigit` (rare in recogniser output).

The CLI runs `session.offline_session(partials=True)` per clip on virtual time
(`latency.py`'s clip setup and Pixel cost model) with the real model, so the
partial texts are real and their schedule is the phone's. One run scores three
display policies over the same decodes:
- `before`: the app of #19/#20, cleanPartial(committed + latest decode);
- `tailcut`: the same text with only #28's tail-repeat cut (no agreement), to tell the two apart;
- `after`: the #28 app: the PartialStabilizer's text (which includes the tail cut) through cleanPartial.
Each clip's partial texts go to `$WORK/<results>/<engine>/partials-<set>.jsonl`;
`--rescore` recomputes the tables from those files without decoding.

Metric, per utterance (VAD onset to its Final), over the partials the app would
display in order (a Partial whose cleaned text is "" blanks the display and counts):
- partials: Partial events sent;
- repeat partials: shown text with a word or 2-3 word block repeated 3+ times in a row
  (compared as cutLoops compares; digits and digit words never count);
- flicker: words shown and then taken back: over consecutive partials A, B, the words
  of A after their longest common prefix with B (word keys: lower-case letters and digits);
- first word: onset to the first partial with a word;
- first stable word: onset to the first partial whose first word is the first word of
  every later partial of the utterance;
- last partial vs Final: word errors (edit distance on word keys) of the utterance's last
  shown partial against its Final, per Final word. Stabilizing must not cost this much:
  the Final replaces the partial, but a stable word that was wrong stays on screen until then.
"""

import re
import statistics
import sys
import unicodedata

# --- Kotlin ports ------------------------------------------------------------------------------

# Words.WHITESPACE: Java's \s, which is ASCII only.
_WS = " \t\n\x0b\f\r"
WHITESPACE = re.compile(f"[{_WS}]+")
# TextPipeline.CONTROL: [\p{Cc}&&[^\s]].
_CONTROL = re.compile("[" + "".join(re.escape(chr(c)) for c in [*range(0x20), *range(0x7F, 0xA0)]
                                    if chr(c) not in _WS) + "]")
_SENTINEL = "\ufdd0"  # Words.SENTINEL
_TRAILING_PUNCT = re.compile(r"[.?!,;:]+\Z")


def _unicode_class(major: str) -> str:
    """A regex class body of every code point whose category starts with one of `major`, e.g. "LM"."""
    out, start, prev = [], None, None
    for cp in range(0x110000):
        if unicodedata.category(chr(cp))[0] in major:
            if start is None:
                start = cp
            prev = cp
        elif start is not None:
            out.append(re.escape(chr(start)) + ("" if prev == start else "-" + re.escape(chr(prev))))
            start = None
    if start is not None:
        out.append(re.escape(chr(start)) + "-" + re.escape(chr(prev)))
    return "".join(out)


_LM = _unicode_class("LM")  # \p{L}\p{M}
NON_SPEECH = re.compile(f"\\[[{_LM}_ '’-]{{1,40}}]|[♪♫]+")
PAREN_TAG = re.compile(f"\\(([{_LM}_ '’-]{{1,40}})\\)")
STRAY_BRACKETS = re.compile(f"(?<![^{_WS}])[(\\[]+(?:[{_WS}]+[(\\[]+)*(?![^{_WS}])")

NOISE_WORDS = frozenset({
    "applause", "applauding", "clapping", "laugh", "laughs", "laughing", "laughter", "chuckles", "chuckling",
    "giggles", "giggling", "cough", "coughs", "coughing", "sigh", "sighs", "sighing", "sneezes", "sneezing",
    "sniffs", "sniffing", "sniffles", "breathing", "breathes", "gasps", "gasping", "groans", "groaning",
    "clears", "throat", "music", "singing", "sings", "humming", "hums", "whistling", "whistles",
    "buzzing", "buzzes", "buzzer", "beep", "beeps", "beeping", "ringing", "rings", "click", "clicks", "clicking",
    "tapping", "taps", "typing", "knocking", "knocks", "banging", "bangs", "thud", "thumping", "rustling",
    "rattling", "static", "noise", "noises", "silence", "inaudible", "indistinct", "unintelligible", "crosstalk",
    "mumbling", "mumbles", "murmuring", "chattering", "chatter", "footsteps", "howling", "blowing", "rushing",
    "splashing", "dripping", "thunder", "revving", "roaring", "rumbling", "whirring", "whooshing", "hissing",
    "crackling", "sizzling", "barking", "barks", "meowing", "chirping", "crying", "cries", "sobbing",
    "screaming", "screams", "shouting", "cheering", "cheers", "honking", "siren", "sirens", "alarm",
    "vibrating", "creaking", "creaks", "slams", "blank_audio",
})
NOISE_CONTEXT = frozenset({
    "a", "an", "the", "and", "of", "in", "on", "with", "background", "distant", "soft", "softly", "loud",
    "loudly", "faint", "gentle", "upbeat", "dramatic", "man", "woman", "men", "women", "people", "person",
    "child", "baby", "crowd", "audience", "dog", "dogs", "bird", "birds", "wind", "water", "rain", "engine",
    "car", "cars", "traffic", "door", "phone", "keyboard", "bell", "bells", "playing", "plays", "continues",
    "fades", "sound", "sounds", "effect", "effects", "speaking", "talking", "foreign", "language", "audio",
})
DIGIT_WORDS = frozenset({"zero", "oh", "o", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"})
LOOP_MIN_REPEATS = 6
LOOP_MAX_PERIOD = 3
TAIL_REPEATS = 3


def key(word: str) -> str:
    """A word compared without case and punctuation: its letters and digits, lower-cased."""
    return "".join(c for c in word if c.isalpha() or c.isdecimal()).lower()


def _loopable(k: str) -> bool:
    return bool(k) and k not in DIGIT_WORDS and not all(c.isdecimal() for c in k)


def strip_trailing_punctuation(text: str) -> str:
    t = text.strip()
    if not t:
        return ""
    return _TRAILING_PUNCT.sub("", t).rstrip()


def normalize(s: str) -> str:
    """TextPipeline.normalize: NFC, drop control characters and the sentinel, collapse whitespace, trim."""
    t = _CONTROL.sub("", unicodedata.normalize("NFC", s)).replace(_SENTINEL, "")
    return WHITESPACE.sub(" ", t).strip()


def _is_noise_tag(inner: str) -> bool:
    letters = [c for c in inner if c.isalpha()]
    if len(letters) >= 2 and all(c.isupper() for c in letters):
        return True
    words = [w for w in WHITESPACE.split(inner.lower()) if w]
    return (1 <= len(words) <= 4 and any(w in NOISE_WORDS for w in words)
            and all(w in NOISE_WORDS or w in NOISE_CONTEXT for w in words))


def strip_non_speech(text: str) -> str:
    t = NON_SPEECH.sub(" ", text)
    t = PAREN_TAG.sub(lambda m: " " if _is_noise_tag(m.group(1)) else m.group(0), t)
    t = WHITESPACE.sub(" ", STRAY_BRACKETS.sub(" ", t)).strip()
    return "" if not PAREN_TAG.sub("", t).strip() else t


def cut_loops(text: str) -> str:
    words = text.split(" ")
    if len(words) < LOOP_MIN_REPEATS:
        return text
    keys = [key(w) for w in words]
    loopable = [_loopable(k) for k in keys]
    out: list[str] = []
    i = 0
    while i < len(words):
        # The period whose repeats cover the most words from i.
        period, end = 0, i
        for p in range(1, LOOP_MAX_PERIOD + 1):
            if i + p > len(words) or not all(loopable[i:i + p]):
                break
            j = i + p
            while j + p <= len(words) and keys[j:j + p] == keys[i:i + p]:
                j += p
            if (j - i) // p >= LOOP_MIN_REPEATS and j > end:
                period, end = p, j
        if period == 0:
            out.append(words[i])
            i += 1
            continue
        block = keys[i:i + period]
        rest = [k for k in keys[end:] if k]
        cut_short = len(rest) <= period and all(
            block[k].startswith(rest[k]) if k == len(rest) - 1 else block[k] == rest[k] for k in range(len(rest)))
        if cut_short:
            if out:
                out[-1] = out[-1].rstrip(",;:")
            break
        if any(len(k) > 1 for k in block):
            out.extend(words[i:i + period])
        i = end
    return text if len(out) == len(words) else " ".join(out)


def _tail_repeat(keys: list[str]) -> int | None:
    """Phantoms.tailRepeat: where cut_tail_repeats cuts `keys` (the reading that cuts most), or None.

    `keys` may end in an unfinished copy of the block: its first `rest` words, the last one
    possibly cut short.
    """
    best = None
    for p in range(1, LOOP_MAX_PERIOD + 1):
        for rest in range(p + 1):
            end = len(keys) - rest  # where the whole copies end
            if end < p * TAIL_REPEATS:
                continue
            block = keys[end - p:end]
            if any(not k or k in DIGIT_WORDS or all(c.isdecimal() for c in k) for k in block):
                continue
            if not all(keys[end + k] and (block[k].startswith(keys[end + k]) if k == rest - 1
                                          else block[k] == keys[end + k]) for k in range(rest)):
                continue
            start = end - p
            while start - p >= 0 and keys[start - p:start] == block:
                start -= p
            if (end - start) // p < TAIL_REPEATS:
                continue
            cut = start if all(len(k) == 1 and k not in ("a", "i") for k in block) else start + p
            if best is None or cut < best:
                best = cut
    return best


def cut_tail_repeats(text: str) -> str:
    words = [w for w in text.split(" ") if w]
    cut_any = False
    while True:
        keys = [key(w) for w in words]
        end = max((i + 1 for i, k in enumerate(keys) if k), default=0)  # punctuation after the repeats goes too
        if (cut := _tail_repeat(keys[:end])) is None:
            break
        words = words[:cut]
        cut_any = True
    if cut_any and words:
        words[-1] = words[-1].rstrip(",;:")
    out = " ".join(words)
    return text if out == text else out


def clean_partial(text: str) -> str:
    """TextPipeline.cleanPartial (default config). The #28 tail cut is the stabilizer's, not this."""
    return cut_loops(strip_non_speech(normalize(text)))


def aligned_end(stable: list[str], words: list[str], last_tail: list[str] = ()) -> int:
    """The j for which words[:j] is fewest word edits from `stable`; 0 if stable is empty.

    On a tie, the j whose words[j:] starts with most of `last_tail`, then the largest j.
    """
    if not stable:
        return 0
    prev = list(range(len(words) + 1))
    for i in range(1, len(stable) + 1):
        row = [i] + [0] * len(words)
        for j in range(1, len(words) + 1):
            sub = prev[j - 1] + (0 if stable[i - 1] == words[j - 1] else 1)
            row[j] = min(sub, prev[j] + 1, row[j - 1] + 1)
        prev = row
    fewest = min(prev)

    def lead(j: int) -> int:
        n = 0
        while n < min(len(last_tail), len(words) - j) and last_tail[n] == words[j + n]:
            n += 1
        return n

    return max((j for j in range(len(prev)) if prev[j] == fewest), key=lambda j: (lead(j), j))


class PartialStabilizer:
    """PartialStabilizer.kt: LocalAgreement-2 over one utterance's partials."""

    def __init__(self):
        self.reset()

    @property
    def stable_words(self) -> int:
        """How many words at the start of the shown text are stable."""
        return len(self.stable)

    def reset(self) -> None:
        self.stable: list[str] = []
        self.last_tail: list[str] = []

    def update(self, text: str) -> str:
        words = [w for w in cut_tail_repeats(strip_non_speech(text)).split(" ") if w]
        tail = words[aligned_end([key(w) for w in self.stable], [key(w) for w in words],
                                 [key(w) for w in self.last_tail]):]
        agreed = 0  # never this partial's last word
        while agreed < min(len(tail) - 1, len(self.last_tail)) and key(tail[agreed]) == key(self.last_tail[agreed]):
            agreed += 1
        self.stable += tail[:agreed]
        self.last_tail = tail[agreed:]
        return " ".join(self.stable + self.last_tail)


# --- The metric --------------------------------------------------------------------------------

# Display policy: (raw pre-#28 Partial text, #28 Partial text) -> shown text, or None when that
# app sent no Partial. "" in the session's record means no Partial.
POLICIES = {
    "before": lambda raw, stabilized: clean_partial(raw) if raw else None,
    "tailcut": lambda raw, stabilized: clean_partial(cut_tail_repeats(strip_non_speech(raw))) if raw else None,
    "after": lambda raw, stabilized: clean_partial(stabilized) if stabilized else None,
}


def word_keys(text: str) -> list[str]:
    return [k for k in (key(w) for w in text.split()) if k]


def has_repeat(text: str) -> bool:
    """A word or block of 2-3 words repeated 3+ times in a row anywhere, keyed and gated as cut_loops does."""
    keys = [key(w) for w in text.split(" ")]
    for i in range(len(keys)):
        for p in range(1, LOOP_MAX_PERIOD + 1):
            if i + p * TAIL_REPEATS > len(keys) or not all(_loopable(k) for k in keys[i:i + p]):
                break
            if all(keys[i + r * p:i + (r + 1) * p] == keys[i:i + p] for r in range(1, TAIL_REPEATS)):
                return True
    return False


def flicker(prev: str, cur: str) -> int:
    """Words of `prev` taken back by `cur`: those after their longest common prefix."""
    a, b = word_keys(prev), word_keys(cur)
    n = 0
    while n < min(len(a), len(b)) and a[n] == b[n]:
        n += 1
    return len(a) - n


def word_errors(hyp: list[str], ref: list[str]) -> int:
    """Word edit distance (insertions, deletions, substitutions)."""
    prev = list(range(len(ref) + 1))
    for i in range(1, len(hyp) + 1):
        row = [i] + [0] * len(ref)
        for j in range(1, len(ref) + 1):
            row[j] = min(prev[j - 1] + (hyp[i - 1] != ref[j - 1]), prev[j] + 1, row[j - 1] + 1)
        prev = row
    return prev[-1]


def utterance_metrics(onset: float, shown: list[tuple[float, str]], final: str | None = None) -> dict:
    """Metrics of one utterance's displayed partials, (time, text) in order, and its Final's text."""
    firsts = [(word_keys(t) or [None])[0] for _, t in shown]
    first_word = next((t - onset for (t, _), f in zip(shown, firsts) if f is not None), None)
    first_stable = next((shown[k][0] - onset for k, f in enumerate(firsts)
                         if f is not None and all(g == f for g in firsts[k + 1:])), None)
    return {
        "partials": len(shown),
        "repeat_partials": sum(has_repeat(t) for _, t in shown),
        "flicker": sum(flicker(a, b) for (_, a), (_, b) in zip(shown, shown[1:])),
        "first_word_s": first_word,
        "first_stable_s": first_stable,
        # Only for an utterance with a Final and a partial.
        "final_words": len(word_keys(final)) if final is not None and shown else 0,
        "final_errors": word_errors(word_keys(shown[-1][1]), word_keys(final)) if final is not None and shown else 0,
    }


def clip_metrics(record: dict, policy: str) -> list[dict]:
    """One JSONL record (see `_record`) -> metrics per utterance under `policy`."""
    show = POLICIES[policy]
    by_utt: list[list[tuple[float, str]]] = [[] for _ in record["onsets"]]
    for utt, t, raw, stabilized in record["partials"]:
        text = show(raw, stabilized)
        if text is not None:
            by_utt[utt].append((t, text))
    finals: dict[int, str] = {}
    for utt, text in record["finals"]:
        finals[utt] = (finals[utt] + " " + text).strip() if utt in finals else text
    return [utterance_metrics(onset, shown, finals.get(u))
            for u, (onset, shown) in enumerate(zip(record["onsets"], by_utt))]


def table(records: list[dict]) -> str:
    rows = ["| policy | utterances | partials | with a 3+ repeat | utterances with a repeat "
            "| flicker words / utterance | flicker words | first word s (median) | first stable word s (median) "
            "| last partial vs Final WER |",
            "|---|---|---|---|---|---|---|---|---|---|"]
    for policy in POLICIES:
        utts = [m for r in records for m in clip_metrics(r, policy)]
        n = len(utts)
        partials = sum(m["partials"] for m in utts)
        rep = sum(m["repeat_partials"] for m in utts)
        flick = sum(m["flicker"] for m in utts)
        final_words = sum(m["final_words"] for m in utts)
        wer = f"{100 * sum(m['final_errors'] for m in utts) / final_words:.1f} %" if final_words else ""
        med = lambda k: (f"{statistics.median(v):.2f}" if (v := [m[k] for m in utts if m[k] is not None])  # noqa: E731
                         else "")
        rows.append(f"| {policy} | {n} | {partials} | {rep} ({100 * rep / partials if partials else 0:.1f} %) "
                    f"| {sum(m['repeat_partials'] > 0 for m in utts)} | {flick / n if n else 0:.2f} | {flick} "
                    f"| {med('first_word_s')} | {med('first_stable_s')} | {wer} |")
    return "\n".join(rows)


# --- The CLI -----------------------------------------------------------------------------------

# Decode cost relative to distil-small-en, from the host RTFs (#14/#15 runs), to project the
# Pixel cost model onto other engines.
SCALE_VS_DISTIL = {
    "distil-small-en": 1.0, "whisper-tiny-en": 0.34, "whisper-base-en": 0.64, "whisper-small-en": 2.16,
    "parakeet-tdt-0-6b-v3": 0.31, "canary-180m-flash": 0.65,
}


def _record(clip_id: str, audio_s: float, res, settings: dict) -> dict:
    onsets = [t for kind, t, _ in res.events if kind == "onset"]
    return {
        "id": clip_id, "audio_s": round(audio_s, 3), "settings": settings, "onsets": onsets,
        "partials": [[p.utterance, p.time, p.raw, p.stabilized] for p in res.partial_texts],
        "finals": [list(f) for f in res.utterance_finals],
    }


def main() -> None:
    import argparse
    import json
    from pathlib import Path

    from . import engines as E
    from .run import read_jsonl

    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--work", type=Path, required=True)
    ap.add_argument("--engine", required=True, choices=[e.id for e in E.ENGINES + E.CANDIDATES if not e.streaming])
    ap.add_argument("--threads", type=int, default=E.NUM_THREADS)
    ap.add_argument("--limit", type=int, help="first N clips of each set")
    ap.add_argument("--scale", type=float,
                    help="multiplies every decode's cost (default: the engine's SCALE_VS_DISTIL with the Pixel "
                         "cost model, 1 with --host-cost)")
    ap.add_argument("--host-cost", action="store_true", help="host wall time x scale instead of the Pixel model")
    ap.add_argument("--results", default="results-28", help="results directory under $WORK")
    ap.add_argument("--rescore", action="store_true", help="tables from existing JSONL, no decoding")
    ap.add_argument("--vad", type=Path)
    ap.add_argument("--no-agc", action="store_true", help="the clip at its recorded level (no platform-AGC stand-in)")
    ap.add_argument("--soft-agc", action="store_true", help="the app's SoftAgc, as on a phone without a platform AGC")
    ap.add_argument("--whisper-tail-paddings", type=int,
                    help="Whisper only: zero frames after the speech in the encoder input (#46; the app's "
                         "1000 otherwise). Use its own --results directory")
    ap.add_argument("sets", nargs="+")
    args = ap.parse_args()

    engine = E.BY_ID[args.engine]
    scale = args.scale if args.scale is not None else 1.0 if args.host_cost else SCALE_VS_DISTIL.get(engine.id)
    if scale is None:
        sys.exit(f"no default --scale for {engine.id} with the Pixel cost model; pass --scale")
    settings = {"cost": "host" if args.host_cost else "pixel-distil-2t", "scale": scale, "threads": args.threads}
    if args.no_agc or args.soft_agc:  # only when set, so older result files still match
        settings.update(agc=not args.no_agc, soft_agc=args.soft_agc)
    extra = {}
    if args.whisper_tail_paddings is not None:
        if engine.family != E.WHISPER:
            sys.exit("--whisper-tail-paddings is for Whisper engines")
        settings["whisper_tail_paddings"] = extra["whisper_tail_paddings"] = args.whisper_tail_paddings
    label = (f"{engine.id}, {args.threads} threads, "
             f"{'host wall time' if args.host_cost else 'pixel-distil-2t cost model'} x{scale:g}")

    transcriber = vad = None
    for set_name in args.sets:
        out = args.work / args.results / engine.id / f"partials-{set_name}.jsonl"
        done = read_jsonl(out)
        if not args.rescore:
            if any(r["settings"] != settings for r in done):
                sys.exit(f"{out} was run with other settings ({done[0]['settings']}); move it or use --results")
            manifest = read_jsonl(args.work / "data" / set_name / "manifest.jsonl")
            if not manifest:
                sys.exit(f"no manifest for {set_name}")
            lang = manifest[0]["lang"]
            if lang not in engine.languages:
                print(f"skip {set_name}: {lang} not supported by {engine.id}")
                continue
            if transcriber is None:
                from .backends import SAMPLE_RATE, OfflineTranscriber, silero_vad
                from .latency import LEAD_SILENCE, TAIL_SILENCE, pixel_distil_2t
                from .run import load_audio
                from .session import SimClock, agc, offline_session
                transcriber = OfflineTranscriber(engine, args.work / "models" / engine.id, lang,
                                                 num_threads=args.threads, **extra)
                transcriber_lang = lang
                vad = silero_vad(args.vad or args.work / "silero_vad.onnx")
            elif lang != transcriber_lang:
                transcriber = OfflineTranscriber(engine, args.work / "models" / engine.id, lang,
                                                 num_threads=args.threads, **extra)
                transcriber_lang = lang
            if args.host_cost:
                cost = lambda _audio_s, wall_s: wall_s * scale  # noqa: E731
            else:
                cost = lambda audio_s, wall_s: pixel_distil_2t(audio_s, wall_s) * scale  # noqa: E731
            ids = {r["id"] for r in done}
            out.parent.mkdir(parents=True, exist_ok=True)
            with out.open("a") as f:
                for utt in manifest[:args.limit]:
                    if utt["id"] in ids:
                        continue
                    x = load_audio(utt["audio"])
                    if not args.no_agc:
                        x = agc(x)
                    res = offline_session(x, transcriber, vad, TAIL_SILENCE, LEAD_SILENCE, partials=True,
                                          clock=SimClock(cost), soft_agc=args.soft_agc)
                    rec = _record(utt["id"], len(x) / SAMPLE_RATE, res, settings)
                    f.write(json.dumps(rec, ensure_ascii=False) + "\n")
                    f.flush()
                    done.append(rec)
        records = done if args.limit is None else done[:args.limit]
        if not records:
            print(f"# {set_name}: no results in {out}\n")
            continue
        run_label = label if not args.rescore else f"{engine.id}, {records[0]['settings']}"
        print(f"# {set_name}, {run_label}, {len(records)} clips\n\n{table(records)}\n", flush=True)


if __name__ == "__main__":
    main()
