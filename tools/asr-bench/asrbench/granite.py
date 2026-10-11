"""Granite Speech 5.0 470M TurboCTC (#26 spike): a plain ONNX Runtime pipeline, no sherpa-onnx.

sherpa-onnx has no Granite support, so this family is three small pieces a
Kotlin port would copy one for one:

1. `features`: log-mel + deltas with 2x frame stacking, a numpy port of
   transformers' GraniteSpeech5FeatureExtractor (torchaudio MelSpectrogram:
   n_fft 512, 25 ms Hann window, 10 ms hop, 80 HTK mel bins, power 2, centred
   with reflect padding). log10, floored 8 decades below the clip's maximum,
   then /4 + 1. Deltas are torchaudio's compute_deltas with a 3-frame window.
   Each pair of frames [mel+delta](160) is stacked to one 320-wide row.
2. The ONNX model (`granite/export_onnx.py`): features [1, T, 320] -> logits
   [1, T // 4, 16384]; the encoder subsamples time by 4 more, so the output
   is 12.5 frames per second.
3. `ctc_greedy` + `ByteLevelVocab.decode`: argmax per frame, collapse repeats,
   drop blank (id 0), then GPT-2 byte-level BPE pieces back to UTF-8.

The exported files live in $WORK/models/<engine id>/ (`model.int8.onnx` or
`model.onnx`, and `tokens.txt`), made by `granite/export_onnx.py`; they are
not downloaded by `fetch_models`.
"""

import math
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

import numpy as np

from .engines import ModelFile

SAMPLE_RATE = 16_000
N_FFT = 512
WIN_LENGTH = 400
HOP_LENGTH = 160
N_MELS = 80
STACK = 2
LOGMEL_FLOOR = 8.0  # decades of power below the clip's maximum (80 dB)
BLANK = 0
# The encoder halves time twice: 4 stacked frames (8 mel frames) make one output frame.
MIN_SAMPLES = 4 * STACK * HOP_LENGTH

GRANITE_CTC = "granite_ctc"


@dataclass(frozen=True)
class GraniteEngine:
    """A Granite engine. Not in engines.ENGINES: that list is the sherpa-onnx engines.

    `repo`, `revision` and `files` are set for the one the app's catalog ships
    (`tests/test_engines.py` holds them to SttEngineCatalog.kt); the harness
    reads its files from $WORK/models/<id>/ like the others'.
    """

    id: str
    model_file: str
    family: str = GRANITE_CTC
    languages: tuple[str, ...] = ("en",)
    takes_language_hint: bool = False
    streaming: bool = False
    slow: bool = False
    repo: str = ""
    revision: str = ""
    files: tuple = ()


ENGINES = {e.id: e for e in (
    GraniteEngine("granite-speech-5-470m-ctc", "model.int8.onnx"),
    GraniteEngine("granite-speech-5-470m-ctc-fp32", "model.onnx"),
    # The community export at huggingface.co/qwertz92/granite-speech-5.0-470m-turboctc-onnx
    # (onnx/model_int8.onnx at e6e3b4d6f590ac51a05f25028b53591d4b260786, sha256 8173a50e...):
    # per-channel int8, the only published export with its scripts and hashes. Checked here
    # as a download source; tokens.txt is the same file as ours.
    GraniteEngine("granite-speech-5-470m-ctc-qwertz92", "model_int8.onnx"),
    # The app's catalog entry (#32): the qwertz92 file above plus its tokenizer.json, which
    # holds the same vocabulary as tokens.txt. Its model dir needs model_int8.onnx and tokens.txt.
    GraniteEngine(
        "granite-speech-5-470m", "model_int8.onnx",
        repo="qwertz92/granite-speech-5.0-470m-turboctc-onnx",
        revision="e6e3b4d6f590ac51a05f25028b53591d4b260786",
        files=(
            ModelFile("encoder", "onnx/model_int8.onnx",
                      "8173a50ea67f864801971a40622a2e5c7d62fd230912bde94b745f92c74d60e9", 551_294_349),
            ModelFile("tokens", "tokenizer.json",
                      "3ee80b02f0119a040a70eb909c20fac8271c173d7e71d195a3b35f77780061e6", 1_137_492),
        ),
    ),
)}

# The engines in the app's catalog.
APP_ENGINES = [e for e in ENGINES.values() if e.repo]


def _hz_to_mel(f):
    return 2595.0 * np.log10(1.0 + np.asarray(f, dtype=np.float64) / 700.0)


def _mel_to_hz(m):
    return 700.0 * (10.0 ** (np.asarray(m, dtype=np.float64) / 2595.0) - 1.0)


@lru_cache(maxsize=1)
def mel_filterbank() -> np.ndarray:
    """torchaudio.functional.melscale_fbanks(257, 0, 8000, 80, 16000, norm=None, mel_scale="htk"): [257, 80]."""
    n_freqs = N_FFT // 2 + 1
    all_freqs = np.linspace(0, SAMPLE_RATE // 2, n_freqs)
    m_pts = np.linspace(_hz_to_mel(0.0), _hz_to_mel(SAMPLE_RATE / 2), N_MELS + 2)
    f_pts = _mel_to_hz(m_pts)
    f_diff = f_pts[1:] - f_pts[:-1]
    slopes = f_pts[None, :] - all_freqs[:, None]
    down = -slopes[:, :-2] / f_diff[:-1]
    up = slopes[:, 2:] / f_diff[1:]
    return np.maximum(0.0, np.minimum(down, up)).astype(np.float32)


@lru_cache(maxsize=1)
def _window() -> np.ndarray:
    """Periodic Hann of 400 samples, zero-padded to 512 in the middle (torch.stft)."""
    n = np.arange(WIN_LENGTH)
    w = 0.5 - 0.5 * np.cos(2.0 * math.pi * n / WIN_LENGTH)
    left = (N_FFT - WIN_LENGTH) // 2
    out = np.zeros(N_FFT)
    out[left:left + WIN_LENGTH] = w
    return out


def features(samples: np.ndarray) -> np.ndarray:
    """16 kHz mono float32 -> stacked log-mel + delta features [T, 320] (T = ceil((len // 160) / 2))."""
    x = np.asarray(samples, dtype=np.float32)
    mel_frames = len(x) // HOP_LENGTH
    n_frames = STACK * -(-mel_frames // STACK)
    need = (n_frames - 1) * HOP_LENGTH + 1
    if len(x) < need:
        x = np.pad(x, (0, need - len(x)))
    # centre=True, reflect padding by n_fft // 2 on both sides.
    padded = np.pad(x.astype(np.float64), N_FFT // 2, mode="reflect")
    count = 1 + (len(padded) - N_FFT) // HOP_LENGTH
    idx = np.arange(N_FFT)[None, :] + HOP_LENGTH * np.arange(count)[:, None]
    spec = np.fft.rfft(padded[idx] * _window(), axis=1)
    power = spec.real ** 2 + spec.imag ** 2  # [frames, 257]
    mel = (power @ mel_filterbank().astype(np.float64))[:n_frames]  # [n_frames, 80]
    logmel = np.log10(np.maximum(mel, 1e-10))
    logmel = np.maximum(logmel, logmel.max() - LOGMEL_FLOOR) / 4.0 + 1.0
    # compute_deltas(win_length=3): (x[t+1] - x[t-1]) / 2, edges replicated.
    ext = np.concatenate([logmel[:1], logmel, logmel[-1:]], axis=0)
    deltas = (ext[2:] - ext[:-2]) / 2.0
    frames = np.concatenate([logmel, deltas], axis=1)  # [n_frames, 160]
    return frames.reshape(-1, STACK * frames.shape[1]).astype(np.float32)


def ctc_greedy(logits: np.ndarray) -> list[int]:
    """[T, V] -> token ids: argmax per frame, repeats collapsed, blanks dropped."""
    best = logits.argmax(axis=-1)
    keep = np.ones(len(best), dtype=bool)
    keep[1:] = best[1:] != best[:-1]
    return [int(t) for t in best[keep] if t != BLANK]


@lru_cache(maxsize=1)
def _byte_decoder() -> dict[str, int]:
    """Inverse of GPT-2's bytes_to_unicode: the printable stand-in character -> the byte."""
    bs = list(range(ord("!"), ord("~") + 1)) + list(range(ord("¡"), ord("¬") + 1)) + list(range(ord("®"), ord("ÿ") + 1))
    cs = bs[:]
    n = 0
    for b in range(256):
        if b not in bs:
            bs.append(b)
            cs.append(256 + n)
            n += 1
    return {chr(c): b for b, c in zip(bs, cs)}


class ByteLevelVocab:
    """tokens.txt ("<piece> <id>" per line, sherpa's format) with byte-level BPE decoding."""

    def __init__(self, pieces: list[str]):
        self.pieces = pieces

    @classmethod
    def load(cls, path: Path) -> "ByteLevelVocab":
        by_id: dict[int, str] = {}
        for line in Path(path).read_text(encoding="utf-8").splitlines():
            piece, _, i = line.rpartition(" ")
            by_id[int(i)] = piece
        return cls([by_id[i] for i in range(len(by_id))])

    def decode(self, ids: list[int]) -> str:
        table = _byte_decoder()
        raw = bytes(table[ch] for i in ids if i != BLANK for ch in self.pieces[i])
        return raw.decode("utf-8", errors="replace").strip()


class GraniteTranscriber:
    """The OfflineTranscriber interface (`transcribe(samples) -> str`) over ONNX Runtime."""

    def __init__(self, engine: GraniteEngine, model_dir: Path, language: str = "", num_threads: int = 2):
        import onnxruntime as ort

        opts = ort.SessionOptions()
        opts.intra_op_num_threads = num_threads
        opts.inter_op_num_threads = 1
        opts.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
        self.session = ort.InferenceSession(str(Path(model_dir) / engine.model_file), opts,
                                            providers=["CPUExecutionProvider"])
        self.input_name = self.session.get_inputs()[0].name  # "features" in our export
        self.vocab = ByteLevelVocab.load(Path(model_dir) / "tokens.txt")

    def logits(self, samples: np.ndarray) -> np.ndarray:
        feats = features(samples)[None]
        return self.session.run(["logits"], {self.input_name: feats})[0][0]

    def transcribe(self, samples: np.ndarray) -> str:
        if len(samples) < MIN_SAMPLES:
            return ""
        return self.vocab.decode(ctc_greedy(self.logits(samples)))
