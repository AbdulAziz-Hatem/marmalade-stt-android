"""Score every result file and write $WORK/results/summary.{md,json}.

    python -m asrbench.score --work $WORK [--results results] [--baseline results-v2]

With --baseline, the variant tables compare against that run's default files
instead of this run's (e.g. the app before a fix vs after it).

WER is corpus-level (all errors over all reference words in the set).
Normalisation: Whisper's EnglishTextNormalizer for English (case,
punctuation, spelled-out vs digit numbers, British/American spelling,
fillers like "uh"), Whisper's BasicTextNormalizer for es/de/fr (case and
punctuation only), both from the `whisper-normalizer` package. "raw" WER
splits on whitespace and compares as is: case and punctuation count, which is
what a dictation user sees. The ± figure is the half-width of a 95 %
bootstrap interval over utterances (1000 resamples, seed 14).

Failure counters per engine and set:
  empty       utterances with no text at all
  runaway     hypothesis longer than 2x the reference + 4 words (hallucinated or repeated text)
  head_drop   the reference's first word is missing: the alignment starts with a deletion
  tail_drop   the reference's last word is missing: the alignment ends in a deletion
  flushed     stop() had to flush speech the VAD hadn't closed (with 1 s of tail silence, a sign
              the VAD didn't find the end)

Japanese, Chinese and Korean sets (CJK_SETS) are scored by character error
rate and punctuation F1 instead; see cer.py. The Japanese homophone set (#52)
also gets a kanji-choice table; see homophones.py.

Hindi, Arabic, Turkish, Vietnamese and Norwegian (WORLD_SETS, #37) get WER
with normalise_world(): Whisper's BasicTextNormalizer turns combining marks
into spaces, which splits Hindi words at every vowel sign and Arabic ones at
every short vowel.
"""

import argparse
import json
import re
import unicodedata
from collections import defaultdict
from pathlib import Path

import jiwer
import numpy as np
from whisper_normalizer.basic import BasicTextNormalizer
from whisper_normalizer.english import EnglishTextNormalizer

from . import cer, homophones
from .engines import CANDIDATES, ENGINES
from .granite import ENGINES as GRANITE_ENGINES

EN = EnglishTextNormalizer()
BASIC = BasicTextNormalizer()

ENGLISH_SETS = ["ls-clean", "ls-other", "fleurs-en", "ami-ihm", "dictation"]
OTHER_SETS = ["fleurs-es", "fleurs-de", "fleurs-fr"]
# Scored by character error rate (cer.py), #29; homophones-ja (#52) also by kanji choice (homophones.py).
CJK_SETS = ["fleurs-ja", "kokoro-speech-ja", "fleurs-zh", "aishell1", "fleurs-ko", "zeroth-ko", "homophones-ja"]
# Nemotron 3.5's most-spoken languages that Parakeet doesn't cover (#37).
WORLD = ["hi", "ar", "tr", "vi", "nb"]
WORLD_SETS = [f"fleurs-{x}" for x in WORLD]

# Arabic: short vowels, shadda, sukun and dagger alif dropped; tatweel dropped;
# hamza-carrying alifs, alif maqsura and taa marbuta folded, as is usual for
# Arabic ASR scoring. Digits: Arabic-Indic, Persian and Devanagari to ASCII.
_ARABIC_MARKS = re.compile("[\u064b-\u0652\u0670\u0640]")
_ARABIC_FOLD = str.maketrans("أإآٱىة", "اااايه")
_DIGITS = str.maketrans("٠١٢٣٤٥٦٧٨٩۰۱۲۳۴۵۶۷۸۹०१२३४५६७८९", "0123456789" * 3)


def normalise_world(text: str, lang: str) -> str:
    """NFKC, lower case (Turkish dotted/dotless i kept apart), punctuation and symbols to spaces; marks kept."""
    t = unicodedata.normalize("NFKC", text).translate(_DIGITS)
    if lang == "tr":
        t = t.replace("I", "ı").replace("İ", "i")
    t = t.lower()
    if lang == "ar":
        t = _ARABIC_MARKS.sub("", t).translate(_ARABIC_FOLD)
    t = "".join(" " if unicodedata.category(c)[0] in "PS" else c for c in t)
    return " ".join(t.split())


def normalise(text: str, lang: str) -> str:
    if lang in WORLD:
        return normalise_world(text, lang)
    return (EN if lang == "en" else BASIC)(text).strip()


def _chunk_errors(c) -> int:
    if c.type in ("substitute", "delete"):
        return c.ref_end_idx - c.ref_start_idx
    return c.hyp_end_idx - c.hyp_start_idx if c.type == "insert" else 0


def wer_stats(rows: list[dict], normalised: bool) -> dict:
    refs, hyps = [], []
    for r in rows:
        ref = normalise(r["ref"], r["lang"]) if normalised else r["ref"].strip()
        hyp = normalise(r["hyp"], r["lang"]) if normalised else r["hyp"].strip()
        if ref:  # jiwer can't score an empty reference
            refs.append(ref)
            hyps.append(hyp)
    out = jiwer.process_words(refs, hyps)
    errs = np.array([sum(map(_chunk_errors, a)) for a in out.alignments], float)
    words = np.array([len(r) for r in out.references], float)
    rng = np.random.default_rng(14)
    boot = [errs[i].sum() / words[i].sum() for i in (rng.integers(0, len(refs), len(refs)) for _ in range(1000))]
    lo, hi = np.percentile(boot, [2.5, 97.5])
    return {
        "n": len(refs), "wer": 100 * out.wer, "ci95": 100 * (hi - lo) / 2,
        "sub": out.substitutions, "del": out.deletions, "ins": out.insertions, "ref_words": int(words.sum()),
        "empty": sum(1 for h in hyps if not h),
        "runaway": sum(1 for r, h in zip(refs, hyps) if len(h.split()) > 2 * len(r.split()) + 4),
        "head_drop": sum(1 for a in out.alignments if a and a[0].type == "delete"),
        "tail_drop": sum(1 for a in out.alignments if a and a[-1].type == "delete"),
    }


def load(results: Path) -> dict:
    """results[engine][set+suffix] = rows."""
    res = defaultdict(dict)
    for f in sorted(results.glob("*/*.jsonl")):
        rows = [json.loads(line) for line in f.read_text().splitlines() if line.strip()]
        if rows:
            res[f.parent.name][f.stem] = rows
    return res


def meta(results: Path, engine: str, key: str) -> dict:
    p = results / engine / f"{key}.meta.json"
    return json.loads(p.read_text()) if p.exists() else {}


def fmt(s: dict | None, ci: bool = True) -> str:
    if not s:
        return "–"
    return f"{s['wer']:.1f} ±{s['ci95']:.1f}" if ci else f"{s['wer']:.1f}"


def cjk_tables(engines, res, summary, results: Path) -> list[str]:
    """CER, digit-free CER, punctuation and failure tables for the CJK sets (#29)."""
    cjk = [e for e in engines if any(k.split("@")[0] in CJK_SETS for k in res[e.id])]
    if not cjk:
        return []
    sets = [k for k in CJK_SETS if any(k in res[e.id] or f"{k}@auto" in res[e.id] for e in cjk)]
    md = []
    for e in cjk:
        r, s = res[e.id], summary[e.id]
        c = s["cjk"] = {}
        for k in sets:
            for key in (k, f"{k}@auto"):
                if key in r:
                    c[key] = cer.cer_stats(r[key])
                    c[f"{key}:punct"] = cer.punct_stats(r[key])
        rows = [x for k in sets for x in r.get(k, [])]
        audio = sum(x["audio_s"] for x in rows)
        c["rtf"] = sum(x["decode_s"] for x in rows) / audio if audio else None
        c["peak_rss_mb"] = max((meta(results, e.id, k).get("peak_rss_mb") or 0)
                               for k in r if k.split("@")[0] in sets) or None

    def cell(x):
        return "–" if not x else f"{x['cer']:.1f} ±{x['ci95']:.1f}"

    md += ["", "## Japanese, Chinese, Korean CER (%, normalised; ± = 95 % bootstrap half-width)", "",
           "Language hint as a user would pick it. RTF over these runs, relative only.", "",
           "| Engine | " + " | ".join(sets) + " | RTF | Peak RSS MB |", "|---|" + "---|" * (len(sets) + 2)]
    for e in cjk:
        c = summary[e.id]["cjk"]
        rtf = "–" if c["rtf"] is None else f"{c['rtf']:.3f}"
        md.append(f"| {e.id} | " + " | ".join(cell(c.get(k)) for k in sets) + f" | {rtf} | {c['peak_rss_mb'] or '–'} |")

    auto = [e for e in cjk if any(f"{k}@auto" in res[e.id] for k in sets)]
    if auto:
        md += ["", "### Auto-detect (no hint)", "", "| Engine | " + " | ".join(sets) + " |", "|---|" + "---|" * len(sets)]
        for e in auto:
            c = summary[e.id]["cjk"]
            md.append(f"| {e.id} | " + " | ".join(cell(c.get(f"{k}@auto")) for k in sets) + " |")

    # The same digit-free utterances for every engine: recognition without number formatting.
    subsets = {k: cer.common_subset([res[e.id][k] for e in cjk if k in res[e.id]]) for k in sets}
    md += ["", "### CER on utterances without digits (same utterances for every engine)", "",
           "Drops every utterance whose reference or any engine's hinted hypothesis has a digit.", "",
           "| Engine | " + " | ".join(f"{k} (n={len(subsets[k])})" for k in sets) + " |", "|---|" + "---|" * len(sets)]
    for e in cjk:
        c = summary[e.id]["cjk"]
        cells = []
        for k in sets:
            x = c[f"{k}:nodigit"] = cer.cer_stats(res[e.id][k], subsets[k]) if k in res[e.id] else None
            cells.append(cell(x))
        md.append(f"| {e.id} | " + " | ".join(cells) + " |")

    punct_sets = [k for k in sets if any(summary[e.id]["cjk"].get(f"{k}:punct") for e in cjk)]
    if punct_sets:
        md += ["", "### Punctuation (sets with punctuated references)", "",
               "F1 % over all marks (class-aware) / commas / sentence ends; then marks per 100 characters, "
               "hypothesis vs reference; then utterances ending in a sentence mark, hypothesis vs reference.", "",
               "| Engine | " + " | ".join(punct_sets) + " |", "|---|" + "---|" * len(punct_sets)]

        def f(v):
            return "–" if v is None else f"{v:.0f}"

        for e in cjk:
            c = summary[e.id]["cjk"]
            cells = []
            for k in punct_sets:
                p = c.get(f"{k}:punct")
                cells.append("–" if not p else
                             f"{f(p['f1'])} / {f(p['comma_f1'])} / {f(p['end_f1'])}; "
                             f"{p['marks_per_100_hyp']:.1f} vs {p['marks_per_100_ref']:.1f}; "
                             f"{p['ends_hyp']} vs {p['ends_ref']} of {p['n']}")
            md.append(f"| {e.id} | " + " | ".join(cells) + " |")

    md += ["", "### CJK failure counters (hinted runs)", "",
           "empty / runaway / head_drop / tail_drop / hypotheses with digits, per set.", "",
           "| Engine | " + " | ".join(sets) + " |", "|---|" + "---|" * len(sets)]
    for e in cjk:
        c = summary[e.id]["cjk"]
        md.append(f"| {e.id} | " + " | ".join(
            f"{x['empty']}/{x['runaway']}/{x['head_drop']}/{x['tail_drop']}/{x['digit_hyps']}" if (x := c.get(k)) else "–"
            for k in sets) + " |")
    return md


def world_tables(engines, res, summary) -> list[str]:
    """WER for the languages Nemotron 3.5 covers and Parakeet doesn't (#37), hinted and auto."""
    eng = [e for e in engines if any(k.split("@")[0] in WORLD_SETS for k in res[e.id])]
    if not eng:
        return []
    md = ["", "## Hindi, Arabic, Turkish, Vietnamese, Norwegian WER (%, FLEURS, normalise_world)", "",
          "Hint = the language set as a user would pick it; auto = no hint. The app offers Qwen3 no hint for "
          "these languages, so auto is what it does in the app; Whisper turbo takes the hint since #37's review.", "",
          "| Engine | " + " | ".join(WORLD) + " | " + " | ".join(f"{x} auto" for x in WORLD) + " | RTF |",
          "|---|" + "---|" * (2 * len(WORLD) + 1)]
    fails = []
    for e in eng:
        r, w = res[e.id], summary[e.id].setdefault("world", {})
        for k in WORLD_SETS + [f"{x}@auto" for x in WORLD_SETS]:
            if k in r:
                w[k] = wer_stats(r[k], normalised=True)
        rows = [x for k in r if k.split("@")[0] in WORLD_SETS for x in r[k]]
        audio = sum(x["audio_s"] for x in rows)
        w["rtf"] = sum(x["decode_s"] for x in rows) / audio if audio else None
        rtf = "–" if w["rtf"] is None else f"{w['rtf']:.3f}"
        md.append(f"| {e.id} | " + " | ".join(fmt(w.get(k)) for k in WORLD_SETS) + " | "
                  + " | ".join(fmt(w.get(f"{k}@auto"), ci=False) for k in WORLD_SETS) + f" | {rtf} |")
        fails.append(f"| {e.id} | " + " | ".join(
            f"{x['empty']}/{x['runaway']}/{x['head_drop']}/{x['tail_drop']}" if (x := w.get(k)) else "–"
            for k in WORLD_SETS) + " |")
    md += ["", "### Failure counters (hinted)", "", "empty / runaway / head_drop / tail_drop per set.", "",
           "| Engine | " + " | ".join(WORLD) + " |", "|---|" + "---|" * len(WORLD), *fails]
    return md


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--work", type=Path, required=True)
    ap.add_argument("--results", default="results", help="results directory under $WORK")
    ap.add_argument("--baseline", help="results directory under $WORK the variant tables compare against")
    args = ap.parse_args()
    results = args.work / args.results
    res = load(results)
    base_res = load(args.work / args.baseline) if args.baseline else res
    engines = [e for e in [*ENGINES, *CANDIDATES, *GRANITE_ENGINES.values()] if e.id in res]
    # Engines with English or es/de/fr results get the WER tables; CJK-only runs skip them.
    western = [e for e in engines if any(k.split("@")[0] in ENGLISH_SETS + OTHER_SETS + ["noise"] for k in res[e.id])]
    summary: dict = {}
    md = []

    # ---- English WER ---------------------------------------------------------
    md += ["## English WER (%, normalised; ± = 95 % bootstrap half-width)", "",
           "| Engine | LS clean | LS other | FLEURS en | AMI ihm | Dictation (norm) | Dictation (raw) | RTF | Peak RSS MB |",
           "|---|---|---|---|---|---|---|---|---|"]
    for e in engines:
        r = res[e.id]
        s = summary.setdefault(e.id, {})
        for k in ENGLISH_SETS:
            if k in r:
                s[k] = wer_stats(r[k], normalised=True)
        if "dictation" in r:
            s["dictation-raw"] = wer_stats(r["dictation"], normalised=False)
        # RTF over every hinted session run (English and other languages).
        rows = [x for k in ENGLISH_SETS + OTHER_SETS for x in r.get(k, [])]
        audio = sum(x["audio_s"] for x in rows)
        s["rtf"] = sum(x["decode_s"] for x in rows) / audio if audio else None
        s["peak_rss_mb"] = max((meta(results, e.id, k).get("peak_rss_mb") or 0) for k in r) or None
        s["flushed"] = sum(1 for x in rows if x["flushed_tail"])
        rtf = "–" if s["rtf"] is None else f"{s['rtf']:.3f}"
        if e in western:
            md.append(f"| {e.id} | " + " | ".join(fmt(s.get(k)) for k in ENGLISH_SETS) +
                      f" | {fmt(s.get('dictation-raw'), ci=False)} | {rtf} | {s['peak_rss_mb'] or '–'} |")

    # ---- Other languages -----------------------------------------------------
    multi = [e for e in engines if any(k in res[e.id] for k in OTHER_SETS)]
    if multi:
        md += ["", "## Spanish, German, French WER (%, FLEURS, 50 utterances each, normalised)", "",
               "Hint = the language set as a user would pick it; auto = no hint (the app's default "
               "for Whisper turbo, Nemotron 3.5 and Qwen3). Parakeet v3 always detects.", "",
               "| Engine | es | de | fr | es auto | de auto | fr auto | en auto (FLEURS) |",
               "|---|---|---|---|---|---|---|---|"]
        for e in multi:
            r, s = res[e.id], summary[e.id]
            for k in OTHER_SETS + [f"{x}@auto" for x in OTHER_SETS + ["fleurs-en"]]:
                if k in r:
                    s[k] = wer_stats(r[k], normalised=True)
            cols = OTHER_SETS + [f"{x}@auto" for x in OTHER_SETS] + ["fleurs-en@auto"]
            md.append(f"| {e.id} | " + " | ".join(fmt(s.get(k), ci=False) for k in cols) + " |")

    # ---- Variants (@preroll…, @lead…, @noagc) --------------------------------
    def variant(key: str) -> str | None:
        tags = [t for t in key.split("@")[1:] if t not in ("auto", "direct")]
        return "@".join(tags) or None

    variants = sorted({v for e in engines for k in res[e.id] if (v := variant(k))})
    for v in variants:
        md += ["", f"## English WER, variant @{v}", "",
               "WER, then head_drop (first word missing) in brackets: "
               + ("the baseline run's default" if args.baseline else "this run's default") + " → this variant.", "",
               "| Engine | " + " | ".join(ENGLISH_SETS) + " |", "|---|" + "---|" * len(ENGLISH_SETS)]
        for e in engines:
            if not any(variant(k) == v for k in res[e.id]):
                continue
            s, cells = summary[e.id], []
            for k in ENGLISH_SETS:
                if f"{k}@{v}" in res[e.id]:
                    x = s[f"{k}@{v}"] = wer_stats(res[e.id][f"{k}@{v}"], normalised=True)
                    base_rows = base_res.get(e.id, {}).get(k)
                    base = wer_stats(base_rows, normalised=True) if base_rows else None
                    cells.append(f"{base['wer']:.1f} ({base['head_drop']}) → {x['wer']:.1f} ({x['head_drop']})"
                                 if base else f"{x['wer']:.1f} ({x['head_drop']})")
                else:
                    cells.append("–")
            md.append(f"| {e.id} | " + " | ".join(cells) + " |")

    # ---- Failures ------------------------------------------------------------
    md += ["", "## Failure counters (all hinted session runs)", "",
           "empty / runaway / head_drop / tail_drop per set, as defined in score.py; noise = clips (of 6) with any "
           "text through the app's VAD path, and fed straight to the model.", "",
           "| Engine | " + " | ".join(ENGLISH_SETS + OTHER_SETS) + " | flushed | noise (VAD) | noise (no VAD) |",
           "|---|" + "---|" * (len(ENGLISH_SETS + OTHER_SETS) + 3)]
    for e in engines:
        r, s = res[e.id], summary[e.id]
        cells = []
        for k in ENGLISH_SETS + OTHER_SETS:
            x = s.get(k)
            cells.append(f"{x['empty']}/{x['runaway']}/{x['head_drop']}/{x['tail_drop']}" if x else "–")
        noise = {}
        for k in ("noise", "noise@direct"):
            if k in r:
                noise[k] = {x["id"]: x["hyp"] for x in r[k]}
        s["noise"] = noise
        n_vad = sum(1 for t in noise.get("noise", {}).values() if t)
        n_dir = sum(1 for t in noise.get("noise@direct", {}).values() if t)
        if e in western:
            md.append(f"| {e.id} | " + " | ".join(cells) + f" | {s['flushed']} | {n_vad} | {n_dir} |")

    md += cjk_tables(engines, res, summary, results)
    md += homophones.tables(res, engines, summary)
    md += world_tables(engines, res, summary)

    md += ["", "## Noise-clip outputs (non-empty only)", ""]
    for e in engines:
        for k, texts in summary[e.id]["noise"].items():
            for clip, t in texts.items():
                if t:
                    md.append(f"- {e.id} {k} {clip}: \"{t[:120]}\"")

    (results / "summary.md").write_text("\n".join(md) + "\n")
    (results / "summary.json").write_text(json.dumps(summary, indent=1, ensure_ascii=False))
    print("\n".join(md))


if __name__ == "__main__":
    main()
