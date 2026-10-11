"""The harness's engine list must match the app's catalog byte for byte."""

import re
import unittest
from pathlib import Path

from asrbench import granite
from asrbench.engines import BY_ID
from asrbench.engines import ENGINES as SHERPA_ENGINES

ENGINES = [*SHERPA_ENGINES, *granite.APP_ENGINES]

CATALOG = Path(__file__).resolve().parents[3] / "app/src/main/java/app/marmalade/stt/install/SttEngineCatalog.kt"
BACKENDS = CATALOG.parents[1] / "speech/SherpaBackends.kt"
STT_ENGINE = CATALOG.parents[1] / "speech/SttEngine.kt"


def kotlin_map(src: str, name: str) -> dict[str, str]:
    """The string pairs of `val <name> = mapOf("a" to "b", ...)`."""
    body = re.search(rf"val {name} = mapOf\((.*?)\n\)", src + "\n)", re.S).group(1)
    return dict(re.findall(r'"([^"]*)" to "([^"]*)"', body.split(")")[0]))


@unittest.skipUnless(CATALOG.exists(), "needs the repo checkout around tools/asr-bench")
class EnginesMatchCatalog(unittest.TestCase):
    def setUp(self):
        self.kt = CATALOG.read_text()

    def test_same_engine_ids(self):
        ids = set(re.findall(r'^\s+id = "([^"]+)"', self.kt, re.M))
        # Ids the catalog names by constant (`id = PARAKEET_ULTRA`).
        consts = dict(re.findall(r'const val ([A-Z0-9_]+) = "([^"]+)"', self.kt))
        ids |= {consts[c] for c in re.findall(r'^\s+id = ([A-Z0-9_]+),', self.kt, re.M)}
        self.assertEqual(ids, {e.id for e in ENGINES})

    def test_every_hash_repo_and_revision_is_in_the_catalog(self):
        catalog_hashes = set(re.findall(r'"([0-9a-f]{64})"', self.kt))
        for e in ENGINES:
            self.assertIn(f'"{e.repo}"', self.kt, e.id)
            self.assertIn(f'"{e.revision}"', self.kt, e.id)
            for f in e.files:
                self.assertIn(f.sha256, catalog_hashes, f"{e.id}/{f.name}")
                self.assertIn(f"{f.size:_}L", self.kt, f"{e.id}/{f.name} size")

    def test_our_release_urls_are_the_catalogs(self):
        ultra = BY_ID["parakeet-ultra"]
        self.assertEqual(
            ultra.url(ultra.file("encoder")),
            "https://github.com/maxwhipw/marmalade-stt-engines/releases/download/v1/parakeet-ultra-encoder.int8.onnx",
        )
        self.assertIn('const val PARAKEET_ULTRA = "parakeet-ultra"', self.kt)
        self.assertIn('asset(FileRole.ENCODER, base, PARAKEET_ULTRA, "encoder.int8.onnx"', self.kt)

    def test_catalog_has_no_hash_the_harness_lacks(self):
        ours = {f.sha256 for e in ENGINES for f in e.files}
        self.assertEqual(set(re.findall(r'"([0-9a-f]{64})"', self.kt)) - ours, set())



@unittest.skipUnless(BACKENDS.exists(), "needs the repo checkout around tools/asr-bench")
class LanguageHintsMatchApp(unittest.TestCase):
    def setUp(self):
        self.kt = BACKENDS.read_text()

    def test_nemotron_keys_extend_the_apps(self):
        # The app offers Nemotron only hi/ar/vi since #37; the harness still runs it on ja/zh.
        from asrbench.engines import NEMOTRON_LANGUAGE_KEYS
        app = kotlin_map(self.kt, "NEMOTRON_LANGUAGE_KEYS")
        self.assertTrue(app)
        self.assertEqual({k: NEMOTRON_LANGUAGE_KEYS[k] for k in app}, app)

    def test_whisper_codes_are_the_apps(self):
        from asrbench.engines import WHISPER_LANGUAGE_CODES
        self.assertEqual(WHISPER_LANGUAGE_CODES, kotlin_map(self.kt, "WHISPER_LANGUAGE_CODES"))

    def test_qwen3_names_extend_the_apps(self):
        # The harness may add names (#37 measures hints the app doesn't offer yet), never change one.
        from asrbench.engines import QWEN3_LANGUAGE_NAMES
        app = kotlin_map(self.kt, "QWEN3_LANGUAGE_NAMES")
        self.assertTrue(app)
        self.assertEqual({k: QWEN3_LANGUAGE_NAMES[k] for k in app}, app)



@unittest.skipUnless(STT_ENGINE.exists(), "needs the repo checkout around tools/asr-bench")
class VadMatchesApp(unittest.TestCase):
    def test_default_min_silence_is_the_apps(self):
        from asrbench.backends import DEFAULT_MIN_SILENCE_SECONDS
        app = re.search(r"DEFAULT_MIN_SILENCE_SECONDS = ([0-9.]+)f", STT_ENGINE.read_text()).group(1)
        self.assertEqual(DEFAULT_MIN_SILENCE_SECONDS, float(app))


if __name__ == "__main__":
    unittest.main()
