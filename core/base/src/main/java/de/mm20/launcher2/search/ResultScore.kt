package de.mm20.launcher2.search

import com.aallam.similarity.JaroWinkler
import de.mm20.launcher2.search.fuzzy.FzfMatcher

/**
 * How well a search result matches a query, between 0 and 1. Higher is better. Any score of at
 * least [MatchThreshold] is a match.
 */
@JvmInline
value class ResultScore(val score: Float) : Comparable<ResultScore> {

    override fun compareTo(other: ResultScore): Int {
        return score.compareTo(other.score)
    }

    /**
     * An immutable query scorer that can be reused for all candidates in one search. Query
     * whitespace, words and their maximum fuzzy scores are calculated once, rather than once
     * per candidate. The selected keyboard layout belongs to this scorer, not a global cache.
     */
    class PreparedQuery internal constructor(query: String, internal val keyboardLayout: KeyboardLayout) {
        internal val text = collapseSpaces(query)
        internal val maxScore = FzfMatcher.maxScoreFor(text)
        internal val words: List<String>? = if (' ' in text) text.split(' ') else null
        internal val wordMaxScores: IntArray? = words?.let { w ->
            IntArray(w.size) { FzfMatcher.maxScoreFor(w[it]) }
        }

        fun score(
            primaryFields: Iterable<String> = emptyList(),
            secondaryFields: Iterable<String> = emptyList(),
        ): ResultScore {
            // An empty query is a prefix of everything.
            if (text.isEmpty()) return ResultScore(1f)
            val bestPrimary = primaryFields.maxOfOrNull { scoreTerm(this, it) } ?: 0f
            val bestSecondary = secondaryFields.maxOfOrNull {
                scoreTerm(this, it) * SecondaryFieldFactor
            } ?: 0f
            return ResultScore(maxOf(bestPrimary, bestSecondary))
        }
    }

    companion object {
        /**
         * Scores [query] against the best of [primaryFields] and [secondaryFields]. Queries and
         * fields are expected to be normalized (see [StringNormalizer]) by the caller.
         *
         * A field matches if it contains the query's characters in order (an fzf-style
         * subsequence match, e.g. "ytm" in "yt music"), or failing that, if it is close enough to
         * the query to be a typo of it (e.g. "settimgs" for "settings"). Word prefixes also
         * account for neighboring keys of [keyboardLayout]: with QWERTY, "swtt" can find
         * "settings" while still typing.
         *
         * Every match lands in one of three non-overlapping bands, so that the kind of evidence
         * decides the order before the similarity within a band does:
         *
         * - `[0.95, 1]` Strong literal match: the characters are all there, tightly clustered or at
         *   word boundaries. An exact match scores 1.
         * - `[0.90, 0.95)` Weak literal match: the characters are all there, but scattered.
         * - `[0.80, 0.90)` Typo match: some typed character is not in the field at all, so this is
         *   a guess at a correction.
         *
         * All bands sit above [MatchThreshold], where the previous substring-or-similar scorer put
         * its matches too, so the balance against usage weights in ranking is unchanged.
         *
         * Spaces at the start or end of the query and repeated spaces are ignored: keyboards often
         * add a space after a word that was picked from the suggestions.
         *
         * The words of a query with several words may also match in another order ("maps google"
         * for "google maps"): every word has to match on its own then, and the match ranks just
         * below the same match in the right order, in the band of its weakest word.
         */
        fun from(
            query: String,
            primaryFields: Iterable<String> = emptyList(),
            secondaryFields: Iterable<String> = emptyList(),
            keyboardLayout: KeyboardLayout = KeyboardLayout.Qwerty,
        ): ResultScore = prepare(query, keyboardLayout).score(primaryFields, secondaryFields)

        /**
         * Prepare the normalized query once before scoring a batch of labels or tags. Create a
         * new scorer when the query or keyboard layout changes; no candidate labels are retained.
         */
        fun prepare(query: String, keyboardLayout: KeyboardLayout = KeyboardLayout.Qwerty): PreparedQuery =
            PreparedQuery(query, keyboardLayout)

        private fun scoreTerm(query: PreparedQuery, term: String): Float {
            val whole = scoreField(query.text, term, query.maxScore, query.keyboardLayout)
            val words = query.words ?: return whole
            val wordMaxScores = query.wordMaxScores ?: return whole
            // Already found as typed: no other order can do better
            if (whole >= WeakLiteralBand) return whole

            var weakest = 1f
            for (i in words.indices) {
                val wordScore = scoreField(words[i], term, wordMaxScores[i], query.keyboardLayout)
                if (wordScore == 0f) return whole
                weakest = minOf(weakest, wordScore)
            }
            val band = when {
                weakest >= StrongLiteralBand -> StrongLiteralBand
                weakest >= WeakLiteralBand -> WeakLiteralBand
                else -> TypoBand
            }
            val anyOrder = maxOf(band, weakest - OtherOrderPenalty)
            return maxOf(whole, anyOrder)
        }

        private fun scoreField(query: String, term: String, maxScore: Int, keyboardLayout: KeyboardLayout): Float {
            val literal = FzfMatcher.normalizedScore(query, term, maxScore)
            if (literal > 0f) {
                val banded = if (literal >= StrongLiteralThreshold) {
                    lerp(
                        StrongLiteralBand,
                        1f,
                        (literal - StrongLiteralThreshold) / (1f - StrongLiteralThreshold)
                    )
                } else {
                    lerp(WeakLiteralBand, StrongLiteralBand, literal / StrongLiteralThreshold)
                }
                if (query.length > ShortQueryLength) return banded
                // One or two characters are in the middle of nearly every name ("c" in "music"):
                // only a match from the start of a word counts as strong then.
                return if (startsAtWordStart(query, term)) {
                    maxOf(banded, StrongLiteralBand)
                } else {
                    lerp(WeakLiteralBand, StrongLiteralBand, literal * 0.99f)
                }
            }

            // With only one or two characters, there is nothing to base a correction on.
            if (query.length < MinTypoQueryLength) return 0f

            var similarity = jaroWinkler.similarity(query, term).toFloat()
            // A typo in one word of a longer name, e.g. "photso" for "google photos": compared
            // with the whole name, the other words make it look too different. Only for queries
            // of a single word: a longer query would look similar to the word it starts with.
            if (similarity < MatchThreshold && query.length >= MinWordTypoQueryLength && ' ' !in query) {
                similarity = maxOf(similarity, bestWordSimilarity(query, term))
            }
            // Similarity is only 1 for identical strings, which the literal path has already
            // caught, so this stays below WeakLiteralBand.
            val similar = if (similarity >= MatchThreshold) {
                lerp(
                    TypoBand,
                    WeakLiteralBand,
                    (similarity - MatchThreshold) / (1f - MatchThreshold)
                )
            } else 0f
            // A typo in a word that is only partly typed, e.g. "swtti" for "settings": compared
            // with the whole word, the missing rest makes it look too different.
            val partial = if (' ' !in query) partlyTypedWordScore(query, term, keyboardLayout) else 0f
            return maxOf(similar, partial)
        }

        /**
         * A typo score for a word prefix. Four characters allow one neighboring-key substitution;
         * general edits still need five characters, and two errors still need nine. Keyboard
         * proximity changes confidence, not the maximum number of mistakes we accept.
         */
        private fun partlyTypedWordScore(query: String, term: String, keyboardLayout: KeyboardLayout): Float {
            val maxEdits = when {
                query.length >= TwoTyposQueryLength -> 2
                query.length >= OneTypoQueryLength -> 1
                else -> 0
            }
            if (query.length < MinKeyboardTypoQueryLength) return 0f
            var best = 0f
            for (start in term.indices) {
                if (!term[start].isLetterOrDigit()) continue
                if (start > 0 && term[start - 1].isLetterOrDigit()) continue
                val substitutionCost = prefixSubstitutionCost(query, term, start, maxOf(1, maxEdits), keyboardLayout)
                if (substitutionCost != null) {
                    best = maxOf(best, prefixTypoScore(substitutionCost / 2f, query.length))
                }
                // A valid substitution path costing at most one ordinary edit cannot be
                // improved by insertions, deletions or transpositions. Avoid the DP in that case.
                if (maxEdits > 0 && (substitutionCost == null || substitutionCost > 2)) {
                    val edits = prefixEditDistance(query, term, start, maxEdits)
                    if (edits <= maxEdits) best = maxOf(best, prefixTypoScore(edits.toFloat(), query.length))
                }
            }
            return best
        }

        /** Substitutions only: one unit for neighboring keys, two otherwise, or no valid path. */
        private fun prefixSubstitutionCost(query: String, term: String, start: Int, maxErrors: Int, keyboardLayout: KeyboardLayout): Int? {
            if (term.length - start < query.length) return null
            var errors = 0
            var cost = 0
            for (i in query.indices) {
                val typed = query[i]
                val expected = term[start + i]
                // Never complete a prefix across a word boundary.
                if (!expected.isLetterOrDigit()) return null
                if (typed == expected) continue
                if (++errors > maxErrors) return null
                if (keyboardLayout.areNeighbors(typed, expected)) {
                    cost++
                } else {
                    if (query.length < OneTypoQueryLength) return null
                    cost += 2
                }
            }
            return cost
        }

        private fun prefixTypoScore(cost: Float, typedLength: Int): Float {
            // More correct characters provide more evidence; near-key substitutions count as
            // half an edit. Every correction remains below the literal-match band.
            return lerp(TypoBand, WeakLiteralBand, 1f - cost / typedLength)
        }

        /**
         * The fewest edits (inserting, removing or replacing a character, or swapping two
         * neighbouring characters) that turn [query] into a word prefix of [term] from [start] on,
         * or more than [maxEdits] if that takes more than [maxEdits] edits.
         */
        internal fun prefixEditDistance(query: String, term: String, start: Int, maxEdits: Int): Int {
            val limit = minOf(term.length - start, query.length + maxEdits)
            var m = 0
            while (m < limit && term[start + m].isLetterOrDigit()) m++
            // Rows of the edit distance matrix: for the first i - 2, i - 1 and i characters of
            // the query, against the first j characters of the term from start on
            var older = IntArray(m + 1)
            var previous = IntArray(m + 1) { it }
            var current = IntArray(m + 1)
            for (i in 1..query.length) {
                current[0] = i
                var rowMin = i
                for (j in 1..m) {
                    val q = query[i - 1]
                    val t = term[start + j - 1]
                    var edits = minOf(
                        previous[j] + 1,
                        current[j - 1] + 1,
                        previous[j - 1] + if (q == t) 0 else 1,
                    )
                    if (i > 1 && j > 1 && q == term[start + j - 2] && query[i - 2] == t) {
                        edits = minOf(edits, older[j - 2] + 1)
                    }
                    current[j] = edits
                    if (edits < rowMin) rowMin = edits
                }
                if (rowMin > maxEdits) return maxEdits + 1
                val recycled = older
                older = previous
                previous = current
                current = recycled
            }
            // Any beginning of the term will do: the rest of it hasn't been typed yet
            return previous.min()
        }

        /**
         * Whether a word of [term] starts with the first character of [query], followed by the rest
         * of [query] (in order, not necessarily contiguously): "c" in "music clock", "wa" in
         * "whatsapp", "gm" in "google maps".
         */
        private fun startsAtWordStart(query: String, term: String): Boolean {
            val first = query[0]
            for (i in term.indices) {
                if (term[i] != first) continue
                if (i > 0 && term[i - 1].isLetterOrDigit()) continue
                if (FzfMatcher.matches(query.substring(1), term.substring(i + 1))) return true
            }
            return false
        }

        /**
         * The highest similarity of [query] to one of the words of [term], or 0 if [term] has only
         * one word.
         */
        private fun bestWordSimilarity(query: String, term: String): Float {
            var best = 0f
            var wordStart = -1
            for (i in 0..term.length) {
                val isWordChar = i < term.length && term[i].isLetterOrDigit()
                if (isWordChar) {
                    if (wordStart < 0) wordStart = i
                } else if (wordStart >= 0) {
                    if (wordStart > 0 || i < term.length) {
                        val word = term.substring(wordStart, i)
                        best = maxOf(best, jaroWinkler.similarity(query, word).toFloat())
                    }
                    wordStart = -1
                }
            }
            return best
        }

        /**
         * [query] without spaces at the start and end, and with runs of whitespace replaced by a
         * single space.
         */
        internal fun collapseSpaces(query: String): String {
            var clean = true
            for (i in query.indices) {
                val char = query[i]
                if (!char.isWhitespace()) continue
                if (char != ' ' || i == 0 || i == query.lastIndex || query[i + 1].isWhitespace()) {
                    clean = false
                    break
                }
            }
            if (clean) return query
            return query.split(Whitespace).filter { it.isNotEmpty() }.joinToString(" ")
        }

        private val Whitespace = Regex("\\s+")

        private fun lerp(start: Float, end: Float, fraction: Float): Float {
            return start + (end - start) * fraction.coerceIn(0f, 1f)
        }

        /**
         * The lowest score that counts as a match.
         */
        internal const val MatchThreshold = 0.8f

        /**
         * Normalized fzf score from which a literal match is considered strong.
         */
        private const val StrongLiteralThreshold = 0.5f

        internal const val StrongLiteralBand = 0.95f
        internal const val WeakLiteralBand = 0.9f
        private const val TypoBand = MatchThreshold

        /**
         * Queries up to this length only match strongly at the start of a word.
         */
        private const val ShortQueryLength = 2

        private const val MinTypoQueryLength = 3

        /**
         * Typos are also looked for in single words of a name from this query length on: there
         * are more words to compare with, so it takes a little more evidence.
         */
        private const val MinWordTypoQueryLength = 4

        /** Three exact letters plus one plausible keyboard slip provide enough evidence. */
        private const val MinKeyboardTypoQueryLength = 4

        /**
         * From this query length on, one typo is tolerated in a word that is only partly typed
         */
        private const val OneTypoQueryLength = 5

        /**
         * From this query length on, two typos are tolerated in a word that is only partly typed
         */
        private const val TwoTyposQueryLength = 9

        private val jaroWinkler = JaroWinkler()

        /**
         * How much less words in another order are worth than the same words in the order of the
         * name.
         */
        private const val OtherOrderPenalty = 0.005f

        /**
         * How much a match on a secondary field is worth relative to the same match on a primary
         * field.
         */
        private const val SecondaryFieldFactor = 0.8f

        val Zero = ResultScore(0f)

        /**
         * No score has been computed yet. Results that carry this are scored lazily when they are
         * ranked, since not every repository scores its own results.
         */
        val Unspecified = ResultScore(Float.NaN)
    }
}

inline val ResultScore.isUnspecified: Boolean
    get() = score.isNaN()

/**
 * Whether this score is a match at all, typo matches included
 */
val ResultScore.isMatch: Boolean
    get() = score >= ResultScore.MatchThreshold

/**
 * Whether all the characters of the query are there, tightly clustered or at the start of words
 */
val ResultScore.isStrongMatch: Boolean
    get() = score >= ResultScore.StrongLiteralBand
