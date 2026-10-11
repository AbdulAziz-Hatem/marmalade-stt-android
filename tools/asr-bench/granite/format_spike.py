"""Granite formatting spike (#33): restore case, punctuation and apostrophes after Granite.

    python granite/format_spike.py --work $WORK --results results-26 --out $WORK/results-33 \\
        --candidates none ours:$WORK/format-33/tiny llm:Qwen/Qwen2.5-0.5B-Instruct ...

Run from $WORK/harness-<n>. Reads Granite's hypotheses from a harness run (no
re-decoding) and each candidate formats them:

  none            Granite as is
  ours:DIR        our formatter (format_model.py), DIR/formatter.int8.onnx + tokenizer.json
  pcs:DIR         1-800-BAD-CODE/punct_cap_seg_en (ONNX + SentencePiece), files in DIR
  sherpa:DIR      sherpa-onnx's English online punctuation model, files in DIR
  llm:REPO        an instruct LLM rewrite (transformers, greedy), the quality ceiling

Scores:
  raw WER         dictation and FLEURS-en, whose references are cased and punctuated:
                  what a user sees. Parakeet v3's raw rows are the bar.
  norm WER        Whisper-normalised, every English set: a formatter must not damage words.
  text-only raw   the references themselves, normalised Granite-style (format_data.normalise)
                  then formatted: formatting quality without recognition errors.
  apostrophes     reference words with an apostrophe (possessives, contractions) found
                  in the output, of all such words.
  ms/utt          formatting time per utterance on this CPU (--threads).
"""

import argparse
import json
import re
import sys
import time
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import format_data as F  # noqa: E402
from asrbench.score import wer_stats  # noqa: E402

RAW_SETS = ["dictation", "fleurs-en"]
NORM_SETS = ["ls-clean", "ls-other", "fleurs-en", "ami-ihm", "dictation"]


# ---- candidates -------------------------------------------------------------------------------

class Ours:
    def __init__(self, d: Path, threads: int):
        import onnxruntime as ort
        from tokenizers import Tokenizer

        opts = ort.SessionOptions()
        opts.intra_op_num_threads = threads
        opts.inter_op_num_threads = 1
        model = d / "formatter.int8.onnx"
        self.session = ort.InferenceSession(str(model), opts, providers=["CPUExecutionProvider"])
        self.tok = Tokenizer.from_file(str(d / "tokenizer.json"))
        self.size = model.stat().st_size

    def __call__(self, text: str) -> str:
        words = text.split()
        if not words:
            return text
        ids, starts = F.encode_words(self.tok, words)
        logits = self.session.run(["logits"], {"ids": np.asarray([ids], dtype=np.int64)})[0][0][starts]
        return F.apply(words, logits[:, :3].argmax(-1).tolist(), logits[:, 3:9].argmax(-1).tolist(),
                       logits[:, 9:].argmax(-1).tolist())


class Llm:
    PROMPT = ("Restore capitalization and punctuation in this lowercase speech-recognition transcript. "
              "Put back apostrophes where the speaker said a possessive or a contraction: the recognizer "
              "writes \"'s\" as \" is\" (\"the tiger is roar\" means \"the tiger's roar\") and \"i'll\" as "
              "\"i will\". Do not add, remove or change any other words. Reply with the corrected text only.")

    def __init__(self, repo: str, threads: int):
        import torch
        from transformers import AutoModelForCausalLM, AutoTokenizer

        torch.set_num_threads(threads)
        self.torch = torch
        self.tok = AutoTokenizer.from_pretrained(repo)
        self.model = AutoModelForCausalLM.from_pretrained(repo, dtype=torch.float32)
        self.model.eval()
        self.size = sum(p.numel() for p in self.model.parameters()) * 2  # as bf16/fp16 on disk

    def __call__(self, text: str) -> str:
        if not text.strip():
            return text
        msgs = [{"role": "system", "content": self.PROMPT}, {"role": "user", "content": text}]
        ids = self.tok.apply_chat_template(msgs, add_generation_prompt=True, return_tensors="pt", return_dict=True)
        with self.torch.no_grad():
            out = self.model.generate(**ids, max_new_tokens=2 * ids["input_ids"].shape[1] + 16, do_sample=False)
        return self.tok.decode(out[0, ids["input_ids"].shape[1]:], skip_special_tokens=True).strip()


def build(spec: str, threads: int):
    if spec == "none":
        f = lambda t: t  # noqa: E731
        f.size = 0
        return f
    kind, _, arg = spec.partition(":")
    if kind == "ours":
        return Ours(Path(arg), threads)
    if kind == "llm":
        return Llm(arg, threads)
    if kind == "pcs":
        from format_candidates import PunctCapSeg
        return PunctCapSeg(Path(arg), threads)
    if kind == "sherpa":
        from format_candidates import SherpaPunct
        return SherpaPunct(Path(arg), threads)
    raise SystemExit(f"unknown candidate {spec}")


# ---- scoring --------------------------------------------------------------------------------

def apostrophes(rows: list[dict]) -> tuple[int, int]:
    found = total = 0
    for r in rows:
        hyp = {w.strip(".,?!:;\"").lower() for w in r["hyp"].replace("’", "'").split()}
        for w in r["ref"].replace("’", "'").split():
            w = w.strip(".,?!:;\"()").lower()
            if "'" in w:
                total += 1
                found += w in hyp
    return found, total


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--work", type=Path, required=True)
    ap.add_argument("--results", default="results-26")
    ap.add_argument("--engine", default="granite-speech-5-470m-ctc-qwertz92")
    ap.add_argument("--baseline", default="parakeet-tdt-0-6b-v3")
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--candidates", nargs="+", required=True)
    ap.add_argument("--threads", type=int, default=2)
    ap.add_argument("--sets", nargs="+", default=NORM_SETS)
    args = ap.parse_args()

    res = args.work / args.results
    rows = {s: [json.loads(line) for line in open(res / args.engine / f"{s}.jsonl")] for s in args.sets}
    base = {s: [json.loads(line) for line in open(res / args.baseline / f"{s}.jsonl")] for s in args.sets}
    args.out.mkdir(parents=True, exist_ok=True)
    summary = json.loads((args.out / "summary.json").read_text()) if (args.out / "summary.json").exists() else {}

    def score(sets_rows: dict[str, list[dict]]) -> dict:
        s = {}
        for k in RAW_SETS:
            if k in sets_rows:
                s[f"{k}-raw"] = wer_stats(sets_rows[k], normalised=False)["wer"]
        for k in args.sets:
            s[f"{k}-norm"] = wer_stats(sets_rows[k], normalised=True)["wer"]
        f, t = apostrophes([r for k in RAW_SETS if k in sets_rows for r in sets_rows[k]])
        s["apostrophes"] = f"{f}/{t}"
        return s

    summary[f"baseline:{args.baseline}"] = score(base)
    for spec in args.candidates:
        fmt = build(spec, args.threads)
        out_rows, t_total, n = {}, 0.0, 0
        for k in args.sets:
            out_rows[k] = []
            for r in rows[k]:
                t0 = time.perf_counter()
                hyp = fmt(r["hyp"])
                t_total += time.perf_counter() - t0
                n += 1
                out_rows[k].append({**r, "hyp": hyp, "granite": r["hyp"]})
        text_only = {}
        for k in RAW_SETS:
            if k in rows:
                text_only[k] = [{**r, "hyp": fmt(F.normalise(r["ref"]))} for r in rows[k]]
        s = score(out_rows)
        for k, v in text_only.items():
            s[f"{k}-text-only-raw"] = wer_stats(v, normalised=False)["wer"]
        s["ms_per_utt"] = round(1000 * t_total / max(n, 1), 1)
        s["size_mb"] = round(getattr(fmt, "size", 0) / 1e6, 1)
        summary[spec] = s
        name = re.sub(r"[^\w.-]+", "_", spec)
        with open(args.out / f"{name}.jsonl", "w") as f:
            for k in args.sets:
                for r in out_rows[k]:
                    f.write(json.dumps({"set": k, **r}, ensure_ascii=False) + "\n")
        print(spec, json.dumps(s), flush=True)
        (args.out / "summary.json").write_text(json.dumps(summary, indent=1))


if __name__ == "__main__":
    main()
