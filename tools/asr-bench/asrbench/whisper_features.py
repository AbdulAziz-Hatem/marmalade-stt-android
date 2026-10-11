"""Whisper-style log-mel features in pure numpy, the input of the smart-turn v3 model (#43).

Copied unchanged in its maths from marmalade-stt-cli's `whisper_features.py`,
which is ported from pipecat's
`src/pipecat/audio/turn/smart_turn/_whisper_features.py`
(https://github.com/pipecat-ai/pipecat), itself a numpy transcription of
`transformers.WhisperFeatureExtractor` (Apache-2.0, HuggingFace). Kept
bit-faithful so the model sees what it was trained on. Harness-only: not in
the APK.

BSD 2-Clause License

Copyright (c) 2024-2026, Daily

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
"""

import numpy as np
from numpy.lib.stride_tricks import sliding_window_view

_N_FFT = 400
_HOP_LENGTH = 160
_N_MELS = 80
_SAMPLING_RATE = 16_000
_MEL_FLOOR = 1e-10
_NORM_VARIANCE_EPS = 1e-7

N_SAMPLES = _SAMPLING_RATE * 8  # 128_000 — the model's fixed 8 s window
N_FRAMES = 800


def _hertz_to_mel_slaney(freq: np.ndarray) -> np.ndarray:
    min_log_hertz = 1000.0
    min_log_mel = 15.0
    logstep = 27.0 / np.log(6.4)
    freq = np.atleast_1d(np.asarray(freq, dtype=np.float64))
    mels = 3.0 * freq / 200.0
    log_region = freq >= min_log_hertz
    mels[log_region] = min_log_mel + np.log(freq[log_region] / min_log_hertz) * logstep
    return mels


def _mel_to_hertz_slaney(mels: np.ndarray) -> np.ndarray:
    min_log_hertz = 1000.0
    min_log_mel = 15.0
    logstep = np.log(6.4) / 27.0
    mels = np.atleast_1d(np.asarray(mels, dtype=np.float64))
    freq = 200.0 * mels / 3.0
    log_region = mels >= min_log_mel
    freq[log_region] = min_log_hertz * np.exp(logstep * (mels[log_region] - min_log_mel))
    return freq


def _build_mel_filterbank(num_frequency_bins: int, num_mel_filters: int,
                          min_frequency: float, max_frequency: float,
                          sampling_rate: int) -> np.ndarray:
    """Slaney-normalized triangular mel filterbank, (bins, mels)."""
    mel_min = float(_hertz_to_mel_slaney(np.array([min_frequency], dtype=np.float64))[0])
    mel_max = float(_hertz_to_mel_slaney(np.array([max_frequency], dtype=np.float64))[0])
    mel_freqs = np.linspace(mel_min, mel_max, num_mel_filters + 2)
    filter_freqs = _mel_to_hertz_slaney(mel_freqs)
    fft_freqs = np.linspace(0, sampling_rate // 2, num_frequency_bins)

    filter_diff = np.diff(filter_freqs)
    slopes = np.expand_dims(filter_freqs, 0) - np.expand_dims(fft_freqs, 1)
    down_slopes = -slopes[:, :-2] / filter_diff[:-1]
    up_slopes = slopes[:, 2:] / filter_diff[1:]
    mel_filters = np.maximum(np.zeros(1), np.minimum(down_slopes, up_slopes))

    # Slaney area normalization.
    enorm = 2.0 / (filter_freqs[2:num_mel_filters + 2] - filter_freqs[:num_mel_filters])
    mel_filters *= np.expand_dims(enorm, 0)
    return mel_filters


def _periodic_hann_window(window_length: int) -> np.ndarray:
    """Periodic Hann window — what torch.hann_window gives, not np.hanning."""
    return np.hanning(window_length + 1)[:-1]


_HANN_WINDOW = _periodic_hann_window(_N_FFT)
_MEL_FILTERS = _build_mel_filterbank(
    num_frequency_bins=_N_FFT // 2 + 1,
    num_mel_filters=_N_MELS,
    min_frequency=0.0,
    max_frequency=_SAMPLING_RATE / 2.0,
    sampling_rate=_SAMPLING_RATE,
)


def _power_spectrogram(waveform: np.ndarray, window: np.ndarray,
                       frame_length: int, hop_length: int) -> np.ndarray:
    """Centred (reflect-padded) power spectrogram, (bins, frames), float64.

    float64 matches the reference, which promotes internally.
    """
    pad = frame_length // 2
    padded = np.pad(waveform.astype(np.float64), (pad, pad), mode="reflect")
    win = window.astype(np.float64)

    # Zero-copy view of every length-`frame_length` window, decimated by the
    # hop: one batched rFFT instead of ~800 per-frame calls.
    windows = sliding_window_view(padded, frame_length)[::hop_length]
    spec = np.fft.rfft(windows * win, axis=-1)
    return (np.abs(spec) ** 2).T


def log_mel(audio_f32_16k: np.ndarray, *, do_normalize: bool = True) -> np.ndarray:
    """Whisper log-mel features for 8 s of 16 kHz mono audio → (80, 800) float32.

    Reproduces `transformers.WhisperFeatureExtractor(chunk_length=8)(audio,
    sampling_rate=16000, padding="max_length", max_length=128000,
    truncation=True, return_tensors="np").input_features.squeeze(0)`.

    Callers should hand over exactly 128 000 samples — the *last* 8 s of the
    utterance, zero-padded at the START when shorter (that framing is
    `SmartTurnDetector`'s job). Anything else is padded at the end / truncated
    from the front here, as the reference extractor does.
    """
    if audio_f32_16k.ndim != 1:
        raise ValueError(f"expected 1-D audio, got shape {audio_f32_16k.shape}")

    x = np.asarray(audio_f32_16k, dtype=np.float32)
    if x.size < N_SAMPLES:
        x = np.pad(x, (0, N_SAMPLES - x.size), mode="constant")
    elif x.size > N_SAMPLES:
        x = x[:N_SAMPLES]

    # Zero-mean/unit-variance on the WAVEFORM, before the spectrogram — the
    # order matters, and it happens in float32 like the reference.
    if do_normalize:
        x = (x - x.mean()) / np.sqrt(x.var() + _NORM_VARIANCE_EPS)

    magnitudes = _power_spectrogram(x, _HANN_WINDOW, _N_FFT, _HOP_LENGTH)
    mel_spec = np.maximum(_MEL_FLOOR, _MEL_FILTERS.T @ magnitudes)
    log_spec = np.log10(mel_spec)
    log_spec = log_spec[:, :-1]                       # drop the trailing frame
    log_spec = np.maximum(log_spec, log_spec.max() - 8.0)
    log_spec = (log_spec + 4.0) / 4.0
    return log_spec.astype(np.float32)
