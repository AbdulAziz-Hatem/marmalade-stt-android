"""Character error rate and punctuation scores for Japanese, Chinese and Korean (#29).

CER is corpus-level over characters, after `normalise`: Unicode NFKC
(full-width letters and digits to ASCII, half-width kana to full-width),
lower case, Traditional to Simplified Chinese for zh (OpenCC's t2s tables,
via the pure-Python `opencc-python-reimplemented`), then every punctuation
mark, symbol and space removed. Korean is scored on syllables with the spaces
removed, the usual Korean CER.

Numbers are not normalised: "2008年" and "二零零八年" differ in every
character, and the test sets disagree (FLEURS writes digits, AISHELL-1
spells numbers out). `common_subset` picks the utterances where neither the
reference nor any engine's hypothesis has a digit, so a CER on that subset
compares recognition alone, on the same utterances for every engine.

Punctuation (`punct_stats`): both texts are reduced to their characters, each
with the mark that follows it, if any: comma (、 ,), stop (。 .), question,
exclamation. The characters are aligned (edit distance); a mark after an
aligned character pair counts as a hit when both sides have the same class,
and every other mark on either side is a miss (reference) or a false alarm
(hypothesis). F1 over all classes, over commas, and over sentence ends
(stop, question and exclamation as one class). Only meaningful where the
references are punctuated (FLEURS; AISHELL-1 has none).
"""

import unicodedata
from functools import lru_cache

import jiwer
import numpy as np

CJK_LANGS = ("ja", "zh", "ko", "yue")

COMMA, STOP, QUESTION, EXCLAIM = ",", ".", "?", "!"
_CLASS = {"、": COMMA, ",": COMMA, "。": STOP, ".": STOP, "?": QUESTION, "!": EXCLAIM}
_END = {STOP, QUESTION, EXCLAIM}


@lru_cache(maxsize=1)
def _t2s():
    import opencc  # opencc-python-reimplemented

    return opencc.OpenCC("t2s")


def _prepare(text: str, lang: str) -> str:
    text = unicodedata.normalize("NFKC", text).lower()
    if lang in ("zh", "yue"):
        text = _t2s().convert(text)
    return text


def _dropped(c: str) -> bool:
    return c.isspace() or unicodedata.category(c)[0] in "PSZC"


def normalise(text: str, lang: str) -> str:
    """The characters CER is computed on (no spaces, punctuation or symbols)."""
    return "".join(c for c in _prepare(text, lang) if not _dropped(c))


def chars_with_marks(text: str, lang: str) -> tuple[str, list[str | None]]:
    """The normalised characters, and the punctuation class that follows each one."""
    chars, marks = [], []
    for c in _prepare(text, lang):
        if not _dropped(c):
            chars.append(c)
            marks.append(None)
        elif c in _CLASS and marks:
            marks[-1] = _CLASS[c]
    return "".join(chars), marks


def has_digit(text: str) -> bool:
    return any(c.isdigit() for c in unicodedata.normalize("NFKC", text))


def _align(refs: list[str], hyps: list[str]):
    # Characters as words, so jiwer's word alignment is a character alignment.
    return jiwer.process_words([" ".join(r) for r in refs], [" ".join(h) if h else "" for h in hyps])


def _chunk_errors(c) -> int:
    if c.type in ("substitute", "delete"):
        return c.ref_end_idx - c.ref_start_idx
    return c.hyp_end_idx - c.hyp_start_idx if c.type == "insert" else 0


def cer_stats(rows: list[dict], ids: set[str] | None = None) -> dict | None:
    """Corpus CER (%) with a 95 % bootstrap half-width and the failure counters of score.py."""
    refs, hyps = [], []
    for r in rows:
        if ids is not None and r["id"] not in ids:
            continue
        ref = normalise(r["ref"], r["lang"])
        if ref:
            refs.append(ref)
            hyps.append(normalise(r["hyp"], r["lang"]))
    if not refs:
        return None
    out = _align(refs, hyps)
    errs = np.array([sum(map(_chunk_errors, a)) for a in out.alignments], float)
    chars = np.array([len(r) for r in refs], float)
    rng = np.random.default_rng(14)
    boot = [errs[i].sum() / chars[i].sum() for i in (rng.integers(0, len(refs), len(refs)) for _ in range(1000))]
    lo, hi = np.percentile(boot, [2.5, 97.5])
    return {
        "n": len(refs), "cer": 100 * errs.sum() / chars.sum(), "ci95": 100 * (hi - lo) / 2,
        "sub": out.substitutions, "del": out.deletions, "ins": out.insertions, "ref_chars": int(chars.sum()),
        "empty": sum(1 for h in hyps if not h),
        "runaway": sum(1 for r, h in zip(refs, hyps) if len(h) > 2 * len(r) + 8),
        "head_drop": sum(1 for a in out.alignments if a and a[0].type == "delete"),
        "tail_drop": sum(1 for a in out.alignments if a and a[-1].type == "delete"),
        "digit_hyps": sum(1 for r in rows if (ids is None or r["id"] in ids) and has_digit(r["hyp"])),
    }


def common_subset(runs: list[list[dict]]) -> set[str]:
    """Utterance ids where no reference and no hypothesis in any of `runs` contains a digit."""
    ids = None
    for rows in runs:
        ok = {r["id"] for r in rows if not has_digit(r["ref"]) and not has_digit(r["hyp"])}
        ids = ok if ids is None else ids & ok
    return ids or set()


def _f1(tp: int, fp: int, fn: int) -> float | None:
    if tp + fp + fn == 0:
        return None
    return 100 * 2 * tp / (2 * tp + fp + fn)


def punct_stats(rows: list[dict]) -> dict | None:
    """Punctuation F1 (%) against punctuated references; None if the references have no marks."""
    counts = {k: [0, 0, 0] for k in ("all", "comma", "end")}  # tp, fp, fn

    def tally(ref_mark, hyp_mark):
        for key, cls in (("all", lambda m: m), ("comma", lambda m: m if m == COMMA else None),
                         ("end", lambda m: "end" if m in _END else None)):
            r, h = (cls(ref_mark) if ref_mark else None), (cls(hyp_mark) if hyp_mark else None)
            c = counts[key]
            if r and h and r == h:
                c[0] += 1
            else:
                c[1] += h is not None
                c[2] += r is not None

    ref_marks = hyp_marks = ref_chars = ends_ref = ends_hyp = 0
    items = []
    for row in rows:
        ref, rm = chars_with_marks(row["ref"], row["lang"])
        hyp, hm = chars_with_marks(row["hyp"], row["lang"])
        if ref:
            items.append((ref, rm, hyp, hm))
    if not items:
        return None
    out = _align([i[0] for i in items], [i[2] for i in items])
    for (ref, rm, hyp, hm), chunks in zip(items, out.alignments):
        ref_marks += sum(m is not None for m in rm)
        hyp_marks += sum(m is not None for m in hm)
        ref_chars += len(ref)
        ends_ref += bool(rm) and rm[-1] in _END
        ends_hyp += bool(hm) and hm[-1] in _END
        for c in chunks:
            if c.type in ("equal", "substitute"):
                for i, j in zip(range(c.ref_start_idx, c.ref_end_idx), range(c.hyp_start_idx, c.hyp_end_idx)):
                    tally(rm[i], hm[j])
            elif c.type == "delete":
                for i in range(c.ref_start_idx, c.ref_end_idx):
                    tally(rm[i], None)
            elif c.type == "insert":
                for j in range(c.hyp_start_idx, c.hyp_end_idx):
                    tally(None, hm[j])
    if ref_marks == 0:
        return None
    return {
        "n": len(items),
        "f1": _f1(*counts["all"]), "comma_f1": _f1(*counts["comma"]), "end_f1": _f1(*counts["end"]),
        "marks_per_100_ref": 100 * ref_marks / ref_chars, "marks_per_100_hyp": 100 * hyp_marks / ref_chars,
        "ends_ref": ends_ref, "ends_hyp": ends_hyp,
        "counts": counts,
    }
