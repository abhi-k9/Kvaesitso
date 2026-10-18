package de.mm20.launcher2.search

/**
 * Orders search results by how well they match and how often they have been used.
 *
 * Results that have been learned for the query (see [LearnedSearches]) come first, in the order
 * of [learnedRank]. Then the kind of match decides (see the bands of [ResultScore]): names that
 * contain what was typed rank above names that only look like a typo of it, however often those
 * have been used, and for very short queries, names where a word starts with the query rank above
 * names that only contain it somewhere. Within each kind, the score and the usage weight are
 * balanced. Results that don't match at all (e.g. found by a tag) come last, by usage.
 *
 * @param score how well an item matches the query
 * @param weight how often an item has been used, between 0 and 1
 * @param learnedRank the position of an item among the results learned for the query (0 first),
 * or null if it hasn't been learned
 */
fun <T> rankSearchResults(
    items: List<T>,
    score: (T) -> Float,
    weight: (T) -> Double,
    learnedRank: (T) -> Int? = { null },
): List<T> {
    if (items.size <= 1) return items
    val learned = IntArray(items.size)
    val tiers = IntArray(items.size)
    val totals = FloatArray(items.size)
    for ((i, item) in items.withIndex()) {
        val s = score(item)
        learned[i] = learnedRank(item) ?: Int.MAX_VALUE
        tiers[i] = matchTier(s)
        totals[i] = s * ScoreFactor + weight(item).toFloat() * WeightFactor
    }
    return items.indices
        .sortedWith(
            compareBy<Int> { learned[it] }
                .thenBy { tiers[it] }
                .thenByDescending { totals[it] }
        )
        .map { items[it] }
}

/**
 * 0 for strong literal matches, 1 for weak literal matches, 2 for typo matches, 3 for results
 * that don't match.
 */
internal fun matchTier(score: Float): Int = when {
    score >= ResultScore.StrongLiteralBand -> 0
    score >= ResultScore.WeakLiteralBand -> 1
    score >= ResultScore.MatchThreshold -> 2
    else -> 3
}

private const val ScoreFactor = 0.6f
private const val WeightFactor = 0.4f
