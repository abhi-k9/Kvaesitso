package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class UiState internal constructor(
    private val launcherDataStore: LauncherDataStore,
){
    val favoritesTagsExpanded
        get() = launcherDataStore.data.map { it.stateTagsMultiline }.distinctUntilChanged()

    fun setFavoritesTagsExpanded(favoritesTagsExpanded: Boolean) {
        launcherDataStore.update {
            it.copy(stateTagsMultiline = favoritesTagsExpanded)
        }
    }

    /**
     * The tags that have last been added to items, the most recent one first
     */
    val recentTags
        get() = launcherDataStore.data.map { it.stateRecentTags }.distinctUntilChanged()

    /**
     * Remembers that [tags] have been added to an item, in this order
     */
    fun addRecentTags(tags: List<String>) {
        if (tags.isEmpty()) return
        launcherDataStore.update {
            it.copy(stateRecentTags = it.stateRecentTags.withRecent(tags))
        }
    }
}

private const val RecentTagsCount = 3

/**
 * This list of recent items, most recent first, after [added] have been used in this order
 */
internal fun List<String>.withRecent(added: List<String>): List<String> {
    return (added.asReversed() + this).distinct().take(RecentTagsCount)
}
