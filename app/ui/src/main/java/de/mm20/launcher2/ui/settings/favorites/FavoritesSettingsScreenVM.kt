package de.mm20.launcher2.ui.settings.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.WeightFactor
import de.mm20.launcher2.preferences.search.FavoritesSettings
import de.mm20.launcher2.preferences.search.FavoritesCardSettingsData
import de.mm20.launcher2.preferences.search.RankingSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FavoritesSettingsScreenVM: ViewModel(), KoinComponent {
    private val favoritesSettings: FavoritesSettings by inject()
    private val rankingSettings: RankingSettings by inject()

    val card = favoritesSettings.card
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), FavoritesCardSettingsData())

    fun setCardColumns(value: Int) = favoritesSettings.setCardColumns(value)
    fun setCardIconSize(value: Int) = favoritesSettings.setCardIconSize(value)
    fun setCardLabels(value: Boolean) = favoritesSettings.setCardLabels(value)
    fun setCardGroupLabels(value: Boolean) = favoritesSettings.setCardGroupLabels(value)
    fun setCardPinnedCount(value: Int) = favoritesSettings.setCardPinnedCount(value)
    fun setCardRecentCount(value: Int) = favoritesSettings.setCardRecentCount(value)
    fun setCardFrequentCount(value: Int) = favoritesSettings.setCardFrequentCount(value)
    fun resetCardLayout() = favoritesSettings.resetCardLayout()

    val frequentlyUsed = favoritesSettings.frequentlyUsed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setFrequentlyUsed(frequentlyUsed: Boolean) {
        favoritesSettings.setFrequentlyUsed(frequentlyUsed)
    }

    val frequentlyUsedRows = favoritesSettings.frequentlyUsedRows
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 1)
    fun setFrequentlyUsedRows(frequentlyUsedRows: Int) {
        favoritesSettings.setFrequentlyUsedRows(frequentlyUsedRows)
    }

    val editButton = favoritesSettings.showEditButton
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setEditButton(editButton: Boolean) {
        favoritesSettings.setShowEditButton(editButton)
    }

    val searchResultWeightFactor = rankingSettings.weightFactor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), WeightFactor.Default)
    fun setSearchResultWeightFactor(searchResultWeightFactor: WeightFactor) {
        rankingSettings.setWeightFactor(searchResultWeightFactor)
    }

    val compactTags = favoritesSettings.compactTags
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setCompactTags(compactTags: Boolean) {
        favoritesSettings.setCompactTags(compactTags)
    }

    val tagsFixed = favoritesSettings.tagsFixed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setTagsFixed(tagsFixed: Boolean) {
        favoritesSettings.setTagsFixed(tagsFixed)
    }
}
