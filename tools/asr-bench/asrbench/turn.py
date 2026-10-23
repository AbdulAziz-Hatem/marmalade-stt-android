"""End of dictation: Pause vs Smart Turn on AMI, on virtual time (#43).

    python -m asrbench.turn --work $WORK ami-ihm ami-long ami-multi
    python -m asrbench.turn --work $WORK --engine parakeet-tdt-0-6b-v3 ami-ihm ami-long ami-multi   # + WER
    python -m asrbench.turn --work $WORK --rescore ami-ihm ami-long ami-multi
    python -m asrbench.turn --work $WORK --bench-threads 1,2,4       # one check's cost per thread count

Two ways the app can decide that a dictation has ended:

- **Pause**: Silero closes a segment after `min_silence` seconds of silence
  (the settings slider, 0.3-1.5 s, default 0.9 s) and that segment is the
  utterance.
- **Smart Turn**: Silero runs with a 0.2 s minimum silence. When a segment
  closes, pipecat's smart-turn v3.2 scores the turn so far: the last 8 s of
  its audio from the first onset's pre-roll to now, earlier segments and the
  trailing silence included. p > threshold ends the turn. Otherwise, if
  speech resumes the turn goes on; if the silence goes on, the model is asked
  again every `recheck` seconds, and at `ceiling` seconds of silence the turn
  ends regardless. A turn that reaches 28 s ends at its last segment boundary.
  The whole turn is then decoded once.
- **Smart Turn with an early check** (#91): as Smart Turn, but the check at the
  propose point ends the turn only if p > `early_threshold`; if it doesn't, the
  next check comes at `normal_at` seconds of silence, with `threshold`, and the
  rule goes on as above (rechecks, ceiling).

Both modes cut a turn still going at 28 s plus the pre-roll with no segment
boundary to end at (#48, mirrored here in #54): at its quietest window in the
last 1.5 s, as StreamingSession's cutAtCap. The speech goes on as the next
turn, which is decoded only if it holds speech (`decodable`).

`TurnGate` is that rule on its own (tested with made-up probabilities);
`turn_session` runs it on a clip: the harness's mic emulation (lead-in, AGC
stand-in, 100 ms hops, 100 Hz high-pass), the app's Silero, and virtual time
(hop i arrives at (i + 1) x 100 ms; each model check costs its host wall time
x `--detector-scale`).

Ground truth comes from AMI's manual word timings (`ami_words.py`). Their
word ENDS are useless here (the annotation stretches a word over the pause
after it), but word STARTS are good. So:

- A **false cut-off** is a turn end decided before the start of the clip's
  last annotated word: the gate ended the dictation and the speaker still had
  words to say.
- **Pauses** are the gaps between consecutive Silero segments of the
  reference pass (Pause at 0.2 s: every pause the Smart Turn gate sees),
  between the first and the last word. Each false cut-off is charged to the
  pause it happened in (the latest pause that began before it).
- **Latency** is from the end of the last speech (the end of the reference
  pass's segment that holds the last word, i.e. Silero's own end of speech)
  to the turn end that came after the last word started.

Results: $WORK/results-43/turn/<set>.jsonl, one line per clip with every
mode's turns (and, with --engine, each mode's text).
"""

import argparse
import json
import statistics
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable

import numpy as np

from .backends import MAX_UTTERANCE_SECONDS, SAMPLE_RATE, VAD_WINDOW_SIZE
from .session import (CAP_CUT_SEARCH_SAMPLES, CAP_HEADROOM_SAMPLES, MIC_FLOOR_DBFS, MIC_HOP_SAMPLES,
                      NEAR_SILENT_DB, OFFLINE_PREROLL_SAMPLES, mic_hops, quietest_window, rest_has_speech,
                      rms_db)
from .whisper_features import N_SAMPLES, log_mel

MODEL_FILE = "smart-turn-v3.2-cpu.onnx"
MODEL_SHA256 = "2bb026316b14a660486a75b1733cd3fbab8c2fd0314dc9af7be49f8cca967e4f"
LEAD_SILENCE = 0.5
TAIL_SILENCE = 5.0  # enough for the 3 s ceiling (plus Silero's hangover) to end every turn before stop()
# Pause lengths are Silero's (its segment starts a little before the onset it detects), so a
# pause the 0.2 s gate closed on can measure a little under 0.2 s.
PAUSE_BUCKETS = [(0.0, 0.5), (0.5, 1.0), (1.0, 2.0), (2.0, 99.0)]
# How long after a pause's end a cut still counts as cut in that pause: Silero confirms an onset
# only after 0.25 s of speech, so the gate can end a turn just after the next word has started.
ONSET_LAG = 0.35


@dataclass(frozen=True)
class TurnConfig:
    min_silence: float  # Silero's min silence: Pause's slider, or Smart Turn's propose point
    smart: bool = False
    recheck: float = 0.5  # further silence between model checks
    ceiling: float = 3.0  # silence that ends the turn regardless of the model
    threshold: float = 0.5  # p above this = complete
    max_turn: float = MAX_UTTERANCE_SECONDS  # a turn is cut with no boundary at this plus the pre-roll
    early_threshold: float = 0.0  # > 0: the check at min_silence ends the turn only above this (#91)
    normal_at: float = 0.0  # with early_threshold: the silence of the next check, which uses threshold


MODES = {
    "pause-0.2": TurnConfig(0.2),  # the reference pass: every pause the Smart Turn gate is proposed at
    "pause-0.5": TurnConfig(0.5),
    "pause-0.9": TurnConfig(0.9),  # the app's default slider
    "pause-1.2": TurnConfig(1.2),
    "pause-1.5": TurnConfig(1.5),
    "smart": TurnConfig(0.2, smart=True),  # the app's default mode
    "smart-t0.3": TurnConfig(0.2, smart=True, threshold=0.3),
    "smart-t0.7": TurnConfig(0.2, smart=True, threshold=0.7),
    "smart-p0.3": TurnConfig(0.3, smart=True),
    "smart-p0.5": TurnConfig(0.5, smart=True),
    "smart-p0.3-t0.7": TurnConfig(0.3, smart=True, threshold=0.7),
    "smart-p0.5-t0.7": TurnConfig(0.5, smart=True, threshold=0.7),
    "smart-c2": TurnConfig(0.2, smart=True, ceiling=2.0),
    "smart-p0.5-t0.7-c2": TurnConfig(0.5, smart=True, threshold=0.7, ceiling=2.0),
}
REFERENCE_MODE = "pause-0.2"


def mode_config(name: str) -> TurnConfig:
    """A mode by name: one of MODES, or `pause-<s>` / `smart[-p<s>][-e<p>-n<s>][-t<p>][-c<s>]`
    (propose, early threshold and the next check's silence, threshold, ceiling; unnamed ones keep
    TurnConfig's defaults), e.g. smart-p0.4-t0.6-c1.5 or smart-p0.5-e0.95-n1.2-t0.7-c2.5."""
    if name in MODES:
        return MODES[name]
    kind, *parts = name.split("-")
    try:
        if kind == "pause" and len(parts) == 1:
            return TurnConfig(float(parts[0]))
        if kind == "smart":
            keys = {"p": "min_silence", "e": "early_threshold", "n": "normal_at", "t": "threshold", "c": "ceiling"}
            kw = {}
            for part in parts:
                key = keys.get(part[:1])
                if key is None or key in kw:
                    raise ValueError(part)
                kw[key] = float(part[1:])
            cfg = TurnConfig(kw.pop("min_silence", 0.2), smart=True, **kw)
            if ("early_threshold" in kw) != ("normal_at" in kw) or ("normal_at" in kw
                                                                    and cfg.normal_at <= cfg.min_silence):
                raise ValueError(name)
            return cfg
    except ValueError:
        pass
    raise ValueError(f"unknown mode {name!r}")


class TurnGate:
    """The end-of-turn rule, fed one event at a time. Positions are stream samples.

    `segment_closed(end)` when Silero closes a segment ending at `end`, `speech_resumed()`
    when it detects an onset, `step(now, prob)` once per hop: returns why the turn ends now
    ("pause", "complete", "ceiling") or None. `prob()` is called only when a check is due.
    """

    def __init__(self, cfg: TurnConfig):
        self.cfg = cfg
        self.speech_end: int | None = None  # where the pause in progress began; None while speaking
        self.next_check = 0.0  # silence (s) at which the model is asked next
        self.early = False  # the next check is the early one (early_threshold)

    def segment_closed(self, end: int) -> None:
        self.speech_end = end
        self.next_check = self.cfg.min_silence
        self.early = self.cfg.early_threshold > 0

    def speech_resumed(self) -> None:
        self.speech_end = None

    def step(self, now: int, prob: Callable[[], float]) -> str | None:
        if self.speech_end is None:
            return None
        if not self.cfg.smart:
            return "pause"
        silence = (now - self.speech_end) / SAMPLE_RATE
        if silence >= self.cfg.ceiling - 1e-9:
            return "ceiling"
        if silence < self.next_check - 1e-9:
            return None
        p = prob()
        if self.early:
            self.early = False
            if p > self.cfg.early_threshold:
                return "complete"
            self.next_check = self.cfg.normal_at
            return None
        if p > self.cfg.threshold:
            return "complete"
        self.next_check = silence + self.cfg.recheck
        return None


@dataclass
class Turn:
    start: int  # first sample of the turn's audio (its first onset's pre-roll)
    end: int  # its last segment's end: a Final decodes [start, end)
    at: float  # virtual time the turn ended (the decision, model time included), s since mic open
    reason: str  # pause / complete / ceiling / cap / cut / flush
    checks: list = field(default_factory=list)  # (silence s, p, model wall s) per model check
    from_cut: bool = False  # starts at a forced cut: the rest of a segment that was cut
    near_silent: bool = True  # every segment's part in the turn was near-silent (NearSilence)


def decodable(turn: Turn) -> bool:
    """StreamingSession.endTurn: a turn is decoded unless it is a cut segment's rest without speech."""
    return turn.end > turn.start and (not turn.from_cut or rest_has_speech(turn.end - turn.start, turn.near_silent))


@dataclass
class TurnResult:
    turns: list[Turn] = field(default_factory=list)
    segments: list[tuple[int, int]] = field(default_factory=list)  # every Silero segment (start, end)
    audio: np.ndarray | None = None  # the whole stream after the high-pass, for decoding turns


def turn_session(samples: np.ndarray, vad, cfg: TurnConfig,
                 detector: Callable[[np.ndarray, int, int], tuple[float, float]] | None = None,
                 lead_silence: float = LEAD_SILENCE, tail_silence: float = TAIL_SILENCE,
                 preroll: int = OFFLINE_PREROLL_SAMPLES, detector_scale: float = 1.0,
                 floor_db: float | None = MIC_FLOOR_DBFS) -> TurnResult:
    """One clip through the gate. `vad` must be built with `cfg.min_silence`. `detector(stream,
    start, end)` scores stream[start:end] and returns (p, wall seconds). The lead and tail
    silence are `mic_floor` at `floor_db` (None: digital zeros).

    As in StreamingSession, "now" for the gate is where the VAD is (`fed`, whole windows), not
    the hop's end: the silence is counted and the model's window ends there (#64)."""
    if cfg.smart and detector is None:
        raise ValueError("Smart Turn needs a detector")
    vad.reset()
    stream = np.concatenate([h for h, _ in mic_hops(samples, tail_silence, lead_silence, floor_db=floor_db)])
    res = TurnResult(audio=stream)
    gate = TurnGate(cfg)
    max_turn = int(cfg.max_turn * SAMPLE_RATE)
    max_decode = max_turn + preroll  # UtteranceCap: the turn's cap less the pre-roll is max_turn
    now, fed, speaking = 0.0, 0, False
    floor = 0  # a turn's pre-roll never reaches back past the previous turn's end (the app drops its buffer)
    turn: Turn | None = None

    def end_turn(reason: str) -> None:
        nonlocal turn, floor
        turn.at, turn.reason = now, reason
        res.turns.append(turn)
        floor, turn = fed, None
        gate.speech_resumed()

    def check() -> float:
        nonlocal now
        p, wall = detector(stream, turn.start, fed)
        now += wall * detector_scale
        turn.checks.append((round((fed - gate.speech_end) / SAMPLE_RATE, 3), round(p, 4), round(wall, 4)))
        return p

    for i in range(len(stream) // MIC_HOP_SAMPLES):
        hop_end = (i + 1) * MIC_HOP_SAMPLES
        now = max(now, hop_end / SAMPLE_RATE)
        while fed + VAD_WINDOW_SIZE < hop_end:  # as StreamingSession: whole windows of what has arrived
            vad.accept_waveform(stream[fed:fed + VAD_WINDOW_SIZE])
            fed += VAD_WINDOW_SIZE
            detected = vad.is_speech_detected()
            if detected and not speaking:
                if turn is None:
                    turn = Turn(max(fed - preroll, floor), -1, 0.0, "")
                else:
                    gate.speech_resumed()
            speaking = detected
            while not vad.empty():
                seg = vad.front
                s0, s1 = seg.start, seg.start + len(seg.samples)
                vad.pop()
                res.segments.append((s0, s1))
                if turn is None:  # the onset came and went inside one window
                    turn = Turn(max(s0 - preroll, floor), -1, 0.0, "")
                turn.end = s1
                # Judged on the segment's part after a cut (at turn.start), as the app's rest.
                part = stream[max(s0, turn.start):s1] if turn.from_cut else stream[s0:s1]
                turn.near_silent = turn.near_silent and rms_db(part) < NEAR_SILENT_DB
                gate.segment_closed(s1)
                if s1 - turn.start >= max_turn:
                    end_turn("cap")
        if turn is not None and speaking and turn.end >= 0 and hop_end - turn.start >= max_turn:
            # 28 s and still talking: end at the last segment boundary; the segment in progress
            # starts the next turn.
            boundary = turn.end
            end_turn("cap")
            floor = boundary
            turn = Turn(boundary, -1, 0.0, "")
        if turn is not None and speaking and fed - turn.start > max_decode - CAP_HEADROOM_SAMPLES:
            # StreamingSession.cutAtCap (#48): still talking at the cap with no boundary to end
            # at (the 28 s split above comes first otherwise). The turn ends at the quietest
            # window in the last 1.5 s; the speech goes on as the next turn, from the cut.
            cut = quietest_window(stream, fed - CAP_CUT_SEARCH_SAMPLES, fed)
            turn.end = cut
            end_turn("cut")
            floor = cut
            turn = Turn(cut, -1, 0.0, "", from_cut=True)
        if turn is not None:
            reason = gate.step(fed, check)
            if reason:
                end_turn(reason)
    if turn is not None:
        if turn.end < 0:
            turn.end = len(stream)
        end_turn("flush")
    return res


class SmartTurnModel:
    """smart-turn v3.2 (pipecat, BSD-2-Clause) on ONNX Runtime, as marmalade-stt-cli's turn.py:
    the last 8 s, zero-padded at the start; Whisper log-mel (1, 80, 800); the output is already
    a probability (the sigmoid is inside the graph)."""

    def __init__(self, path: Path, threads: int = 4):
        import onnxruntime as ort

        opts = ort.SessionOptions()
        opts.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL
        opts.inter_op_num_threads = 1
        opts.intra_op_num_threads = threads
        opts.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
        self.session = ort.InferenceSession(str(path), opts, providers=["CPUExecutionProvider"])

    @staticmethod
    def window(audio: np.ndarray) -> np.ndarray:
        x = np.asarray(audio, dtype=np.float32).reshape(-1)
        if len(x) >= N_SAMPLES:
            return x[-N_SAMPLES:]
        return np.pad(x, (N_SAMPLES - len(x), 0))

    def complete_prob(self, audio: np.ndarray) -> float:
        feats = log_mel(self.window(audio))[None, :, :].astype(np.float32)
        return float(np.asarray(self.session.run(None, {"input_features": feats})[0]).reshape(-1)[0])


def bench(model_path: Path, thread_counts: list[int], runs: int = 50) -> None:
    """Median time of one check (8 s of audio): the numpy features, then the model."""
    audio = (np.random.default_rng(43).standard_normal(N_SAMPLES) * 0.05).astype(np.float32)
    print("| threads | features ms | model ms | total ms |\n|---|---|---|---|")
    for threads in thread_counts:
        model = SmartTurnModel(model_path, threads)
        feats_t, run_t = [], []
        for _ in range(runs + 5):
            t0 = time.perf_counter()
            feats = log_mel(model.window(audio))[None, :, :].astype(np.float32)
            t1 = time.perf_counter()
            model.session.run(None, {"input_features": feats})
            feats_t.append(t1 - t0)
            run_t.append(time.perf_counter() - t1)
        f, r = statistics.median(feats_t[5:]) * 1000, statistics.median(run_t[5:]) * 1000
        print(f"| {threads} | {f:.1f} | {r:.1f} | {f + r:.1f} |")


# ---- scoring against the AMI word timings -------------------------------------------------

def reference(words: list, segments: list[tuple[int, int]], lead: float = LEAD_SILENCE) -> dict | None:
    """Where the speech is, on the stream's time axis (s since mic open): the first and last
    word starts, the end of the last speech, and the pauses between them, each marked "mid"
    (inside a sentence) or "end" (after a sentence's last word)."""
    if not words:
        return None
    first, last = words[0][0] + lead, words[-1][0] + lead
    spans = [(s / SAMPLE_RATE, e / SAMPLE_RATE) for s, e in segments]
    holding = [e for s, e in spans if e > last]
    pauses = []
    for (_, a), (b, _) in zip(spans, spans[1:]):
        if a > first - 0.5 and b < last + 0.5 and b > a:
            before = [w for w in words if w[0] + lead < a]  # the word the pause follows
            kind = "end" if before and before[-1][3] else "mid"
            pauses.append([round(a, 3), round(b, 3), kind])
    return {"first": round(first, 3), "last": round(last, 3), "ends_sentence": words[-1][3],
            "speech_end": round(holding[0], 3) if holding else None, "pauses": pauses}


def score_clip(ref: dict, turns: list[dict]) -> dict:
    """False cut-offs (and the pause each happened in) and the end-of-dictation latency of one clip."""
    cuts = [t["at"] for t in turns if t["at"] < ref["last"]]
    cut_pauses = set()
    for at in cuts:
        before = [k for k, (a, b, _) in enumerate(ref["pauses"]) if a <= at <= b + ONSET_LAG]
        if before:
            cut_pauses.add(before[-1])
    final = next((t for t in turns if t["at"] >= ref["last"]), None)
    latency = None
    if final is not None and ref["speech_end"] is not None:
        latency = final["at"] - ref["speech_end"]
    return {"cuts": len(cuts), "cut_pauses": sorted(cut_pauses),
            "unattributed": len(cuts) - len(cut_pauses), "latency": latency,
            "final_reason": final["reason"] if final else None,
            "final_checks": len(final["checks"]) if final else 0}


def _p90(xs: list[float]) -> float:
    return xs[min(len(xs) - 1, int(0.9 * len(xs)))] if xs else float("nan")


def _pct(a: int, b: int) -> str:
    return f"{100 * a / b:.0f} % ({a}/{b})" if b else "-"


def tables(set_name: str, rows: list[dict], modes: list[str]) -> str:
    out = [f"## {set_name}\n"]
    clips = [r for r in rows if r["ref"] is not None]
    pauses = [(r["id"], k, b - a, kind) for r in clips for k, (a, b, kind) in enumerate(r["ref"]["pauses"])]
    lengths = sorted(p[2] for p in pauses)
    out.append(f"{len(clips)} clips with word timings (of {len(rows)}); {len(pauses)} pauses inside a turn "
               f"(median {statistics.median(lengths) if lengths else 0:.2f} s; "
               + ", ".join(f"{lo:g}-{hi:g} s: {sum(lo <= x < hi for x in lengths)}" if hi < 99
                           else f">= {lo:g} s: {sum(lo <= x for x in lengths)}" for lo, hi in PAUSE_BUCKETS)
               + f"; {sum(p[3] == 'mid' for p in pauses)} inside a sentence, "
               f"{sum(p[3] == 'end' for p in pauses)} after a sentence end)\n")
    header = ("| mode | clips cut off | cut-offs | "
              + " | ".join(f"pauses < {hi:g} s cut" if lo == 0 else f"pauses {lo:g}-{hi:g} s cut" if hi < 99
                           else f"pauses >= {lo:g} s cut" for lo, hi in PAUSE_BUCKETS)
              + " | mid-sentence pauses cut | sentence-end pauses cut"
              + " | latency median s | p90 | max | latency, clips ending a sentence: median / p90"
              + " | final end: model / ceiling | checks per clip | model ms (median) |"
              + (" WER % |" if any("hyp" in r for r in clips) else ""))
    out += [header, "|" + "---|" * (header.count("|") - 1)]
    for mode in modes:
        scored = [(r, score_clip(r["ref"], r["modes"][mode]["turns"])) for r in clips if mode in r["modes"]]
        if not scored:
            continue
        cut_clips = sum(1 for _, s in scored if s["cuts"])
        cuts = sum(s["cuts"] for _, s in scored)
        cut_set = {(r["id"], k) for r, s in scored for k in s["cut_pauses"]}
        bucket_cells = []
        for lo, hi in PAUSE_BUCKETS:
            inb = [(cid, k) for cid, k, length, _ in pauses if lo <= length < hi]
            bucket_cells.append(_pct(sum(p in cut_set for p in inb), len(inb)))
        for kind in ("mid", "end"):
            inb = [(cid, k) for cid, k, _, pk in pauses if pk == kind]
            bucket_cells.append(_pct(sum(p in cut_set for p in inb), len(inb)))
        lat = sorted(s["latency"] for _, s in scored if s["latency"] is not None)
        p90 = _p90(lat)
        done = sorted(s["latency"] for r, s in scored if s["latency"] is not None and r["ref"]["ends_sentence"])
        finals = [s["final_reason"] for _, s in scored if s["final_reason"]]
        model_end = sum(f == "complete" for f in finals)
        ceiling = sum(f == "ceiling" for f in finals)
        checks = [c for r, _ in scored for t in r["modes"][mode]["turns"] for c in t["checks"]]
        n_checks = len(checks) / len(scored)
        ms = statistics.median(c[2] for c in checks) * 1000 if checks else None
        row = (f"| {mode} | {_pct(cut_clips, len(scored))} | {cuts} | " + " | ".join(bucket_cells)
               + f" | {statistics.median(lat):.2f} | {p90:.2f} | {max(lat):.2f}"
               + f" | {statistics.median(done) if done else float("nan"):.2f} / {_p90(done):.2f} ({len(done)}) | "
               + (f"{model_end} / {ceiling}" if mode_config(mode).smart else "-")
               + f" | {n_checks:.1f} | " + (f"{ms:.0f}" if ms is not None else "-") + " |")
        hyps = [r for r, _ in scored if mode in r.get("hyp", {})]
        if hyps:
            from .score import wer_stats
            w = wer_stats([{"ref": r["text"], "hyp": r["hyp"][mode], "lang": "en"} for r in hyps], True)
            row += f" {w['wer']:.1f} ± {w['ci95']:.1f} |"
        out.append(row)
    return "\n".join(out) + "\n"


def main() -> None:
    from .ami_words import AmiWords, fetch
    from .backends import silero_vad
    from .run import load_audio, read_jsonl
    from .session import agc

    ap = argparse.ArgumentParser()
    ap.add_argument("--work", type=Path, required=True)
    ap.add_argument("--results", default="results-43")
    ap.add_argument("--modes", default=",".join(MODES), help="names from MODES or mode_config's pattern")
    ap.add_argument("--model", type=Path, help=f"default: $WORK/models/smart-turn-v3.2/{MODEL_FILE}")
    ap.add_argument("--threads", type=int, default=4, help="ONNX Runtime intra-op threads for smart-turn")
    ap.add_argument("--detector-scale", type=float, default=1.0,
                    help="multiplies each model check's wall time on the virtual clock")
    ap.add_argument("--engine", help="also decode every turn with this engine and score WER per mode")
    ap.add_argument("--limit", type=int)
    ap.add_argument("--rescore", action="store_true", help="print the tables from the saved results")
    ap.add_argument("--bench-threads", help="only time one model check at these thread counts, e.g. 1,2,4")
    ap.add_argument("sets", nargs="*")
    args = ap.parse_args()
    modes = [m for m in args.modes.split(",") if m]
    if REFERENCE_MODE not in modes:
        modes.insert(0, REFERENCE_MODE)
    out_dir = args.work / args.results / "turn"

    if args.rescore:  # the reference is rebuilt from the saved segments, so scoring changes apply
        words = AmiWords(fetch(args.work))
        for set_name in args.sets:
            rows = read_jsonl(out_dir / f"{set_name}.jsonl")
            for row in rows:
                row["ref"] = reference(words.clip_words(row["id"]), row["segments"])
            print(tables(set_name, rows, modes))
        return

    import hashlib
    model_path = args.model or args.work / "models" / "smart-turn-v3.2" / MODEL_FILE
    digest = hashlib.sha256(model_path.read_bytes()).hexdigest()
    if digest != MODEL_SHA256:
        raise SystemExit(f"{model_path}: sha256 {digest}, expected {MODEL_SHA256}")
    if args.bench_threads:
        bench(model_path, [int(t) for t in args.bench_threads.split(",")])
        return
    model = SmartTurnModel(model_path, args.threads)
    words = AmiWords(fetch(args.work))
    vads = {m: silero_vad(args.work / "silero_vad.onnx", mode_config(m).min_silence) for m in modes}
    transcriber = None
    if args.engine:
        from . import engines as E
        from .backends import OfflineTranscriber
        engine = E.BY_ID[args.engine]
        transcriber = OfflineTranscriber(engine, args.work / "models" / engine.id, "en")
    out_dir.mkdir(parents=True, exist_ok=True)

    for set_name in args.sets:
        manifest = read_jsonl(args.work / "data" / set_name / "manifest.jsonl")[:args.limit]
        rows = []
        t0 = time.perf_counter()
        for utt in manifest:
            x = agc(load_audio(utt["audio"]))
            probs: dict[tuple[int, int], tuple[float, float]] = {}  # the same window scores the same

            def detector(stream, start, end):
                if (start, end) not in probs:
                    t = time.perf_counter()
                    p = model.complete_prob(stream[start:end])
                    probs[(start, end)] = (p, time.perf_counter() - t)
                return probs[(start, end)]

            row = {"id": utt["id"], "text": utt["text"], "audio_s": round(len(x) / SAMPLE_RATE, 3), "modes": {}}
            decoded: dict[tuple[int, int], str] = {}
            for m in modes:
                res = turn_session(x, vads[m], mode_config(m), detector, detector_scale=args.detector_scale)
                row["modes"][m] = {"turns": [{"start": t.start, "end": t.end, "at": round(t.at, 3),
                                              "reason": t.reason, "checks": t.checks, "from_cut": t.from_cut}
                                             for t in res.turns]}
                if m == REFERENCE_MODE:
                    row["ref"] = reference(words.clip_words(utt["id"]), res.segments)
                    row["segments"] = res.segments
                if transcriber is not None:
                    texts = []
                    for t in filter(decodable, res.turns):
                        if (t.start, t.end) not in decoded:
                            decoded[(t.start, t.end)] = transcriber.transcribe(res.audio[t.start:t.end]).strip()
                        texts.append(decoded[(t.start, t.end)])
                    row.setdefault("hyp", {})[m] = " ".join(s for s in texts if s)
            rows.append(row)
        with (out_dir / f"{set_name}.jsonl").open("w") as f:
            for row in rows:
                f.write(json.dumps(row) + "\n")
        print(f"<!-- {set_name}: {len(rows)} clips in {time.perf_counter() - t0:.0f} s, "
              f"smart-turn at {args.threads} threads -->", flush=True)
        print(tables(set_name, rows, modes), flush=True)


if __name__ == "__main__":
    main()
