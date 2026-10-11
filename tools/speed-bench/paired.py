#!/usr/bin/env python3
"""Compare engines with a baseline clip by clip, inside each run (#55).

A Pixel run's turns differ by ~20 % on their own (thermal state, battery, what
else the phone does), and within a turn later clips decode slower than earlier
ones. Every turn of a run decodes the clips in the same order, so the fair
comparison is per clip, against the baseline's turn in the same run: the
geometric mean of the time ratios per length group, then over runs.
Undisturbed turns only.

Usage: paired.py results/*.json [--base whisper-turbo]
"""
import argparse
import json
import math
import statistics

from summarize import GROUPS, first_decodes


def decodes(r):
    return {d["clip"]: d["decodeMs"] for d in first_decodes(r)}


def finals(r):
    return {s["clip"]: s["endToFinalMs"] for s in (r.get("sessions") or {}).get("runs", []) if s.get("endToFinalMs")}


def geomean(xs):
    return math.exp(statistics.mean(math.log(x) for x in xs))


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("files", nargs="+")
    ap.add_argument("--base", default="whisper-turbo")
    args = ap.parse_args()

    runs = [[r for r in json.load(open(p))["results"] if not r.get("disturbed")] for p in args.files]
    engines = sorted({r["engine"] for rs in runs for r in rs} - {args.base})
    labels = " / ".join(g.removeprefix("ls").lstrip("0") for g in GROUPS)
    print(f"| Engine | Runs | Decode × {args.base} ({labels} s) | End of speech → Final × {args.base} |")
    print("|---|---|---|---|")
    for e in engines:
        pairs = []
        for rs in runs:
            a = next((r for r in rs if r["engine"] == e), None)
            b = next((r for r in rs if r["engine"] == args.base), None)
            if a and b:
                pairs.append((a, b))
        if not pairs:
            continue

        def column(get):
            out = []
            for g in GROUPS:
                per_run = []
                for a, b in pairs:
                    x, y = get(a), get(b)
                    ratios = [x[c] / y[c] for c in x if c.startswith(g) and c in y]
                    if ratios:
                        per_run.append(geomean(ratios))
                if not per_run:
                    out.append("–")
                elif len(per_run) == 1:
                    out.append(f"{per_run[0]:.2f}")
                else:
                    out.append(f"{geomean(per_run):.2f} ({min(per_run):.2f}–{max(per_run):.2f})")
            return " / ".join(out)

        print(f"| {e} | {len(pairs)} | {column(decodes)} | {column(finals)} |")


if __name__ == "__main__":
    main()
