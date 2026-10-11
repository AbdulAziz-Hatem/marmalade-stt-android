"""The session ports: high-pass filter and the offline loop's segment/flush flow."""

import math
import re
import unittest
from pathlib import Path

import numpy as np

from asrbench import engines as E
from asrbench.session import (PARTIAL_WINDOW_SAMPLES, WHISPER_DECODE_SECONDS, HighPass, LagMonitor, SimClock,
                              SoftAgc, agc, direct_offline, max_decode_samples, mic_hops, offline_session,
                              online_session,
                              quietest_window)

UTTERANCE_CAP = Path(__file__).resolve().parents[3] / "app/src/main/java/app/marmalade/stt/speech/UtteranceCap.kt"
SHERPA_BACKENDS = UTTERANCE_CAP.parent / "SherpaBackends.kt"


def kotlin_high_pass(x, cutoff=100.0, sr=16_000):
    """AudioPreprocessor.process, sample by sample, as a reference."""
    omega = 2 * math.pi * cutoff / sr
    cosw, alpha = math.cos(omega), math.sin(omega) / (2 * 0.70710678)
    a0 = 1 + alpha
    b0, b1, b2 = (1 + cosw) / 2 / a0, -(1 + cosw) / a0, (1 + cosw) / 2 / a0
    a1, a2 = -2 * cosw / a0, (1 - alpha) / a0
    z1 = z2 = 0.0
    y = []
    for xi in x.astype(np.float64).tolist():  # Kotlin computes in Double
        yi = b0 * xi + z1
        z1 = b1 * xi - a1 * yi + z2
        z2 = b2 * xi - a2 * yi
        y.append(yi)
    return np.array(y, np.float32)


class FakeVad:
    """Energy VAD with sherpa's queue API: a segment closes after 5 quiet windows.

    Like Silero at mic open, it can notice speech late: with `late` > 0 the
    segment starts only at the `late`-th loud window, so the earlier ones
    (the first word) aren't in it. `start` counts samples since reset().
    """

    def __init__(self, late: int = 0, loud_above: float = 0.1):
        self.late = late
        self.loud_above = loud_above
        self.reset()

    def reset(self):
        self.queue, self.current, self.quiet, self.speaking = [], [], 0, False
        self.fed, self.loud_run, self.start = 0, 0, 0

    def accept_waveform(self, w):
        loud = np.abs(w).max() > self.loud_above
        self.fed += len(w)
        self.loud_run = self.loud_run + 1 if loud else 0
        if loud and not self.speaking and self.loud_run > self.late:
            self.speaking, self.start = True, self.fed - len(w)
        if loud:
            self.quiet = 0
        if self.speaking:
            self.current.append(w)
            if not loud:
                self.quiet += 1
                if self.quiet == 5:
                    self.queue.append((self.start, np.concatenate(self.current)))
                    self.current, self.speaking = [], False

    def is_speech_detected(self):
        return self.speaking

    def empty(self):
        return not self.queue

    @property
    def front(self):
        start, samples = self.queue[0]
        return type("Segment", (), {"start": start, "samples": samples})

    def pop(self):
        self.queue.pop(0)

    def flush(self):
        if self.current:
            self.queue.append((self.start, np.concatenate(self.current)))
            self.current, self.speaking = [], False


class LengthTranscriber:
    def transcribe(self, x):
        return f" seg{len(x)} "


class SessionTest(unittest.TestCase):
    def test_high_pass_matches_kotlin_across_hops(self):
        x = np.random.default_rng(1).standard_normal(16_000).astype(np.float32)
        hp = HighPass()
        hopped = np.concatenate([hp.process(x[i:i + 1600]) for i in range(0, len(x), 1600)])
        np.testing.assert_allclose(hopped, kotlin_high_pass(x), atol=1e-6)

    def test_mic_hops_are_100ms_and_padded(self):
        hops = [h for h, pre in mic_hops(np.zeros(2_000, np.float32), tail_silence=0.5, lead_silence=0.25)]
        self.assertTrue(all(len(h) == 1600 for h in hops))
        self.assertEqual(len(hops), 9)  # 4000 + 2000 + 8000 samples -> 14000 -> 9 hops
        self.assertLess(np.abs(np.concatenate(hops)).max(), 0.01)  # the tail is a faint floor

    def test_agc_levels_quiet_clips_within_limits(self):
        rng = np.random.default_rng(2)
        quiet = (rng.standard_normal(16_000) * 0.001).astype(np.float32)  # -60 dBFS
        loud = (rng.standard_normal(16_000) * 0.1).astype(np.float32)  # -20 dBFS
        rms_db = lambda x: 20 * np.log10(np.sqrt(np.mean(x.astype(np.float64) ** 2)))
        self.assertAlmostEqual(rms_db(agc(quiet)) - rms_db(quiet), 30.0, places=3)  # capped at +30 dB
        out = agc(loud)
        self.assertLessEqual(np.abs(out).max(), 10 ** (-1 / 20) + 1e-6)  # peak cap wins
        self.assertTrue(np.array_equal(agc(np.zeros(100, np.float32)), np.zeros(100)))

    def test_soft_agc_lifts_quiet_input_and_leaves_loud_input(self):
        rms_db = lambda x: 20 * np.log10(np.sqrt(np.mean(x.astype(np.float64) ** 2)))
        rng = np.random.default_rng(3)
        quiet = [(rng.standard_normal(1600) * 10 ** (-60 / 20)).astype(np.float32) for _ in range(5)]
        g = SoftAgc()
        out = [g.process(h) for h in quiet]
        self.assertAlmostEqual(rms_db(out[0]) - rms_db(quiet[0]), 30.0, places=3)  # full gain from the start
        loud = (rng.standard_normal(1600) * 10 ** (-15 / 20)).astype(np.float32)
        self.assertTrue(np.array_equal(g.process(loud), loud))  # never attenuates, no lag on a loud hop
        # A quiet hop right after the loud one gets little gain (the envelope releases at 3 dB/s).
        after = quiet[0]
        self.assertLess(rms_db(g.process(after)) - rms_db(after), 1.0)
        spiky = np.zeros(1600, np.float32)
        spiky[5] = 0.05  # low RMS, -26 dBFS peak
        self.assertLessEqual(np.abs(SoftAgc().process(spiky)).max(), 10 ** (-1 / 20) + 1e-6)

    def test_first_word_at_mic_open_survives_a_late_onset(self):
        # Speech from the first sample; the VAD notices it 10 windows (0.32 s) late.
        tone = (0.5 * np.sin(np.arange(16_000) * 0.3)).astype(np.float32)
        res = offline_session(tone, LengthTranscriber(), FakeVad(late=10), tail_silence=1.0, lead_silence=0.0)
        self.assertEqual(len(res.finals), 1)
        self.assertFalse(res.flushed_tail)
        # The Final covers the whole tone, from the mic's first sample.
        self.assertGreaterEqual(int(res.finals[0][3:]), 16_000)
        # Without the pre-roll, the segment alone misses the first 10 windows.
        bare = offline_session(tone, LengthTranscriber(), FakeVad(late=10), tail_silence=1.0, lead_silence=0.0,
                               preroll=0)
        self.assertEqual(int(res.finals[0][3:]) - int(bare.finals[0][3:]), 10 * 512)

    def test_continuous_speech_is_cut_at_the_cap_and_keeps_its_tail(self):
        # 40 s of speech with no pause the VAD could end at, and one quiet gap at 27.8 s (#48).
        audio = (0.5 * np.sin(np.arange(40 * 16_000) * 0.3)).astype(np.float32)
        audio[int(27.8 * 16_000):int(27.9 * 16_000)] = 0.001
        res = offline_session(audio, LengthTranscriber(), FakeVad(), tail_silence=0.0, lead_silence=0.0)
        self.assertEqual(res.forced_cuts, 1)
        self.assertLessEqual(max(res.final_samples), 28 * 16_000 + 9_600)
        # The cut is in the gap, and the two parts cover the whole 40 s (less the VAD's last part-window).
        self.assertTrue(int(27.8 * 16_000) <= res.final_samples[0] <= int(27.9 * 16_000))
        self.assertGreaterEqual(sum(res.final_samples), 40 * 16_000 - 512)

    def test_whisper_in_hindi_is_cut_at_21_s(self):
        # 35 s of speech with no pause the VAD could end at, and one quiet gap at 20.2 s (#54).
        audio = (0.5 * np.sin(np.arange(35 * 16_000) * 0.3)).astype(np.float32)
        audio[int(20.2 * 16_000):int(20.3 * 16_000)] = 0.001
        cap = max_decode_samples(E.WHISPER, "hi")
        self.assertEqual(cap, 21 * 16_000)
        res = offline_session(audio, LengthTranscriber(), FakeVad(), tail_silence=0.0, lead_silence=0.0,
                              max_decode=cap)
        self.assertEqual(res.forced_cuts, 1)
        self.assertTrue(int(20.2 * 16_000) <= res.final_samples[0] <= int(20.3 * 16_000))
        self.assertLessEqual(max(res.final_samples), cap)
        # Other languages and families keep 28 s plus the pre-roll.
        for family, lang in ((E.WHISPER, "en"), (E.WHISPER, ""), (E.NEMO_TRANSDUCER, "hi"), (E.QWEN3_ASR, "hi")):
            self.assertEqual(max_decode_samples(family, lang), 28 * 16_000 + 9_600)

    @unittest.skipUnless(UTTERANCE_CAP.exists(), "needs the repo checkout around tools/asr-bench")
    def test_whisper_decode_seconds_are_the_apps(self):
        body = re.search(r"WHISPER_DECODE_SECONDS = mapOf\((.*?)\)", UTTERANCE_CAP.read_text()).group(1)
        app = {lang: float(s) for lang, s in re.findall(r'"([^"]+)" to ([0-9.]+)f', body)}
        self.assertTrue(app)
        self.assertEqual(WHISPER_DECODE_SECONDS, app)

    @unittest.skipUnless(SHERPA_BACKENDS.exists(), "needs the repo checkout around tools/asr-bench")
    def test_whisper_token_budgets_are_the_apps(self):
        from asrbench.backends import WHISPER_TOKEN_BUDGETS, whisper_max_new_tokens
        body = re.search(r"WHISPER_TOKEN_BUDGETS = mapOf\((.*?)\n", SHERPA_BACKENDS.read_text()).group(1)
        app = {lang: (int(k), int(c)) for lang, k, c in re.findall(r'"([^"]+)" to \((\d+) to (\d+)\)', body)}
        self.assertTrue(app)
        self.assertEqual(WHISPER_TOKEN_BUDGETS, app)
        self.assertEqual(whisper_max_new_tokens("ja", 6.86), 79)
        self.assertEqual(whisper_max_new_tokens("hi", 12.0), 250)
        self.assertIsNone(whisper_max_new_tokens("zh", 6.86))

    def test_preroll_never_reaches_into_the_previous_utterance(self):
        tone = (0.5 * np.sin(np.arange(8_000) * 0.3)).astype(np.float32)
        gap = np.zeros(8_000, np.float32)  # 0.5 s: shorter than the pre-roll
        audio = np.concatenate([tone, gap, tone])
        res = offline_session(audio, LengthTranscriber(), FakeVad(), tail_silence=1.0, lead_silence=0.0)
        bare = offline_session(audio, LengthTranscriber(), FakeVad(), tail_silence=1.0, lead_silence=0.0, preroll=0)
        self.assertEqual(len(res.finals), 2)
        self.assertEqual(res.finals[0], bare.finals[0])  # nothing before the mic's first sample
        # The second Final gets the part of the gap after the first segment closed
        # (5 quiet windows in), not a full pre-roll reaching back into the first tone.
        extra = int(res.finals[1][3:]) - int(bare.finals[1][3:])
        self.assertGreater(extra, 0)
        self.assertLessEqual(extra, 8_000 - 5 * 512)

    class Rec:
        """Streaming recognizer whose text is the number of samples fed; records each chunk."""

        def create_stream(self):
            return type("S", (), {"fed": 0, "chunks": None, "input_finished": lambda s: None})()

        def accept(self, s, x):
            s.chunks = (s.chunks or []) + [np.array(x)]
            s.fed += len(x)

        def decode(self, s):
            pass

        def text(self, s):
            return f"fed{s.fed}"

    def test_online_stream_pads_the_preroll_with_silence_at_mic_open(self):
        tone = (0.5 * np.sin(np.arange(16_000) * 0.3) + 0.01).astype(np.float32)
        streams = []
        rec = self.Rec()
        create = rec.create_stream
        rec.create_stream = lambda: streams.append(create()) or streams[-1]
        online_session(tone, rec, FakeVad(late=10), tail_silence=1.0, lead_silence=0.0)
        pad = streams[0].chunks[0]
        # Detected after 11 loud windows: silence makes up the rest of the 1 s pre-roll.
        self.assertEqual(len(pad), 16_000 - 11 * 512)
        self.assertFalse(pad.any())
        self.assertTrue(streams[0].chunks[1].any())

    def test_online_stream_gets_the_streaming_preroll(self):
        class Rec:
            def create_stream(self):
                return type("S", (), {"fed": 0, "input_finished": lambda s: None})()

            def accept(self, s, x):
                s.fed += len(x)

            def decode(self, s):
                pass

            def text(self, s):
                return f"fed{s.fed}"

        quiet = np.zeros(32_000, np.float32)
        tone = (0.5 * np.sin(np.arange(16_000) * 0.3)).astype(np.float32)
        res = online_session(np.concatenate([quiet, tone]), Rec(), FakeVad(), tail_silence=1.0, lead_silence=0.0)
        # 1 s of pre-roll + the 1 s tone + the 5 windows that close the segment (+ hop slack).
        fed = int(res.finals[0][3:])
        self.assertGreaterEqual(fed, 16_000 + 16_000)
        self.assertLess(fed, 16_000 + 16_000 + 5 * 512 + 2 * 1600)

    def test_closed_segment_then_flushed_tail(self):
        tone = (0.5 * np.sin(np.arange(16_000) * 0.3)).astype(np.float32)
        quiet = np.zeros(16_000, np.float32)
        audio = np.concatenate([quiet, tone, quiet, tone[:8000]])
        res = offline_session(audio, LengthTranscriber(), FakeVad(), tail_silence=0.0, lead_silence=0.0)
        self.assertEqual(len(res.finals), 2)
        self.assertTrue(all(f.startswith("seg") for f in res.finals))  # trimmed
        self.assertTrue(res.flushed_tail)
        # The flushed tail starts a pre-roll before onset.
        self.assertGreater(int(res.finals[1][3:]), 8000)


    def test_near_silence_is_judged_before_soft_agc(self):
        # SoftAgc lifts a -60 dBFS tone by 30 dB, enough for the VAD; the
        # judgement still sees -60 (NearSilence, #20).
        tone = np.sin(np.arange(16_000) * 0.3).astype(np.float32)
        for level_db, near_silent in ((-60, True), (-20, False)):
            x = (tone * np.sqrt(2) * 10 ** (level_db / 20)).astype(np.float32)
            for session in (offline_session, online_session):
                rec = LengthTranscriber() if session is offline_session else self.Rec()
                res = session(x, rec, FakeVad(loud_above=0.01), tail_silence=1.0, lead_silence=0.0, soft_agc=True)
                self.assertEqual(res.near_silent, [near_silent], (session.__name__, level_db))
                self.assertAlmostEqual(res.rms_db[0], level_db, delta=4)  # the segment has some quiet in it

    def test_direct_decodes_record_the_clip_level(self):
        x = (np.sin(np.arange(16_000) * 0.3) * np.sqrt(2) * 10 ** (-60 / 20)).astype(np.float32)
        res = direct_offline(x, LengthTranscriber())
        self.assertEqual(res.near_silent, [True])
        self.assertAlmostEqual(res.rms_db[0], -60, delta=0.5)
        self.assertEqual(direct_offline(x, type("Mute", (), {"transcribe": lambda self, x: ""})()).rms_db, [])

    def test_quietest_window_finds_the_gap(self):
        x = np.full(10_000, 0.5, np.float32)
        x[5_000:6_000] = 0
        cut = quietest_window(x, 0, 10_000)
        self.assertTrue(5_000 <= cut <= 6_000 - 512)
        self.assertEqual(quietest_window(x, 0, 300), 300)

    def test_partials_never_delay_a_final_and_stay_short(self):
        # 20 s of speech (a pause at 12 s ends the first segment) with 1 s decodes on virtual time.
        tone = (0.5 * np.sin(np.arange(16_000 * 12) * 0.3)).astype(np.float32)
        audio = np.concatenate([tone, np.zeros(8_000, np.float32), tone[:16_000 * 8]])
        clock = SimClock(lambda _audio_s, _wall_s: 1.0)
        res = offline_session(audio, LengthTranscriber(), FakeVad(), tail_silence=1.0, lead_silence=0.0,
                              partials=True, clock=clock)
        partials = [(t, n) for kind, t, n in res.events if kind == "partial"]
        self.assertGreater(len(partials), 4)
        self.assertTrue(all(n <= PARTIAL_WINDOW_SAMPLES + 2 * 1600 for _, n in partials))
        for (a, _), (b, _) in zip(partials, partials[1:]):
            self.assertGreaterEqual(b - 1.0 - a, 1.0 - 1e-9)  # b started at least 1 s after a ended
        self.assertEqual(len(res.finals), 2)

    def test_partial_texts_track_committed_text_and_reset_per_utterance(self):
        class WordsTranscriber:
            """One word per 0.5 s decoded, with Whisper's full stop."""

            def transcribe(self, x):
                return " " + " ".join(f"w{i}" for i in range(len(x) // 8_000)) + "."

        tone = (0.5 * np.sin(np.arange(16_000 * 12) * 0.3)).astype(np.float32)
        audio = np.concatenate([tone, np.zeros(16_000, np.float32), tone[:16_000 * 3]])
        clock = SimClock(lambda _audio_s, _wall_s: 0.5)
        res = offline_session(audio, WordsTranscriber(), FakeVad(), tail_silence=1.0, lead_silence=0.0,
                              partials=True, clock=clock)
        texts = res.partial_texts
        self.assertEqual({p.utterance for p in texts}, {0, 1})
        self.assertTrue(all(a.time < b.time for a, b in zip(texts, texts[1:])))
        self.assertFalse(any(t.endswith(".") for p in texts for t in (p.raw, p.stabilized)))
        # Past PARTIAL_WINDOW_SAMPLES the older audio's text is committed: raw is it plus the new decode.
        self.assertTrue(any(p.raw.split(" ").count("w0") == 2 for p in texts if p.utterance == 0))
        # The stabilizer starts over at the second onset.
        second = [p for p in texts if p.utterance == 1]
        self.assertEqual(second[0].stabilized, second[0].raw)
        self.assertEqual([u for u, _ in res.utterance_finals], [0, 1])
        self.assertNotIn("w5", second[0].stabilized)
        # Without partials nothing is recorded.
        bare = offline_session(audio, WordsTranscriber(), FakeVad(), tail_silence=1.0, lead_silence=0.0)
        self.assertEqual(bare.partial_texts, [])
        self.assertEqual(bare.finals, res.finals)


    def test_lag_monitor_matches_the_kotlin_cases(self):
        """LagMonitorTest.kt's cases."""
        def feed(m, steps):
            return [e for c, p in steps for e in m.update(c, p)]

        m = LagMonitor()
        self.assertEqual(feed(m, [(i, i - 10) for i in range(1, 301)]), [])
        self.assertEqual(m.max_lag_hops, 10)
        m = LagMonitor()
        self.assertEqual([m.update(100, 84), m.update(105, 90), m.update(110, 94), m.update(111, 101),
                          m.update(112, 108)], [[], [], ["behind"], [], ["caught_up"]])
        m = LagMonitor()
        self.assertEqual(m.update(100, 70) + m.update(101, 100), [])
        self.assertEqual(m.max_lag_hops, 30)
        m = LagMonitor()
        self.assertEqual(feed(m, [(i, i // 2) for i in range(1, 601)]), ["behind", "too_slow"])
        m = LagMonitor()
        self.assertEqual(feed(m, [(i, max(i - 20, 0)) for i in range(1, 221)]), ["behind"])
        m = LagMonitor()
        self.assertEqual(feed(m, [(100, 70), (110, 80), (150, 149), (300, 250), (310, 260), (320, 319)]),
                         ["behind", "caught_up", "behind", "caught_up"])

    def test_offline_catch_up_changes_nothing_when_the_backlog_drains_at_once(self):
        # Two 10 s utterances 1 s apart on a slow device: the first Final's decode (3.5 s) ends
        # well into the second utterance, so the session is then behind the mic.
        tone = (0.5 * np.sin(np.arange(16_000 * 10) * 0.3)).astype(np.float32)
        audio = np.concatenate([tone, np.zeros(16_000, np.float32), tone])
        runs = {}
        for catch_up in (False, True):
            clock = SimClock(lambda audio_s, _wall_s: 0.5 + 0.3 * audio_s)
            runs[catch_up] = offline_session(audio, LengthTranscriber(), FakeVad(), tail_silence=3.0, lead_silence=0.0,
                                             partials=True, clock=clock, catch_up=catch_up)
        before, after = runs[False], runs[True]
        # The Final's decode is uninterruptible, so the session is 3.5 s behind at worst, and
        # #20's partial gate already lets that backlog drain before the next partial: catch-up
        # (a guarantee) changes nothing here, and a lag gone within 1 s isn't reported.
        self.assertAlmostEqual(after.max_lag_s, 3.5)
        self.assertEqual(after.events, before.events)
        self.assertNotIn("behind", [kind for kind, _, _ in after.events])
        self.assertEqual(after.finals, before.finals)


    def test_streaming_catch_up_batches_decodes_of_a_slow_engine(self):
        # 20 s of speech into a streaming engine slower than real time (0.05 s a call + RTF 1.2).
        class Rec:
            def create_stream(self):
                return type("S", (), {"fed": 0, "input_finished": lambda s: None})()

            def accept(self, s, x):
                s.fed += len(x)

            def decode(self, s):
                pass

            def text(self, s):
                return f"fed{s.fed}"

        tone = (0.5 * np.sin(np.arange(16_000 * 20) * 0.3)).astype(np.float32)
        runs = {}
        for catch_up in (False, True):
            clock = SimClock(lambda audio_s, _wall_s: 0.05 + 1.2 * audio_s)
            runs[catch_up] = online_session(tone, Rec(), FakeVad(), tail_silence=1.0, lead_silence=0.0,
                                            clock=clock, catch_up=catch_up)
        before, after = runs[False], runs[True]
        decodes = {k: sum(kind == "partial" for kind, _, _ in r.events) for k, r in runs.items()}
        self.assertLess(decodes[True], decodes[False] / 3)
        self.assertLess(after.max_lag_s, before.max_lag_s)
        # Even batched it can't keep up: reported once, and the Final holds all the audio.
        self.assertTrue(before.too_slow and after.too_slow)
        self.assertEqual([k for k, _, _ in after.events].count("too_slow"), 1)
        self.assertEqual(after.finals, before.finals)
        # Without a clock nothing is behind, and the session is as before #30.
        plain = online_session(tone, Rec(), FakeVad(), tail_silence=1.0, lead_silence=0.0)
        self.assertEqual((plain.max_lag_s, plain.events), (0.0, []))

    def test_first_text_events_offline_and_online(self):
        # #29: "text" marks the first partial or Final with words, once per utterance.
        tone = (0.5 * np.sin(np.arange(16_000 * 3) * 0.3)).astype(np.float32)
        clock = SimClock(lambda _audio_s, _wall_s: 0.05)
        res = offline_session(tone, LengthTranscriber(), FakeVad(), tail_silence=1.0, lead_silence=0.0,
                              partials=True, clock=clock)
        kinds = [k for k, _, _ in res.events]
        self.assertEqual(kinds.count("text"), 1)
        self.assertEqual(kinds[kinds.index("text") - 1], "partial")  # shown by the first partial

        class Late(self.Rec):
            """Shows a stray "." at first, words once 2 s (1 s pre-roll + 1 s audio) were fed."""

            def text(self, s):
                return "hi" if s.fed >= 16_000 * 2 else "."

        clock = SimClock(lambda _audio_s, wall_s: wall_s)
        res = online_session(tone, Late(), FakeVad(), tail_silence=1.0, lead_silence=0.0, clock=clock)
        first = {k: t for k, t, _ in reversed(res.events)}  # first time of each kind
        self.assertEqual([k for k, _, _ in res.events].count("text"), 1)
        self.assertAlmostEqual(first["text"] - first["onset"], 1.0, delta=0.25)
        self.assertLessEqual(first["text"], first["final"])


if __name__ == "__main__":
    unittest.main()
