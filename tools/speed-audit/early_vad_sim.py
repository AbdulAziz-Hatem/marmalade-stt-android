"""#91: how often the speculative Final's early VAD fires where the main VAD doesn't end the utterance.

Run from an asr-bench harness dir on the benchmark host (it imports `asrbench`):

    cp tools/speed-audit/early_vad_sim.py $WORK/harness-91/ && cd $WORK/harness-91
    ../venv/bin/python early_vad_sim.py --work .. --early 0.4,0.6,0.8 ls-clean ami-long dictation

Feeds each clip as the app's mic would (gain, 0.5 s lead, 2 s tail, 100 Hz high-pass) window by window into two
Silero detectors: the main one (1.2 s minimum silence) and the early one (`EarlyVad.minSilenceSeconds`, ~0.4 s).
`--early` tries other early silences (each rounded to whole windows below the main one). An early segment that ends where a main segment ends is a hit: in Pause mode both decodes run from the utterance's
pre-roll to that end (`withPreroll`), so the speculative text becomes the Final even when a pause split the early
VAD's segment. Any other is a miss: a decode of the utterance so far that the app throws away, the extra work the
prototype adds (the audio from the main segment's start to the early end). Prints per set:
utterances, hits, misses, misses per minute of speech, and the audio seconds the misses decode (decode cost is about
linear in it).
"""
import argparse
import sys
from pathlib import Path

import numpy as np

from asrbench.backends import silero_vad
from asrbench.run import load_audio, read_jsonl
from asrbench.session import SAMPLE_RATE, VAD_WINDOW_SIZE, agc, mic_hops

MAIN_SECONDS = 1.2
TARGET_SAMPLES = 6_400


def early_seconds(main: float, target: int = TARGET_SAMPLES) -> float:
    """EarlyVad.minSilenceSeconds (SpeedKnobs.kt), with its target as a parameter."""
    m = int(np.float32(SAMPLE_RATE) * np.float32(main))
    k = (m - target) // VAD_WINDOW_SIZE
    return (m - k * VAD_WINDOW_SIZE + 0.5) / SAMPLE_RATE


def segments(vad) -> list[tuple[int, int]]:
    out = []
    while not vad.empty():
        s = vad.front
        out.append((s.start, s.start + len(s.samples)))
        vad.pop()
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--work", type=Path, required=True)
    ap.add_argument("--limit", type=int, default=200)
    ap.add_argument("--early", default="0.4", help="early silences to try, seconds, comma-separated")
    ap.add_argument("sets", nargs="+")
    a = ap.parse_args()
    vad_model = a.work / "silero_vad.onnx"
    earlies = [early_seconds(MAIN_SECONDS, int(float(x) * SAMPLE_RATE)) for x in a.early.split(",")]
    print(f"main {MAIN_SECONDS} s, early {', '.join(f'{e:.4f}' for e in earlies)} s")
    print("set\tearly_s\tutts\tspeech_min\tmain_segs\thits\tmisses\tmisses_per_min\tmiss_audio_s\tfinal_audio_s")
    for name in a.sets:
        utts = read_jsonl(a.work / "data" / name / "manifest.jsonl")[:a.limit]
        n = main_segs = 0
        speech = final_audio = 0.0
        hits, misses, miss_audio = [0] * len(earlies), [0] * len(earlies), [0.0] * len(earlies)
        for u in utts:
            audio = agc(load_audio(str(a.work / "harness-91" / u["audio"])))
            speech += len(audio) / SAMPLE_RATE
            main = silero_vad(vad_model, MAIN_SECONDS)
            early = [silero_vad(vad_model, e) for e in earlies]
            buf = np.concatenate([hop for hop, _ in mic_hops(audio, tail_silence=2.0)])
            m_segs, e_segs = [], [[] for _ in earlies]
            for i in range(0, len(buf) - VAD_WINDOW_SIZE + 1, VAD_WINDOW_SIZE):
                w = buf[i:i + VAD_WINDOW_SIZE]
                main.accept_waveform(w)
                m_segs += segments(main)
                for v, segs in zip(early, e_segs):
                    v.accept_waveform(w)
                    segs += segments(v)
            main_ends = {e for _, e in m_segs}
            n += 1
            main_segs += len(m_segs)
            final_audio += sum(e - s for s, e in m_segs) / SAMPLE_RATE
            for j, segs in enumerate(e_segs):
                for _, end in segs:
                    if end in main_ends:
                        hits[j] += 1
                    else:
                        misses[j] += 1
                        start = min((s for s, e in m_segs if e > end), default=end)
                        miss_audio[j] += (end - start) / SAMPLE_RATE
        for j, e in enumerate(earlies):
            print(f"{name}\t{e:.3f}\t{n}\t{speech / 60:.1f}\t{main_segs}\t{hits[j]}\t{misses[j]}"
                  f"\t{misses[j] / (speech / 60):.2f}\t{miss_audio[j]:.0f}\t{final_audio:.0f}", flush=True)


if __name__ == "__main__":
    sys.exit(main())
