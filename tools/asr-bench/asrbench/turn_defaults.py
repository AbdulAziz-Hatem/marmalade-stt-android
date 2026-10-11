"""Smart Turn's shipped defaults across languages and phone-like silence (#64).

    python -m asrbench.turn_defaults --work $WORK ami-ihm ami-long fleurs-en fleurs-ja vox-it
    python -m asrbench.turn_defaults --work $WORK --rescore ami-ihm ami-long fleurs-en fleurs-ja vox-it
    python -m asrbench.turn_defaults --work $WORK --rescore --max-speech 5 ...   # short requests only

#43 (`turn.py`) picked the Patience triples on English meeting speech (AMI) with the harness's
synthetic -70 dBFS silence after each clip. This runs the same gate (`turn.turn_session`) on

- **more languages**: FLEURS (read single sentences, the 9 UI languages plus ko and ar) and
  VoxPopuli (parliament speeches with real hesitation pauses: de, es, fr, it, en);
- **other silence**: the phone's noise suppression and SoftAgc make pauses quieter than AMI's
  room tone, and the model was seen to call a mid-word cut "finished" more readily after digital
  zeros (#43 Caveats). Each clip runs once per silence model (`SILENCES`): as recorded, or with
  every pause and the lead/tail replaced by white noise at -70 or -90 dBFS or by exact zeros.

Ground truth. AMI keeps `turn.reference` (manual word starts, sentence ends). FLEURS and VoxPopuli
have no word timings, so `segment_reference` takes the clip's Silero segments (the 0.2 s
reference pass) instead: only clips that are one sentence (`prepare_data.one_sentence`) are
used, every pause between segments is mid-sentence, and a turn ending before the last
segment's onset (plus Silero's onset lag) cut the speaker off.

Results: $WORK/results-64/turn-defaults/<set>@<silence>.jsonl, one line per clip, as turn.py's.
"""

import argparse
import json
import statistics
import time
from pathlib import Path

import numpy as np

from .backends import SAMPLE_RATE
from .turn import LEAD_SILENCE, ONSET_LAG, REFERENCE_MODE, SmartTurnModel, mode_config, reference, score_clip, \
    turn_session

# The app's Patience values (SmartTurnGate.kt since #64), by name, and Normal before #64.
PATIENCE = {"quick": "smart-p0.5-t0.7-c1.5", "normal": "smart-p1.2-t0.7-c2.5", "relaxed": "smart-p1.5-t0.7-c3"}
NAMES = {**{v: k for k, v in PATIENCE.items()}, "smart-p0.5-t0.7-c2": "normal-43"}
# #64's run: Pause 1.2 s, Quick, Normal before and after, and two in-between candidates. (Relaxed's
# 1.5 s / 3 s ran separately, with --modes.)
DEFAULT_MODES = ["pause-1.2", PATIENCE["quick"], "smart-p0.5-t0.7-c2", PATIENCE["normal"], "smart-p1.0-t0.7-c2.5",
                 "smart-p0.8-t0.7-c2"]
# Silence model -> the floor level in dBFS (None: digital zeros). "room" keeps the clip as recorded
# with the harness's usual -70 dBFS floor around it (#43); the others replace every pause as well.
SILENCES = {"room": -70.0, "n70": -70.0, "n90": -90.0, "zero": None}
# How much audio around Silero's speech `quieten` keeps: word onsets and tails Silero trims.
KEEP_MARGIN = 0.1
FADE = 0.01


def quieten(x: np.ndarray, segments: list[tuple[int, int]], db: float | None, seed: int = 64) -> np.ndarray:
    """A noise-suppression stand-in: everything further than KEEP_MARGIN from the speech in
    `segments` (clip samples) becomes white noise at `db` dBFS (None: zeros), with 10 ms ramps."""
    mask = np.zeros(len(x), np.int64)
    m = int(KEEP_MARGIN * SAMPLE_RATE)
    for s, e in segments:
        mask[max(0, s - m):min(len(x), e + m)] = 1
    ramp = int(FADE * SAMPLE_RATE)
    # A moving average in whole numbers, edges extended: exactly 1 or 0 away from the ramps.
    padded = np.pad(mask, (ramp // 2, ramp - 1 - ramp // 2), mode="edge")
    keep = (np.convolve(padded, np.ones(ramp, np.int64), mode="valid") / ramp).astype(np.float32)
    floor = (np.zeros(len(x), np.float32) if db is None
             else (np.random.default_rng(seed).standard_normal(len(x)) * 10 ** (db / 20)).astype(np.float32))
    return x * keep + floor * (1.0 - keep)


def segment_reference(segments: list[tuple[int, int]], lead: float = LEAD_SILENCE) -> dict | None:
    """turn.reference without word timings, for a one-sentence clip: the speech is the reference
    pass's Silero segments (stream samples). A turn end before the last segment's onset, or within
    Silero's onset lag after it, cut the sentence; every pause between segments is mid-sentence."""
    spans = [(s / SAMPLE_RATE, e / SAMPLE_RATE) for s, e in segments if e / SAMPLE_RATE > lead]
    if not spans:
        return None
    last_start, last_end = spans[-1]
    return {"first": round(spans[0][0], 3), "last": round(min(last_start + ONSET_LAG, last_end), 3),
            "ends_sentence": True, "speech_end": round(last_end, 3),
            "pauses": [[round(a, 3), round(b, 3), "mid"] for (_, a), (b, _) in zip(spans, spans[1:]) if b > a]}


def summary(rows_by: dict[tuple[str, str], list[dict]], modes: list[str], max_speech: float | None = None) -> str:
    """One line per (set, silence, mode): cut-offs, latency, and how the final end came."""
    def speech_s(ref: dict) -> float:  # first word to the end of speech (or the last word's start)
        return (ref["speech_end"] or ref["last"]) - ref["first"]

    out = ["| set | silence | mode | clips | clips cut off | mid-sentence pauses cut | latency median s | p90 s "
           "| mean s | final end by model | by ceiling |", "|" + "---|" * 11]
    for (set_name, silence), rows in rows_by.items():
        clips = [r for r in rows if r["ref"] is not None
                 and (max_speech is None or speech_s(r["ref"]) <= max_speech)]
        mids = [(r["id"], k) for r in clips for k, (_, _, kind) in enumerate(r["ref"]["pauses"]) if kind == "mid"]
        for mode in modes:
            scored = [(r, score_clip(r["ref"], r["modes"][mode]["turns"])) for r in clips if mode in r["modes"]]
            if not scored:
                continue
            cut_clips = sum(1 for _, s in scored if s["cuts"])
            cut_set = {(r["id"], k) for r, s in scored for k in s["cut_pauses"]}
            lat = sorted(s["latency"] for _, s in scored if s["latency"] is not None)
            finals = [s["final_reason"] for _, s in scored if s["final_reason"]]
            smart = mode_config(mode).smart
            name = NAMES.get(mode, mode)
            out.append(
                f"| {set_name} | {silence} | {name} | {len(scored)} | {100 * cut_clips / len(scored):.0f} % "
                f"| {100 * sum(p in cut_set for p in mids) / len(mids) if mids else float('nan'):.0f} % ({len(mids)}) "
                f"| {statistics.median(lat):.2f} | {lat[min(len(lat) - 1, int(0.9 * len(lat)))]:.2f} "
                f"| {statistics.mean(lat):.2f} | "
                + (f"{100 * finals.count('complete') / len(finals):.0f} % | {100 * finals.count('ceiling') / len(finals):.0f} % |"
                   if smart else "- | - |"))
    return "\n".join(out) + "\n"


def main() -> None:
    import hashlib

    from .ami_words import AmiWords, fetch
    from .backends import silero_vad
    from .prepare_data import one_sentence
    from .run import load_audio, read_jsonl
    from .session import agc
    from .turn import MODEL_FILE, MODEL_SHA256

    ap = argparse.ArgumentParser()
    ap.add_argument("--work", type=Path, required=True)
    ap.add_argument("--results", default="results-64")
    ap.add_argument("--modes", default=",".join(DEFAULT_MODES), help="turn.mode_config names")
    ap.add_argument("--silence", default=",".join(SILENCES), help=f"of {', '.join(SILENCES)}")
    ap.add_argument("--threads", type=int, default=2, help="ONNX Runtime intra-op threads for smart-turn")
    ap.add_argument("--limit", type=int)
    ap.add_argument("--rescore", action="store_true", help="print the summary from the saved results")
    ap.add_argument("--max-speech", type=float, help="rescore: only clips whose speech spans at most this (s)")
    ap.add_argument("sets", nargs="+")
    args = ap.parse_args()
    modes = [m for m in args.modes.split(",") if m]
    silences = [s for s in args.silence.split(",") if s]
    out_dir = args.work / args.results / "turn-defaults"

    if args.rescore:
        rows_by = {(s, sil): read_jsonl(out_dir / f"{s}@{sil}.jsonl") for s in args.sets for sil in silences}
        print(summary({k: v for k, v in rows_by.items() if v}, modes, args.max_speech))
        return

    model_path = args.work / "models" / "smart-turn-v3.2" / MODEL_FILE
    if hashlib.sha256(model_path.read_bytes()).hexdigest() != MODEL_SHA256:
        raise SystemExit(f"{model_path}: unexpected sha256")
    model = SmartTurnModel(model_path, args.threads)
    words = None
    all_modes = [REFERENCE_MODE, *modes]
    vads = {m: silero_vad(args.work / "silero_vad.onnx", mode_config(m).min_silence) for m in all_modes}
    out_dir.mkdir(parents=True, exist_ok=True)

    for set_name in args.sets:
        ami = set_name.startswith("ami-")
        if ami and words is None:
            words = AmiWords(fetch(args.work))
        manifest = read_jsonl(args.work / "data" / set_name / "manifest.jsonl")
        if not ami:
            manifest = [u for u in manifest if one_sentence(u["text"])]
        manifest = manifest[:args.limit]
        files = {sil: (out_dir / f"{set_name}@{sil}.jsonl").open("w") for sil in silences}
        t0 = time.perf_counter()
        for utt in manifest:
            x = agc(load_audio(utt["audio"]))
            lead = int(LEAD_SILENCE * SAMPLE_RATE)
            room = turn_session(x, vads[REFERENCE_MODE], mode_config(REFERENCE_MODE))
            speech = [(s - lead, e - lead) for s, e in room.segments]
            for sil in silences:
                db = SILENCES[sil]
                xs = x if sil == "room" else quieten(x, speech, db)
                probs: dict[tuple[int, int], tuple[float, float]] = {}

                def detector(stream, start, end):
                    if (start, end) not in probs:
                        t = time.perf_counter()
                        p = model.complete_prob(stream[start:end])
                        probs[(start, end)] = (p, time.perf_counter() - t)
                    return probs[(start, end)]

                row = {"id": utt["id"], "text": utt["text"], "audio_s": round(len(x) / SAMPLE_RATE, 3), "modes": {}}
                for m in all_modes:
                    res = turn_session(xs, vads[m], mode_config(m), detector, floor_db=db)
                    row["modes"][m] = {"turns": [{"start": t.start, "end": t.end, "at": round(t.at, 3),
                                                  "reason": t.reason, "checks": t.checks} for t in res.turns]}
                    if m == REFERENCE_MODE:
                        row["segments"] = res.segments
                        row["ref"] = (reference(words.clip_words(utt["id"]), res.segments) if ami
                                      else segment_reference(res.segments))
                files[sil].write(json.dumps(row, ensure_ascii=False) + "\n")
                files[sil].flush()
        for f in files.values():
            f.close()
        print(f"<!-- {set_name}: {len(manifest)} clips x {len(silences)} silences in "
              f"{time.perf_counter() - t0:.0f} s -->", flush=True)
        rows_by = {(set_name, sil): read_jsonl(out_dir / f"{set_name}@{sil}.jsonl") for sil in silences}
        print(summary(rows_by, modes), flush=True)


if __name__ == "__main__":
    main()
