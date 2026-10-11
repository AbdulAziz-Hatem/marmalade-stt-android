"""Decode test sets with one engine, the way the app would, and record hypotheses and timing.

    python -m asrbench.run --work $WORK --engine parakeet-tdt-0-6b-v3 ls-clean fleurs-de ...
    python -m asrbench.run --work $WORK --engine qwen3-asr-0-6b --auto fleurs-de   # no language hint
    python -m asrbench.run --work $WORK --engine whisper-tiny-en --direct noise   # no VAD

Writes $WORK/results/<engine>/<set>[@auto|@direct].jsonl (one line per
utterance) and <same>.meta.json (load time, peak RSS). Resumable: utterances
already in the output file are skipped.

Every clip is levelled by session.agc first (the platform AGC's stand-in),
unless --no-agc. --preroll, --lead-silence and --no-agc tag the output file
(@preroll0.6s, @lead0s, @noagc) so variants sit next to the default run.

Language hint: engines that take one (catalog languageOptions) get the set's
language, as for a user who picked it; --auto sends "" (auto-detect, the app's
default for Whisper turbo, Nemotron 3.5 and Qwen3). Parakeet v3 always detects.
"""

import argparse
import json
import resource
import time
from math import gcd
from pathlib import Path

import miniaudio
import numpy as np
from scipy.signal import resample_poly

from . import engines as E
from . import granite
from .backends import (SAMPLE_RATE, WHISPER_TAIL_PADDINGS, OfflineTranscriber, StreamingRecognizer,
                       no_arena_provider, silero_vad)
from .session import (OFFLINE_PREROLL_SAMPLES, STREAMING_PREROLL_SAMPLES, agc, direct_offline, direct_online,
                      max_decode_samples, offline_session, online_session)


def load_audio(path: str) -> np.ndarray:
    """Mono float32 at 16 kHz. miniaudio decodes FLAC/WAV/MP3; scipy resamples if needed."""
    info = miniaudio.get_file_info(path)
    d = miniaudio.decode_file(path, output_format=miniaudio.SampleFormat.FLOAT32, nchannels=1,
                              sample_rate=info.sample_rate)
    x = np.asarray(d.samples, dtype=np.float32)
    if d.sample_rate != SAMPLE_RATE:
        g = gcd(SAMPLE_RATE, d.sample_rate)
        x = resample_poly(x, SAMPLE_RATE // g, d.sample_rate // g).astype(np.float32)
    return x


def read_jsonl(path: Path) -> list[dict]:
    if not path.exists():
        return []
    return [json.loads(line) for line in path.read_text().splitlines() if line.strip()]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--work", type=Path, required=True)
    ap.add_argument("--engine", required=True, choices=[*E.BY_ID, *granite.ENGINES])
    ap.add_argument("--auto", action="store_true", help="no language hint (auto-detect)")
    ap.add_argument("--hint", help="this language hint instead of the set's (#57: phantoms per language on the "
                                   "noise sets); tags the output @hint-<lang>")
    ap.add_argument("--direct", action="store_true", help="no VAD: whole clip to the model")
    ap.add_argument("--vad", type=Path, help="silero_vad.onnx (default: $WORK/silero_vad.onnx)")
    ap.add_argument("--preroll", type=float,
                    help="seconds kept before the VAD onset (default: the app's, 0.6 offline / 1.0 streaming); "
                         "tags the output @prerollNs")
    ap.add_argument("--lead-silence", type=float, default=0.5,
                    help="seconds of mic noise before the clip; another value tags the output @leadNs")
    ap.add_argument("--no-agc", action="store_true",
                    help="skip the AGC stand-in (the clip at its recorded level); tags the output @noagc")
    ap.add_argument("--soft-agc", action="store_true",
                    help="run session.SoftAgc on the hops (the app without a platform AGC); tags @softagc")
    ap.add_argument("--results", default="results", help="results directory under $WORK")
    ap.add_argument("--tail-silence", type=float, default=1.0)
    ap.add_argument("--threads", type=int, default=E.NUM_THREADS)
    ap.add_argument("--limit", type=int,
                    help="only the first N utterances of each set (for engines too slow for the full set)")
    ap.add_argument("--tail-padding", type=float,
                    help="seconds of zeros after each offline decode (default: the app's, 0.3 s for NeMo, else 0), "
                         "or, for a streaming engine, fed before each utterance's input_finished (default: the "
                         "app's, its chunk + 0.2 s); "
                         "tags the output @tailNs")
    ap.add_argument("--whisper-tail-paddings", type=int,
                    help="Whisper only: feature frames of zeros after the speech in the encoder input (the "
                         f"app's {WHISPER_TAIL_PADDINGS} otherwise; #46); tags the output @wtailN")
    ap.add_argument("--no-whisper-budget", action="store_true",
                    help="Whisper only: no per-language token budget (#54; needs sherpa patch 0005 to matter); "
                         "tags the output @nobudget")
    ap.add_argument("--max-decode", type=float,
                    help="offline only: cut utterances at this many seconds, pre-roll included (the app's "
                         "UtteranceCap otherwise: 28.6 s, Whisper in Hindi 21 s; #54); tags the output @capNs")
    ap.add_argument("--model-dir", type=Path,
                    help="the engine's files (same names) from this directory instead, e.g. a re-quantised "
                         "variant (#55); needs --variant")
    ap.add_argument("--variant", help="tags the output @<variant> (with --model-dir)")
    ap.add_argument("--no-arena", action="store_true",
                    help="offline only: ORT's CPU memory arena off, as OrtTuning -noarena (#47); tags @noarena")
    ap.add_argument("sets", nargs="+")
    args = ap.parse_args()

    engine = E.BY_ID.get(args.engine) or granite.ENGINES[args.engine]
    if (args.model_dir is None) != (args.variant is None):
        raise SystemExit("--model-dir and --variant go together")
    model_dir = args.model_dir or args.work / "models" / engine.id
    vad = silero_vad(args.vad or args.work / "silero_vad.onnx")
    if args.hint and args.auto:
        raise SystemExit("--hint and --auto exclude each other")
    suffix = ("@direct" if args.direct else "") + ("@auto" if args.auto else "")
    if args.hint:
        if not engine.takes_language_hint:
            raise SystemExit(f"{engine.id} takes no language hint")
        suffix += f"@hint-{args.hint}"
    if args.preroll is not None:
        suffix += f"@preroll{args.preroll:g}s"
    if args.lead_silence != 0.5:
        suffix += f"@lead{args.lead_silence:g}s"
    if args.no_agc:
        suffix += "@noagc"
    if args.soft_agc:
        suffix += "@softagc"
    if args.tail_padding is not None:
        suffix += f"@tail{args.tail_padding:g}s"
    if args.whisper_tail_paddings is not None:
        if engine.family != E.WHISPER:
            raise SystemExit("--whisper-tail-paddings is for Whisper engines")
        suffix += f"@wtail{args.whisper_tail_paddings}"
    if args.no_whisper_budget:
        if engine.family != E.WHISPER:
            raise SystemExit("--no-whisper-budget is for Whisper engines")
        suffix += "@nobudget"
    if args.max_decode is not None:
        if engine.streaming:
            raise SystemExit("--max-decode is for offline engines")
        suffix += f"@cap{args.max_decode:g}s"
    if args.variant:
        suffix += f"@{args.variant}"
    if args.no_arena:
        if engine.streaming or engine.family == granite.GRANITE_CTC:
            raise SystemExit("--no-arena is for sherpa-onnx offline engines")
        suffix += "@noarena"
    if args.preroll is not None:
        preroll = int(args.preroll * SAMPLE_RATE)
    else:
        preroll = STREAMING_PREROLL_SAMPLES if engine.streaming else OFFLINE_PREROLL_SAMPLES
    loaded: dict[str, object] = {}  # language hint -> recognizer

    for set_name in args.sets:
        manifest = read_jsonl(args.work / "data" / set_name / "manifest.jsonl")[:args.limit]
        if not manifest:
            raise SystemExit(f"no manifest for {set_name}; run prepare_data / make_synthetic")
        lang = manifest[0]["lang"]
        if args.hint:
            lang = args.hint
        elif lang not in engine.languages:
            print(f"skip {engine.id} {set_name}: {lang} not supported", flush=True)
            continue
        hint = "" if args.auto else lang
        max_decode = (int(args.max_decode * SAMPLE_RATE) if args.max_decode is not None
                      else max_decode_samples(engine.family, hint, preroll))
        out = args.work / args.results / engine.id / f"{set_name}{suffix}.jsonl"
        out.parent.mkdir(parents=True, exist_ok=True)
        done = {r["id"] for r in read_jsonl(out)}

        load_seconds = 0.0
        if hint not in loaded:
            t0 = time.perf_counter()
            cls = (granite.GraniteTranscriber if engine.family == granite.GRANITE_CTC
                   else StreamingRecognizer if engine.streaming else OfflineTranscriber)
            loaded.clear()  # one model in memory at a time
            extra = {} if args.tail_padding is None else {"tail_padding": int(args.tail_padding * SAMPLE_RATE)}
            if args.whisper_tail_paddings is not None:
                extra["whisper_tail_paddings"] = args.whisper_tail_paddings
            if args.no_whisper_budget:
                extra["whisper_token_budget"] = False
            if args.no_arena:
                extra["provider"] = no_arena_provider(args.work)
            loaded[hint] = cls(engine, model_dir, hint, num_threads=args.threads, **extra)
            load_seconds = time.perf_counter() - t0
        rec = loaded[hint]

        with out.open("a") as f:
            for utt in manifest:
                if utt["id"] in done:
                    continue
                audio = load_audio(utt["audio"])
                if not args.no_agc:
                    audio = agc(audio)
                if args.direct:
                    res = direct_online(audio, rec) if engine.streaming else direct_offline(audio, rec)
                elif engine.streaming:
                    res = online_session(audio, rec, vad, args.tail_silence, args.lead_silence, preroll,
                                         args.soft_agc)
                else:
                    res = offline_session(audio, rec, vad, args.tail_silence, args.lead_silence, preroll,
                                          args.soft_agc, max_decode=max_decode)
                f.write(json.dumps({
                    "id": utt["id"], "lang": lang, "ref": utt["text"], "hyp": res.text,
                    "audio_s": round(len(audio) / SAMPLE_RATE, 3), "decode_s": round(res.decode_seconds, 4),
                    "segments": res.segments, "flushed_tail": res.flushed_tail, "near_silent": res.near_silent,
                    "finals": res.finals, "rms_db": res.rms_db,
                }, ensure_ascii=False) + "\n")
                f.flush()
        meta = out.with_suffix(".meta.json")
        old = json.loads(meta.read_text()) if meta.exists() else {}
        meta.write_text(json.dumps({
            "engine": engine.id, "set": set_name, "hint": hint, "direct": args.direct,
            "threads": args.threads, "lead_silence": args.lead_silence, "tail_silence": args.tail_silence,
            "preroll_s": None if args.direct else preroll / SAMPLE_RATE,
            "max_decode_s": None if args.direct or engine.streaming else max_decode / SAMPLE_RATE,
            "agc": not args.no_agc, "soft_agc": args.soft_agc,
            "tail_padding_s": len(rec.tail) / SAMPLE_RATE if hasattr(rec, "tail") else None,
            "whisper_tail_paddings": (args.whisper_tail_paddings or WHISPER_TAIL_PADDINGS) if engine.family == E.WHISPER else None,
            "model_dir": str(model_dir), "arena": not args.no_arena,
            "load_s": round(load_seconds, 2) or old.get("load_s"),
            "peak_rss_mb": round(resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024),
        }, indent=1))
        print(f"done {engine.id} {set_name}{suffix}", flush=True)


if __name__ == "__main__":
    main()
