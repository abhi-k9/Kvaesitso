package de.mm20.launcher2.services.favorites

import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.Tag
import de.mm20.launcher2.searchable.LearnedSearchRepository
import de.mm20.launcher2.searchable.PinnedLevel
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.searchable.VisibilityLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class FavoritesService(
    private val searchableRepository: SavableSearchableRepository,
    private val learnedSearchRepository: LearnedSearchRepository,
) {
    fun getCardFavorites(
        pinnedCount: Int,
        recentCount: Int,
        frequentCount: Int,
        excludeTypes: List<String>,
        folderTags: Set<String>,
    ): Flow<FavoritesCardItems> {
        val pinned: Flow<List<SavableSearchable>> = if (pinnedCount == 0) flowOf(emptyList()) else getFavorites(
            excludeTypes = excludeTypes.takeIf { it.isNotEmpty() },
            minPinnedLevel = PinnedLevel.AutomaticallySorted,
            limit = 9999,
        ).map { items -> items.filter { it !is Tag || it.tag in folderTags } }
        val recent = searchableRepository.getAppSuggestions(recentFirst = true, limit = recentCount)
        // Overfetch by the recent quota so removing duplicates doesn't starve frequent slots.
        val frequent = searchableRepository.getAppSuggestions(
            recentFirst = false,
            limit = if (frequentCount <= 0) 0 else frequentCount + recentCount,
        )
        return combine(pinned, recent, frequent) { pins, recents, frequents ->
            selectFavoritesCardItems(pins, recents, frequents, pinnedCount, recentCount, frequentCount)
        }
    }

    fun getFavorites(
        includeTypes: List<String>? = null,
        excludeTypes: List<String>? = null,
        minPinnedLevel: PinnedLevel = PinnedLevel.FrequentlyUsed,
        maxPinnedLevel: PinnedLevel = PinnedLevel.ManuallySorted,
        limit: Int = 100,
    ): Flow<List<SavableSearchable>> {
        return searchableRepository.get(
            includeTypes = includeTypes,
            excludeTypes = excludeTypes,
            minPinnedLevel = minPinnedLevel,
            maxPinnedLevel = maxPinnedLevel,
            limit = limit,
            minVisibility = VisibilityLevel.SearchOnly,
            filterExcludedSuggestions = true,
        )
    }

    fun isExcludedFromFrequentlyUsed(searchable: SavableSearchable): Flow<Boolean> =
        searchableRepository.isExcludedFromFrequentlyUsed(searchable)

    fun setExcludedFromFrequentlyUsed(searchable: SavableSearchable, excluded: Boolean) {
        searchableRepository.setExcludedFromFrequentlyUsed(searchable, excluded)
    }

    fun isPinned(searchable: SavableSearchable): Flow<Boolean> {
        return searchableRepository.isPinned(searchable)
    }

    fun getVisibility(searchable: SavableSearchable): Flow<VisibilityLevel> {
        return searchableRepository.getVisibility(searchable)
    }

    fun pinItem(searchable: SavableSearchable) {
        searchableRepository.upsert(
            searchable,
            pinned = true,
        )
    }

    fun reset(searchable: SavableSearchable) {
        searchableRepository.update(
            searchable,
            pinned = false,
            visibility = VisibilityLevel.Default,
            weight = 0.0,
            launchCount = 0,
        )
    }

    fun unpinItem(searchable: SavableSearchable) {
        searchableRepository.upsert(
            searchable,
            pinned = false,
        )
    }

    fun setVisibility(searchable: SavableSearchable, visibility: VisibilityLevel) {
        searchableRepository.upsert(
            searchable,
            visibility = visibility,
        )
    }

    fun reportLaunch(searchable: SavableSearchable) {
        searchableRepository.touch(searchable)
        learnedSearchRepository.reportLaunch(searchable.key)
    }

    fun updateFavorites(
        manuallySorted: List<SavableSearchable>,
        automaticallySorted: List<SavableSearchable>
    ) {
        searchableRepository.updateFavorites(
            manuallySorted = manuallySorted,
            automaticallySorted = automaticallySorted
        )
    }

    fun delete(searchable: SavableSearchable) {
        searchableRepository.delete(searchable)
    }

    fun upsert(searchable: SavableSearchable) {
        searchableRepository.upsert(searchable)
    }
}
