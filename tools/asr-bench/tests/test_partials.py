"""The Kotlin ports in asrbench.partials (cases copied from the Kotlin tests) and the partial-quality metric.

Standard library only: runs without the venv.
"""

import unittest

from asrbench.partials import (PartialStabilizer, aligned_end, clean_partial, clip_metrics,
                               cut_loops, cut_tail_repeats, flicker, has_repeat, normalize, strip_non_speech,
                               strip_trailing_punctuation, utterance_metrics, word_errors)


class KotlinPortTest(unittest.TestCase):
    def check(self, fn, cases):
        failures = [f"{name}: expected <{want}> but was <{got}>"
                    for name, given, want in cases if (got := fn(given)) != want]
        self.assertFalse(failures, "\n".join(failures))

    def test_clean_partial(self):
        # TextPipelineTest.cleanPartial
        self.check(clean_partial, [
            ("loop at the end is removed", "It was the " + "E " * 19 + "E", "It was the"),
            ("loop alone leaves nothing", "E " * 39 + "E", ""),
            ("recovered stutter keeps one copy", "No " + "the " * 8 + "scrolling", "No the scrolling"),
            ("noise caption stripped", "Hello (buzzing) there", "Hello there"),
            ("tag alone leaves nothing", "[BLANK_AUDIO]", ""),
            ("whitespace normalised", "  hello \n  wor ", "hello wor"),
            ("tail repeat left to the stabilizer", "this is a test test test", "this is a test test test"),
            ("ordinary text unchanged", "um so twenty five thank you", "um so twenty five thank you"),
        ])

    def test_strip_non_speech(self):
        # TextPipelineTest.hallucinationFilter, the tag cases (process() adds nothing else to these).
        self.check(strip_non_speech, [
            ("blank-audio tag", "[BLANK_AUDIO]", ""),
            ("square-bracket tag after text", "That's funny. [Music]", "That's funny."),
            ("parenthesised tag alone", "(laughs)", ""),
            ("real parenthetical kept", "Call me (after five) tomorrow.", "Call me (after five) tomorrow."),
            ("noise caption after speech", "We need 25 millimeters long. (buzzer buzzing)",
             "We need 25 millimeters long."),
            ("noise caption mid-sentence", "I think (coughs) we should go.", "I think we should go."),
            ("two-word noise caption", "Hello (wind howling) there.", "Hello there."),
            ("three-word noise caption", "(soft music playing) Welcome back.", "Welcome back."),
            ("capitalised noise caption", "Nice. (Laughs)", "Nice."),
            ("all-caps caption", "(MUSIC PLAYING) Hello there.", "Hello there."),
            ("unknown all-caps word in brackets", "Hi (BLANK_AUDIO)", "Hi"),
            ("parenthetical with a sound word", "The (music was loud) part.", "The (music was loud) part."),
            ("lowercase parenthetical", "See the notes (page 4) first.", "See the notes (page 4) first."),
            ("lone brackets from hum", "( ( ( ( (", ""),
            ("one lone bracket", "(", ""),
            ("music notes", "♪ ♪", ""),
            ("accented tag letters", "[Música] hola", "hola"),
            ("combining mark in a tag", "[Mus\u0301ic] hi", "hi"),
            ("only a real parenthetical", "(after five)", ""),
        ])

    def test_cut_loops(self):
        # TextPipelineTest.decoderLoops, where process() adds only capitalisation.
        self.check(cut_loops, [
            ("letter loop to the token cap", "E " * 134 + "E", ""),
            ("loop after real words", "It was the " + "T " * 39 + "T", "It was the"),
            ("loop with trailing punctuation", "Get Get Get Get Get Get Get Get.", ""),
            ("filler loop keeps what came before", "Um, " + "Uh, " * 53 + "Uh,", "Um"),
            ("recovered loop keeps one copy", "No " + "the " * 12 + "scrolling was different",
             "No the scrolling was different"),
            ("letter loop before real words", "T " * 32 + "Looks a bit like a.", "Looks a bit like a."),
            ("loop cut short by the token cap", "Um, " + "Uh, " * 54 + "U", "Um"),
            ("Whisper letter loop after a word", "XL, " + "X, " * 9 + "X", "XL"),
            ("stop-flush loop after a different word", "It's it it it it it it it it", "It's"),
            ("two-letter cycle to the cap", "It was " + "S E " * 40 + "S", "It was"),
            ("two-word stutter", "I think " + "you know " * 7 + "it works", "I think you know it works"),
            ("a short spelled name", "my name is S M I T H", "my name is S M I T H"),
            ("five repeats are speech", "no no no no no", "no no no no no"),
            ("repeated digit words", "call five five five five five five zero one four two",
             "call five five five five five five zero one four two"),
            ("repeated digit characters", "PIN 0 0 0 0 0 0", "PIN 0 0 0 0 0 0"),
            ("different words", "a b c d e f g h", "a b c d e f g h"),
        ])

    def test_cut_tail_repeats(self):
        # TextPipelineTest.tailRepeats, then edge cases.
        self.check(cut_tail_repeats, [
            ("tail repeat keeps one copy", "this is a test test test", "this is a test"),
            ("seven repeats keep one copy", "this is a" + " test" * 7, "this is a test"),
            ("tail repeat of a block", "go to the shop the shop the shop", "go to the shop"),
            ("a word cut short, and the comma after the kept copy", "a test, test, test, te", "a test"),
            ("tail repeat with an unfinished block", "the shop the shop the shop the sh", "the shop"),
            ("tail repeat with a whole first word after it", "the shop the shop the shop the", "the shop"),
            ("two repeats stay", "yeah yeah", "yeah yeah"),
            ("repeat before the end stays", "no no no I said", "no no no I said"),
            ("tail digits stay", "call five five five", "call five five five"),
            ("single-letter tail goes", "It was E E E", "It was"),
            ("but I and a are words", "and then I I I", "and then I"),
            ("punctuation after the repeats goes too", 'pronounced "skura" and "s" and "s" and "s" and "',
             'pronounced "skura" and "s"'),
            ("punctuation alone at the end stays", "a test -", "a test -"),
            ("ordinary text unchanged", "um so twenty five thank you", "um so twenty five thank you"),
            ("empty", "", ""),
            ("cut until the end is no repeat", "go go go the shop the shop the shop", "go go go the shop"),
            ("stub equal to the word is a fourth copy", "a test test test test", "a test"),
            ("digits", "PIN 4 4 4", "PIN 4 4 4"),
            ("case and punctuation", "Test, test. TEST!", "Test"),
        ])

    def test_normalize_and_trailing_punctuation(self):
        self.assertEqual(normalize("  hello \n  wor "), "hello wor")
        self.assertEqual(normalize("a\u0007b \x1f"), "ab")
        self.assertEqual(normalize("e\u0301"), "\u00e9")
        self.assertEqual(normalize("a\u00a0\u00a0b"), "a\u00a0\u00a0b")  # Java's \s is ASCII only
        # PunctuationStripTest
        for given, want in [("Hello world.", "Hello world"), ("What?", "What"), ("Hello...", "Hello"),
                            ("Dr. Smith said", "Dr. Smith said"), ("", ""), ("Really?!", "Really"),
                            ("Hello:", "Hello"), ("  Hi .  ", "Hi"), ("...", "")]:
            self.assertEqual(strip_trailing_punctuation(given), want, given)


class StabilizerTest(unittest.TestCase):
    """PartialStabilizerTest."""

    @staticmethod
    def shown(*partials):
        s = PartialStabilizer()
        return [s.update(p) for p in partials]

    def test_first_partial_as_it_is(self):
        self.assertEqual(self.shown("hello there"), ["hello there"])

    def test_agreed_words_stay(self):
        self.assertEqual(self.shown("the quick brown", "the quick brown fox", "a quick brown fox jumps")[-1],
                         "the quick brown fox jumps")

    def test_last_word_never_stable(self):
        self.assertEqual(self.shown("I went to the sto", "I went to the sto", "I went to the store today")[-1],
                         "I went to the store today")

    def test_repeats_never_become_stable(self):
        self.assertEqual(self.shown("this is a test test test", "this is a test test test test",
                                    "this is a test of the system"),
                         ["this is a test", "this is a test", "this is a test of the system"])

    def test_stable_words_never_change_or_vanish(self):
        partials = [
            "So", "So I", "So I think we", "So I think we should", "So I think we should go",
            "So I think we should go to the", "Also I think we should go to the park",
            "So I think we go to the park after", "So I think we should go to the park after lunch",
        ]
        s = PartialStabilizer()
        stable = []
        for p in partials:
            words = s.update(p).split(" ")
            self.assertEqual(words[:len(stable)], stable, p)
            self.assertGreaterEqual(s.stable_words, len(stable))
            stable = words[:s.stable_words]
        self.assertEqual(" ".join(stable), "So I think we should go to the park after")
        self.assertEqual(s.update(partials[-1]), "So I think we should go to the park after lunch")

    def test_stable_word_survives_a_redecode_that_loses_it(self):
        self.assertEqual(self.shown("please call Anna", "please call Anna now", "please call now and")[-1],
                         "please call Anna now and")

    def test_reworded_stable_word_not_shown_twice(self):
        self.assertEqual(self.shown("please call Anna", "please call Anna now", "please call Ana now and")[-1],
                         "please call Anna now and")

    def test_rewording_realigns(self):
        # A re-decode that rewords the stable start: the tail starts after the best-aligned words.
        s = PartialStabilizer()
        s.update("we met at the station")
        s.update("we met at the station today")  # "we met at the station" stable
        self.assertEqual(s.update("We meet at a station today and"), "we met at the station today and")

    def test_case_and_punctuation_dont_stop_agreement(self):
        # Agreed words are kept as the second of the two partials spelled them.
        self.assertEqual(self.shown("hello, world how", "Hello world, how are", "HELLO WORLD how are you")[-1],
                         "Hello world, how are you")

    def test_empty_partial_shows_the_stable_words(self):
        # The second partial agreed all of the first's words, its last one included.
        out = self.shown("one two three", "one two three four", "")
        self.assertEqual(out[-1], "one two three")

    def test_reset(self):
        s = PartialStabilizer()
        s.update("one two three")
        s.update("one two three four")
        s.reset()
        self.assertEqual(s.update("five six"), "five six")

    def test_aligned_end(self):
        self.assertEqual(aligned_end([], ["a", "b"]), 0)
        self.assertEqual(aligned_end(["a", "b"], ["a", "b", "c"]), 2)
        self.assertEqual(aligned_end(["a", "b"], ["a", "x", "b", "c"]), 3)
        self.assertEqual(aligned_end(["a", "b", "c"], ["a", "c", "d"]), 2)
        self.assertEqual(aligned_end(["a", "b"], ["a"]), 1)
        self.assertEqual(aligned_end(["a"], []), 0)
        # Ties: most of the last tail next, then the largest j.
        self.assertEqual(aligned_end(["a", "b"], ["a", "c", "d"]), 2)
        self.assertEqual(aligned_end(["a", "b"], ["a", "c", "d"], ["c"]), 1)


class MetricTest(unittest.TestCase):
    def test_has_repeat(self):
        self.assertTrue(has_repeat("a test test test"))
        self.assertTrue(has_repeat("the shop the shop the shop is closed"))
        self.assertTrue(has_repeat("E E E"))
        self.assertTrue(has_repeat("Test, test. TEST"))
        self.assertFalse(has_repeat("yeah yeah"))
        self.assertFalse(has_repeat("five five five"))
        self.assertFalse(has_repeat("PIN 4 4 4"))
        self.assertFalse(has_repeat(""))
        self.assertFalse(has_repeat("a b a b a c"))

    def test_flicker(self):
        self.assertEqual(flicker("the quick brown", "the quick brown fox"), 0)
        self.assertEqual(flicker("the quick brown", "the quick brow"), 1)
        self.assertEqual(flicker("a test test test", "a test of"), 2)
        self.assertEqual(flicker("Hello, world", "hello world again"), 0)  # case and punctuation don't count
        self.assertEqual(flicker("one two", ""), 2)  # a blanked display takes both back

    def test_utterance_metrics(self):
        shown = [(1.0, ""), (1.5, "So"), (2.0, "Also I"), (2.5, "So I think think think"), (3.0, "So I think we")]
        m = utterance_metrics(0.5, shown)
        self.assertEqual(m["partials"], 5)
        self.assertEqual(m["repeat_partials"], 1)
        # "So"->"Also I": 1; "Also I"->"So I...": 2; "So I think think think"->"So I think we": 2.
        self.assertEqual(m["flicker"], 5)
        self.assertEqual(m["first_word_s"], 1.0)
        self.assertEqual(m["first_stable_s"], 2.0)  # "So" at 1.5 is followed by "Also"

    def test_no_words_no_times(self):
        m = utterance_metrics(0.0, [(1.0, "")])
        self.assertIsNone(m["first_word_s"])
        self.assertIsNone(m["first_stable_s"])
        self.assertEqual(utterance_metrics(0.0, [])["partials"], 0)

    def test_policies_over_a_record(self):
        record = {"onsets": [0.0, 5.0], "finals": [[0, "A test of the system."], [1, "Hi."]], "partials": [
            [0, 1.0, "a test test test", "a test"],
            [0, 2.0, "a test of", "a test of"],
            [1, 6.0, "Hi.", ""],  # pre-#28 sent it, #28 didn't
            [1, 7.0, "", "hi"],  # #28 sent it, pre-#28 didn't
        ]}
        before = clip_metrics(record, "before")
        self.assertEqual([m["repeat_partials"] for m in before], [1, 0])
        self.assertEqual(before[0]["flicker"], 2)
        self.assertEqual([m["partials"] for m in before], [2, 1])
        tailcut = clip_metrics(record, "tailcut")
        self.assertEqual([m["repeat_partials"] for m in tailcut], [0, 0])
        after = clip_metrics(record, "after")
        self.assertEqual([m["partials"] for m in after], [2, 1])
        self.assertEqual(after[1]["first_word_s"], 2.0)
        self.assertEqual([(m["final_errors"], m["final_words"]) for m in after], [(2, 5), (0, 1)])

    def test_last_partial_vs_final(self):
        self.assertEqual(word_errors(["a", "b", "c"], ["a", "x", "c", "d"]), 2)
        m = utterance_metrics(0.0, [(1.0, "the quick"), (2.0, "The quick brown fox")], "The quick brown fox.")
        self.assertEqual((m["final_errors"], m["final_words"]), (0, 4))
        m = utterance_metrics(0.0, [], "Hello.")  # no partial: not counted
        self.assertEqual((m["final_errors"], m["final_words"]), (0, 0))


if __name__ == "__main__":
    unittest.main()
