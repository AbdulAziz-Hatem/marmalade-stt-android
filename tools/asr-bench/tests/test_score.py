"""normalise_world (#37): marks kept, per-language folding."""

import unittest

from asrbench.score import normalise, normalise_world


class NormaliseWorld(unittest.TestCase):
    def test_hindi_keeps_vowel_signs(self):
        self.assertEqual(normalise_world("नमस्ते, दुनिया! यह परीक्षण है।", "hi"), "नमस्ते दुनिया यह परीक्षण है")

    def test_arabic_drops_short_vowels_and_folds_letters(self):
        self.assertEqual(normalise_world("العَرَبِيَّة، أهلاً", "ar"), "العربيه اهلا")
        self.assertEqual(normalise_world("٣ مدارس", "ar"), "3 مدارس")

    def test_turkish_dotted_and_dotless_i(self):
        self.assertEqual(normalise_world("İstanbul'da IŞIK", "tr"), "istanbul da ışık")

    def test_vietnamese_tones_kept(self):
        self.assertEqual(normalise_world("Tiếng Việt có dấu.", "vi"), "tiếng việt có dấu")

    def test_normalise_routes_world_languages(self):
        self.assertEqual(normalise("Hei, verden!", "nb"), "hei verden")


if __name__ == "__main__":
    unittest.main()
