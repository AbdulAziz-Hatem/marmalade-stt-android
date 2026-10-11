"""Kanji choice on the Japanese homophone set (#52).

`homophones-ja.tsv` has sentences whose target words share a reading with
other words (橋/箸/端, 化学/科学, 機会/機械 …), so only the context says
which kanji is right. CER counts a wrong kanji as one character among many;
this scores each target on its own.

Each target is located in the hypothesis by aligning the normalised
reference and hypothesis characters (cer.py's alignment). The hypothesis
characters aligned to the target's span are then:
  correct    the target as written in the TSV
  homophone  one of the TSV's competitors (same reading, wrong word)
  kana       the reading in hiragana or katakana (heard right, no kanji chosen)
  other      anything else: misheard, dropped, or a form the TSV doesn't list
Insertions just outside the span are tried too, so 柿 → 牡蠣 still counts as
a homophone.

    python -m asrbench.homophones --work $WORK --results results-52 [--set homophones-ja]
"""

import argparse
import csv
from collections import Counter
from pathlib import Path

from . import cer

SET = "homophones-ja"
TSV = Path(__file__).resolve().parent.parent / "homophones-ja.tsv"
OUTCOMES = ("correct", "homophone", "kana", "other")


def parse_targets(spec: str) -> list[dict]:
    """"橋:はし:箸,端|端:はし:橋,箸" -> [{"word", "reading", "competitors"}, ...] in sentence order."""
    out = []
    for t in spec.split("|"):
        word, reading, comp = t.split(":")
        out.append({"word": word, "reading": reading, "competitors": comp.split(",") if comp else []})
    return out


def _hira(s: str) -> str:
    return "".join(chr(ord(c) - 0x60) if "ァ" <= c <= "ヶ" else c for c in s)


def _spans(ref: str, targets: list[dict]) -> list[tuple[int, int]]:
    spans, pos = [], 0
    for t in targets:
        i = ref.index(cer.normalise(t["word"], "ja"), pos)
        spans.append((i, i + len(cer.normalise(t["word"], "ja"))))
        pos = spans[-1][1]
    return spans


def aligned_spans(ref: str, hyp: str, spans: list[tuple[int, int]]) -> list[tuple[str, str, str]]:
    """For each ref span: (insertions just before, aligned hypothesis text, insertions just after)."""
    chunks = cer._align([ref], [hyp]).alignments[0] if hyp else []
    out = []
    for a, b in spans:
        before = core = after = ""
        if not hyp:
            out.append(("", "", ""))
            continue
        for c in chunks:
            h = hyp[c.hyp_start_idx:c.hyp_end_idx]
            if c.type == "insert":
                p = c.ref_start_idx
                if p == a:
                    before += h
                elif p == b:
                    after += h
                elif a < p < b:
                    core += h
            elif c.type in ("equal", "substitute"):
                for i, j in zip(range(c.ref_start_idx, c.ref_end_idx), range(c.hyp_start_idx, c.hyp_end_idx)):
                    if a <= i < b:
                        core += hyp[j]
        out.append((before, core, after))
    return out


def classify(target: dict, before: str, core: str, after: str) -> tuple[str, str]:
    """(outcome, the hypothesis text it was judged on)."""
    word = cer.normalise(target["word"], "ja")
    comps = {cer.normalise(c, "ja") for c in target["competitors"]}
    reading = target["reading"]
    # Try the aligned text alone first, then with the insertions next to it.
    for text in (core, before + core, core + after, before + core + after):
        if text == word:
            return "correct", text
    for text in (core, before + core, core + after, before + core + after):
        if text in comps:
            return "homophone", text
    for text in (core, before + core, core + after, before + core + after):
        if text and _hira(text) == reading:
            return "kana", text
    return "other", before + core + after


def score_row(row: dict, targets: list[dict]) -> list[dict]:
    ref, hyp = cer.normalise(row["ref"], "ja"), cer.normalise(row["hyp"], "ja")
    out = []
    for t, parts in zip(targets, aligned_spans(ref, hyp, _spans(ref, targets))):
        outcome, text = classify(t, *parts)
        out.append({"id": row["id"], "word": t["word"], "outcome": outcome, "wrote": text})
    return out


def sentence_id(clip_id: str) -> str:
    """"h06-jm_kumo" -> "h06": every voice reads the same sentence."""
    return clip_id.split("-", 1)[0]


def score(rows: list[dict], targets: dict[str, str]) -> dict:
    items = [x for r in rows for x in score_row(r, parse_targets(targets[sentence_id(r["id"])]))]
    n = Counter(x["outcome"] for x in items)
    total = len(items)
    return {"n": total, **{k: 100 * n[k] / total for k in OUTCOMES}, "items": items}


def load_targets(tsv: Path = TSV) -> dict[str, str]:
    """Sentence id -> its targets column, read from the TSV (not the manifest), so a corrected
    competitor list scores old results without new audio."""
    with tsv.open(newline="") as f:
        return {r["id"]: r["targets"] for r in csv.DictReader(f, delimiter="\t", quoting=csv.QUOTE_NONE)}


def tables(res: dict, engines: list, summary: dict, set_name: str = SET) -> list[str]:
    """Markdown: the outcome table per engine, then every target some engine missed."""
    have = [e for e in engines if set_name in res[e.id]]
    if not have:
        return []
    targets = load_targets()
    scores = {e.id: score(res[e.id][set_name], targets) for e in have}
    md = ["", f"### Homophone kanji ({set_name}, % of {scores[have[0].id]['n']} targets)", "",
          "correct = the right kanji; homophone = a same-reading word with the wrong kanji; kana = the reading "
          "with no kanji; other = misheard or dropped (asrbench/homophones.py).", "",
          "| Engine | correct | homophone | kana | other |", "|---|---|---|---|---|"]
    for e in have:
        s = scores[e.id]
        summary.setdefault(e.id, {}).setdefault("cjk", {})[f"{set_name}:targets"] = {k: s[k] for k in ("n", *OUTCOMES)}
        md.append(f"| {e.id} | " + " | ".join(f"{s[k]:.1f}" for k in OUTCOMES) + " |")
    # Every target that some engine missed, with what each engine wrote.
    by_key = {}
    for e in have:
        for x in scores[e.id]["items"]:
            by_key.setdefault((x["id"], x["word"]), {})[e.id] = x
    missed = [(k, v) for k, v in by_key.items() if any(x["outcome"] != "correct" for x in v.values())]
    md += ["", f"#### Targets missed by any engine ({len(missed)} of {len(by_key)})", "",
           "| Clip | Target | " + " | ".join(e.id for e in have) + " |", "|---|---|" + "---|" * len(have)]
    for (cid, word), v in missed:
        md.append(f"| {cid} | {word} | " + " | ".join(
            "✓" if (x := v.get(e.id)) and x["outcome"] == "correct" else f"{x['outcome']}: {x['wrote'] or '∅'}" if x else "–"
            for e in have) + " |")
    return md


def main() -> None:
    from .engines import BY_ID
    from .granite import ENGINES as GRANITE_ENGINES
    from .score import load

    ap = argparse.ArgumentParser()
    ap.add_argument("--work", type=Path, required=True)
    ap.add_argument("--results", default="results")
    ap.add_argument("--set", default=SET)
    args = ap.parse_args()
    res = load(args.work / args.results)
    engines = [e for e in [*BY_ID.values(), *GRANITE_ENGINES.values()] if e.id in res]
    print("\n".join(tables(res, engines, {}, args.set)))


if __name__ == "__main__":
    main()
