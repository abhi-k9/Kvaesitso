package de.mm20.launcher2.ui.launcher.sheets

import androidx.compose.runtime.mutableStateOf
import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.data.customattrs.CustomIcon
import de.mm20.launcher2.icons.CustomIconWithPreview
import de.mm20.launcher2.icons.IconPack
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.preferences.ui.UiState
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.searchable.VisibilityLevel
import de.mm20.launcher2.services.favorites.FavoritesService
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.coroutineContext

class CustomizeSearchableSheetVM(
    private val searchable: SavableSearchable
) : KoinComponent {
    private val iconService: IconService by inject()
    private val customAttributesRepository: CustomAttributesRepository by inject()
    private val favoritesService: FavoritesService by inject()
    private val uiState: UiState by inject()

    val isIconPickerOpen = mutableStateOf(false)

    fun getIcon(size: Int): Flow<LauncherIcon?> {
        return iconService.getIcon(searchable, size)
    }

    fun openIconPicker() {
        isIconPickerOpen.value = true
    }

    fun closeIconPicker() {
        isIconPickerOpen.value = false
    }

    fun pickIcon(icon: CustomIcon?) {
        iconService.setCustomIcon(searchable, icon)
    }

    fun setCustomLabel(label: String) {
        if (label.isBlank()) {
            customAttributesRepository.clearCustomLabel(searchable)
        } else {
            customAttributesRepository.setCustomLabel(searchable, label)
        }
    }

    /**
     * Saves the tags of the item. The tags that weren't in [savedTags] become the most recently
     * used ones.
     */
    fun setTags(tags: List<String>, savedTags: List<String>) {
        customAttributesRepository.setTags(searchable, tags)
        uiState.addRecentTags(tags - savedTags.toSet())
    }

    fun setVisibility(visibility: VisibilityLevel) {
        favoritesService.setVisibility(searchable, visibility)
    }

    fun getTags(): Flow<List<String>> {
        return customAttributesRepository.getTags(searchable)
    }

    fun getVisibility(): Flow<VisibilityLevel> {
        return favoritesService.getVisibility(searchable)
    }

    suspend fun autocompleteTags(query: String): List<String> {
        return customAttributesRepository.getAllTags(startsWith = query).first()
    }

    fun getTagChoices(): Flow<TagChoices> {
        return combine(
            customAttributesRepository.getAllTags(),
            uiState.recentTags,
        ) { tags, recentTags ->
            val recent = recentTags.filter { it in tags }
            TagChoices(recent = recent, others = tags - recent.toSet())
        }
    }
}

/**
 * The tags to choose from: the [recent]ly used ones, and the [others] by name.
 */
data class TagChoices(
    val recent: List<String>,
    val others: List<String>,
) {
    fun isEmpty() = recent.isEmpty() && others.isEmpty()
}
