package de.mm20.launcher2.search

/**
 * Orders search results by how well they match and how often they have been used.
 *
 * The kind of match comes first (see the bands of [ResultScore]): names that contain what was
 * typed rank above names that only look like a typo of it, however often those have been used,
 * and for very short queries, names where a word starts with the query rank above names that
 * only contain it somewhere. Within each kind, the score and the usage weight are balanced.
 * Results that don't match at all (e.g. found by a tag) come last, by usage.
 *
 * @param score how well an item matches the query
 * @param weight how often an item has been used, between 0 and 1
 */
fun <T> rankSearchResults(
    items: List<T>,
    score: (T) -> Float,
    weight: (T) -> Double,
): List<T> {
    if (items.size <= 1) return items
    val tiers = IntArray(items.size)
    val totals = FloatArray(items.size)
    for ((i, item) in items.withIndex()) {
        val s = score(item)
        tiers[i] = matchTier(s)
        totals[i] = s * ScoreFactor + weight(item).toFloat() * WeightFactor
    }
    return items.indices
        .sortedWith(compareBy<Int> { tiers[it] }.thenByDescending { totals[it] })
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
