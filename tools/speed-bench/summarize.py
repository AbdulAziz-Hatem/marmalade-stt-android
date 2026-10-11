#!/usr/bin/env python3
"""Markdown tables from SpeedBenchmarkService result files (#23).

    python3 summarize.py results/pixel-*.json [--baseline parakeet-ultra]
    python3 summarize.py results/threads-*.json --sweep   # #34 thread sweep, #31 tunings

For each engine, thread count and ONNX Runtime tuning (#31; files from
before it are "cpu", the default) the last undisturbed turn wins. Load time,
RTF per clip length and pooled over the LibriSpeech clips, cost relative to
the baseline engine at the same thread count, a speed tier, first partial and
end of speech -> Final medians per length, peak RSS, WER on the LS clips, and
the highest thermal status seen.
"""

import argparse
import json
import statistics
import sys

GROUPS = ("ls02", "ls05", "ls10", "ls20", "ls28")


def tier(rel: float) -> str:
    if rel < 0.8:
        return f"faster ({1 / rel:.1f}x)"
    if rel <= 1.25:
        return "about the same"
    return f"slower by {rel:.1f}x"


def fmt(v, spec=".2f", scale=1.0):
    return "–" if v is None else format(v * scale, spec)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("files", nargs="+")
    ap.add_argument("--baseline", default="parakeet-ultra")
    ap.add_argument("--threads", type=int, default=4, help="thread count for --kotlin")
    ap.add_argument("--kotlin", action="store_true", help="also print SpeedBudget.REFERENCE entries")
    ap.add_argument("--cold", action="store_true", help="first dictation with and without the warm-up decode (#31)")
    ap.add_argument("--tuning", default="cpu", help="the tuning the main table and --kotlin use (#31)")
    ap.add_argument("--sweep", action="store_true",
                    help="per-clip median RTF by thread count and tuning (--ei repeats N runs)")
    args = ap.parse_args()

    turns = {}
    pooled = {}  # (engine, threads, arena on) -> every undisturbed default-tuning turn, for --kotlin
    devices = set()
    probes = []
    for path in args.files:
        run = json.load(open(path))
        devices.add(run.get("device", "?"))
        if run.get("probeMs") is not None:
            probes.append((run["probeMs"], run.get("probeThreads"), path))
        for r in run["results"]:
            if r.get("tuning", "cpu") in ARENA_TUNINGS and not r.get("disturbed"):
                pooled.setdefault((r["engine"], r["threads"], arena_on(r)), []).append(r)
            key = (r["engine"], r["threads"], r.get("tuning", "cpu"))
            if key in turns and r.get("disturbed") and not turns[key].get("disturbed"):
                continue
            turns[key] = r

    if args.sweep:
        return sweep(turns)
    if args.cold:
        return cold_starts(turns)
    turns = {(e, t): r for (e, t, u), r in turns.items() if u == args.tuning}

    base = {t: r["rtfByLength"]["ls"] for (e, t), r in turns.items() if e == args.baseline}
    print(f"Device: {', '.join(sorted(devices))}\n")
    print("| Engine | Threads | Load s | RTF 5 s | RTF 10 s | RTF 20 s | RTF 28 s | RTF LS | × Ultra | Tier "
          "| First partial s (2/5/10/20/28) | End of speech → Final s (2/5/10/20/28) | Peak RSS MB | Own MB | WER LS % "
          "| Thermal |")
    print("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|")
    for (engine, threads), r in sorted(turns.items(), key=lambda kv: (kv[1]["rtfByLength"]["ls"], kv[0][1])):
        rtf = r["rtfByLength"]
        rel = rtf["ls"] / base[threads] if threads in base else None
        s = (r.get("sessions") or {}).get("byLength", {})

        def lat(key):
            vals = [s.get(g, {}).get(key) for g in GROUPS]
            return "/".join(fmt(v, ".1f", 0.001) for v in vals)

        wers = [d["wer"] for d in r["decodes"] if d.get("wer") is not None]
        wer = statistics.mean(wers) * 100 if wers else None
        flag = " ⚠" if r.get("disturbed") else ""
        print(f"| {engine}{flag} | {threads} | {r['loadMs'] / 1000:.1f} | {fmt(rtf.get('ls05'), '.3f')} "
              f"| {fmt(rtf.get('ls10'), '.3f')} | {fmt(rtf.get('ls20'), '.3f')} | {fmt(rtf.get('ls28'), '.3f')} "
              f"| {rtf['ls']:.3f} | {fmt(rel)} | {tier(rel) if rel else '–'} | {lat('firstPartialMs')} "
              f"| {lat('endToFinalMs')} | {r['peakRssMb']} | {own_memory_mb(r)} | {fmt(wer, '.1f')} | {r['maxThermal']} |")

    # Thread scaling, for engines measured at more than one count.
    by_engine = {}
    for (engine, threads), r in turns.items():
        by_engine.setdefault(engine, {})[threads] = r["rtfByLength"]["ls"]
    multi = {e: t for e, t in by_engine.items() if len(t) > 1}
    if multi:
        counts = sorted({c for t in multi.values() for c in t})
        print("\n| Engine | " + " | ".join(f"RTF LS, {c} threads" for c in counts) + " | Speed-up |")
        print("|---|" + "---|" * (len(counts) + 1))
        for e, t in sorted(multi.items()):
            lo, hi = min(t), max(t)
            print(f"| {e} | " + " | ".join(fmt(t.get(c), ".3f") for c in counts) + f" | {t[lo] / t[hi]:.2f}× |")

    if args.kotlin:
        kotlin_reference(pooled, args.threads, args.baseline)
        for p in probes:
            # Not a PROBE_REFERENCE_MS: the service probes outside the top-app cpuset, slower than the app (#31).
            print(f"// service probe {p[0]} ms (at {p[1]} threads, {p[2]}); PROBE_REFERENCE_MS is the app's own probe")
        # Max's rule is "nothing slower than distil-small.en" (#45 removed it; see REFERENCE_BUDGET).
        distil = turns.get(("distil-small-en", args.threads))
        if distil and args.threads in base:
            print(f"// SpeedBudget.REFERENCE_BUDGET = {distil['rtfByLength']['ls'] / base[args.threads]:.2f}"
                  " (distil-small.en's cost; check both turns came from the same phone)")
        else:
            print("// SpeedBudget.REFERENCE_BUDGET: no distil-small-en turn at these threads (the app can't run it"
                  " since #45). Keep the current value, or add a pre-#45 result file from the same phone.")

# The tunings --kotlin reads: the default and the arena forced either way (#47).
ARENA_TUNINGS = ("cpu", "cpu-arena", "cpu-noarena")


def arena_on(r) -> bool:
    """Whether ONNX Runtime's memory arena was on (recorded since #47; on before)."""
    return r.get("arena", True)


def first_decodes(r):
    """The first timed decode of each clip, leaving out the clip the warm-up
    already decoded (#47): dictation's utterances are each a new length, and
    without the arena a new length is the slow decode. Files from before #47
    warmed up on their first clip."""
    warm = r.get("warmup", r["decodes"][0]["clip"] if r["decodes"] else None)
    seen, out = set(), []
    for d in r["decodes"]:
        if d["clip"] not in seen and d["clip"] != warm:
            out.append(d)
        seen.add(d["clip"])
    return out


def first_rtf(rs) -> float:
    """Pooled RTF over the first decodes of turns [rs]."""
    ds = [d for r in rs for d in first_decodes(r)]
    return sum(d["decodeMs"] for d in ds) / sum(d["audioMs"] for d in ds)


def kotlin_reference(pooled, threads, baseline) -> None:
    """SpeedBudget.REFERENCE rows (#47): cost = first-decode RTF / Parakeet
    Ultra's, both with the arena; memory with the arena; and a LeanMode
    (memory and cost factor without the arena) where both were measured.
    Every turn of every file given is pooled."""
    base = pooled.get((baseline, threads, True))
    print(f"\n// SpeedBudget.REFERENCE, {threads} threads: cost vs Parakeet Ultra (both with ORT's arena,"
          " first decode of each length), memory MB, lean mode (arena off)")
    print("// memory: see reference_memory_mb (the engine's own peak at the 28 s utterance cap)")
    if not base:
        print(f"// no {baseline} turn with the arena at {threads} threads: no costs")
        return
    base_rtf = first_rtf(base)
    engines = sorted({e for (e, t, _) in pooled if t == threads})
    for engine in engines:
        on, off = pooled.get((engine, threads, True)), pooled.get((engine, threads, False))
        if not on:
            print(f"// {engine}: no turn with the arena")
            continue
        key = "SttEngineCatalog.PARAKEET_ULTRA" if engine == baseline else f'"{engine}"'
        mem = max(reference_memory_mb(r) for r in on)
        lean = ""
        if off:
            lean = (f", LeanMode({max(reference_memory_mb(r) for r in off)}, "
                    f"{first_rtf(off) / first_rtf(on):.2f})")
        print(f"        {key} to EngineSpeed({first_rtf(on) / base_rtf:.2f}, {mem}{lean}), "
              f"// {len(on)}+{len(off or [])} turns")


# SpeedBudget.REFERENCE's memory rule (#31 review, #47): the engine's own peak
# (the turn's peak minus the process baseline before its load, so an earlier
# turn's leftovers don't count) plus a clean process's baseline. Offline
# engines decode up to MAX_UTTERANCE_SECONDS (28 s) at once, and their memory
# grows with the utterance, so their turn must include a clip of about 28 s
# (ls28-*, #47). A turn without one (files from before #47) gets x1.54, how much
# Parakeet Ultra's grew with ORT's arena from clips up to 20 s to one 30.6 s
# utterance (emulator, docs/benchmarks/2026-10-pixel8a-ort.md). Streaming
# Nemotron decodes fixed chunks and Whisper a fixed 30 s window: theirs
# doesn't grow.
PROCESS_BASELINE_MB = 180
LONG_CLIP_MS = 25_000
LONG_UTTERANCE_GROWTH = 1.54
FIXED_WINDOW_FAMILIES = ("STREAMING_TRANSDUCER", "WHISPER")


def own_memory_mb(r) -> int:
    return r["peakRssMb"] - r.get("baselineRssMb", PROCESS_BASELINE_MB)


def reference_memory_mb(r) -> int:
    own = own_memory_mb(r)
    long_clip = any(d["audioMs"] >= LONG_CLIP_MS for d in r["decodes"])
    if r.get("family") not in FIXED_WINDOW_FAMILIES and not long_clip:
        print(f"// {r['engine']}: no ~28 s clip in this turn, memory x{LONG_UTTERANCE_GROWTH}", file=sys.stderr)
        own *= LONG_UTTERANCE_GROWTH
    return -(-round(own + PROCESS_BASELINE_MB) // 10) * 10  # up to 10 MB


def sweep(turns) -> None:
    """One row per engine, thread count and tuning: median RTF per clip over
    the repeats (min–max), relative to the engine's best row, the thermal
    status / battery temperature before and after the turn, and how many
    threads ran on the pinned cores (#31)."""
    by_engine = {}
    for (engine, threads, tuning), r in turns.items():
        by_engine.setdefault(engine, {})[(threads, tuning)] = r
    for engine, rows in sorted(by_engine.items()):
        clips = sorted({d["clip"] for r in rows.values() for d in r["decodes"]})

        def med(r, clip):
            return statistics.median(d["rtf"] for d in r["decodes"] if d["clip"] == clip)

        def total(r):
            return sum(med(r, c) for c in clips)

        best = min(rows.values(), key=total)
        print(f"\n{engine}\n")
        print("| Threads | Tuning | " + " | ".join(f"RTF {c} (min–max)" for c in clips)
              + " | vs best | Thermal | Battery °C | Battery % | Load s | Threads on pinned cores |")
        print("|---|---|" + "---|" * (len(clips) + 6))
        for (threads, tuning), r in sorted(rows.items()):
            cells = []
            for c in clips:
                v = [d["rtf"] for d in r["decodes"] if d["clip"] == c]
                cells.append(f"{statistics.median(v):.3f} ({min(v):.3f}–{max(v):.3f})")
            b, a = r["envBefore"], r["envAfter"]
            flag = " ⚠" if r.get("disturbed") else ""
            pinned = r.get("pinnedCores")
            on_pinned = "–"
            if pinned:
                # Android intersects the pin with the process's cpuset (a service: cores 0-7 on a Pixel 8a).
                on_pinned = str(sum(n for cpus, n in (r.get("threadAffinity") or {}).items()
                                    if parse_cpu_list(cpus) <= set(pinned)))
            print(f"| {threads}{flag} | {tuning} | " + " | ".join(cells) + f" | {total(r) / total(best):.2f}× "
                  f"| {b['thermal']}→{a['thermal']} (max {r['maxThermal']}) | {b['batteryTempC']:.1f}→{a['batteryTempC']:.1f} "
                  f"| {b['batteryPct']}→{a['batteryPct']}{'' if b.get('plugged') else ' (unplugged)'} "
                  f"| {r['loadMs'] / 1000:.1f} | {on_pinned} |")


def cold_starts(turns) -> None:
    """#31: first dictation after a plain load (cold) and after warmUp's
    silence decode (coldWarmed), against the steady-state median, same clip."""
    print("\n| Engine | Tuning | Clip | First partial s: cold / warmed / steady | End of speech → Final s: cold / warmed / steady | Warm-up decode s |")
    print("|---|---|---|---|---|---|")
    for (engine, threads, tuning), r in sorted(turns.items()):
        s = r.get("sessions") or {}
        if "coldWarmed" not in s:
            continue
        clip = s["cold"]["clip"]
        steady = [x for x in s["runs"] if x["clip"] == clip]

        def trio(key):
            seen = [x[key] for x in steady if x.get(key) is not None]
            vals = [s["cold"].get(key), s["coldWarmed"].get(key), statistics.median(seen) if seen else None]
            return " / ".join(fmt(v, ".2f", 0.001) for v in vals)

        print(f"| {engine} | {tuning} | {clip} | {trio('firstPartialMs')} | {trio('endToFinalMs')} "
              f"| {fmt(s.get('warmUpMs'), '.2f', 0.001)} |")


def parse_cpu_list(text) -> set:
    """"4-7,9" (/proc/<pid>/status's Cpus_allowed_list) -> {4, 5, 6, 7, 9}."""
    cpus = set()
    for part in text.split(","):
        a, _, b = part.partition("-")
        cpus.update(range(int(a), int(b or a) + 1))
    return cpus


if __name__ == "__main__":
    sys.exit(main())
