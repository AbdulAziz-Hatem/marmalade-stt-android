"""#57: the stock phrases each engine writes for noise, per language hint.

    python -m asrbench.phantoms --work $WORK --results results-57
    python -m asrbench.phantoms --work $WORK --mine results-55/whisper-turbo results-52/parakeet-ja-ctc

The default reads run_57.sh's --direct runs of the noise sets and prints one
table row per engine, language hint and text: on how many clips it came, with
and without the AGC stand-in, and the clips' levels (dBFS before any gain, as
NearSilence judges them; the app calls an utterance below -50 near-silent).

--mine reads ordinary runs of speech sets (one engine's results directory
each) and prints the texts that recur as a whole first or last Final of a
clip with more than one: what an engine writes for the lead-in or the pause
at the end. Runs from before #57 have no per-Final texts; for Chinese,
Japanese and Korean the joined hypothesis is split on spaces instead, and
other languages are skipped.
"""
import argparse
import collections
import json
import re
from pathlib import Path

NOISE_SETS = ("noise", "noise-short")
CJK = {"ja", "zh", "ko"}
NEAR_SILENT_DB = -50.0


def _tags(name: str) -> tuple[str, list[str]]:
    """'noise-short@direct@noagc@hint-ja' -> ('noise-short', ['direct', 'noagc', 'hint-ja'])."""
    base, *tags = name.split("@")
    return base, tags


def noise_table(results: Path) -> list[str]:
    seen = collections.defaultdict(lambda: {"agc": 0, "noagc": 0, "levels": [], "clips": []})
    totals = collections.Counter()  # (engine, hint, agc) -> clips decoded
    for f in sorted(results.glob("*/*.jsonl")):
        base, tags = _tags(f.stem)
        if base not in NOISE_SETS or "direct" not in tags:
            continue
        hint = next((t[5:] for t in tags if t.startswith("hint-")), "auto" if "auto" in tags else "-")
        variant = "".join(f"@{t}" for t in tags if t not in ("direct", "noagc", "auto") and not t.startswith(("hint-", "wtail")))
        engine = f.parent.name + variant
        agc = "noagc" if "noagc" in tags else "agc"
        for line in f.read_text().splitlines():
            r = json.loads(line)
            totals[engine, hint, agc] += 1
            for i, text in enumerate(r.get("finals") or ([r["hyp"]] if r["hyp"] else [])):
                s = seen[engine, hint, text]
                s[agc] += 1
                s["clips"].append(r["id"])
                levels = r.get("rms_db") or []
                if i < len(levels) and levels[i] is not None:
                    s["levels"].append(levels[i])
    out = ["| Engine | Hint | Text | Clips with AGC | Clips without | Levels (dBFS) | Near-silent |",
           "|---|---|---|---|---|---|---|"]
    for (engine, hint, text), s in sorted(seen.items(), key=lambda kv: (kv[0][0], kv[0][1], -kv[1]["agc"] - kv[1]["noagc"])):
        lv = s["levels"]
        span = f"{min(lv):.0f} … {max(lv):.0f}" if lv else ""
        quiet = f"{sum(v < NEAR_SILENT_DB for v in lv)}/{len(lv)}" if lv else ""
        out.append(f"| {engine} | {hint} | {text} | {s['agc']}/{totals[engine, hint, 'agc']} | "
                   f"{s['noagc']}/{totals[engine, hint, 'noagc']} | {span} | {quiet} |")
    return out


def _edges(r: dict) -> list[tuple[str, float | None]]:
    """The first and last Final of a clip with more than one, with their levels."""
    finals = r.get("finals")
    levels = r.get("rms_db") or []
    if not (isinstance(finals, list) and all(isinstance(t, str) for t in finals)):  # before #57
        if r.get("lang") not in CJK or r.get("segments", 0) < 2:
            return []
        finals = r["hyp"].split()
    if len(finals) < 2:
        return []
    lv = lambda i: levels[i] if i < len(levels) else None
    return [(finals[0], lv(0)), (finals[-1], lv(len(finals) - 1))]


_NOT_WORDY = re.compile(r"[\W_]+")


def mine(dirs: list[Path], min_count: int) -> list[str]:
    out = ["| Engine | Set | Text | Clips | Levels (dBFS) | Text in the reference |", "|---|---|---|---|---|---|"]
    for d in dirs:
        for f in sorted(d.glob("*.jsonl")):
            base, tags = _tags(f.stem)
            if base in NOISE_SETS or "direct" in tags:
                continue
            rows = [json.loads(line) for line in f.read_text().splitlines() if line.strip()]
            counts = collections.Counter()
            levels = collections.defaultdict(list)
            in_ref = collections.Counter()
            for r in rows:
                for text, level in set(_edges(r)):
                    counts[text] += 1
                    if level is not None:
                        levels[text].append(level)
                    key = _NOT_WORDY.sub("", text.lower())
                    in_ref[text] += bool(key) and key in _NOT_WORDY.sub("", r["ref"].lower())
            for text, n in counts.most_common():
                if n < min_count:
                    break
                lv = levels[text]
                span = f"{min(lv):.0f} … {max(lv):.0f}" if lv else ""
                out.append(f"| {d.name} | {f.stem} | {text} | {n}/{len(rows)} | {span} | {in_ref[text]} |")
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--work", type=Path, required=True)
    ap.add_argument("--results", default="results-57", help="run_57.sh's results directory under $WORK")
    ap.add_argument("--mine", nargs="+", help="engine results directories under $WORK to mine instead")
    ap.add_argument("--min-count", type=int, default=2, help="--mine: texts seen on fewer clips are left out")
    args = ap.parse_args()
    if args.mine:
        lines = mine([args.work / d for d in args.mine], args.min_count)
    else:
        lines = noise_table(args.work / args.results)
    print("\n".join(lines))


if __name__ == "__main__":
    main()
