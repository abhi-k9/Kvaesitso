package de.mm20.launcher2.ui.settings.learnedsearches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.data.customattrs.utils.withCustomLabels
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.preferences.search.RankingSettings
import de.mm20.launcher2.search.LearnedSearch
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.searchable.LearnedSearchRepository
import de.mm20.launcher2.searchable.SavableSearchableRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * A learned search and the item it leads to
 */
data class LearnedResult(
    val search: LearnedSearch,
    val item: SavableSearchable,
)

class LearnedSearchesSettingsScreenVM : ViewModel(), KoinComponent {
    private val learnedSearchRepository: LearnedSearchRepository by inject()
    private val searchableRepository: SavableSearchableRepository by inject()
    private val customAttributesRepository: CustomAttributesRepository by inject()
    private val rankingSettings: RankingSettings by inject()
    private val iconService: IconService by inject()

    val learnFromLaunches = rankingSettings.learnFromLaunches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setLearnFromLaunches(learn: Boolean) {
        rankingSettings.setLearnFromLaunches(learn)
    }

    /**
     * Everything that has been learned, most recently used first. Items that don't exist anymore
     * (e.g. uninstalled apps) are left out.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val results: StateFlow<List<LearnedResult>?> = learnedSearchRepository.entries
        .flatMapLatest { entries ->
            searchableRepository.getByKeys(entries.map { it.key }.distinct())
                .withCustomLabels(customAttributesRepository)
                .map { items ->
                    val byKey = items.associateBy { it.key }
                    entries.mapNotNull { entry ->
                        byKey[entry.key]?.let { LearnedResult(entry, it) }
                    }
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun remove(search: LearnedSearch) {
        learnedSearchRepository.remove(search)
    }

    fun clear() {
        learnedSearchRepository.clear()
    }

    fun getIcon(searchable: SavableSearchable, size: Int): Flow<LauncherIcon?> {
        return iconService.getIcon(searchable, size)
    }
}
