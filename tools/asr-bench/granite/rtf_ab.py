"""Interleaved decode-speed comparison of engines on the same clips (#26, #29).

    OMP_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1 python granite/rtf_ab.py --work $WORK \\
        --engines parakeet-tdt-0-6b-v3 granite-speech-5-470m-ctc --threads 2 4
    ... --engines sense-voice-small nemotron-3-5-streaming-560ms --sets fleurs-zh fleurs-ja --lang   # #29

Run from $WORK/harness-<n> (the manifests' audio paths are relative). Each
clip (levelled by session.agc, no VAD) is decoded by every engine in turn, in
a rotating order, so load from other jobs on the machine lands on all engines
alike. RTF = decode time / audio time. One warm-up decode per engine is not
counted. For Granite the time includes the numpy front-end; `ort` is the ONNX
Runtime call alone. Streaming engines (Nemotron) get the whole clip in one
stream (`session.direct_online`), the same model work as hop by hop.
`--lang` gives each engine that takes a hint the set's language (default: "en").
"""

import argparse
import json
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from asrbench import engines as E  # noqa: E402
from asrbench import granite as G  # noqa: E402
from asrbench.backends import SAMPLE_RATE, OfflineTranscriber, StreamingRecognizer  # noqa: E402
from asrbench.run import load_audio, read_jsonl  # noqa: E402
from asrbench.session import agc, direct_online  # noqa: E402


class _Streaming:
    def __init__(self, rec):
        self.rec = rec

    def transcribe(self, clip):
        return direct_online(clip, self.rec).text


def build(engine_id: str, work: Path, threads: int, lang: str = "en"):
    if engine_id in G.ENGINES:
        return G.GraniteTranscriber(G.ENGINES[engine_id], work / "models" / engine_id, num_threads=threads)
    engine = E.BY_ID[engine_id]
    if engine.streaming:
        return _Streaming(StreamingRecognizer(engine, work / "models" / engine_id, lang, num_threads=threads))
    return OfflineTranscriber(engine, work / "models" / engine_id, lang, num_threads=threads)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--work", type=Path, required=True)
    ap.add_argument("--engines", nargs="+", required=True)
    ap.add_argument("--threads", type=int, nargs="+", default=[2])
    ap.add_argument("--sets", nargs="+", default=["ls-other", "ami-ihm"])
    ap.add_argument("--n", type=int, default=30, help="clips per set")
    ap.add_argument("--lang", action="store_true", help="hint each set's language (default: en)")
    args = ap.parse_args()

    clips = []  # (language, audio)
    for s in args.sets:
        for u in read_jsonl(args.work / "data" / s / "manifest.jsonl")[: args.n]:
            clips.append((u["lang"] if args.lang else "en", agc(load_audio(u["audio"]))))
    audio_s = sum(len(c) for _, c in clips) / SAMPLE_RATE
    out = {}
    for threads in args.threads:
        # One recognizer per engine and language; weights load once per language.
        langs = sorted({lang for lang, _ in clips})
        recs = {(e, lang): build(e, args.work, threads, lang) for e in args.engines for lang in langs}
        for (_, lang), r in recs.items():
            r.transcribe(next(c for l, c in clips if l == lang))
        total = {e: 0.0 for e in args.engines}
        ort_only = {e: 0.0 for e in args.engines}
        for i, (lang, clip) in enumerate(clips):
            order = args.engines[i % len(args.engines):] + args.engines[: i % len(args.engines)]
            for e in order:
                r = recs[(e, lang)]
                t0 = time.perf_counter()
                r.transcribe(clip)
                total[e] += time.perf_counter() - t0
                if isinstance(r, G.GraniteTranscriber):
                    feats = G.features(clip)[None]
                    t0 = time.perf_counter()
                    r.session.run(["logits"], {r.input_name: feats})
                    ort_only[e] += time.perf_counter() - t0
        for e in args.engines:
            row = {"rtf": round(total[e] / audio_s, 4)}
            if ort_only[e]:
                row["rtf_ort_only"] = round(ort_only[e] / audio_s, 4)
            out[f"{e}@{threads}t"] = row
            print(f"{e:36s} {threads} threads  RTF {row['rtf']:.4f}" +
                  (f"  (ORT only {row['rtf_ort_only']:.4f})" if "rtf_ort_only" in row else ""), flush=True)
        del recs
    print(json.dumps({"clips": len(clips), "audio_s": round(audio_s, 1), "results": out}, indent=1))


if __name__ == "__main__":
    main()
