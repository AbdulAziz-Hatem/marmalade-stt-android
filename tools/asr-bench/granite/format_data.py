"""Training pairs for a Granite formatter (#33): Granite-style text in, word edits out.

Granite writes what an ASR scorer's normaliser writes: lowercase, no
punctuation, contractions and possessives expanded ("bjorn is birthday", "i
will"). That is Whisper's EnglishTextNormalizer almost exactly (#26), so a
cased, punctuated sentence gives a training pair for free:

    input   EnglishTextNormalizer(sentence), split into words
    labels  per input word, read off the sentence:
            case    0 lower, 1 Capitalised, 2 UPPER
            punct   the mark after the word: 0 none, 1 ".", 2 ",", 3 "?", 4 "!", 5 ":"
            merge   1 if the word is an expanded contraction to join back onto the
                    word before it ("bjorn is" -> "bjorn's", "i will" -> "i'll")

Sentences whose normalisation can't be matched word for word (spelled-out
numbers the normaliser turns into digits, "'d been" -> "had been", ...) are
dropped rather than guessed. `apply` turns predicted labels back into text;
the Kotlin post-pass must do exactly what it does.

    python granite/format_data.py build --tokenizer tokenizer.json --out data.pt sentences.txt [...]
"""

import argparse
import random
import re
import sys
from pathlib import Path

from whisper_normalizer.english import EnglishTextNormalizer

EN = EnglishTextNormalizer()

CASE_LOWER, CASE_CAP, CASE_UPPER = 0, 1, 2
PUNCT = ["", ".", ",", "?", "!", ":"]
# Marks folded into the nearest class above. Quotes, brackets and dashes aren't restored.
PUNCT_ALIASES = {";": ",", "…": "."}

# The normaliser's contraction expansions (whisper_normalizer/english.py), reversed:
# the expanded word -> the suffix it came from.
SUFFIX = {"not": "n't", "are": "'re", "is": "'s", "has": "'s", "would": "'d", "had": "'d",
          "will": "'ll", "have": "'ve", "am": "'m", "us": "'s"}
IRREGULAR = {("will", "not"): "won't", ("can", "not"): "can't"}
CONTRACTION = re.compile(r"(?i)(n't|'re|'s|'d|'ll|'ve|'m|'t)$")

_EDGE = re.compile(r"^[\W_]+|[\W_]+$")
_TRAIL = re.compile(r"[.,?!:;…]+(?=[\"'”’)\]]*$)")


def normalise(text: str) -> str:
    return EN(text).strip()


def _case(part: str) -> int:
    letters = [c for c in part if c.isalpha()]
    if not letters:
        return CASE_LOWER
    if len(letters) > 1 and all(c.isupper() for c in letters):
        return CASE_UPPER
    return CASE_CAP if letters[0].isupper() else CASE_LOWER


def _punct(word: str) -> int:
    m = _TRAIL.search(word)
    if not m:
        return 0
    mark = m.group(0)
    # "?!" -> "?", "..." -> "."; the first mark decides.
    first = PUNCT_ALIASES.get(mark[0], mark[0])
    return PUNCT.index(first) if first in PUNCT else 0


def labels(sentence: str) -> tuple[list[str], list[int], list[int], list[int]] | None:
    """(words, case, punct, merge) for one cased, punctuated sentence, or None if it can't be aligned."""
    sentence = sentence.replace("’", "'")
    words, case, punct, merge = [], [], [], []
    tokens = sentence.split()
    for n, w in enumerate(tokens):
        pieces = normalise(w).split()
        if not pieces:
            if _punct(w) and punct:  # a detached mark (" - ", " ?"): attach it to the word before
                punct[-1] = punct[-1] or _punct(w)
            continue
        core = _EDGE.sub("", w)
        contraction = CONTRACTION.search(core)
        joined = contraction is not None and len(pieces) >= 2 and pieces[-1] in SUFFIX
        stem = core[: contraction.start()] if joined else core
        parts = [p for p in re.split(r"[^\w]+|_", stem) if p]
        body = pieces[:-1] if joined else pieces
        if len(parts) == len(body):
            cases = [_case(p) for p in parts]
        else:  # "Dr." -> "doctor", "$1,249" -> "$1249": case of the whole word on its first piece
            cases = [_case(stem)] + [CASE_LOWER] * (len(body) - 1)
        for i, p in enumerate(body):
            words.append(p)
            case.append(cases[i])
            punct.append(0)
            merge.append(0)
        if joined:
            words.append(pieces[-1])
            case.append(CASE_LOWER)
            punct.append(0)
            merge.append(1)
        mark = _punct(w)
        # "Dr." -> "doctor": an abbreviation's full stop isn't a sentence end.
        if mark == 1 and not joined and " ".join(body) != stem.lower() and n < len(tokens) - 1:
            mark = 0
        punct[-1] = mark
    if not words or " ".join(words) != normalise(sentence):
        return None
    return words, case, punct, merge


def apply(words: list[str], case: list[int], punct: list[int], merge: list[int]) -> str:
    """Formatted text from per-word labels (the inverse of `labels`, up to what normalising lost)."""
    out: list[str] = []
    for i, w in enumerate(words):
        if merge[i] and out and w in SUFFIX:
            prev = out[-1]
            key = (prev.lower(), w)
            if key in IRREGULAR:
                fixed = IRREGULAR[key]
                out[-1] = fixed[0].upper() + fixed[1:] if prev[:1].isupper() else fixed
            else:
                out[-1] = prev + SUFFIX[w]
        else:
            if case[i] == CASE_UPPER:
                w = w.upper()
            elif case[i] == CASE_CAP:
                w = w[:1].upper() + w[1:]
            out.append(w)
        if punct[i]:
            out[-1] += PUNCT[punct[i]]
    return " ".join(out)


def encode_words(tokenizer, words: list[str]) -> tuple[list[int], list[int]]:
    """Token ids for the words (each encoded as " word") and the index of each word's first token."""
    ids, starts = [], []
    for w in words:
        starts.append(len(ids))
        ids += tokenizer.encode(" " + w, add_special_tokens=False).ids
    return ids, starts


def build(files: list[Path], tokenizer_json: Path, out: Path, max_tokens: int, seed: int) -> None:
    import torch
    from tokenizers import Tokenizer

    tok = Tokenizer.from_file(str(tokenizer_json))
    rng = random.Random(seed)
    examples, dropped = [], 0
    for f in files:
        for line in Path(f).read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line:
                continue
            lab = labels(line)
            if lab is None:
                dropped += 1
                continue
            examples.append(lab)
    rng.shuffle(examples)
    # Join 1-3 sentences so the model sees sentence boundaries inside an utterance too.
    # Stored flat: example i's tokens are ids[tok_off[i]:tok_off[i+1]], its words'
    # first-token indices (relative) and labels are at [word_off[i]:word_off[i+1]].
    ids, starts, case, punct, merge = [], [], [], [], []
    tok_off, word_off = [0], [0]
    i = 0
    while i < len(examples):
        k = rng.choice((1, 1, 2, 3))
        group = examples[i: i + k]
        i += k
        words = [w for g in group for w in g[0]]
        e_ids, e_starts = encode_words(tok, words)
        if len(e_ids) > max_tokens:
            continue
        ids += e_ids
        starts += e_starts
        for g in group:
            case += g[1]
            punct += g[2]
            merge += g[3]
        tok_off.append(len(ids))
        word_off.append(len(starts))
    torch.save({
        "ids": torch.tensor(ids, dtype=torch.int16), "starts": torch.tensor(starts, dtype=torch.int16),
        "case": torch.tensor(case, dtype=torch.int8), "punct": torch.tensor(punct, dtype=torch.int8),
        "merge": torch.tensor(merge, dtype=torch.int8),
        "tok_off": torch.tensor(tok_off, dtype=torch.int64), "word_off": torch.tensor(word_off, dtype=torch.int64),
    }, out)
    print(f"{len(examples)} sentences ({dropped} dropped), {len(tok_off) - 1} examples, "
          f"{len(ids)} tokens -> {out}", file=sys.stderr)


def main() -> None:
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    b = sub.add_parser("build")
    b.add_argument("files", nargs="+", type=Path)
    b.add_argument("--tokenizer", type=Path, required=True)
    b.add_argument("--out", type=Path, required=True)
    b.add_argument("--max-tokens", type=int, default=192)
    b.add_argument("--seed", type=int, default=33)
    args = ap.parse_args()
    build(args.files, args.tokenizer, args.out, args.max_tokens, args.seed)


if __name__ == "__main__":
    main()
