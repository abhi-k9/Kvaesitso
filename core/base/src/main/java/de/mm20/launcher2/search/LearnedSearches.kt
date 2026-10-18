package de.mm20.launcher2.search

/**
 * A search result that was opened after typing [query].
 *
 * @param query the normalized query, see [LearnedSearches.cleanQuery]
 * @param key the key of the result
 * @param count how strongly the result is associated with the query: increased when it's opened
 * after typing the query, decreased when another result is opened after typing it
 * @param lastUsed when the result was last opened after typing the query
 */
data class LearnedSearch(
    val query: String,
    val key: String,
    val count: Int,
    val lastUsed: Long,
)

/**
 * Learns which results are opened after typing a query, so that they can come first the next
 * time: if "tel" was typed to open Telegram, Telegram comes first for "t", "te" and "tel".
 */
object LearnedSearches {

    /**
     * How many entries are kept: the ones that have been used the longest time ago are removed
     * first.
     */
    const val MaxEntries = 300

    private const val MaxCount = 20

    /**
     * A normalized query as it is stored and looked up: without spaces at the start and end and
     * without repeated spaces.
     */
    fun cleanQuery(normalizedQuery: String): String = ResultScore.collapseSpaces(normalizedQuery)

    /**
     * [entries] after the result with [key] has been opened after typing [query]. Other results
     * that had been opened after typing exactly [query] lose some of their weight, so that a new
     * habit takes over after a few times.
     */
    fun record(
        entries: List<LearnedSearch>,
        query: String,
        key: String,
        time: Long,
    ): List<LearnedSearch> {
        if (query.isEmpty()) return entries
        var found = false
        val updated = entries.mapNotNull {
            when {
                it.query != query -> it
                it.key == key -> {
                    found = true
                    it.copy(count = minOf(it.count + 1, MaxCount), lastUsed = time)
                }

                it.count > 1 -> it.copy(count = it.count - 1)
                else -> null
            }
        }
        val all = if (found) updated else updated + LearnedSearch(query, key, 1, time)
        return all.sortedByDescending { it.lastUsed }.take(MaxEntries)
    }

    /**
     * The results learned for [query], by key, with their position: 0 comes first.
     *
     * Every entry for a query that starts with [query] counts ("tel" counts for "t"), entries for
     * longer queries less than entries for [query] itself. Results with the same weight are
     * ordered by when they were last opened.
     */
    fun ranks(entries: List<LearnedSearch>, query: String): Map<String, Int> {
        if (query.isEmpty()) return emptyMap()
        val weights = mutableMapOf<String, Float>()
        val lastUsed = mutableMapOf<String, Long>()
        for (entry in entries) {
            if (!entry.query.startsWith(query)) continue
            val weight = entry.count * query.length.toFloat() / entry.query.length
            weights[entry.key] = (weights[entry.key] ?: 0f) + weight
            lastUsed[entry.key] = maxOf(lastUsed[entry.key] ?: 0L, entry.lastUsed)
        }
        return weights.keys
            .sortedWith(
                compareByDescending<String> { weights[it] }.thenByDescending { lastUsed[it] }
            )
            .withIndex()
            .associate { (i, key) -> key to i }
    }

    /**
     * [entries] without the entries of the result with [key], e.g. after it was removed.
     */
    fun withoutKey(entries: List<LearnedSearch>, key: String): List<LearnedSearch> {
        return entries.filter { it.key != key }
    }
}
