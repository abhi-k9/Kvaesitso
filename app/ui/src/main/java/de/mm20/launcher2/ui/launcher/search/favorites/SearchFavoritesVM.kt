package de.mm20.launcher2.ui.launcher.search.favorites

import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.data.customattrs.TagFoldersRepository
import de.mm20.launcher2.data.customattrs.utils.withCustomLabels
import de.mm20.launcher2.preferences.search.FavoritesCardSettingsData
import de.mm20.launcher2.preferences.ui.UiState
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.services.favorites.FavoritesCardItems
import de.mm20.launcher2.ui.common.FavoritesVM
import de.mm20.launcher2.widgets.CalendarWidget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import org.koin.core.component.get
import org.koin.core.component.inject

class SearchFavoritesVM : FavoritesVM() {
    private val uiState: UiState by inject()

    val cardSettings = settings.card.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(), FavoritesCardSettingsData(),
    )

    val cardItems: Flow<FavoritesCardItems> = selectedTag.flatMapLatest { tag ->
        if (tag != null) flowOf(FavoritesCardItems()) else combine(
            settings.card,
            widgetRepository.exists(CalendarWidget.Type),
            folderTags(),
        ) { card, calendar, folders -> Triple(card, calendar, folders) }
            .flatMapLatest { (card, calendar, folders) ->
                favoritesService.getCardFavorites(
                    pinnedCount = card.pinnedCount,
                    recentCount = card.recentCount,
                    frequentCount = if (card.frequentCount < 0) card.frequentlyUsedRows * card.columns else card.frequentCount,
                    excludeTypes = if (calendar) listOf("calendar", "tasks.org", "plugin.calendar") else emptyList(),
                    folderTags = folders,
                )
            }.flatMapLatest { groups ->
                flowOf(groups.all).withCustomLabels(customAttributesRepository).map { labelled ->
                    val byKey = labelled.associateBy { it.key }
                    groups.copy(
                        pinned = groups.pinned.map { byKey[it.key] ?: it },
                        recent = groups.recent.map { byKey[it.key] ?: it },
                        frequent = groups.frequent.map { byKey[it.key] ?: it },
                    )
                }
            }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    override val favorites: Flow<List<SavableSearchable>> = selectedTag.flatMapLatest { tag ->
        if (tag == null) cardItems.map { it.all } else customAttributesRepository.getItemsForTag(tag)
            .withCustomLabels(customAttributesRepository).map { it.sorted() }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    override val tagsExpanded: Flow<Boolean> = uiState.favoritesTagsExpanded

    override fun setTagsExpanded(expanded: Boolean) {
        uiState.setFavoritesTagsExpanded(expanded)
    }

    override val compactTags: Flow<Boolean> = settings.compactTags

    val tagsFixed: Flow<Boolean> = settings.tagsFixed

    override fun folderTags(): Flow<Set<String>> = get<TagFoldersRepository>().folderTags

}
