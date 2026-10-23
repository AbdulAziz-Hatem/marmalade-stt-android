#!/usr/bin/env python3
"""#91: turn pass logs into one row per trial (TSV) and per-group medians.

Usage: summarize.py <dir>... [--tsv out.tsv]
All times in ms. Stamps are SystemClock.elapsedRealtime from the app's own lines.
"""
import glob, os, re, statistics as st, sys

MARGIN = 50  # the clips are trimmed to 50 ms before/after their speech
LEAD = 300   # ClipMic's zeros before the clip


def num(pat, txt, cast=float, default=None):
    m = re.search(pat, txt)
    return cast(m.group(1)) if m else default


def parse(path):
    txt = open(path, errors="replace").read()
    m = re.search(r"listen (\w+) (\S+) loaded=(\w+) process=(-?\d+)ms at=(\d+): (.*)", txt)
    if not m:
        return None
    surface, engine, loaded, proc, req, stages = m.groups()
    req = int(req)
    stg = dict((k, int(v)) for k, v in (kv.split("=") for kv in stages.split()))
    r = dict(trial=os.path.basename(path)[:-4], surface=surface, engine=engine, loaded=loaded, process_ms=int(proc))
    for k in ["mic", "ready", "level", "speech", "partial", "final"]:
        r[k] = stg.get(k)
    r["load_ms"] = num(r"Loaded \S+ in (\d+) ms", txt, int)
    r["vad_create"] = 1 if "Silero VAD ready" in txt else 0
    r["smart_turn_load"] = 1 if "Smart Turn loaded" in txt else 0
    clip_open = num(r"clip mic open t=(\d+)", txt, int)
    clip_end = num(r"clip speech end t=(\d+)", txt, int)
    sent = [int(x) for x in re.findall(r"final sent t=(\d+)", txt)]
    deliv = re.findall(r"(\w+) delivered t=(\d+) took=(\d+)ms", txt)
    stop = num(r"stop requested t=(\d+)", txt, int)
    fd = re.findall(r"final decode (\d+) ms for (\d+) ms of audio(?: \(speculative, decoded (\d+) ms (-?\d+) ms after the speech's end\))?; at its start the VAD was (-?\d+) ms past the speech's end, the mic (-?\d+) ms", txt)
    if clip_open is not None:
        onset = clip_open + LEAD + MARGIN
        r["req_to_clip"] = clip_open - req
        if r["partial"] is not None:
            r["onset_to_partial"] = req + r["partial"] - onset
        if r["speech"] is not None:
            r["onset_to_vad"] = req + r["speech"] - onset
    if clip_end is not None:
        eos = clip_end - MARGIN
        if sent:
            r["eos_to_sent"] = sent[0] - eos
        if deliv:
            r["eos_to_delivered"] = int(deliv[0][1]) - eos
    if stop is not None and surface == "keyboard":
        r["eos_to_stop"] = stop - clip_end + MARGIN if clip_end else None
        if sent:
            r["stop_to_sent"] = sent[-1] - stop
        if deliv:
            r["stop_to_delivered"] = int(deliv[-1][1]) - stop
    if deliv:
        r["cleanup_commit"] = int(deliv[0][2])
        if sent:
            r["sent_to_delivered"] = int(deliv[0][1]) - sent[0]
    if fd:
        d, a, sd, sa, v, mic = fd[0]
        d, a, v, mic = int(d), int(a), int(v), int(mic)
        if sd:
            r.update(spec_decode=int(sd), spec_done_after_end=int(sa), spec_hit=1)
        elif "speculative decode" in txt:
            r["spec_hit"] = 0
        # Builds before the fix read the mic's position after the decode (FIXED_MIC marks the new log).
        mic_at_start = mic if "FIXED_MIC" in os.environ else mic - d
        r.update(final_decode=d, final_audio=a, wait_vad=v, wait_mic=mic_at_start, backlog=mic_at_start - v)
    if "speculative decode " in txt:
        # Every speculative decode, the ones a Final used, and the clip's length (for wasted decodes per minute).
        r["spec_decodes"] = txt.count("speculative decode ")
        r["spec_wasted"] = r["spec_decodes"] - txt.count("(speculative, decoded")
    r["clip_ms"] = num(r"clip mic open t=\d+ lead=\d+ms clip=(\d+)ms", txt, int)
    r["parity_diff"] = txt.count("speculation parity: DIFFERENT") if "speculation parity" in txt else None
    ses = re.search(r"session vad=(\d+)ms/(\d+)win max=(\d+)us partials=(\d+) total=(\d+)ms median=(\d+)ms max=(\d+)ms", txt)
    if ses:
        vad, win, vmax, n, tot, med, mx = map(int, ses.groups())
        r.update(vad_ms=vad, vad_win=win, vad_max_us=vmax, partials=n, partial_total=tot, partial_median=med, partial_max=mx)
    sm = re.findall(r"smart turn (\d+) ms: p=([\d.Ee-]+) after (\d+) ms of silence", txt)
    if sm:
        r["st_checks"] = len(sm)
        r["st_ms"] = ",".join(x[0] for x in sm)
        r["st_last_silence"] = int(sm[-1][2])
    r["warmup"] = num(r"Warm-up decode of \S+: (\d+) ms", txt, int)
    return r


COLS = ["trial", "surface", "engine", "loaded", "process_ms", "mic", "ready", "load_ms", "req_to_clip", "onset_to_vad",
        "onset_to_partial", "partials", "partial_median", "partial_max", "partial_total", "vad_ms", "vad_win", "vad_max_us",
        "st_checks", "st_ms", "st_last_silence", "eos_to_stop", "wait_vad", "wait_mic", "backlog", "final_decode", "final_audio",
        "spec_hit", "spec_decode", "spec_done_after_end", "eos_to_sent", "stop_to_sent", "cleanup_commit", "sent_to_delivered", "eos_to_delivered", "stop_to_delivered", "parity_diff", "spec_decodes", "spec_wasted", "clip_ms"]


def main():
    args = sys.argv[1:]
    tsv = None
    if "--tsv" in args:
        i = args.index("--tsv")
        tsv = args[i + 1]
        del args[i:i + 2]
    rows = []
    for d in args:
        for f in sorted(glob.glob(os.path.join(d, "*.log"))):
            r = parse(f)
            if r:
                rows.append(r)
            else:
                print(f"no listen line: {f}", file=sys.stderr)
    lines = ["\t".join(COLS)] + ["\t".join("" if r.get(c) is None else str(r.get(c)) for c in COLS) for r in rows]
    if tsv:
        open(tsv, "w").write("\n".join(lines) + "\n")
    groups = {}
    for r in rows:
        tag = r["trial"].split("-")[-2] if r["trial"].count("-") >= 3 else ""
        groups.setdefault((r["engine"], r["surface"], tag + ("" if r["loaded"] == "false" or r["surface"] == "dictate" else "/warm")), []).append(r)
    numeric = [c for c in COLS[4:] if c not in ("st_ms", "loaded")]
    for (eng, surf, tag), rs in sorted(groups.items()):
        print(f"\n## {eng} / {surf} / {tag}  (n={len(rs)})")
        for c in numeric:
            vals = [r[c] for r in rs if isinstance(r.get(c), (int, float))]
            if not vals:
                continue
            q = st.quantiles(vals, n=4) if len(vals) >= 4 else [min(vals), st.median(vals), max(vals)]
            print(f"  {c:18s} median {st.median(vals):8.0f}   IQR {q[0]:7.0f}–{q[-1]:7.0f}   range {min(vals):7.0f}–{max(vals):7.0f}  (n={len(vals)})")


if __name__ == "__main__":
    main()
