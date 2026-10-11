package app.marmalade.stt.textproc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/** Table-driven tests for [Itn]: each pair is input → expected output. */
class ItnTest {
    private fun run(vararg cases: Pair<String, String>) {
        val failures = cases.mapNotNull { (input, expected) ->
            val actual = Itn.apply(input)
            if (actual == expected) null else "<$input>: expected <$expected> but was <$actual>"
        }
        if (failures.isNotEmpty()) fail("${failures.size} of ${cases.size} failed:\n" + failures.joinToString("\n"))
    }

    @Test fun cardinals() = run(
        "ten" to "10",
        "Set a timer for twenty minutes." to "Set a timer for 20 minutes.",
        "twenty five" to "25",
        "twenty-five" to "25",
        "ninety-nine problems" to "99 problems",
        "Twenty Two" to "22",
        "one hundred" to "100",
        "seven hundred and forty two" to "742",
        "two hundred forty-nine" to "249",
        "one thousand and five" to "1005",
        "eighteen hundred" to "1800",
        "twelve thousand three hundred" to "12,300",
        "one million two hundred thousand" to "1,200,000",
        "one million downloads" to "1 million downloads",
        "three hundred million people" to "300 million people",
        "two billion" to "2 billion",
        "four hundred four" to "404",
        "eleven, twelve" to "11, 12",
        "He is fifteen." to "He is 15.",
        "(seventeen)" to "(17)",
    )

    @Test fun smallNumbersStayWords() = run(
        "one of them" to "one of them",
        "a couple of days" to "a couple of days",
        "We need three boxes." to "We need three boxes.",
        "no one came" to "no one came",
        "at one point" to "at one point",
        "in three weeks" to "in three weeks",
        "the lab opens at eight" to "the lab opens at eight",
        "a hundred times" to "a hundred times",
        "a dozen eggs and half a cup" to "a dozen eggs and half a cup",
        "the fifth" to "the fifth",
        "hundreds of people" to "hundreds of people",
        "thousands" to "thousands",
        "the first time" to "the first time",
        "wait a second" to "wait a second",
        "Fifth Avenue" to "Fifth Avenue",
        "one two" to "one two",
        "zero" to "zero",
        "someone" to "someone",
        "oh no" to "oh no",
        "I may one day" to "I may one day",
        "march one step" to "march one step",
    )

    @Test fun smallNumbersInContext() = run(
        "two cups of flour" to "2 cups of flour",
        "five percent" to "5%",
        "five per cent" to "5%",
        "five dollars" to "$5",
        "one dollar" to "$1",
        "three kilometers" to "3 kilometers",
        "page seven" to "page 7",
        "Android nine" to "Android 9",
        "gate B two" to "gate B2",
        "v two" to "v2",
        "seven PM" to "7 PM",
        "seven p.m. sharp" to "7 p.m. sharp",
        "at five o'clock" to "at 5 o'clock",
        "minus four degrees" to "-4 degrees",
        "negative two" to "-2",
        "two point five" to "2.5",
        "zero point five" to "0.5",
        "fifty cents" to "50 cents",
    )

    @Test fun ordinalsAndDates() = run(
        "the fifteenth" to "the 15th",
        "twenty first century" to "21st century",
        "one hundredth time" to "100th time",
        "the hundredth time" to "the hundredth time",
        "June third" to "June 3rd",
        "April seventh at two PM" to "April 7th at 2 PM",
        "March fourteenth" to "March 14th",
        "march fourteenth" to "march 14th",
        "the second of October" to "the 2nd of October",
        "April seven" to "April 7",
        "on May first" to "on May 1st",
        "March fourteen twenty twenty seven" to "March 14, 2027",
        "in May twenty twenty six" to "in May 2026",
        "June twenty" to "June 20",
        "March fourteenth, twenty twenty seven" to "March 14th, 2027",
        "twenty second of May" to "22nd of May",
        "a twenty second delay" to "a 20 second delay",
        "eleventh twelfth thirteenth" to "11th 12th 13th",
        "twenty third" to "23rd",
        "thirty first" to "31st",
    )

    @Test fun yearsAndPairs() = run(
        "in twenty twenty six" to "in 2026",
        "nineteen fifty" to "1950",
        "nineteen oh five" to "1905",
        "two thousand and five" to "2005",
        "The address is seven forty two Evergreen Terrace" to "The address is 742 Evergreen Terrace",
        "seven forty-two" to "742",
    )

    @Test fun times() = run(
        "at nine thirty AM" to "at 9:30 AM",
        "nine thirty am" to "9:30 am",
        "four fifteen p.m." to "4:15 p.m.",
        "at six forty five in the morning" to "at 6:45 in the morning",
        "ten thirty" to "10:30",
        "twelve fifteen pm" to "12:15 pm",
        "nine oh five am" to "9:05 am",
        "at seven forty two" to "at 7:42",
        "eleven at night" to "11 at night",
        "from nine thirty until ten" to "from 9:30 until 10",
    )

    @Test fun decimalsPercentsMoney() = run(
        "twelve point five percent" to "12.5%",
        "one point thirteen point eight" to "1.13.8",
        "three point one four one five" to "3.1415",
        "point five" to "point five",
        "two point five million" to "2.5 million",
        "twenty five dollars" to "$25",
        "forty nine dollars and ninety nine cents" to "$49.99",
        "eight thousand two hundred and forty nine dollars ninety nine cents" to "$8,249.99",
        "eighteen hundred dollars to nineteen fifty dollars" to "$1,800 to $1,950",
        "one million dollars" to "$1 million",
        "thirty five euros" to "€35",
        "ten dollars and five cents" to "$10.05",
        "twenty dollar bill" to "$20 bill",
    )

    @Test fun digitRuns() = run(
        "Order number four eight two one three" to "Order number 48213",
        "four oh four" to "404",
        "five five five zero one four two" to "5550142",
        "nine one one" to "911",
        "one, two, three" to "one, two, three",
    )

    @Test fun phoneNumbers() = run(
        "My phone number is five five five zero one four two." to "My phone number is 555-0142.",
        "my number is five five five, zero one four two" to "my number is 555-0142",
        "Call me at five five five five five five zero one four two" to "Call me at 555-555-0142",
        "text me at 5555550142." to "text me at 555-555-0142.",
        "Call one five five five five five five zero one four two" to "Call 1-555-555-0142",
        "call 1 555 555 0142 tomorrow" to "call 1-555-555-0142 tomorrow",
        "his cell is 555 0142" to "his cell is 555-0142",
        "call me at five five five, five five five, zero one four two" to "call me at 555-555-0142",
        "Call 555 555 0142. Order 5550142 shipped." to "Call 555-555-0142. Order 5550142 shipped.",
        "phone 555 555 0142, please" to "phone 555-555-0142, please",
        "5550142 is my mobile" to "555-0142 is my mobile",
        "My number is plus four four two zero seven nine four six zero one two three" to
            "My number is +442079460123",
        "call plus one five five five five five five zero one four two" to "call +1 555-555-0142",
        "call +44 20 7946 0123" to "call +442079460123",
        // Already punctuated: unchanged, so the rule is idempotent.
        "Call 555-0142" to "Call 555-0142",
        "call 1-555-555-0142" to "call 1-555-555-0142",
        "call +1 555-555-0142" to "call +1 555-555-0142",
    )

    @Test fun digitRunsWithoutPhoneContext() = run(
        "Order number five five five zero one four two" to "Order number 5550142",
        "the number is 5550142" to "the number is 5550142",
        "Invoice 5555550142 is due." to "Invoice 5555550142 is due.",
        "I called. Order 5550142 shipped" to "I called. Order 5550142 shipped",
        "call about 5550142 dollars" to "call about 5550142 dollars",
        "phone 12345678" to "phone 12345678",
        "call 555 01 42" to "call 555 01 42",
        "phone 25555550142" to "phone 25555550142",
        "one plus 5550142" to "one plus 5550142",
    )

    @Test fun digitsAlreadyThere() = run(
        "Set a timer for 20 minutes." to "Set a timer for 20 minutes.",
        "Version 1.13.8 fixed it." to "Version 1.13.8 fixed it.",
        "1 million downloads" to "1 million downloads",
        "at 9.30am" to "at 9:30am",
        "at 4.15 p.m." to "at 4:15 p.m.",
        "It weighs 9.30 kg" to "It weighs 9.30 kg",
        "came to\$8,249.99" to "came to \$8,249.99",
        "from\$1800 to\$1950" to "from \$1,800 to \$1,950",
        "\$1,800" to "\$1,800",
        "March 14, 2027" to "March 14, 2027",
        "Call 555-0142" to "Call 555-0142",
        "B-22" to "B-22",
    )

    @Test fun edgeCases() = run(
        "" to "",
        "twenty-five-year-old" to "twenty-five-year-old",
        "twenty. Five" to "20. Five",
        "twenty, twenty" to "20, 20",
        "A four" to "A four",
        "I two" to "I two",
        "\"twenty\"" to "\"20\"",
        "one twenty" to "120",
        "ninety" to "90",
        "zero five" to "zero five",
    )

    @Test fun idempotentOnBenchmarkOutputs() {
        val lines = javaClass.getResource("/dictation-hyps.tsv")!!.readText().lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }.drop(1)
        for (line in lines) {
            val once = Itn.apply(line.split('\t')[3].trim().replace(Words.WHITESPACE, " "))
            assertEquals(once, Itn.apply(once), line)
        }
    }

    @Test fun pipelineToggle() {
        val on = TextPipeline(TextProcConfig())
        val off = TextPipeline(TextProcConfig(numbersAsDigits = false))
        assertEquals("Twelve point five percent.", off.process("twelve point five percent.").insert)
        assertEquals("12.5%.", on.process("twelve point five percent.").insert)
        // A replacement rule sees the words before they become digits.
        val rules = TextPipeline(TextProcConfig(replacements = listOf(Replacement("catch twenty two", "Catch-22"))))
        assertEquals("A Catch-22 at 9:30 AM.", rules.process("a catch twenty two at nine thirty AM.").insert)
        // Snippet text is never touched.
        val snip = TextPipeline(TextProcConfig(snippets = listOf(Snippet("my code", "twenty five"))))
        assertEquals("Code twenty five", snip.process("code my code").insert)
    }
}
