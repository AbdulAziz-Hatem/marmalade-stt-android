"""The app's engines, mirrored from SttEngineCatalog.kt and SherpaBackends.kt.

Same pinned files (URL at a HuggingFace commit, or at a release tag of our
engines repo, + sha256) and the same
sherpa-onnx configs the app builds. `tests/test_engines.py` fails if a hash
here is missing from the Kotlin catalog, so a catalog change can't silently
leave the benchmark on old files.

`CANDIDATES` are engines the harness can run that the app doesn't ship:
comparison runs (#25: the 110M model's CTC head against the TDT head the app
ships, and Parakeet v3 in fp32 against the int8 export), SenseVoice-Small
(#29), the Japanese candidates of #52 (JAPANESE), multilingual Whisper tiny
and base as drafts for turbo's partials (#46, DRAFTS), and the engines that
left the app in #45 (RETIRED).
"""

import re
from dataclasses import dataclass, field

WHISPER, NEMO_TRANSDUCER, NEMO_CTC, CANARY, QWEN3_ASR, STREAMING_TRANSDUCER, SENSE_VOICE = (
    "whisper", "nemo_transducer", "nemo_ctc", "canary", "qwen3_asr", "streaming_transducer", "sense_voice",
)
# #52: icefall's offline Zipformer transducer (ReazonSpeech k2) and Fun-ASR-Nano.
TRANSDUCER, FUNASR_NANO = "transducer", "funasr_nano"

# Languages the multilingual engines are scored on: FLEURS es/de/fr (#14), ja/zh/ko (#29).
MULTILINGUAL = ("en", "es", "de", "fr", "ja", "zh", "ko")
# #37: the most-spoken languages Nemotron 3.5 covers and Parakeet doesn't
# (EngineRatings.kt). Whisper turbo has all five; Qwen3-ASR has no Norwegian.
WORLD = ("hi", "ar", "tr", "vi", "nb")

# SherpaBackends.kt NUM_THREADS: the same for every engine.
NUM_THREADS = 2


@dataclass(frozen=True)
class ModelFile:
    role: str
    path: str  # path inside the HF repo; the app installs it flat under its basename
    sha256: str
    size: int

    @property
    def name(self) -> str:
        return self.path.rsplit("/", 1)[-1]


@dataclass(frozen=True)
class Engine:
    id: str
    family: str
    repo: str
    revision: str
    files: list[ModelFile]
    languages: tuple[str, ...]  # what the harness scores it on
    takes_language_hint: bool = False  # catalog languageOptions != null
    slow: bool = False
    tags: dict = field(default_factory=dict)
    # Built locally by tools/parakeet-ultra/convert.py rather than downloaded (#25):
    # repo and revision then name the source checkpoint, and fetch_models only checks.
    converted: bool = False
    # Our own files (#39): assets of GitHub release `revision` of `repo`, named
    # `<engine id>-<path>`, as SttEngineCatalog's asset() pins them.
    github_release: bool = False

    @property
    def streaming(self) -> bool:
        return self.family == STREAMING_TRANSDUCER

    @property
    def chunk_ms(self) -> int | None:
        """A streaming export's chunk size (the catalog's chunkMs), from the id ("…-560ms")."""
        m = re.search(r"-(\d+)ms$", self.id)
        return int(m.group(1)) if self.streaming and m else None

    def url(self, f: ModelFile) -> str:
        if self.github_release:
            return f"https://github.com/{self.repo}/releases/download/{self.revision}/{self.id}-{f.path}"
        return f"https://huggingface.co/{self.repo}/resolve/{self.revision}/{f.path}"

    def file(self, role: str) -> ModelFile:
        return next(f for f in self.files if f.role == role)


_EN_TOKENS = ("306cd27f03c1a714eca7108e03d66b7dc042abe8c258b44c199a7ed9838dd930", 835_554)


def _whisper(id, repo, rev, prefix, enc, dec, tokens=_EN_TOKENS, multilingual=False, slow=False,
             languages=None, converted=False):
    return Engine(
        id=id, family=WHISPER, repo=repo, revision=rev, slow=slow, converted=converted,
        languages=languages or (MULTILINGUAL + WORLD if multilingual else ("en",)),
        takes_language_hint=multilingual,
        files=[
            ModelFile("encoder", f"{prefix}-encoder.int8.onnx", *enc),
            ModelFile("decoder", f"{prefix}-decoder.int8.onnx", *dec),
            ModelFile("tokens", f"{prefix}-tokens.txt", *tokens),
        ],
    )


def _nemotron(id, repo, rev, enc, dec, joiner, tokens, multilingual):
    return Engine(
        id=id, family=STREAMING_TRANSDUCER, repo=repo, revision=rev,
        languages=MULTILINGUAL + WORLD if multilingual else ("en",),
        takes_language_hint=multilingual,
        files=[
            ModelFile("encoder", "encoder.int8.onnx", *enc),
            ModelFile("decoder", "decoder.int8.onnx", *dec),
            ModelFile("joiner", "joiner.int8.onnx", *joiner),
            ModelFile("tokens", "tokens.txt", *tokens),
        ],
    )


def _nemo_transducer(id, repo, rev, enc, dec, joiner, tokens, languages=("en",), family=NEMO_TRANSDUCER,
                     encoder_path="encoder.int8.onnx", decoder_path="decoder.int8.onnx",
                     joiner_path="joiner.int8.onnx", extra=(), converted=False, github_release=False):
    return Engine(
        id=id, family=family, repo=repo, revision=rev, languages=languages, converted=converted,
        github_release=github_release,
        files=[
            ModelFile("encoder", encoder_path, *enc),
            ModelFile("decoder", decoder_path, *dec),
            ModelFile("joiner", joiner_path, *joiner),
            ModelFile("tokens", "tokens.txt", *tokens),
            *extra,
        ],
    )


# Parakeet TDT_CTC 110M (#25). k2-fsa publishes its int8 exports only as
# tarballs in the sherpa-onnx `asr-models` GitHub release; their HuggingFace
# repos of the same name are empty. These HuggingFace mirrors hold the same
# bytes (sha256 checked against the extracted official tarballs), which lets
# the app install them file by file like every other engine.
_P110_TOKENS = ("450e56bd2f036fe5b6aa821865838cc5aa9d8b0106134ce9a9ba0664abe6cd10", 9_953)
# The unified model shares Nemotron EN's vocabulary file.
_UNIFIED_TOKENS = ("dc0b4584ab2e4ddbf888425c076c61b736e7356a015250db7d307e6f1a8188ff", 8_952)
_UNIFIED_JOINER = ("869f43f7d24595c55581ad3bf249a935fb8a71389fbdaa7504b9f46f93140f8a", 1_735_860)

_N35_DEC = ("19f9c98fc6d0a2c33a65a43b36fdb2e914c26c0aa9764be3aebc502a1e982fb0", 14_978_075)
_N35_JOIN = ("4101c7c679a0bc30483794b27a059e34e79232aa2068d78d51231a22c8b0d7ce", 9_504_438)
_N35_TOK = ("729cc103155bafa785f9cd45746cd41cabe97eab7182fc04d594129587958f8a", 131_440)

ENGINES: list[Engine] = [
    _whisper(
        "distil-small-en", "csukuangfj/sherpa-onnx-whisper-distil-small.en",
        "0492324bca9e12a6fca0089bb846f2dd723b50d8", "distil-small.en",
        ("397a76d2308c2c1ec91a4ecc12f20fede69bb17be41a1cef050993520328beca", 102_961_431),
        ("3074092bca078786ecda9c9e88449f14e9ebde1d60be4d41de8cacda55e065e0", 195_079_097),
    ),
    _whisper(
        "whisper-tiny-en", "csukuangfj/sherpa-onnx-whisper-tiny.en",
        "d026532c022fa99fd789d6b32446a1df7b6bfc43", "tiny.en",
        ("0ce578b827c94a961aacb8fa14b02f096504b337e5c94be37c36238cbe3e8bc6", 12_937_772),
        ("06c0e6ff6348d427e51839219d1c886c18cfdf411e629e33f5e1679bff9c1527", 89_853_865),
    ),
    _whisper(
        "whisper-base-en", "csukuangfj/sherpa-onnx-whisper-base.en",
        "59eea950fc76df2453efb57e6c0fd334548e8ffe", "base.en",
        ("ef6b936f4c9b1d90a3b68634b60c4ed8576b26172b33c2535ec0e933c9edb823", 29_120_534),
        ("f7162ad6db2dbef16cfaeaa7f945b9d7dd9c1b8d472f6aca82f2273d185e4d41", 130_669_978),
    ),
    _whisper(
        "whisper-small-en", "csukuangfj/sherpa-onnx-whisper-small.en",
        "d9533f69affd85061aee349af7fea5cb2996dbbe", "small.en",
        ("8bdac288f369aa94ee2194059238c465ed82ea9d47ee8fa4a8c0a891873e462f", 112_442_483),
        ("710ccf890e10f3faa15f51ec346081a2723c9f3adb6e4da81c6573a5a6f877fb", 262_223_042),
    ),
    _whisper(
        "whisper-turbo", "csukuangfj/sherpa-onnx-whisper-turbo",
        "2ca6ff69fc878651b770880507669577ac41c2ff", "turbo",
        ("b02dcdf54f348741e93fe732b67d933c8dcb6735655f710640143081db38878b", 674_716_297),
        ("20accd02388482eb3a46bd615631adfdc85e1eb2c7db9ea3f02a40ffe6b81547", 361_080_764),
        tokens=("b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126", 816_730),
        multilingual=True, slow=True,
    ),
    Engine(
        id="parakeet-tdt-0-6b-v3", family=NEMO_TRANSDUCER,
        repo="csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8",
        revision="2bda32ec70b097a55adaa07d9a7173915b43cc78",
        languages=("en", "es", "de", "fr"),  # auto-detects; no hint in the app
        files=[
            ModelFile("encoder", "encoder.int8.onnx", "acfc2b4456377e15d04f0243af540b7fe7c992f8d898d751cf134c3a55fd2247", 652_184_281),
            ModelFile("decoder", "decoder.int8.onnx", "179e50c43d1a9de79c8a24149a2f9bac6eb5981823f2a2ed88d655b24248db4e", 11_845_275),
            ModelFile("joiner", "joiner.int8.onnx", "3164c13fc2821009440d20fcb5fdc78bff28b4db2f8d0f0b329101719c0948b3", 6_355_277),
            ModelFile("tokens", "tokens.txt", "d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d", 93_939),
        ],
    ),
    Engine(
        id="canary-180m-flash", family=CANARY,
        repo="csukuangfj/sherpa-onnx-nemo-canary-180m-flash-en-es-de-fr-int8",
        revision="9077164e0d3dd1d5353743e89ceaa1d3a770838c",
        languages=("en", "es", "de", "fr"), takes_language_hint=True,
        files=[
            ModelFile("encoder", "encoder.int8.onnx", "7a75b4e2a5857a6dcc0819503bbe3fad66943db4a3ccf21d3f27c633667d303f", 132_678_643),
            ModelFile("decoder", "decoder.int8.onnx", "e41a2ab9c0c2fe81a1e8ade5a45fb02a74bc4db7d1f91b89a54a25e2cf79cba2", 74_437_848),
            ModelFile("tokens", "tokens.txt", "2dae6fc7815f9640645e0c765522b278ee0cef49b482d91f6913e334628d3e77", 53_555),
        ],
    ),
    _nemo_transducer(
        "parakeet-tdt-0-6b-v2", "csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8",
        "1ab9323565ddb038682214b292f588070a538ce2",
        ("a32b12d17bbbc309d0686fbbcc2987b5e9b8333a7da83fa6b089f0a2acd651ab", 652_184_296),
        ("b6bb64963457237b900e496ee9994b59294526439fbcc1fecf705b31a15c6b4e", 7_257_753),
        ("7946164367946e7f9f29a122407c3252b680dbae9a51343eb2488d057c3c43d2", 1_739_080),
        ("ec182b70dd42113aff6c5372c75cac58c952443eb22322f57bbd7f53977d497d", 9_384),
    ),
    _nemo_transducer(
        "parakeet-unified-en-0-6b", "csukuangfj2/sherpa-onnx-nemo-parakeet-unified-en-0.6b-int8-non-streaming",
        "8c3a10fb13408c7a7054f6898958bf1c64a8d6c7",
        ("6716910b7a0833997fec7a410494c995d70124001a0e9b66d6370d6aced577e0", 654_040_552),
        ("a5e223392c90e75f8144cdb5eb95af7625db389e39edef2bd1a9c872b3298fe6", 7_257_753),
        _UNIFIED_JOINER, _UNIFIED_TOKENS,
    ),
    _nemo_transducer(
        "parakeet-tdt-110m-en", "punitd/sherpa-onnx-nemo-parakeet_tdt_transducer_110m-en-36000-int8",
        "66a4fa70643dc7ce25c9b38b2f87e1b35ddad33d",
        ("0f35509ddeb9b39002fb077d979a9fe74f06eb0bc4dd5c34f512f82e5111d657", 131_113_202),
        ("f7c331c5504c2e593c76ed22b728e3f554af6c4a383dde862e719ced08b1da19", 3_955_863),
        ("bf7dff69e9f2cdbe9943d70da358f38b361c115ba0105bae7e908e0d6ec782f6", 1_411_403),
        _P110_TOKENS,
    ),
    # Moondream's Parakeet Ultra, converted by tools/parakeet-ultra/convert.py (#25) and
    # hosted on our engines repo (#39).
    _nemo_transducer(
        "parakeet-ultra", "maxwhipw/marmalade-stt-engines", "v1",
        ("7ef3ca4ffb49d60d4faa757b2930c8cec9927e73c24f6155d5b23a00229a7ed6", 652_282_526),
        ("1fab98fe6c12aded87d2da66272cc9e148d0a0044ce3850a12fe56302ec4a922", 11_845_274),
        ("8ba94c6919c17a6bd27368fb89533628a72ffc81d4acedebf3cdcb2cae331dbb", 6_355_277),
        ("d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d", 93_939),
        languages=("en", "es", "de", "fr"), github_release=True,
    ),
    # Buffered streaming: sherpa-onnx recognises the export from its metadata
    # and re-runs the encoder over a window of left context for every chunk.
    _nemo_transducer(
        "parakeet-unified-en-streaming-560ms",
        "csukuangfj2/sherpa-onnx-nemo-parakeet-unified-en-0.6b-int8-streaming-560ms",
        "7551fd26fc810cc1e4e043e608db4d13b59be31e",
        ("e566c3f014598a41724f2df028779a2d4cf7943cbefa324964f6a72e8ee255fb", 654_046_389),
        ("34fea72425d2506600772ba191a6d3f99c0710abdb68d9a3dc89fa8cb2aa473a", 7_257_777),
        _UNIFIED_JOINER, _UNIFIED_TOKENS, family=STREAMING_TRANSDUCER,
    ),
    _nemotron(
        "nemotron-3-5-streaming-320ms",
        "csukuangfj2/sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-320ms-int8-2026-06-11",
        "424ce58898995b713f84341f2e1492f9207a26aa",
        ("f79c3fcc149f268b54b7d5754bdc2ba5c47c16b1fc70d15728a56f6efbf60ca5", 657_601_518),
        _N35_DEC, _N35_JOIN, _N35_TOK, multilingual=True,
    ),
    _nemotron(
        "nemotron-3-5-streaming-560ms",
        "csukuangfj2/sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-560ms-int8-2026-06-11",
        "ab43d895f5985b1bbab8b6eac8607fcdc05343f3",
        ("012e9321373af99021415e0b0eb3ec827b4be3153be6f30d9b448fe65e896e68", 657_601_403),
        _N35_DEC, _N35_JOIN, _N35_TOK, multilingual=True,
    ),
    # The same checkpoint exported with a 1120 ms chunk: decoder, joiner and
    # tokens are byte-identical to the 560 ms export's.
    _nemotron(
        "nemotron-3-5-streaming-1120ms",
        "csukuangfj2/sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-1120ms-int8-2026-06-11",
        "cba1c96ca5ef0e8393b50584ae153a79145dc492",
        ("2fff2166acaa535bd969fb223c1f0783d71029f143cb298bc54c2afe85abf772", 657_601_521),
        _N35_DEC, _N35_JOIN, _N35_TOK, multilingual=True,
    ),
    _nemotron(
        "nemotron-en-streaming-560ms",
        "csukuangfj2/sherpa-onnx-nemotron-speech-streaming-en-0.6b-560ms-int8-2026-04-25",
        "52056fdc070914a48dcd68b31b44d6a6f5b85902",
        ("7d932213491ad355c6e5576705dc3494731a52af87d7a1b954559340147909d8", 652_916_849),
        ("0be9702c2f427a2b6bb241d298e0d3836a558de1f5b9fd3018f1cce6e2b3fa98", 7_257_753),
        ("a35eac38a22ebceb04d230ed7afe0d68f446ba6914a036b97f14fece95967e23", 1_735_862),
        ("dc0b4584ab2e4ddbf888425c076c61b736e7356a015250db7d307e6f1a8188ff", 8_952),
        multilingual=False,
    ),
    Engine(
        id="qwen3-asr-0-6b", family=QWEN3_ASR,
        repo="csukuangfj2/sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25",
        revision="68818b2313fe77bd06f6a7c5068ff3ef59d02b8a",
        languages=MULTILINGUAL + ("hi", "ar", "tr", "vi"), takes_language_hint=True, slow=True,
        files=[
            ModelFile("conv_frontend", "conv_frontend.onnx", "d22dc4423e0940e49884e903d2ea2f7e5567c14fc1aed97e4e26d6b8f208ef9e", 44_148_281),
            ModelFile("encoder", "encoder.int8.onnx", "60748d3e6744a57c9c91e1b17424a6c2990567e8adceb0783940c03ed98fa9d9", 182_491_662),
            ModelFile("decoder", "decoder.int8.onnx", "4f6885be5959ae26af3089d38ee7972c5fafbeeb1cf8d5e76eab6d8b61ca5771", 755_914_231),
            ModelFile("tokenizer_vocab", "tokenizer/vocab.json", "ca10d7e9fb3ed18575dd1e277a2579c16d108e32f27439684afa0e10b1440910", 2_776_833),
            ModelFile("tokenizer_merges", "tokenizer/merges.txt", "8831e4f1a044471340f7c0a83d7bd71306a5b867e95fd870f74d0c5308a904d5", 1_671_853),
            ModelFile("tokenizer_config", "tokenizer/tokenizer_config.json", "4942d005604266809309cabc9f4e9cb89ce855d59b14681fdc0e1cc62ea26c4c", 12_487),
        ],
    ),
]

CANDIDATES: list[Engine] = [
    Engine(
        id="parakeet-tdt-ctc-110m-en", family=NEMO_CTC,
        repo="VocaHQ/sherpa-onnx-nemo-parakeet-tdt-ctc-110m-en-int8",
        revision="43869f73831659dd8b0ccee617ee42474b7aa974",
        languages=("en",),
        files=[
            ModelFile("model", "model.int8.onnx", "9177a9146cf32ee0cc8152276ef95116f312018d316be37ccf57f7efea81fc1a", 131_652_171),
            ModelFile("tokens", "tokens.txt", *_P110_TOKENS),
        ],
    ),
    # The fp32 export of the app's Parakeet v3, for the German gap (#25).
    _nemo_transducer(
        "parakeet-tdt-0-6b-v3-fp32", "csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3",
        "1a468a35cbba69418f126de829e75261dea4a4e4",
        ("3eed7ce424bf8339ad09233533c687e2dbd07e74ccf5027b5e7344019ea373b0", 41_766_257),
        ("d593cdb0e571f5a457ec2219af9968cbf6b0e8198e8f7839b40a8754593bf68c", 47_233_743),
        ("b9b0bcf88ac571902e69a6536223ed2d94885e981b85045410f1403d53121a63", 25_286_330),
        ("d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d", 93_939),
        languages=("en", "es", "de", "fr"),
        encoder_path="encoder.onnx", decoder_path="decoder.onnx", joiner_path="joiner.onnx",
        extra=(ModelFile("encoder_weights", "encoder.weights",
                         "3af3f51af5f2d01dbbf5af47d42c7962a2c205f11004254bb4f2b979862f39a8", 2_435_420_160),),
    ),
]

CANDIDATES.append(
    # Ultra's fp32 export from the same conversion run, to measure what int8 costs it.
    _nemo_transducer(
        "parakeet-ultra-fp32", "moondream/parakeet-ultra", "73175eb7aeb0d82f1e2a6b53b3aabc10a90bcd0b",
        ("e207af4517f3bc4cba780728edaa08d9453b736a3997bf9cbce14324c8762a6c", 41_838_577),
        ("f0b644799b0a26286aecf82d4922ea344e8a6b782aedc846c2c8be29a2236403", 47_233_744),
        ("bc3008fe589aa8667e38080b52b7e661eda76ded4644ef79273446d2f0079123", 25_286_331),
        ("d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d", 93_939),
        languages=("en", "es", "de", "fr"), converted=True,
        encoder_path="encoder.onnx", decoder_path="decoder.onnx", joiner_path="joiner.onnx",
        extra=(ModelFile("encoder_weights", "encoder.weights",
                         "35014118835d0efb8423246e118c18ab5bd386f334d6d8860560bda840ac04f1", 2_435_420_160),),
    ),
)

CANDIDATES.append(
    # SenseVoice-Small (#29): Alibaba's official iic/SenseVoiceSmall, exported by
    # k2-fsa. Not the int8-2025-09-09 export: that is a third-party Cantonese
    # fine-tune trained on CC-BY-NC data (docs/model-reviews/sensevoice-small.md).
    Engine(
        id="sense-voice-small", family=SENSE_VOICE,
        repo="csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17",
        revision="2365baeacb507f821a0c8120fcee3d484dba7a07",
        languages=("en", "ja", "zh", "ko", "yue"), takes_language_hint=True,
        files=[
            ModelFile("model", "model.int8.onnx", "c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51", 239_233_841),
            ModelFile("tokens", "tokens.txt", "f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc", 315_894),
        ],
    ),
)

# Japanese candidates (#52), harness only. All but Qwen3-ASR 1.7B and Fun-ASR-Nano were
# trained on ReazonSpeech (Japanese TV); see docs/benchmarks/2026-10-japanese.md.
_QWEN3_TOKENIZER = [
    ModelFile("tokenizer_vocab", "tokenizer/vocab.json", "ca10d7e9fb3ed18575dd1e277a2579c16d108e32f27439684afa0e10b1440910", 2_776_833),
    ModelFile("tokenizer_merges", "tokenizer/merges.txt", "8831e4f1a044471340f7c0a83d7bd71306a5b867e95fd870f74d0c5308a904d5", 1_671_853),
    ModelFile("tokenizer_config", "tokenizer/tokenizer_config.json", "4942d005604266809309cabc9f4e9cb89ce855d59b14681fdc0e1cc62ea26c4c", 12_487),
]
JAPANESE: list[Engine] = [
    # NVIDIA's Japanese Parakeet (CC-BY-4.0), k2-fsa's export of its CTC head (the TDT head isn't exported).
    Engine(
        id="parakeet-ja-ctc", family=NEMO_CTC,
        repo="csukuangfj/sherpa-onnx-nemo-parakeet-tdt_ctc-0.6b-ja-35000-int8",
        revision="bef18eb066808c90bd0f5df5be685767b0732de8",
        languages=("ja",),
        files=[
            ModelFile("model", "model.int8.onnx", "3addd00ef5bd1742078389e540b77394e4a508bdf2f4c9ad1b4a76d93e76598e", 655_542_604),
            ModelFile("tokens", "tokens.txt", "732f64c53909f2620c713f4106b487d92e6f54a6915b3cd3d1dbd32f9f4f392a", 28_557),
        ],
    ),
    # Reazon's own sherpa-layout export (Apache-2.0): 159M, characters, no punctuation.
    _nemo_transducer(
        "reazonspeech-k2-v2", "reazon-research/reazonspeech-k2-v2", "291488c8151be24d7da4bf7af26e533fad96e407",
        ("2c7bd08a8a99f9ddd0d9e458456577b1f6279214e51426f114f9eced44c54e1d", 154_670_139),
        ("8f0bff94d38797b03b762634ed03211a8e303d06cc4603cdd0cf4199d6eb1485", 2_959_337),
        ("49cc7ea1d3d35a40a27442db5e89996da64bf0e683a903dce76e99e57a12e4de", 2_696_970),
        ("2c3ac659818a48a0c04010e0593bbc4d7c8a24a054340b01131499c05fd52def", 45_754),
        languages=("ja",), family=TRANSDUCER,
        encoder_path="encoder-epoch-99-avg-1.int8.onnx", decoder_path="decoder-epoch-99-avg-1.int8.onnx",
        joiner_path="joiner-epoch-99-avg-1.int8.onnx",
    ),
    # Fun-ASR-Nano-2512 (FunAudioLLM): audio encoder + adaptor into a Qwen3-0.6B LLM. k2-fsa's int8 export.
    Engine(
        id="funasr-nano", family=FUNASR_NANO,
        repo="csukuangfj/sherpa-onnx-funasr-nano-int8-2025-12-30",
        revision="6f16bd378457e13f36ccf3910df9017f96c346fb",
        languages=("ja", "zh", "en"), takes_language_hint=True, slow=True,
        files=[
            ModelFile("encoder_adaptor", "encoder_adaptor.int8.onnx", "f36dea2e30fbc33b5db1d7a7265cc976c5e5586c77b042d5adb1ad27c72db422", 237_792_748),
            ModelFile("llm", "llm.int8.onnx", "dfbf9aa3be41bccc257587f151e15c63fbe1b549f2b517f5ccd5bdce3bf4322a", 600_356_593),
            ModelFile("embedding", "embedding.int8.onnx", "95e61cd0c9c3b9543339a4cf973c95c116815e745ccc1e0285cbd81f76d18644", 155_584_380),
            ModelFile("tokenizer_vocab", "Qwen3-0.6B/vocab.json", "ca10d7e9fb3ed18575dd1e277a2579c16d108e32f27439684afa0e10b1440910", 2_776_833),
            ModelFile("tokenizer_merges", "Qwen3-0.6B/merges.txt", "8831e4f1a044471340f7c0a83d7bd71306a5b867e95fd870f74d0c5308a904d5", 1_671_853),
            ModelFile("tokenizer_json", "Qwen3-0.6B/tokenizer.json", "aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4", 11_422_654),
        ],
    ),
    # Kotoba-Whisper v2.0 (kotoba-tech, Apache-2.0): Whisper large-v3 distilled on ReazonSpeech, 2 decoder
    # layers. No sherpa-onnx export exists: built by tools/asr-bench/kotoba/convert.py (our own conversion,
    # not published). Its tokens file is byte-identical to Whisper turbo's.
    _whisper(
        "kotoba-whisper-v2-0", "kotoba-tech/kotoba-whisper-v2.0", "7eb575277d18909a4af8a24e3ae8cce2e99794ae",
        "kotoba-whisper-v2-0",
        ("dc62d0bd9ebbe35ebe41da73ec91fd9ece8dfaf6ea3a81e6fdab79c421e795af", 668_053_558),
        ("2e9c8edd673335d6235fe580491b290ea5747e558dbb640286548f44d883c5aa", 314_866_022),
        tokens=("b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126", 816_730),
        multilingual=True, slow=True, languages=("ja",), converted=True,
    ),
    # Qwen3-ASR 1.7B: no k2-fsa export; a community int8 export in sherpa's layout (its exporter,
    # Wasser1462/Qwen3-ASR-onnx, has no licence: fine to measure, not to ship). Measured for the memory.
    Engine(
        id="qwen3-asr-1-7b", family=QWEN3_ASR,
        repo="thieunv-asilla/sherpa-onnx-qwen3-asr-1.7B-int8",
        revision="69eb686fd94a4a865bb5340a3d6ac0d7f1fec0d5",
        languages=MULTILINGUAL, takes_language_hint=True, slow=True,
        files=[
            ModelFile("conv_frontend", "conv_frontend.onnx", "3cb27a9fe94d95c938e476f2012b21aba2ec0bfceef33b0e58acd208946bafdd", 48_080_441),
            ModelFile("encoder", "encoder.int8.onnx", "a5deedae034ece715de8ed204378d8c77f889af3a60c2566581135e84cced7cd", 314_222_162),
            ModelFile("decoder", "decoder.int8.onnx", "c43c853fa6e97d08365cb8a5502b360b595cd43c00dc60e4d8ca7cc18cad460b", 2_037_458_645),
            *_QWEN3_TOKENIZER,
        ],
    ),
]
CANDIDATES += JAPANESE

# #46: small multilingual Whispers as possible hidden drafts for Whisper turbo's live partials.
# Same tokens file as turbo; 80 mel bins (turbo: 128).
DRAFTS = [
    _whisper(
        "whisper-tiny", "csukuangfj/sherpa-onnx-whisper-tiny", "65176e2deb88badc814a94058666cadccc29b61c", "tiny",
        ("d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434", 12_937_772),
        ("d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925", 89_855_401),
        tokens=("b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126", 816_730),
        multilingual=True,
    ),
    _whisper(
        "whisper-base", "csukuangfj/sherpa-onnx-whisper-base", "bb53ee204431c90d314c1cc08d28d23e5b7927cc", "base",
        ("0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11", 29_120_534),
        ("9759d217388a01b3a4c7c15533201067b48ae819c4daafc8624e64b9409dc02d", 130_672_026),
        tokens=("b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126", 816_730),
        multilingual=True,
    ),
]
CANDIDATES += DRAFTS

# Left the app's catalog in #45 (the v1 lineup) or later. Their files and configs are
# as the app had them, so earlier results stay reproducible.
RETIRED = {
    "distil-small-en", "whisper-tiny-en", "whisper-base-en", "whisper-small-en", "parakeet-tdt-0-6b-v3",
    "parakeet-tdt-0-6b-v2", "parakeet-unified-en-0-6b", "parakeet-unified-en-streaming-560ms",
    "nemotron-3-5-streaming-320ms", "nemotron-en-streaming-560ms",
    # #37 (Max, option A): Nemotron 3.5 stays only as its 1120 ms export, for hi/ar/vi.
    "nemotron-3-5-streaming-560ms",
}
CANDIDATES = [e for e in ENGINES if e.id in RETIRED] + CANDIDATES
ENGINES = [e for e in ENGINES if e.id not in RETIRED]

BY_ID = {e.id: e for e in ENGINES + CANDIDATES}

# SherpaBackends.kt: Qwen3 takes an English language name, Nemotron 3.5 a
# prompt-dictionary key (only ja/zh/vi need a region; the app offers Nemotron
# only hi/ar/vi since #37, so its map has just vi).
QWEN3_LANGUAGE_NAMES = {
    "en": "English", "de": "German", "es": "Spanish", "fr": "French", "it": "Italian",
    "pt": "Portuguese", "nl": "Dutch", "pl": "Polish", "ru": "Russian", "ja": "Japanese",
    "ko": "Korean", "zh": "Chinese",
    # Not in the app, which offers Qwen3 no hint for WORLD (Whisper turbo has
    # one since #37's review): the hinted runs measure what adding one would give.
    "hi": "Hindi", "ar": "Arabic", "tr": "Turkish", "vi": "Vietnamese",
}
NEMOTRON_LANGUAGE_KEYS = {"ja": "ja-JP", "zh": "zh-CN", "vi": "vi-VN"}
# Fun-ASR-Nano's prompt takes Chinese language names ("语音转写成日文"), as upstream Fun-ASR does.
FUNASR_LANGUAGE_NAMES = {"ja": "日文", "zh": "中文", "en": "英文"}
# SherpaBackends.kt WHISPER_LANGUAGE_CODES: Whisper's own code where it differs from the app's.
WHISPER_LANGUAGE_CODES = {"nb": "no"}
