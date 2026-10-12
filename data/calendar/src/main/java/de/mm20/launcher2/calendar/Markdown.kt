package de.mm20.launcher2.calendar

/**
 * Text with formatting, independent of a UI toolkit.
 */
data class FormattedText(
    val text: String,
    val spans: List<Span> = emptyList(),
) {
    data class Span(val start: Int, val end: Int, val style: Style)

    sealed interface Style {
        data object Bold : Style
        data object Italic : Style
        data object Strikethrough : Style
        data object Code : Style
        data class Link(val url: String) : Style
    }
}

/**
 * Formats the Markdown that the Tasks app stores task notes in. Supports the common subset:
 * headings, lists and checklists, quotes, code, bold, italic, strikethrough and links.
 *
 * Unlike CommonMark, every line break is kept: many notes are plain text, which the Tasks app
 * shows with its line breaks (it only formats Markdown if that's turned on in its settings).
 */
object Markdown {

    fun parse(markdown: String): FormattedText {
        // Only the beginning of long notes is shown anyway
        val source = if (markdown.length > MaxLength) markdown.take(MaxLength) + "…" else markdown
        val out = StringBuilder()
        val spans = mutableListOf<FormattedText.Span>()
        var inCodeBlock = false
        var first = true
        for (line in source.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            if (CodeFence.matches(line)) {
                inCodeBlock = !inCodeBlock
                continue
            }
            if (!first) out.append('\n')
            first = false
            if (inCodeBlock) {
                spans.styled(out, FormattedText.Style.Code) { out.append(line) }
            } else {
                block(line, out, spans)
            }
        }
        return FormattedText(out.toString(), spans.filter { it.end > it.start })
    }

    private fun block(line: String, out: StringBuilder, spans: MutableList<FormattedText.Span>) {
        if (Rule.matches(line)) return

        Heading.matchEntire(line)?.let {
            spans.styled(out, FormattedText.Style.Bold) {
                inline(it.groupValues[1], out, spans)
            }
            return
        }
        Quote.matchEntire(line)?.let {
            spans.styled(out, FormattedText.Style.Italic) {
                block(it.groupValues[1], out, spans)
            }
            return
        }
        TaskItem.matchEntire(line)?.let {
            out.append(it.groupValues[1])
            out.append(if (it.groupValues[2] == " ") "☐ " else "☑ ")
            inline(it.groupValues[3], out, spans)
            return
        }
        Bullet.matchEntire(line)?.let {
            out.append(it.groupValues[1])
            out.append("• ")
            inline(it.groupValues[2], out, spans)
            return
        }
        Numbered.matchEntire(line)?.let {
            out.append(it.groupValues[1])
            out.append(it.groupValues[2]).append(". ")
            inline(it.groupValues[3], out, spans)
            return
        }
        inline(line, out, spans)
    }

    private fun inline(src: String, out: StringBuilder, spans: MutableList<FormattedText.Span>) {
        // Where emphasis opened at a position closes, so that it's only searched once
        val closers = HashMap<Int, Int>()
        var i = 0
        while (i < src.length) {
            val c = src[i]
            i = when {
                c == '\\' && i + 1 < src.length && src[i + 1] in Escapable -> {
                    out.append(src[i + 1])
                    i + 2
                }

                c == '`' -> codeSpan(src, i, out, spans)
                c == '!' && i + 1 < src.length && src[i + 1] == '[' -> {
                    // An image: show its description
                    link(src, i + 1, out, spans) ?: run {
                        out.append(c)
                        i + 1
                    }
                }

                c == '[' -> link(src, i, out, spans) ?: run {
                    out.append(c)
                    i + 1
                }

                c == '<' -> autolink(src, i, out, spans) ?: run {
                    out.append(c)
                    i + 1
                }

                c == '~' && src.startsWith("~~", i) ->
                    emphasis(src, i, "~~", listOf(FormattedText.Style.Strikethrough), out, spans, closers)

                c == '*' || c == '_' -> {
                    val run = src.runLength(i, c)
                    when {
                        run >= 3 -> emphasis(
                            src, i, "$c$c$c",
                            listOf(FormattedText.Style.Bold, FormattedText.Style.Italic), out, spans,
                            closers,
                        )

                        run == 2 -> emphasis(
                            src, i, "$c$c", listOf(FormattedText.Style.Bold), out, spans, closers
                        )

                        else -> emphasis(
                            src, i, "$c", listOf(FormattedText.Style.Italic), out, spans, closers
                        )
                    }
                }

                (c == 'h' || c == 'H') && (i == 0 || !src[i - 1].isLetterOrDigit()) ->
                    bareUrl(src, i, out, spans) ?: run {
                        out.append(c)
                        i + 1
                    }

                else -> {
                    out.append(c)
                    i + 1
                }
            }
        }
    }

    /**
     * @return the index after the code span
     */
    private fun codeSpan(
        src: String,
        start: Int,
        out: StringBuilder,
        spans: MutableList<FormattedText.Span>
    ): Int {
        val run = src.runLength(start, '`')
        val delimiter = "`".repeat(run)
        var close = src.indexOf(delimiter, start + run)
        // The closing backticks must be exactly as many as the opening ones
        while (close >= 0 && src.runLength(close, '`') != run) {
            close = src.indexOf(delimiter, close + src.runLength(close, '`'))
        }
        if (close < 0) {
            out.append(delimiter)
            return start + run
        }
        var code = src.substring(start + run, close)
        if (code.length >= 2 && code.startsWith(' ') && code.endsWith(' ') && code.isNotBlank()) {
            code = code.substring(1, code.length - 1)
        }
        spans.styled(out, FormattedText.Style.Code) { out.append(code) }
        return close + run
    }

    /**
     * Emphasis (bold, italic, strikethrough) between two [delimiter]s.
     *
     * @return the index after the closing delimiter, or after the opening one if it isn't closed
     */
    private fun emphasis(
        src: String,
        start: Int,
        delimiter: String,
        styles: List<FormattedText.Style>,
        out: StringBuilder,
        spans: MutableList<FormattedText.Span>,
        closers: MutableMap<Int, Int>,
    ): Int {
        val close = findCloser(src, start, delimiter, closers)
        if (close < 0) {
            out.append(delimiter)
            return start + delimiter.length
        }
        val inner = src.substring(start + delimiter.length, close)
        val from = out.length
        inline(inner, out, spans)
        for (style in styles) spans += FormattedText.Span(from, out.length, style)
        return close + delimiter.length
    }

    private fun findCloser(
        src: String,
        start: Int,
        delimiter: String,
        closers: MutableMap<Int, Int>,
    ): Int {
        val key = start * 4 + delimiter.length
        closers[key]?.let { return it }
        return findCloserUncached(src, start, delimiter, closers).also { closers[key] = it }
    }

    private fun findCloserUncached(
        src: String,
        start: Int,
        delimiter: String,
        closers: MutableMap<Int, Int>,
    ): Int {
        val char = delimiter[0]
        val contentStart = start + delimiter.length
        // An opening delimiter must be followed by text
        if (contentStart >= src.length || src[contentStart].isWhitespace()) return -1
        // Underscores within words (snake_case) don't count
        if (char == '_' && start > 0 && src[start - 1].isLetterOrDigit()) return -1

        var j = contentStart + 1
        while (j < src.length) {
            val c = src[j]
            if (c == '`') {
                // Delimiters in code don't count
                val run = src.runLength(j, '`')
                val close = src.indexOf("`".repeat(run), j + run)
                j = if (close < 0) j + run else close + run
                continue
            }
            if (c != char) {
                j++
                continue
            }
            val runEnd = j + src.runLength(j, char)
            val run = runEnd - j
            val textBefore = !src[j - 1].isWhitespace()
            val textAfter = runEnd < src.length && !src[runEnd].isWhitespace()
            val wordAfter = runEnd < src.length && src[runEnd].isLetterOrDigit()
            if (textBefore && run >= delimiter.length && !(char == '_' && wordAfter)) {
                // In a longer run (***), the inner emphasis closes first
                return j
            }
            if (textAfter && !textBefore) {
                // Opens emphasis within this one: skip it
                val nested = char.toString().repeat(minOf(run, 3))
                val nestedClose = findCloser(src, j, nested, closers)
                if (nestedClose >= 0) {
                    j = nestedClose + nested.length
                    continue
                }
            }
            j = runEnd
        }
        return -1
    }

    /**
     * A link, [text](url), starting at the [ at [start].
     *
     * @return the index after the link, or null if this isn't a link
     */
    private fun link(
        src: String,
        start: Int,
        out: StringBuilder,
        spans: MutableList<FormattedText.Span>,
    ): Int? {
        val textEnd = src.matching(start, '[', ']') ?: return null
        if (textEnd + 1 >= src.length || src[textEnd + 1] != '(') return null
        val urlEnd = src.matching(textEnd + 1, '(', ')') ?: return null
        val url = src.substring(textEnd + 2, urlEnd).trim()
            .substringBefore(' ')
            .removePrefix("<").removeSuffix(">")
        val text = src.substring(start + 1, textEnd)
        val from = out.length
        inline(text, out, spans)
        if (isSafeUrl(url)) spans += FormattedText.Span(from, out.length, FormattedText.Style.Link(url))
        return urlEnd + 1
    }

    /**
     * <https://example.com>
     */
    private fun autolink(
        src: String,
        start: Int,
        out: StringBuilder,
        spans: MutableList<FormattedText.Span>,
    ): Int? {
        val end = src.indexOf('>', start)
        if (end < 0) return null
        val url = src.substring(start + 1, end)
        if (!isSafeUrl(url) || url.any { it.isWhitespace() }) return null
        spans.styled(out, FormattedText.Style.Link(url)) { out.append(url) }
        return end + 1
    }

    private fun bareUrl(
        src: String,
        start: Int,
        out: StringBuilder,
        spans: MutableList<FormattedText.Span>,
    ): Int? {
        val match = BareUrl.matchAt(src, start) ?: return null
        var url = match.value
        // Punctuation at the end belongs to the sentence, not the address
        while (url.isNotEmpty() && url.last() in ".,;:!?'\"") url = url.dropLast(1)
        if (url.endsWith(')') && url.count { it == '(' } < url.count { it == ')' }) url = url.dropLast(1)
        spans.styled(out, FormattedText.Style.Link(url)) { out.append(url) }
        return start + url.length
    }

    private fun isSafeUrl(url: String): Boolean {
        val scheme = url.substringBefore(':', "").lowercase()
        return scheme in SafeSchemes && url.length > scheme.length + 1
    }

    /**
     * The index of the bracket that closes the one at [start], or null
     */
    private fun String.matching(start: Int, open: Char, close: Char): Int? {
        var depth = 0
        var i = start
        while (i < length) {
            when (this[i]) {
                '\\' -> i++
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return null
    }

    private fun String.runLength(start: Int, char: Char): Int {
        var i = start
        while (i < length && this[i] == char) i++
        return i - start
    }

    private inline fun MutableList<FormattedText.Span>.styled(
        out: StringBuilder,
        style: FormattedText.Style,
        block: () -> Unit,
    ) {
        val from = out.length
        block()
        add(FormattedText.Span(from, out.length, style))
    }

    private val CodeFence = Regex("^ {0,3}(```|~~~).*$")
    private val Rule = Regex("^ {0,3}([-*_])(\\s*\\1){2,}\\s*$")
    private val Heading = Regex("^ {0,3}#{1,6}\\s+(.*?)(\\s+#+)?\\s*$")
    private val Quote = Regex("^ {0,3}>\\s?(.*)$")
    private val TaskItem = Regex("^(\\s*)[-*+]\\s+\\[([ xX])]\\s+(.*)$")
    private val Bullet = Regex("^(\\s*)[-*+]\\s+(.*)$")
    private val Numbered = Regex("^(\\s*)(\\d{1,9})[.)]\\s+(.*)$")
    private val BareUrl = Regex("https?://[^\\s<>\\[\\]]+", RegexOption.IGNORE_CASE)

    private const val MaxLength = 5000
    private const val Escapable = "\\`*_{}[]()#+-.!~>|<"
    private val SafeSchemes = setOf("http", "https", "mailto", "tel", "geo")
}
