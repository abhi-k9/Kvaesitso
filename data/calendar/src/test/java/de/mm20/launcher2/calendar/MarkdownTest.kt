package de.mm20.launcher2.calendar

import de.mm20.launcher2.calendar.FormattedText.Style
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {

    /**
     * The formatted parts of [text], as (part, style) pairs in order
     */
    private fun FormattedText.styled(): List<Pair<String, Style>> =
        spans.sortedWith(compareBy({ it.start }, { it.end })).map { text.substring(it.start, it.end) to it.style }

    @Test
    fun plainTextKeepsItsLineBreaks() {
        val result = Markdown.parse("Buy milk\nand eggs\n\nThen call mum")
        assertEquals("Buy milk\nand eggs\n\nThen call mum", result.text)
        assertTrue(result.spans.isEmpty())
    }

    @Test
    fun boldItalicAndStrikethrough() {
        val result = Markdown.parse("**bold** *italic* _also italic_ ~~gone~~ __strong__")
        assertEquals("bold italic also italic gone strong", result.text)
        assertEquals(
            listOf(
                "bold" to Style.Bold,
                "italic" to Style.Italic,
                "also italic" to Style.Italic,
                "gone" to Style.Strikethrough,
                "strong" to Style.Bold,
            ),
            result.styled()
        )
    }

    @Test
    fun nestedEmphasis() {
        assertEquals(
            listOf("a b c" to Style.Italic, "b" to Style.Bold),
            Markdown.parse("*a **b** c*").styled()
        )
        assertEquals(
            listOf("bold italic" to Style.Bold, "italic" to Style.Italic),
            Markdown.parse("**bold *italic***").styled()
        )
        assertEquals(
            listOf("italic bold" to Style.Italic, "bold" to Style.Bold),
            Markdown.parse("*italic **bold***").styled()
        )
        val both = Markdown.parse("***both***")
        assertEquals("both", both.text)
        assertEquals(setOf(Style.Bold, Style.Italic), both.spans.map { it.style }.toSet())
    }

    @Test
    fun lonelyDelimitersStayAsTheyAre() {
        for (text in listOf("2 * 3 * 4", "snake_case_name", "a ** b", "*not closed", "~~ nope", "price: 5*")) {
            val result = Markdown.parse(text)
            assertEquals(text, result.text)
            assertTrue(text, result.spans.isEmpty())
        }
    }

    @Test
    fun listsAndChecklists() {
        val result = Markdown.parse("- one\n* two\n  + nested\n- [ ] todo\n- [x] done\n1. first\n2) second")
        assertEquals(
            "• one\n• two\n  • nested\n☐ todo\n☑ done\n1. first\n2. second",
            result.text
        )
    }

    @Test
    fun headingsAreBold() {
        val result = Markdown.parse("# Shopping ##\nText")
        assertEquals("Shopping\nText", result.text)
        assertEquals(listOf("Shopping" to Style.Bold), result.styled())
    }

    @Test
    fun quotesAreItalic() {
        val result = Markdown.parse("> Wise words")
        assertEquals("Wise words", result.text)
        assertEquals(listOf("Wise words" to Style.Italic), result.styled())
    }

    @Test
    fun rulesBecomeEmptyLines() {
        assertEquals("above\n\nbelow", Markdown.parse("above\n---\nbelow").text)
        assertEquals("a\n\nb", Markdown.parse("a\n* * *\nb").text)
    }

    @Test
    fun code() {
        val inline = Markdown.parse("Run `make *all*` now")
        assertEquals("Run make *all* now", inline.text)
        assertEquals(listOf("make *all*" to Style.Code), inline.styled())

        val block = Markdown.parse("Before\n```kotlin\nval x = **1**\n```\nAfter")
        assertEquals("Before\nval x = **1**\nAfter", block.text)
        assertEquals(listOf("val x = **1**" to Style.Code), block.styled())

        // Emphasis isn't closed by delimiters within code
        val mixed = Markdown.parse("*a `*` b*")
        assertEquals("a * b", mixed.text)
        assertEquals(listOf("a * b" to Style.Italic, "*" to Style.Code), mixed.styled())
    }

    @Test
    fun links() {
        val result = Markdown.parse("See [the docs](https://example.com/a_b \"Title\") or <https://tasks.org>.")
        assertEquals("See the docs or https://tasks.org.", result.text)
        assertEquals(
            listOf(
                "the docs" to Style.Link("https://example.com/a_b"),
                "https://tasks.org" to Style.Link("https://tasks.org"),
            ),
            result.styled()
        )
    }

    @Test
    fun bareAddressesBecomeLinks() {
        val result = Markdown.parse("Tickets: https://example.com/t?id=1_2. (see https://x.org/a_(b))")
        assertEquals("Tickets: https://example.com/t?id=1_2. (see https://x.org/a_(b))", result.text)
        assertEquals(
            listOf(
                "https://example.com/t?id=1_2" to Style.Link("https://example.com/t?id=1_2"),
                "https://x.org/a_(b)" to Style.Link("https://x.org/a_(b)"),
            ),
            result.styled()
        )
    }

    @Test
    fun onlySafeLinksAreClickable() {
        val result = Markdown.parse("[run](javascript:alert(1)) [open](intent://x) [mail](mailto:a@b.c)")
        assertEquals("run open mail", result.text)
        assertEquals(listOf("mail" to Style.Link("mailto:a@b.c")), result.styled())
    }

    @Test
    fun imagesShowTheirDescription() {
        assertEquals("A cat", Markdown.parse("![A cat](https://example.com/cat.png)").text)
    }

    @Test
    fun escapedCharactersAreShownAsTheyAre() {
        val result = Markdown.parse("\\*not italic\\* and 1\\. not a list")
        assertEquals("*not italic* and 1. not a list", result.text)
        assertTrue(result.spans.isEmpty())
    }

    @Test
    fun manyUnclosedDelimitersAreFast() {
        val text = "*a _b ~~c **d ".repeat(1000)
        val start = System.nanoTime()
        val result = Markdown.parse(text)
        assertTrue((System.nanoTime() - start) / 1_000_000 < 2000)
        assertTrue(result.text.isNotEmpty())
    }

    @Test
    fun spansAreWithinTheText() {
        val inputs = listOf(
            "**", "*", "__a", "[a](", "[a](https://x.y", "<https://", "`", "``a`", "> ", "# ", "- ",
            "- [ ] ", "***a**", "a***", "~~~~", "![](https://x.y)", "h", "http://", "*a*b*c*",
        )
        for (input in inputs) {
            val result = Markdown.parse(input)
            for (span in result.spans) {
                assertTrue(input, span.start in 0..result.text.length)
                assertTrue(input, span.end in span.start..result.text.length)
            }
        }
    }
}
