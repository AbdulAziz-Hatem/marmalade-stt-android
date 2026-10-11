"""Python ports of the app's two listening loops, fed from a file instead of the mic.

`offline_session` follows StreamingSession.consume (Whisper, Parakeet, Canary,
Qwen3) and `online_session` follows OnlineSession.consume (Nemotron), in
endpointing mode: each VAD segment becomes a Final, and stop() flushes the
utterance in progress. The final text is the Finals joined with spaces, which
is also what patient mode produces. Live partials are skipped unless
`partials=True`: they never change a Final's text, only cost time. With
them, each partial's text as the app sent it before and since #28 is kept in
`SessionResult.partial_texts` for `partials.py`.

Mic emulation: `agc` levels the clip as the platform AutomaticGainControl
would; `lead_silence` seconds of faint noise (a real mic never delivers exact
zeros) come before it, as when a user taps the mic and then starts speaking,
and `tail_silence` seconds follow it before stop(), as when a user pauses and
then taps stop. `lead_silence=0` is speech from the mic's first sample. The
result is cut into 100 ms hops (the last one padded with the same noise), and
each hop goes through the app's 100 Hz high-pass (AudioPreprocessor), then
optionally `SoftAgc`.

Pre-roll: each utterance starts `preroll` samples before Silero's onset
(0.6 s offline, 1.0 s streaming, as in the app since #15). Offline Finals
decode that pre-roll followed by Silero's segment (`with_preroll`). A
streaming stream near mic open gets silence for the part of its pre-roll
that wasn't recorded.

Utterance cap (#48): an offline utterance still going at 28 s plus the
pre-roll is cut at its quietest window in the last 1.5 s, as
StreamingSession does, because Silero's max_speech_duration isn't a hard
cut. Whisper in Hindi is cut at 21 s instead (#54, `max_decode_samples`),
where its token budget (`backends.whisper_max_new_tokens`) would pass what
the decoder holds. `SessionResult.final_samples` has every Final decode's
length.
"""

import math
import time
from dataclasses import dataclass, field
from typing import NamedTuple

import numpy as np
from scipy.signal import lfilter

from . import engines as E
from .backends import MAX_UTTERANCE_SECONDS, SAMPLE_RATE, VAD_WINDOW_SIZE
from .partials import PartialStabilizer, strip_trailing_punctuation

MIC_HOP_SAMPLES = 1_600  # 100 ms
# StreamingSession's live-partial schedule (#20): at least PARTIAL_INTERVAL_S
# apart and never sooner than the last partial took; at most PARTIAL_WINDOW
# samples decoded, cut at the quietest window 2.5-0.5 s before the end.
PARTIAL_INTERVAL_S = 0.2
PARTIAL_WINDOW_SAMPLES = 96_000
PARTIAL_CUT_FROM_END = 40_000
PARTIAL_CUT_TO_END = 8_000
# NearSilence.THRESHOLD_DB, judged on the audio before SoftAgc.
NEAR_SILENT_DB = -50.0
# Audio kept before Silero's onset detection (StreamingSession / OnlineSession.PREROLL_SAMPLES).
OFFLINE_PREROLL_SAMPLES = 9_600  # 0.6 s
STREAMING_PREROLL_SAMPLES = 16_000  # 1.0 s
# UtteranceCap (#48): MAX_DECODE_SAMPLES is 28 s plus the pre-roll; the cut
# goes at the quietest window in the last CAP_CUT_SEARCH samples, one hop's
# VAD windows early. The cut segment's rest is decoded only if it holds
# speech: at least one VAD window, and not near-silent (rest_has_speech).
CAP_CUT_SEARCH_SAMPLES = 24_000
CAP_HEADROOM_SAMPLES = MIC_HOP_SAMPLES + VAD_WINDOW_SIZE
# UtteranceCap.WHISPER_DECODE_SECONDS (#54): Whisper's cap, pre-roll included, where its token
# budget would pass the 443 tokens its decoder holds before 28.6 s.
WHISPER_DECODE_SECONDS = {"hi": 21.0}


def max_decode_samples(family: str, lang: str, preroll: int = OFFLINE_PREROLL_SAMPLES) -> int:
    """UtteranceCap.maxDecodeSamples: 28 s plus the pre-roll, or Whisper's cap in a dense language."""
    if family == E.WHISPER and lang in WHISPER_DECODE_SECONDS:
        return int(WHISPER_DECODE_SECONDS[lang] * SAMPLE_RATE)
    return int(MAX_UTTERANCE_SECONDS * SAMPLE_RATE) + preroll


def rest_has_speech(n: int, near_silent: bool) -> bool:
    """UtteranceCap.restHasSpeech."""
    return n >= VAD_WINDOW_SIZE and not near_silent

AGC_TARGET_DBFS = -25.0
AGC_MAX_GAIN_DB = 30.0
AGC_PEAK_DBFS = -1.0
MIC_FLOOR_DBFS = -70.0


def agc(x: np.ndarray) -> np.ndarray:
    """Stand-in for the platform AGC: one gain per clip bringing its RMS to
    -25 dBFS, at most +30 dB, never pushing the peak above -1 dBFS.

    Without it, quiet recordings (FLEURS peaks at -40 dBFS) make Silero find
    onsets late and hold segments open for seconds, which measures the VAD on
    unrealistic levels rather than the model.
    """
    rms = float(np.sqrt(np.mean(np.square(x, dtype=np.float64)))) if len(x) else 0.0
    peak = float(np.abs(x).max()) if len(x) else 0.0
    if rms == 0.0:
        return x
    gain = min(10 ** (AGC_TARGET_DBFS / 20) / rms, 10 ** (AGC_MAX_GAIN_DB / 20),
               10 ** (AGC_PEAK_DBFS / 20) / peak)
    return (x * gain).astype(np.float32)


def rms_db(x: np.ndarray) -> float:
    """NearSilence.rmsDb: dBFS, never below -100."""
    if len(x) == 0:
        return -100.0
    rms = float(np.sqrt(np.mean(np.square(x, dtype=np.float64))))
    return max(20 * math.log10(rms), -100.0) if rms > 0 else -100.0


def quietest_window(x: np.ndarray, lo: int, hi: int) -> int:
    """StreamingSession's quietestWindow: the start of the lowest-energy VAD window in x[lo:hi], half-window steps."""
    best, at = None, hi
    for i in range(max(lo, 0), hi - VAD_WINDOW_SIZE + 1, VAD_WINDOW_SIZE // 2):
        e = float(np.sum(np.square(x[i:i + VAD_WINDOW_SIZE], dtype=np.float64)))
        if best is None or e < best:
            best, at = e, i
    return at


def mic_floor(n: int, seed: int = 14, db: float | None = MIC_FLOOR_DBFS) -> np.ndarray:
    """White noise at -70 dBFS: the quiet between words on a real mic. `db=None`: digital zeros."""
    if db is None:
        return np.zeros(n, np.float32)
    return (np.random.default_rng(seed).standard_normal(n) * 10 ** (db / 20)).astype(np.float32)


class HighPass:
    """AudioPreprocessor: 2nd-order Butterworth high-pass at 100 Hz, stateful across hops."""

    def __init__(self, cutoff_hz: float = 100.0, sample_rate: int = SAMPLE_RATE):
        omega = 2.0 * math.pi * cutoff_hz / sample_rate
        cosw, alpha = math.cos(omega), math.sin(omega) / (2.0 * 0.70710678)
        a0 = 1.0 + alpha
        self.b0 = (1.0 + cosw) / 2.0 / a0
        self.b1 = -(1.0 + cosw) / a0
        self.b2 = (1.0 + cosw) / 2.0 / a0
        self.a1 = -2.0 * cosw / a0
        self.a2 = (1.0 - alpha) / a0
        self.zi = np.zeros(2)

    def process(self, x: np.ndarray) -> np.ndarray:
        # scipy's lfilter is the same transposed direct form II, in float64
        # like the Kotlin; `zi` carries the state across hops.
        y, self.zi = lfilter([self.b0, self.b1, self.b2], [1.0, self.a1, self.a2],
                             x.astype(np.float64), zi=self.zi)
        return y.astype(np.float32)


class SoftAgc:
    """SoftAgc.kt: the app's gain control for mics without a platform AutomaticGainControl,
    per hop after the high-pass.

    Tracks the speech level as an envelope of hop RMS (instant attack,
    release RELEASE_DB_PER_SECOND) and applies gain TARGET_DBFS - envelope,
    between 0 dB (never attenuates) and +MAX_GAIN_DB, capped so the hop's
    peak stays at or below PEAK_DBFS. The gain includes the current hop, so a
    loud first hop is never over-amplified. The envelope starts at
    START_DBFS: full gain at mic open, for a quiet first word.
    """

    TARGET_DBFS = -20.0
    MAX_GAIN_DB = 30.0
    RELEASE_DB_PER_SECOND = 3.0
    START_DBFS = -50.0
    PEAK_DBFS = -1.0

    def __init__(self):
        self.env = self.START_DBFS

    def process(self, x: np.ndarray) -> np.ndarray:
        rms = float(np.sqrt(np.mean(np.square(x, dtype=np.float64))))
        peak = float(np.abs(x).max())
        level = 20 * math.log10(max(rms, 1e-10))
        self.env = max(level, self.env - self.RELEASE_DB_PER_SECOND * len(x) / SAMPLE_RATE)
        gain_db = min(max(self.TARGET_DBFS - self.env, 0.0), self.MAX_GAIN_DB)
        if peak > 0:
            gain_db = min(gain_db, self.PEAK_DBFS - 20 * math.log10(peak))
        if gain_db <= 0:
            return x
        return (x * np.float32(10 ** (gain_db / 20))).astype(np.float32)


def mic_hops(samples: np.ndarray, tail_silence: float, lead_silence: float = 0.5, soft_agc: bool = False,
             floor_db: float | None = MIC_FLOOR_DBFS):
    """(hop after gain, the same hop before gain), as micSession's MicHop. The lead and tail are
    `mic_floor` at `floor_db` (None: digital zeros)."""
    lead = int(lead_silence * SAMPLE_RATE)
    tail = int(tail_silence * SAMPLE_RATE)
    tail += (-(lead + len(samples) + tail)) % MIC_HOP_SAMPLES
    audio = np.concatenate([mic_floor(lead, seed=15, db=floor_db), samples, mic_floor(tail, db=floor_db)])
    hp = HighPass()
    gain = SoftAgc() if soft_agc else None
    for i in range(0, len(audio), MIC_HOP_SAMPLES):
        hop = hp.process(audio[i:i + MIC_HOP_SAMPLES])
        yield (gain.process(hop) if gain else hop), hop


class SimClock:
    """Virtual time for latency runs (`latency.py`).

    The mic delivers hop i at (i + 1) * 100 ms; the consumer can't process it
    earlier, and every decode takes `cost(audio_seconds, wall_seconds)`
    seconds of virtual time: a device's measured cost model, or the host's
    wall time scaled. Everything else (VAD, buffering) is taken as free.
    """

    def __init__(self, cost):
        self.cost = cost
        self.now = 0.0

    def arrive(self, hop_index: int) -> None:
        self.now = max(self.now, (hop_index + 1) * MIC_HOP_SAMPLES / SAMPLE_RATE)

    def decoded(self, samples: int, wall_seconds: float) -> None:
        self.now += self.cost(samples / SAMPLE_RATE, wall_seconds)


class LagMonitor:
    """LagMonitor.kt (#30): how far the session is behind the mic, in 100 ms hops.

    Catching up from more than BEHIND_HOPS behind until under CAUGHT_UP_HOPS, reported
    ("behind") once it has lasted REPORT_AFTER_HOPS; too slow (once) when still catching up
    TOO_SLOW_AFTER_HOPS captured hops after it began and at least TOO_SLOW_GROWTH_HOPS
    further behind than then.
    """

    BEHIND_HOPS = 15  # 1.5 s
    CAUGHT_UP_HOPS = 5  # 0.5 s
    TOO_SLOW_AFTER_HOPS = 100  # 10 s
    TOO_SLOW_GROWTH_HOPS = 10  # 1 s
    REPORT_AFTER_HOPS = 10  # 1 s

    def __init__(self):
        self.catching_up = False
        self.max_lag_hops = 0
        self._since = 0
        self._lag_at_start = 0
        self._too_slow = False
        self._reported = False

    def update(self, captured: int, processed: int) -> list[str]:
        """Events, as SttEvents: "behind" / "caught_up" (CatchingUp) and "too_slow" (TooSlow)."""
        lag = max(captured - processed, 0)
        self.max_lag_hops = max(self.max_lag_hops, lag)
        events = []
        if not self.catching_up and lag > self.BEHIND_HOPS:
            self.catching_up, self._since, self._lag_at_start = True, captured, lag
        elif self.catching_up and lag < self.CAUGHT_UP_HOPS:
            self.catching_up = False
            if self._reported:
                events.append("caught_up")
            self._reported = False
        if self.catching_up and not self._reported and captured - self._since >= self.REPORT_AFTER_HOPS:
            self._reported = True
            events.append("behind")
        if (self.catching_up and not self._too_slow and captured - self._since >= self.TOO_SLOW_AFTER_HOPS
                and lag - self._lag_at_start >= self.TOO_SLOW_GROWTH_HOPS):
            self._too_slow = True
            events.append("too_slow")
        return events


class PartialText(NamedTuple):
    """One live partial of `offline_session`, as the app before and since #28 would send it.

    `raw` is the pre-#28 Partial text, committed + latest decode; `stabilized` is the #28 one,
    the PartialStabilizer's text. "" means that app sent no Partial for this decode (pre-#28
    sent one when the decode or committed text was non-empty, #28 when the stabilized text
    is). Neither is cleaned: the UI runs TextPipeline.cleanPartial on it. The patient-mode
    `kept` prefix is left out; in endpointing mode it is always empty during partials.
    """

    utterance: int  # index of the VAD onset this partial belongs to
    time: float  # when it was sent: virtual time with a SimClock, else perf_counter
    raw: str
    stabilized: str


@dataclass
class SessionResult:
    finals: list[str] = field(default_factory=list)
    decode_seconds: float = 0.0  # wall time inside the recognizer (not the VAD)
    segments: int = 0  # VAD segments (plus a flushed utterance in progress)
    flushed_tail: bool = False  # stop() had to flush speech the VAD hadn't closed
    near_silent: list[bool] = field(default_factory=list)  # per Final, judged before SoftAgc
    rms_db: list[float | None] = field(default_factory=list)  # per Final, the level near_silent judged (#57)
    # With a SimClock: (kind, virtual time, samples decoded) per event, kind in
    # onset / partial / final, and the LagMonitor's behind / caught_up / too_slow (#30).
    # A final is logged even when its text is empty. "text" marks the first partial or
    # Final of an utterance with a letter or digit in it, not just a stray "." (#29:
    # first visible words, streaming vs offline).
    events: list[tuple[str, float, int]] = field(default_factory=list)
    partial_texts: list[PartialText] = field(default_factory=list)  # with partials=True
    # With a SimClock (#30): the most the session was behind the mic, and whether it was too slow.
    max_lag_s: float = 0.0
    too_slow: bool = False
    # (index of the VAD onset, text) per Final decode, empty text included: what partials lead up to.
    utterance_finals: list[tuple[int, str]] = field(default_factory=list)
    final_samples: list[int] = field(default_factory=list)  # each Final decode's length (#48)
    forced_cuts: int = 0  # utterances cut at the cap rather than by the VAD (#48)

    @property
    def text(self) -> str:
        return " ".join(self.finals)


class _PreGain:
    """PreGainAudio: the session's audio before SoftAgc, by sample index since the VAD's reset.

    Kept whole: a clip is short, so the app's trimming doesn't matter here.
    """

    def __init__(self):
        self.hops = []

    def append(self, x):
        self.hops.append(x)

    def level(self, start: int, count: int) -> float:
        audio = np.concatenate(self.hops) if self.hops else np.zeros(0, np.float32)
        return rms_db(audio[max(start, 0):start + count])

    def near_silent(self, start: int, count: int) -> bool:
        return self.level(start, count) < NEAR_SILENT_DB

    def judge(self, res: "SessionResult", start: int, count: int) -> None:
        """Records a Final's level and near-silence."""
        db = self.level(start, count)
        res.rms_db.append(round(float(db), 1))
        res.near_silent.append(db < NEAR_SILENT_DB)


class _Buffer:
    """SampleBuffer, backed by a numpy array."""

    def __init__(self):
        self.data = np.zeros(0, np.float32)

    def __len__(self):
        return len(self.data)

    def append(self, x):
        self.data = np.concatenate([self.data, x])

    def drop_first(self, n):
        self.data = self.data[n:]


def _has_words(text: str) -> bool:
    return any(c.isalnum() for c in text)


def with_preroll(buf: np.ndarray, start: int, segment_start: int, segment: np.ndarray) -> np.ndarray:
    """StreamingSession.withPreroll: buf[start:segment_start] + segment.

    `segment_start` is where the VAD's segment begins in `buf`. Silero starts
    its segment only about 0.3 s before it detects speech, and later still at
    mic open (its state is cold), so a word spoken as the mic opens would
    otherwise be clipped. If the segment starts at or before `start` (or
    before the buffer, just after the previous utterance), it is decoded as is.
    """
    if segment_start <= start:
        return segment
    return np.concatenate([buf[start:segment_start], segment])


def offline_session(samples, transcriber, vad, tail_silence: float = 1.0, lead_silence: float = 0.5,
                    preroll: int = OFFLINE_PREROLL_SAMPLES, soft_agc: bool = False,
                    partials: bool = False, clock: SimClock | None = None, catch_up: bool = True,
                    max_decode: int | None = None) -> SessionResult:
    """StreamingSession.consume. With a `clock`, a LagMonitor follows how far the session is
    behind the mic (hops arrive in real time), and with `catch_up` partials wait while it is
    behind, as in the app since #30. Without a clock the session is never behind.
    `max_decode`: the cap in samples (`max_decode_samples`); default 28 s plus the pre-roll."""
    res = SessionResult()
    if max_decode is None:
        max_decode = int(MAX_UTTERANCE_SECONDS * SAMPLE_RATE) + preroll

    def now() -> float:
        return clock.now if clock else time.perf_counter()

    def log(kind: str, n: int = 0) -> None:
        if clock:
            res.events.append((kind, clock.now, n))

    def transcribe(x):
        t0 = time.perf_counter()
        text = transcriber.transcribe(x).strip()
        wall = time.perf_counter() - t0
        res.decode_seconds += wall
        if clock:
            clock.decoded(len(x), wall)
        return text

    vad.reset()
    buf, offset, started, start_offset = _Buffer(), 0, False, 0
    fed = 0  # samples fed to the VAD since reset: its segment.start counts in these
    last_partial_at = now()
    last_partial_cost = 0.0  # how long the last partial's decode took
    part_from = 0  # live partials decode buf[part_from:offset]; the text before it is committed
    committed: list[str] = []
    stabilizer = PartialStabilizer()
    utterance = -1  # onsets so far, minus one
    cut_at = 0  # where the last forced cut was, counted like segment.start (#48)
    rest_pending = False  # the next segment the VAD closes is the one that was cut
    pre = _PreGain()
    shown = False  # the utterance has shown words (a partial or its Final)

    def first_text(text: str) -> None:
        nonlocal shown
        if not shown and _has_words(text):
            shown = True
            log("text")

    def drain():
        nonlocal started, offset, start_offset, rest_pending
        while not vad.empty():
            # The Final is the VAD's segment with the pre-roll in front of it;
            # the segment that was cut: its rest, if that holds speech.
            seg = vad.front
            samples = np.asarray(seg.samples, np.float32)
            is_rest, rest_pending = rest_pending, False
            rest_from = min(max(cut_at - seg.start, 0), len(samples)) if is_rest else 0
            if not is_rest:
                audio = with_preroll(buf.data, start_offset, seg.start - (fed - offset), samples)[-max_decode:]
            else:
                rest = len(samples) - rest_from
                audio = samples[rest_from:] if rest_has_speech(rest, pre.near_silent(seg.start + rest_from, rest)) else None
            text = transcribe(audio) if audio is not None else ""
            if audio is not None:
                res.final_samples.append(len(audio))
                log("final", len(audio))
            first_text(text)
            res.utterance_finals.append((utterance, text))
            vad.pop()
            res.segments += 1
            started = False
            buf.drop_first(offset)
            offset = start_offset = 0
            if text:
                res.finals.append(text)
                pre.judge(res, seg.start + rest_from, len(samples) - rest_from)

    def cut_at_cap():
        """StreamingSession.cutAtCap: decode up to a quiet point near the end as a finished
        utterance; the rest goes on as the next one."""
        nonlocal offset, start_offset, part_from, cut_at, rest_pending
        cut = quietest_window(buf.data, offset - CAP_CUT_SEARCH_SAMPLES, offset)
        buf_start = fed - offset
        audio = buf.data[start_offset:cut]
        text = transcribe(audio)
        res.final_samples.append(len(audio))
        res.forced_cuts += 1
        log("final", len(audio))
        first_text(text)
        res.utterance_finals.append((utterance, text))
        if text:
            res.finals.append(text)
            pre.judge(res, buf_start + start_offset, cut - start_offset)
        buf.drop_first(cut)
        offset -= cut
        cut_at = buf_start + cut
        rest_pending = True
        start_offset = part_from = 0
        committed.clear()
        stabilizer.reset()

    def partial():
        nonlocal part_from, last_partial_at, last_partial_cost
        t0 = now()
        if offset - part_from > PARTIAL_WINDOW_SAMPLES:
            # Too long to re-decode: transcribe up to a quiet point once and keep that text.
            cut = quietest_window(buf.data, offset - PARTIAL_CUT_FROM_END, offset - PARTIAL_CUT_TO_END)
            text = strip_trailing_punctuation(transcribe(buf.data[part_from:cut]))
            if text:
                committed.append(text)
            log("partial", cut - part_from)
            first_text(text)
            part_from = cut
        else:
            latest = strip_trailing_punctuation(transcribe(buf.data[part_from:offset]))
            log("partial", offset - part_from)
            first_text(latest)
            hypothesis = " ".join(t for t in [*committed, latest] if t)
            stabilized = strip_trailing_punctuation(stabilizer.update(hypothesis))
            if hypothesis or stabilized:
                res.partial_texts.append(PartialText(utterance, now(), hypothesis, stabilized))
        last_partial_at = now()
        last_partial_cost = last_partial_at - t0

    lag = LagMonitor()
    hops = list(mic_hops(samples, tail_silence, lead_silence, soft_agc))
    for i, (hop, pre_gain) in enumerate(hops):
        if clock:
            clock.arrive(i)
        buf.append(hop)
        pre.append(pre_gain)
        while offset + VAD_WINDOW_SIZE < len(buf):
            vad.accept_waveform(buf.data[offset:offset + VAD_WINDOW_SIZE])
            offset += VAD_WINDOW_SIZE
            fed += VAD_WINDOW_SIZE
            if not started and vad.is_speech_detected():
                started, shown = True, False
                start_offset = part_from = max(offset - preroll, 0)
                committed.clear()
                stabilizer.reset()
                utterance += 1
                last_partial_at, last_partial_cost = now(), 0.0
                log("onset")
        drain()  # before any partial: a Final never waits behind one
        if started and offset - start_offset > max_decode - CAP_HEADROOM_SAMPLES:
            cut_at_cap()
        if clock:
            # Hop k has arrived once the clock passes (k + 1) * 100 ms.
            captured = min(len(hops), int(clock.now * SAMPLE_RATE / MIC_HOP_SAMPLES + 1e-9))
            for event in lag.update(captured, i + 1):
                log(event)
                res.too_slow = res.too_slow or event == "too_slow"
        if (partials and started and not (catch_up and lag.catching_up)
                and now() - last_partial_at > max(PARTIAL_INTERVAL_S, last_partial_cost)):
            partial()
        if not started and offset > preroll:
            buf.drop_first(offset - preroll)
            offset = preroll
    res.max_lag_s = lag.max_lag_hops * MIC_HOP_SAMPLES / SAMPLE_RATE
    # stop(): the utterance in progress (what the VAD has windowed) becomes a segment.
    if started:
        res.flushed_tail = True
        vad.flush()
        drain()
    return res


def online_session(samples, recognizer, vad, tail_silence: float = 1.0, lead_silence: float = 0.5,
                   preroll: int = STREAMING_PREROLL_SAMPLES, soft_agc: bool = False,
                   clock: SimClock | None = None, catch_up: bool = True) -> SessionResult:
    """OnlineSession.consume. With a `clock`, each decode costs `clock.cost` of the audio fed
    since the stream's last decode, and a LagMonitor follows how far the session is behind the
    mic; with `catch_up`, while behind it decodes only once the queue is drained (#30).
    Without a clock the session is never behind. `events` gets onset / partial (a decode
    during the utterance) / final and the LagMonitor's events."""
    res = SessionResult()

    def timed(fn, *a):
        t0 = time.perf_counter()
        out = fn(*a)
        res.decode_seconds += time.perf_counter() - t0
        return out

    def log(kind: str, n: int = 0) -> None:
        if clock:
            res.events.append((kind, clock.now, n))

    stream = None
    pending = 0  # samples fed to the stream since its last decode
    shown = False  # the utterance has shown words (the live result is what the app displays)

    def first_text(text: str) -> None:
        nonlocal shown
        if not shown and _has_words(text):
            shown = True
            log("text")

    def accept(x):
        nonlocal pending
        timed(recognizer.accept, stream, x)
        pending += len(x)

    def decode(s) -> int:
        """Decode what is queued; returns how many samples that was."""
        nonlocal pending
        t0 = time.perf_counter()
        recognizer.decode(s)
        wall = time.perf_counter() - t0
        res.decode_seconds += wall
        n, pending = pending, 0
        if clock:
            clock.decoded(n, wall)
        return n

    def finish_utterance() -> str:
        nonlocal stream
        if stream is not None and len(getattr(recognizer, "tail", ())):
            accept(recognizer.tail)
        s, stream = stream, None
        if s is None:
            return ""
        timed(s.input_finished)
        log("final", decode(s))
        text = recognizer.text(s).strip()
        first_text(text)
        return text

    vad.reset()
    buf, offset, fed_to = _Buffer(), 0, 0
    pre = _PreGain()
    lag = LagMonitor()
    decoded_through = 0  # hops whose audio the stream has decoded (all of them between utterances)
    hops = list(mic_hops(samples, tail_silence, lead_silence, soft_agc))

    def captured(i: int) -> int:
        """Hops the mic has delivered by now: hop k arrives at (k + 1) * 100 ms."""
        if not clock:
            return i + 1
        return min(len(hops), int(clock.now * SAMPLE_RATE / MIC_HOP_SAMPLES + 1e-9))

    for i, (hop, pre_gain) in enumerate(hops):
        if clock:
            clock.arrive(i)
        buf.append(hop)
        pre.append(pre_gain)
        while offset + VAD_WINDOW_SIZE < len(buf):
            vad.accept_waveform(buf.data[offset:offset + VAD_WINDOW_SIZE])
            offset += VAD_WINDOW_SIZE
            if stream is None and vad.is_speech_detected():
                start = max(offset - preroll, 0)
                buf.drop_first(start)
                offset -= start
                fed_to = 0
                stream = timed(recognizer.create_stream)
                shown = False
                log("onset")
                if offset < preroll:  # near mic open or the previous utterance: silence makes up the pre-roll
                    accept(np.zeros(preroll - offset, np.float32))
        if stream is not None:
            if fed_to < len(buf):
                accept(buf.data[fed_to:])
                fed_to = len(buf)
            # Behind the mic, decode once the queue is drained: the whole backlog in one go.
            if not (catch_up and lag.catching_up) or captured(i) <= i + 1:
                log("partial", decode(stream))
                if clock:
                    first_text(recognizer.text(stream))
                decoded_through = i + 1
        while not vad.empty():
            seg = vad.front
            vad.pop()
            res.segments += 1
            text = finish_utterance()
            if text:
                res.finals.append(text)
                pre.judge(res, seg.start, len(seg.samples))
        if stream is None:
            decoded_through = i + 1
            if offset > preroll:
                buf.drop_first(offset - preroll)
                offset = preroll
        else:
            buf.drop_first(offset)
            fed_to -= offset
            offset = 0
        if clock:
            for event in lag.update(captured(i), decoded_through):
                log(event)
                res.too_slow = res.too_slow or event == "too_slow"
    res.max_lag_s = lag.max_lag_hops * MIC_HOP_SAMPLES / SAMPLE_RATE
    seg = None
    if stream is not None:
        res.flushed_tail = True
        res.segments += 1
        vad.flush()  # the utterance in progress becomes a segment, for the near-silence judgement
        seg = None if vad.empty() else vad.front
    text = finish_utterance()
    if text:
        res.finals.append(text)
        if seg:
            pre.judge(res, seg.start, len(seg.samples))
        else:
            res.near_silent.append(False)
            res.rms_db.append(None)
    return res


def _direct_result(samples, text: str, t0: float) -> SessionResult:
    """A direct decode's result; a text gets the whole clip's level, judged as a Final's."""
    res = SessionResult(decode_seconds=time.perf_counter() - t0, segments=1)
    if text:
        res.finals.append(text)
        pre = _PreGain()
        pre.append(np.asarray(samples, np.float32))
        pre.judge(res, 0, len(samples))
    return res


def direct_offline(samples, transcriber) -> SessionResult:
    """No VAD: the whole clip straight to the model (what a VAD false trigger feeds it)."""
    t0 = time.perf_counter()
    return _direct_result(samples, transcriber.transcribe(samples).strip(), t0)


def direct_online(samples, recognizer) -> SessionResult:
    t0 = time.perf_counter()
    s = recognizer.create_stream()
    recognizer.accept(s, samples)
    s.input_finished()
    recognizer.decode(s)
    return _direct_result(samples, recognizer.text(s).strip(), t0)
