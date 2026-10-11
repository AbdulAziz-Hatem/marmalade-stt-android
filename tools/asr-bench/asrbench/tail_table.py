"""Whisper tail-padding variants side by side (#46): WER/CER, loops and decode time per set.

Reads `<results>/<engine>/<set>[@wtailN].jsonl` from one or more result directories, keeps the
utterances every listed run has, and prints one row per run and one column per set. `cut` re-scores
each hypothesis after `cut_repeats`: what a decoder that stops at a repeated block would give.

    cd $WORK/harness-<n>
    ../venv/bin/python -m asrbench.tail_table --work .. --engine whisper-turbo \\
        results-46-base: results-46: results-46:@wtail300 fleurs-en fleurs-es ami-ihm fleurs-ja
"""
import argparse
import json
import re
from pathlib import Path

from .cer import cer_stats
from .score import wer_stats

CJK = {"ja", "zh", "ko"}


def cut_repeats(text: str, lang: str, min_block: int | None = None) -> str:
    """Cut a block repeated to the end of the text ("A B C. A B C. A B" -> "A B C.").

    Units are words, or characters for Chinese and Japanese. The block is at least `min_block`
    units (3 words, 4 characters) and appears at least twice in a row, running to the end; words
    compare without case and punctuation.
    """
    units = list(text) if lang in CJK else text.split()
    keys = [re.sub(r"\W", "", u.lower()) for u in units]  # "From the" repeats "from the,"
    sep = "" if lang in CJK else " "
    if min_block is None:
        min_block = 4 if lang in CJK else 3
    n = len(units)
    for start in range(n):
        for p in range(min_block, (n - start) // 2 + 1):
            block = keys[start:start + p]
            if all(keys[i] == block[(i - start) % p] for i in range(start + p, n)):
                return sep.join(units[:start + p])
    return text


def read(work: Path, results: str, engine: str, set_name: str, tag: str) -> dict[str, dict]:
    path = work / results / engine / f"{set_name}{tag}.jsonl"
    if not path.exists():
        return {}
    return {r["id"]: r for r in map(json.loads, path.open())}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--work", type=Path, required=True)
    ap.add_argument("--engine", default="whisper-turbo")
    ap.add_argument("runs", nargs="+", help="results-dir:tag (tag '' for the default file, e.g. '@wtail300'), "
                                           "then set names")
    args = ap.parse_args()
    runs = [r for r in args.runs if ":" in r]
    sets = [r for r in args.runs if ":" not in r]

    header = "| run | " + " | ".join(sets) + " |"
    print("Error rate % (WER, CER for ja/zh) / with repeats cut; runaway outputs; decode s (sum)\n")
    print(header)
    print("|---|" + "---|" * len(sets))
    data = {(run, s): read(args.work, run.split(":", 1)[0], args.engine, s, run.split(":", 1)[1])
            for run in runs for s in sets}
    for run in runs:
        cells = []
        for s in sets:
            common = set.intersection(*(set(data[(r, s)]) for r in runs))
            rows = [data[(run, s)][i] for i in sorted(common)]
            if not rows:
                cells.append("–")
                continue
            lang = rows[0]["lang"]
            cut = [dict(r, hyp=cut_repeats(r["hyp"], lang)) for r in rows]
            if lang in CJK:
                a, b = cer_stats(rows), cer_stats(cut)
                rate, rate_cut = a["cer"], b["cer"]
            else:
                a, b = wer_stats(rows, True), wer_stats(cut, True)
                rate, rate_cut = a["wer"], b["wer"]
            secs = sum(r["decode_s"] for r in rows)
            cells.append(f"{rate:.1f} / {rate_cut:.1f}; {a['runaway']}; {secs:.0f} (n={len(rows)})")
        print(f"| {run} | " + " | ".join(cells) + " |")


if __name__ == "__main__":
    main()
