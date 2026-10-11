"""Japanese punctuation for Parakeet-ja (#60): what the user gets, and rule-based fixes.

Parakeet TDT_CTC 0.6B ja punctuates little (#52: F1 26 on FLEURS-ja against
turbo's 62). `decode` runs it through the app's session (as run.py does) and
keeps each Final's tokens and their start times; `score` then applies
candidate rules offline, so the thresholds can be swept without decoding again:

  raw     the model's text
  end     + 。 at the end of each Final that has no mark (？ after か with --question):
          what the app does (`:textproc`, TextPipeline.endSentence)
  gapN    end, plus 、 where the next token starts N s or more after the one before
          (the speaker paused), where the model wrote no mark. Not adopted: it
          lowered F1 at every N (token starts come in 80 ms steps)

Finals are joined without spaces, as the app's textproc joins Japanese (#60).
CER ignores punctuation, so only the punctuation scores move. Results:
docs/benchmarks/2026-10-japanese.md, "Adoption (#60)".

    cd $WORK/harness-60 && python -m asrbench.ja_punct decode --work $WORK fleurs-ja kokoro-speech-ja homophones-ja
    python -m asrbench.ja_punct score --work $WORK [--turbo results-52]
"""

import argparse
import json
from pathlib import Path

import numpy as np

from . import cer
from . import engines as E
from .backends import OfflineTranscriber, silero_vad
from .run import load_audio, read_jsonl
from .session import OFFLINE_PREROLL_SAMPLES, agc, max_decode_samples, offline_session

ENGINE = "parakeet-ja-ctc"
RESULTS = "results-60"
MARKS = set("、。，．,.？！?!…")


class TokenTranscriber(OfflineTranscriber):
    """OfflineTranscriber that also keeps every decode's tokens and their start times."""

    def __init__(self, *a, **kw):
        super().__init__(*a, **kw)
        self.decodes: list[dict] = []

    def transcribe(self, samples):
        stream = self.recognizer.create_stream()
        stream.accept_waveform(16000, np.concatenate([samples, self.tail]) if len(self.tail) else samples)
        self.recognizer.decode_stream(stream)
        r = stream.result
        self.decodes.append({"text": r.text, "tokens": list(r.tokens), "ts": [round(t, 3) for t in r.timestamps]})
        return r.text


def decode(args) -> None:
    engine = E.BY_ID[ENGINE]
    rec = TokenTranscriber(engine, args.work / "models" / ENGINE, "ja", num_threads=args.threads)
    vad = silero_vad(args.work / "silero_vad.onnx")
    for set_name in args.sets:
        manifest = read_jsonl(args.work / "data" / set_name / "manifest.jsonl")
        out = args.work / RESULTS / ENGINE / f"{set_name}.tokens.jsonl"
        out.parent.mkdir(parents=True, exist_ok=True)
        done = {r["id"] for r in read_jsonl(out)}
        with out.open("a") as f:
            for utt in manifest:
                if utt["id"] in done:
                    continue
                rec.decodes.clear()
                res = offline_session(agc(load_audio(utt["audio"])), rec, vad,
                                      max_decode=max_decode_samples(engine.family, "ja", OFFLINE_PREROLL_SAMPLES))
                finals = [d for d in rec.decodes if d["text"].strip()]
                assert [d["text"].strip() for d in finals] == res.finals, utt["id"]
                f.write(json.dumps({"id": utt["id"], "lang": "ja", "ref": utt["text"], "finals": finals,
                                    "near_silent": res.near_silent}, ensure_ascii=False) + "\n")
                f.flush()
        print(f"done {set_name}", flush=True)


def token_text(token: str) -> str:
    """A token as it appears in the text: sentencepiece's ▁ is a space."""
    return token.replace("▁", " ")


def punctuate(tokens: list[str], ts: list[float], gap: float | None, question: bool) -> str:
    """The end rule (textproc's endSentence, for a Final ending in a letter or digit) plus the gap rule."""
    out = ""
    for i, tok in enumerate(tokens):
        piece = token_text(tok)
        if (gap is not None and i > 0 and ts[i] - ts[i - 1] >= gap and out.strip()
                and out.rstrip()[-1] not in MARKS and piece.strip() and piece.strip()[0] not in MARKS):
            out = out.rstrip() + "、"
            piece = piece.lstrip()
        out += piece
    return end_sentence(out.strip(), question)


def end_sentence(text: str, question: bool = False) -> str:
    """textproc's endSentence: 。 after a final letter or digit (？ after か with `question`)."""
    if text and text[-1].isalnum():
        text += "？" if question and text.endswith("か") else "。"
    return text


def score(args) -> None:
    sets = ["fleurs-ja", "kokoro-speech-ja", "homophones-ja"]
    variants = {"raw": None, "end": "end", **{f"gap{g:g}": g for g in args.gaps}}
    lines = ["| Variant | " + " | ".join(f"{s} F1 (comma / end)" for s in sets) + " |",
             "|---|" + "---|" * len(sets)]
    table = {}
    for name, v in variants.items():
        row = []
        for s in sets:
            rows = read_jsonl(args.work / RESULTS / ENGINE / f"{s}.tokens.jsonl")
            scored = []
            for r in rows:
                if v is None:
                    hyp = "".join(d["text"].strip() for d in r["finals"])
                else:
                    gap = None if v == "end" else v
                    hyp = "".join(punctuate(d["tokens"], d["ts"], gap, args.question) for d in r["finals"])
                scored.append({"id": r["id"], "lang": "ja", "ref": r["ref"], "hyp": hyp})
            p = cer.punct_stats(scored)
            c = cer.cer_stats(scored)
            table.setdefault(name, {})[s] = {"punct": p, "cer": c["cer"]}
            row.append(f"{p['f1']:.0f} ({p['comma_f1'] or 0:.0f} / {p['end_f1'] or 0:.0f}), "
                       f"{p['marks_per_100_hyp']:.1f}/100 (ref {p['marks_per_100_ref']:.1f}), CER {c['cer']:.1f}")
        lines.append(f"| {name} | " + " | ".join(row) + " |")
    if args.turbo:
        # run.py joins Finals with a space and turbo's Japanese has none of its own, so splitting
        # at spaces gives its Finals back.
        for name, fix in (("whisper-turbo (#52)", None), ("whisper-turbo + end", end_sentence)):
            row = []
            for s in sets:
                rows = read_jsonl(args.work / args.turbo / "whisper-turbo" / f"{s}.jsonl")
                if fix:
                    rows = [{**r, "hyp": "".join(fix(f) for f in r["hyp"].split(" "))} for r in rows]
                p = cer.punct_stats(rows)
                c = cer.cer_stats(rows)
                table.setdefault(name, {})[s] = {"punct": p, "cer": c["cer"]}
                row.append(f"{p['f1']:.0f} ({p['comma_f1'] or 0:.0f} / {p['end_f1'] or 0:.0f}), "
                           f"{p['marks_per_100_hyp']:.1f}/100 (ref {p['marks_per_100_ref']:.1f}), CER {c['cer']:.1f}")
            lines.append(f"| {name} | " + " | ".join(row) + " |")
    print("\n".join(lines))
    (args.work / RESULTS / "ja_punct.json").write_text(json.dumps(table, indent=1, ensure_ascii=False))


def main() -> None:
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    d = sub.add_parser("decode")
    d.add_argument("--work", type=Path, required=True)
    d.add_argument("--threads", type=int, default=E.NUM_THREADS)
    d.add_argument("sets", nargs="+")
    s = sub.add_parser("score")
    s.add_argument("--work", type=Path, required=True)
    s.add_argument("--gaps", type=float, nargs="*", default=[0.3, 0.4, 0.5, 0.6, 0.8])
    s.add_argument("--question", action="store_true", help="？ instead of 。 after a final か")
    s.add_argument("--turbo", help="results dir holding whisper-turbo's runs, for comparison")
    args = ap.parse_args()
    decode(args) if args.cmd == "decode" else score(args)


if __name__ == "__main__":
    main()
