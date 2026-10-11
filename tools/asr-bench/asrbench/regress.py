"""Regression check: silence before an abrupt start (#15, #18).

    python -m asrbench.regress --work $WORK [--preroll 0.8]

Parakeet TDT returns nothing when 0.3 s or more of silence comes right before
speech that starts abruptly, mid-word. A real mic rarely produces that, but a
longer pre-roll puts more silence in front of every Final, and AMI clips (cut
mid-word) with the harness's lead-in do produce it: at a 0.8 s pre-roll 14 of
200 AMI clips came back empty, at the app's 0.6 s 2 (#15).

This decodes the 16 AMI clips that went empty in any #15 run, with every
offline NeMo engine, through the offline session at the app's pre-roll (or
`--preroll`), after 0.5 s and 1 s of lead-in, and counts empty results. It
exits 1 when an engine has more empties than `BUDGET` allows, the count at the
app's settings when it was last measured. Run it after any change to the
pre-roll, the NeMo tail padding, or the session's handling of the audio before
an onset. Takes a few minutes.

Only Parakeet v3 shows the failure. The #25 engines (Parakeet 110M, v2,
Unified and Ultra) returned text on every clip, and the 0.3 s tail padding
(#25) took v3 from 2 and 3 empties to 1 and 2.
"""

import argparse
import sys
from pathlib import Path

from . import engines as E
from .backends import SAMPLE_RATE, OfflineTranscriber, silero_vad
from .run import load_audio, read_jsonl
from .session import OFFLINE_PREROLL_SAMPLES, agc, offline_session

CLIPS = [
    "AMI_EN2002c_H02_MEE071_0208990_0209204", "AMI_EN2002c_H03_MEE073_0280998_0281269",
    "AMI_EN2002d_H00_FEO070_0121596_0121866", "AMI_ES2004b_H01_FEE013_0183125_0183634",
    "AMI_ES2004c_H00_MEO015_0158012_0158431", "AMI_ES2004c_H03_FEE016_0137175_0137398",
    "AMI_ES2004d_H00_MEO015_0033228_0033450", "AMI_ES2004d_H00_MEO015_0124252_0124459",
    "AMI_ES2004d_H03_FEE016_0213692_0214036", "AMI_IS1009a_H03_FIO089_0028008_0028551",
    "AMI_IS1009d_H00_FIE088_0183135_0183441", "AMI_TS3003a_H03_MTD012ME_0082994_0083320",
    "AMI_TS3003b_H00_MTD009PM_0019337_0019787", "AMI_TS3003c_H03_MTD012ME_0147798_0148153",
    "AMI_TS3003d_H00_MTD009PM_0065854_0066283", "AMI_TS3003d_H02_MTD0010ID_0180917_0181412",
]

LEAD_INS = [0.5, 1.0]

# (engine, lead-in seconds) -> most empty results allowed, of len(CLIPS).
BUDGET = {
    ("parakeet-tdt-0-6b-v3", 0.5): 1,
    ("parakeet-tdt-0-6b-v3", 1.0): 2,
    ("canary-180m-flash", 0.5): 0,
    ("canary-180m-flash", 1.0): 0,
    **{(e, lead): 0
       for e in ("parakeet-tdt-110m-en", "parakeet-tdt-0-6b-v2", "parakeet-unified-en-0-6b", "parakeet-ultra")
       for lead in LEAD_INS},
}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--work", type=Path, required=True)
    ap.add_argument("--preroll", type=float, help="seconds before the VAD onset (default: the app's 0.6)")
    args = ap.parse_args()

    preroll = OFFLINE_PREROLL_SAMPLES if args.preroll is None else int(args.preroll * SAMPLE_RATE)
    manifest = {u["id"]: u for u in read_jsonl(args.work / "data" / "ami-ihm" / "manifest.jsonl")}
    audio = {c: agc(load_audio(manifest[c]["audio"])) for c in CLIPS}
    vad = silero_vad(args.work / "silero_vad.onnx")

    failed = False
    print(f"pre-roll {preroll / SAMPLE_RATE:g} s, {len(CLIPS)} AMI clips")
    for engine_id in sorted({e for e, _ in BUDGET}):
        engine = E.BY_ID[engine_id]
        rec = OfflineTranscriber(engine, args.work / "models" / engine.id, "en")
        for lead in LEAD_INS:
            empty = [c for c in CLIPS if not offline_session(audio[c], rec, vad, lead_silence=lead,
                                                             preroll=preroll).text.strip()]
            budget = BUDGET[(engine_id, lead)]
            ok = len(empty) <= budget
            failed |= not ok
            print(f"{'ok  ' if ok else 'FAIL'} {engine_id} lead-in {lead:g} s: {len(empty)} empty "
                  f"(budget {budget}) {' '.join(empty)}", flush=True)
        del rec
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
