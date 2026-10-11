"""Paired comparison of variants of one engine against a baseline run, per set (#55).

    python -m asrbench.paired --work .. --results results-55 --engine whisper-turbo \\
        --base '' --variants @int8-h8,@q4b32-h8 fleurs-ja ami-ihm fleurs-en

Each variant's file is `<set><tag>.jsonl` next to the baseline's (`--base` is the baseline's tag, '' for
the plain run). Only utterances both runs decoded count. Error rate as `score.py` (WER, CER for ja/zh/ko),
the difference variant − baseline with a 95 % paired bootstrap interval (2000 resamples over utterances,
seed 14), the utterances worse/better, how many hypotheses changed at all, and the runaways (the int8 Whisper failure mode): hypotheses that
`tail_table.cut_repeats` shortens, or more than twice their reference's length plus 5 (words, or characters
for ja/zh/ko; catches a one-word loop cut off mid-word, "Müzik Müzik … Mü").

`--drop-phantoms` first removes a leading or trailing segment that is only a silence phrase Whisper writes
for noise (`PHANTOMS`: the Japanese and Turkish ones seen in #55), so a variant isn't charged for a longer
phantom on the same noise. The app drops such phrases only on near-silent audio (#57,
`Phantoms.WHISPER`), or holds them back before speech, and the harness's lead-ins are louder than that.
"""
import argparse
import json
import re
from pathlib import Path

import jiwer
import numpy as np

from .cer import _align
from .cer import normalise as cer_normalise
from .score import _chunk_errors, normalise
from .tail_table import cut_repeats

CJK = {"ja", "zh", "ko"}
# Whole-segment silence phrases (segments are joined with a space in `hyp`).
PHANTOMS = ["ご視聴ありがとうございました", "おわり", "お", "MÜZİK", "Müzik"]
_PHANTOM_EDGES = re.compile(r"^(?:(?:%s)\s+)+|(?:\s+(?:%s))+$" % (("|".join(map(re.escape, PHANTOMS)),) * 2))


def drop_phantoms(hyp: str) -> str:
    return "" if hyp.strip() in PHANTOMS else _PHANTOM_EDGES.sub("", hyp)


def per_utterance_errors(rows: list[dict], lang: str) -> tuple[np.ndarray, np.ndarray]:
    """(errors, reference length) per utterance, in words or (CJK) characters."""
    if lang in CJK:
        refs = [cer_normalise(r["ref"], lang) for r in rows]
        hyps = [cer_normalise(r["hyp"], lang) for r in rows]
        out = _align(refs, hyps)
        n = [len(r) for r in refs]
    else:
        refs = [normalise(r["ref"], lang) for r in rows]
        hyps = [normalise(r["hyp"], lang) for r in rows]
        out = jiwer.process_words(refs, hyps)
        n = [len(r) for r in out.references]
    return np.array([sum(map(_chunk_errors, a)) for a in out.alignments], float), np.array(n, float)


def compare(base: list[dict], var: list[dict]) -> dict:
    a = {r["id"]: r for r in base}
    b = {r["id"]: r for r in var}
    ids = [i for i in a if i in b]
    lang = a[ids[0]]["lang"]
    ea, n = per_utterance_errors([a[i] for i in ids], lang)
    eb, _ = per_utterance_errors([b[i] for i in ids], lang)
    rng = np.random.default_rng(14)
    boot = [(eb[s].sum() - ea[s].sum()) / n[s].sum() * 100
            for s in (rng.integers(0, len(ids), len(ids)) for _ in range(2000))]
    lo, hi = np.percentile(boot, [2.5, 97.5])
    size = (lambda t: len(t.replace(" ", ""))) if lang in CJK else (lambda t: len(t.split()))
    runaway = lambda r: cut_repeats(r["hyp"], lang) != r["hyp"] or size(r["hyp"]) > 2 * size(r["ref"]) + 5
    runaways = lambda d: sum(runaway(d[i]) for i in ids)
    return {"n": len(ids), "lang": lang, "base": 100 * ea.sum() / n.sum(), "var": 100 * eb.sum() / n.sum(),
            "diff": 100 * (eb.sum() - ea.sum()) / n.sum(), "lo": lo, "hi": hi,
            "worse": int((eb > ea).sum()), "better": int((eb < ea).sum()),
            "changed": sum(a[i]["hyp"] != b[i]["hyp"] for i in ids),
            "runaways": (runaways(a), runaways(b))}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--work", type=Path, required=True)
    ap.add_argument("--results", required=True)
    ap.add_argument("--engine", required=True)
    ap.add_argument("--base", default="", help="the baseline's tag, e.g. '' or '@wtail300'")
    ap.add_argument("--variants", required=True, help="comma-separated tags, e.g. @int8-h8,@q4b32-h8")
    ap.add_argument("--drop-phantoms", action="store_true", help="remove whole-segment PHANTOMS first")
    ap.add_argument("sets", nargs="+")
    args = ap.parse_args()

    d = args.work / args.results / args.engine
    clean = (lambda r: {**r, "hyp": drop_phantoms(r["hyp"])}) if args.drop_phantoms else (lambda r: r)
    read = lambda f: [clean(json.loads(line)) for line in f.read_text().splitlines() if line.strip()]
    print("| Variant | Set | n | Baseline | Variant | Difference (95 %) | Worse / better | Changed | Runaways |")
    print("|---|---|---|---|---|---|---|---|---|")
    for tag in args.variants.split(","):
        for s in args.sets:
            fb, fv = d / f"{s}{args.base}.jsonl", d / f"{s}{tag}.jsonl"
            if not (fb.exists() and fv.exists()):
                continue
            c = compare(read(fb), read(fv))
            unit = " (CER)" if c["lang"] in CJK else ""
            print(f"| {tag.lstrip('@')} | {s}{unit} | {c['n']} | {c['base']:.2f} | {c['var']:.2f} | "
                  f"{c['diff']:+.2f} ({c['lo']:+.2f} … {c['hi']:+.2f}) | {c['worse']} / {c['better']} | "
                  f"{c['changed']} | {c['runaways'][0]} → {c['runaways'][1]} |")


if __name__ == "__main__":
    main()
