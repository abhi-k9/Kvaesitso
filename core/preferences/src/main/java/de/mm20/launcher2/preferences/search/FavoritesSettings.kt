package de.mm20.launcher2.preferences.search

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class FavoritesSettingsData(
    val columns: Int,
    val frequentlyUsed: Boolean,
    val frequentlyUsedRows: Int,
)

/** Layout and item limits specific to the app-drawer favorites card. */
data class FavoritesCardSettingsData(
    val columns: Int = 5,
    val iconSize: Int = 48,
    val showLabels: Boolean = true,
    val showGroupLabels: Boolean = true,
    val pinnedCount: Int = -1,
    val recentCount: Int = 4,
    val frequentCount: Int = -1,
    val frequentlyUsedRows: Int = 1,
)

class FavoritesSettings internal constructor(
    private val dataStore: LauncherDataStore,
) : Flow<FavoritesSettingsData> by (dataStore.data.map {
    FavoritesSettingsData(
        columns = it.gridColumnCount,
        frequentlyUsed = it.favoritesFrequentlyUsed,
        frequentlyUsedRows = it.favoritesFrequentlyUsedRows,
    )
}.distinctUntilChanged()) {

    val card: Flow<FavoritesCardSettingsData> = dataStore.data.map {
        FavoritesCardSettingsData(
            columns = (it.favoritesCardColumns.takeIf { count -> count > 0 } ?: it.gridColumnCount).coerceIn(1, 10),
            iconSize = (it.favoritesCardIconSize.takeIf { size -> size > 0 } ?: it.gridIconSize).coerceIn(24, 96),
            showLabels = it.favoritesCardLabels ?: it.gridLabels,
            showGroupLabels = it.favoritesCardGroupLabels,
            pinnedCount = it.favoritesCardPinnedCount.coerceIn(-1, 60),
            recentCount = it.favoritesCardRecentCount.coerceIn(0, 60),
            frequentCount = if (it.favoritesFrequentlyUsed) it.favoritesCardFrequentCount.coerceIn(-1, 60) else 0,
            frequentlyUsedRows = it.favoritesFrequentlyUsedRows.coerceIn(1, 8),
        )
    }.distinctUntilChanged()

    fun setCardColumns(columns: Int) = dataStore.update {
        it.copy(favoritesCardColumns = columns.coerceIn(1, 10))
    }

    fun setCardIconSize(size: Int) = dataStore.update {
        it.copy(favoritesCardIconSize = size.coerceIn(24, 96))
    }

    fun setCardLabels(show: Boolean) = dataStore.update { it.copy(favoritesCardLabels = show) }
    fun setCardGroupLabels(show: Boolean) = dataStore.update { it.copy(favoritesCardGroupLabels = show) }
    fun setCardPinnedCount(count: Int) = dataStore.update { it.copy(favoritesCardPinnedCount = count.coerceIn(-1, 60)) }
    fun setCardRecentCount(count: Int) = dataStore.update { it.copy(favoritesCardRecentCount = count.coerceIn(0, 60)) }
    fun setCardFrequentCount(count: Int) = dataStore.update { it.copy(favoritesCardFrequentCount = count.coerceIn(-1, 60)) }

    fun resetCardLayout() = dataStore.update {
        it.copy(favoritesCardColumns = 0, favoritesCardIconSize = 0, favoritesCardLabels = null)
    }

    val showEditButton
        get() = dataStore.data.map { it.favoritesEditButton }.distinctUntilChanged()

    fun setShowEditButton(showEditButton: Boolean) {
        dataStore.update { it.copy(favoritesEditButton = showEditButton) }
    }

    val frequentlyUsed: Flow<Boolean>
        get() = dataStore.data.map { it.favoritesFrequentlyUsed }.distinctUntilChanged()

    fun setFrequentlyUsed(frequentlyUsed: Boolean) {
        dataStore.update { it.copy(favoritesFrequentlyUsed = frequentlyUsed) }
    }

    val frequentlyUsedRows: Flow<Int>
        get() = dataStore.data.map { it.favoritesFrequentlyUsedRows }.distinctUntilChanged()

    fun setFrequentlyUsedRows(frequentlyUsedRows: Int) {
        dataStore.update { it.copy(favoritesFrequentlyUsedRows = frequentlyUsedRows) }
    }

    val compactTags: Flow<Boolean>
        get() = dataStore.data.map { it.favoritesCompactTags }.distinctUntilChanged()

    fun setCompactTags(compactTags: Boolean) {
        dataStore.update { it.copy(favoritesCompactTags = compactTags) }
    }

    /**
     * Whether the tags are shown in a bar at the bottom of the search screen, which doesn't
     * scroll with the list
     */
    val tagsFixed: Flow<Boolean>
        get() = dataStore.data.map { it.favoritesTagsFixed }.distinctUntilChanged()

    fun setTagsFixed(tagsFixed: Boolean) {
        dataStore.update { it.copy(favoritesTagsFixed = tagsFixed) }
    }
}
