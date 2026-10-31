package de.mm20.launcher2.services.favorites

import de.mm20.launcher2.search.SavableSearchable

data class FavoritesCardItems(
    val pinned: List<SavableSearchable> = emptyList(),
    val recent: List<SavableSearchable> = emptyList(),
    val frequent: List<SavableSearchable> = emptyList(),
) {
    val all: List<SavableSearchable> get() = pinned + recent + frequent
}

/** Each item occupies one slot; earlier groups have priority over later ones. */
internal fun selectFavoritesCardItems(
    pinned: List<SavableSearchable>,
    recent: List<SavableSearchable>,
    frequent: List<SavableSearchable>,
    pinnedCount: Int,
    recentCount: Int,
    frequentCount: Int,
): FavoritesCardItems {
    val seen = mutableSetOf<String>()
    fun select(items: List<SavableSearchable>, count: Int): List<SavableSearchable> {
        if (count == 0) return emptyList()
        return buildList {
            for (item in items) {
                if (seen.add(item.key)) add(item)
                if (size >= count) break
            }
        }
    }
    return FavoritesCardItems(
        pinned = select(pinned, if (pinnedCount < 0) Int.MAX_VALUE else pinnedCount),
        recent = select(recent, recentCount.coerceAtLeast(0)),
        frequent = select(frequent, frequentCount.coerceAtLeast(0)),
    )
}
