package de.mm20.launcher2.ui.settings.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.data.customattrs.utils.withCustomLabels
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.searchable.SavableSearchableRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ExcludedSuggestionsSettingsScreenVM : ViewModel(), KoinComponent {
    private val repository: SavableSearchableRepository by inject()
    private val customAttributes: CustomAttributesRepository by inject()
    private val iconService: IconService by inject()

    val items: StateFlow<List<SavableSearchable>?> = repository.getExcludedFromFrequentlyUsed()
        .withCustomLabels(customAttributes)
        .map { it.sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun restore(item: SavableSearchable) {
        repository.setExcludedFromFrequentlyUsed(item, false)
    }

    fun getIcon(item: SavableSearchable, size: Int): Flow<LauncherIcon?> =
        iconService.getIcon(item, size)
}
