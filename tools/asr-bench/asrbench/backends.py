"""sherpa-onnx recognizers and Silero VAD, configured exactly as SherpaBackends.kt does
(SenseVoice, which the app doesn't have, as sherpa-onnx's own examples do).

Uses the native module directly: the from-source build has TTS off, and the
`sherpa_onnx` package's __init__ imports TTS classes unconditionally.
"""

import math
import re
import sys
from pathlib import Path

import numpy as np

from . import engines as E

SAMPLE_RATE = 16_000  # MIC_SAMPLE_RATE
VAD_WINDOW_SIZE = 512  # StreamingSession.VAD_WINDOW_SIZE
MAX_UTTERANCE_SECONDS = 28.0  # SherpaBackends.kt
# SherpaBackends.kt NEMO_TAIL_PADDING_SAMPLES: zeros after every Parakeet decode (#25). The
# app has no NeMo CTC engine; the harness pads the CTC candidate the same way.
NEMO_TAIL_PADDING_SAMPLES = 4_800

# SherpaBackends.kt OfflineWhisperModelConfig.tailPaddings: feature frames (10 ms) of zeros after the
# speech in Whisper's encoder input. The encoder runs on speech + this, capped at 3000 frames (#46).
WHISPER_TAIL_PADDINGS = 1000

# SherpaBackends.kt WHISPER_TOKEN_BUDGETS (#54, sherpa patch 0005): (k, c) per language; a Whisper
# decode of s seconds gets ceil(k * s) + c tokens instead of Whisper's own 224.
WHISPER_TOKEN_BUDGETS = {"en": (6, 10), "ja": (10, 10), "hi": (20, 10)}


def whisper_max_new_tokens(language: str, seconds: float) -> int | None:
    """SherpaBackends.kt whisperMaxNewTokens: the token budget, or None for Whisper's own 224."""
    if language not in WHISPER_TOKEN_BUDGETS:
        return None
    k, c = WHISPER_TOKEN_BUDGETS[language]
    return math.ceil(k * seconds) + c


def streaming_tail_padding_samples(chunk_ms: int | None) -> int:
    """SherpaBackends.kt streamingTailPaddingSamples: zeros fed before each input_finished, the chunk + 0.2 s."""
    return (chunk_ms + 200) * SAMPLE_RATE // 1000 if chunk_ms else 0


def no_arena_provider(work: Path) -> str:
    """OrtTuning.sherpaProvider for `-noarena` (#47): sherpa-onnx's `cpu:<file>`, the file turning ORT's
    CPU memory arena off. The path must not contain ':' (sherpa splits the string there)."""
    f = work / "ort-noarena.cfg"
    if not f.is_file():
        f.write_text("EnableCpuMemArena=0\n")
    path = str(f.resolve())
    assert ":" not in path, path
    return f"cpu:{path}"


def native():
    """The `_sherpa_onnx` extension module, imported without the package __init__."""
    if "_sherpa_onnx" not in sys.modules:
        import importlib.util
        spec = importlib.util.find_spec("sherpa_onnx")
        lib = Path(spec.submodule_search_locations[0]) / "lib"
        sys.path.insert(0, str(lib))
    import _sherpa_onnx
    return _sherpa_onnx


def _paths(engine: E.Engine, model_dir: Path) -> dict[str, str]:
    paths = {f.role: model_dir / f.name for f in engine.files}
    missing = [p.name for p in paths.values() if not p.is_file()]
    if missing:
        raise FileNotFoundError(f"model files missing for {engine.id}: {missing} (in {model_dir})")
    return {role: str(p) for role, p in paths.items()}


class OfflineTranscriber:
    """SherpaOfflineTranscriber: one stream per call, returns the model's text."""

    def __init__(self, engine: E.Engine, model_dir: Path, language: str, num_threads: int = E.NUM_THREADS,
                 tail_padding: int | None = None, whisper_tail_paddings: int = WHISPER_TAIL_PADDINGS,
                 whisper_token_budget: bool = True, provider: str = "cpu", max_new_tokens: int | None = None):
        s = native()
        p = _paths(engine, model_dir)
        self.stream_options: dict[str, str] = {}
        # A fixed Whisper token limit for every decode instead of the budget (#55's stage split:
        # 1 = the encoder and one decoder step).
        self.max_new_tokens = max_new_tokens
        # Whisper's per-decode token budget (#54); a build without patch 0005 ignores the option.
        self.budget_language = (language if engine.family == E.WHISPER and engine.takes_language_hint
                                and whisper_token_budget else None)
        if tail_padding is None:
            tail_padding = NEMO_TAIL_PADDING_SAMPLES if engine.family in (E.NEMO_TRANSDUCER, E.NEMO_CTC) else 0
        self.tail = np.zeros(tail_padding, np.float32)
        if engine.family == E.WHISPER:
            model = s.OfflineModelConfig(
                whisper=s.OfflineWhisperModelConfig(
                    encoder=p["encoder"], decoder=p["decoder"],
                    # English-only models take "en"; "" makes a multilingual one detect.
                    language=E.WHISPER_LANGUAGE_CODES.get(language, language) if engine.takes_language_hint else "en",
                    task="transcribe", tail_paddings=whisper_tail_paddings,
                ),
                tokens=p["tokens"], model_type="whisper",
                num_threads=num_threads, provider=provider,
            )
        elif engine.family == E.NEMO_TRANSDUCER:
            model = s.OfflineModelConfig(
                transducer=s.OfflineTransducerModelConfig(p["encoder"], p["decoder"], p["joiner"]),
                tokens=p["tokens"], model_type="nemo_transducer",
                num_threads=num_threads, provider=provider,
            )
        elif engine.family == E.NEMO_CTC:
            model = s.OfflineModelConfig(
                nemo_ctc=s.OfflineNemoEncDecCtcModelConfig(model=p["model"]),
                tokens=p["tokens"], num_threads=num_threads, provider=provider,
            )
        elif engine.family == E.CANARY:
            lang = language or "en"
            model = s.OfflineModelConfig(
                canary=s.OfflineCanaryModelConfig(
                    encoder=p["encoder"], decoder=p["decoder"], src_lang=lang, tgt_lang=lang, use_pnc=True,
                ),
                tokens=p["tokens"], num_threads=num_threads, provider=provider,
            )
        elif engine.family == E.QWEN3_ASR:
            if language in E.QWEN3_LANGUAGE_NAMES:
                self.stream_options["language"] = E.QWEN3_LANGUAGE_NAMES[language]
            model = s.OfflineModelConfig(
                # Kotlin defaults: max_total_len 512, max_new_tokens 128, temperature 1e-6, top_p 0.8, seed 42.
                qwen3_asr=s.OfflineQwen3ASRModelConfig(
                    conv_frontend=p["conv_frontend"], encoder=p["encoder"], decoder=p["decoder"],
                    tokenizer=str(Path(p["tokenizer_vocab"]).parent),
                ),
                num_threads=num_threads, provider=provider,
            )
        elif engine.family == E.TRANSDUCER:
            model = s.OfflineModelConfig(
                transducer=s.OfflineTransducerModelConfig(p["encoder"], p["decoder"], p["joiner"]),
                tokens=p["tokens"], model_type="transducer", num_threads=num_threads, provider=provider,
            )
        elif engine.family == E.FUNASR_NANO:
            # Harness-only (#52). ITN on, as for SenseVoice; other fields at sherpa's defaults.
            nano = s.OfflineFunASRNanoModelConfig()
            nano.encoder_adaptor, nano.llm, nano.embedding = p["encoder_adaptor"], p["llm"], p["embedding"]
            nano.tokenizer = str(Path(p["tokenizer_vocab"]).parent)
            nano.language = E.FUNASR_LANGUAGE_NAMES.get(language, "")
            model = s.OfflineModelConfig(funasr_nano=nano, num_threads=num_threads, provider=provider)
        elif engine.family == E.SENSE_VOICE:
            # Harness-only (#29). ITN on: punctuation and digits, what a dictation user wants.
            model = s.OfflineModelConfig(
                sense_voice=s.OfflineSenseVoiceModelConfig(p["model"], language or "auto", True),
                tokens=p["tokens"], num_threads=num_threads, provider=provider,
            )
        else:
            raise ValueError(f"{engine.id} is a streaming model")
        config = s.OfflineRecognizerConfig(
            feat_config=s.FeatureExtractorConfig(sampling_rate=SAMPLE_RATE, feature_dim=80, dither=0.0),
            model_config=model,
            decoding_method="greedy_search",
        )
        self.recognizer = s.OfflineRecognizer(config)

    def options_for(self, samples: np.ndarray) -> dict[str, str]:
        """SherpaBackends.kt offlineStreamOptions for one decode of `samples`."""
        budget = self.max_new_tokens if self.max_new_tokens is not None else \
            whisper_max_new_tokens(self.budget_language, len(samples) / SAMPLE_RATE) \
            if self.budget_language is not None else None
        return self.stream_options if budget is None else {**self.stream_options, "max_new_tokens": str(budget)}

    def transcribe(self, samples: np.ndarray) -> str:
        stream = self.recognizer.create_stream()
        for k, v in self.options_for(samples).items():
            stream.set_option(k, v)
        stream.accept_waveform(SAMPLE_RATE, np.concatenate([samples, self.tail]) if len(self.tail) else samples)
        self.recognizer.decode_stream(stream)
        return stream.result.text


_RUNS_OF_SPACES = re.compile(" {2,}")


class StreamingRecognizer:
    """SherpaStreamingRecognizer: OnlineRecognizer, model endpointing off (Silero ends utterances).

    Nemotron (cache-aware) and Parakeet Unified (buffered) use the same config;
    sherpa-onnx tells them apart by the model's metadata.
    """

    def __init__(self, engine: E.Engine, model_dir: Path, language: str, num_threads: int = E.NUM_THREADS,
                 tail_padding: int | None = None):
        s = native()
        p = _paths(engine, model_dir)
        # Zeros fed before input_finished, as the app does (default: its chunk + 0.2 s). sherpa-onnx
        # v1.13.8 decodes a NeMo stream only in whole windows (`window_size` frames, 121 for
        # the 1120 ms export) and input_finished doesn't pad, so without them up to a window of
        # audio at the end of each utterance is never decoded (the 1120 ms run, 2026-09-27).
        if tail_padding is None:
            tail_padding = streaming_tail_padding_samples(engine.chunk_ms)
        self.tail = np.zeros(tail_padding, np.float32)
        config = s.OnlineRecognizerConfig(
            feat_config=s.FeatureExtractorConfig(sampling_rate=SAMPLE_RATE, feature_dim=80, dither=0.0),
            model_config=s.OnlineModelConfig(
                transducer=s.OnlineTransducerModelConfig(p["encoder"], p["decoder"], p["joiner"]),
                # provider lives in provider_config here; its default is "cpu".
                tokens=p["tokens"], num_threads=num_threads,
            ),
            enable_endpoint=False,
            decoding_method="greedy_search",
        )
        self.recognizer = s.OnlineRecognizer(config)
        self.language = language if engine.takes_language_hint else ""

    def create_stream(self):
        stream = self.recognizer.create_stream()
        if self.language:
            stream.set_option("language", E.NEMOTRON_LANGUAGE_KEYS.get(self.language, self.language))
        return stream

    def accept(self, stream, samples: np.ndarray) -> None:
        stream.accept_waveform(SAMPLE_RATE, samples)

    def decode(self, stream) -> None:
        while self.recognizer.is_ready(stream):
            self.recognizer.decode_stream(stream)

    def text(self, stream) -> str:
        # Nemotron 3.5 emits a space token before some sentence starts.
        return _RUNS_OF_SPACES.sub(" ", self.recognizer.get_result(stream).text)


# SessionOptions.DEFAULT_MIN_SILENCE_SECONDS (SttEngine.kt): 1.2 s since #43, 0.9 s before.
DEFAULT_MIN_SILENCE_SECONDS = 1.2


def silero_vad(model: Path, min_silence_seconds: float = DEFAULT_MIN_SILENCE_SECONDS):
    """SherpaSileroVad.create: threshold 0.5, min speech 0.25 s, max 28 s."""
    s = native()
    config = s.VadModelConfig()
    config.silero_vad.model = str(model)
    config.silero_vad.threshold = 0.5
    config.silero_vad.min_silence_duration = min_silence_seconds
    config.silero_vad.min_speech_duration = 0.25
    config.silero_vad.window_size = VAD_WINDOW_SIZE
    config.silero_vad.max_speech_duration = MAX_UTTERANCE_SECONDS
    config.sample_rate = SAMPLE_RATE
    config.num_threads = 1
    config.provider = "cpu"
    # The JNI builds the detector with the C++ default buffer (60 s), as here.
    return s.VoiceActivityDetector(config, buffer_size_in_seconds=60)
