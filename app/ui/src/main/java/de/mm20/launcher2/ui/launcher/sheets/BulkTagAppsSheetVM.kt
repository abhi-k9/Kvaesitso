package de.mm20.launcher2.ui.launcher.sheets

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.data.customattrs.utils.withCustomLabels
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.services.tags.TagsService
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BulkTagAppsSheetVM : ViewModel(), KoinComponent {
    private val appRepository: AppRepository by inject()
    private val customAttributes: CustomAttributesRepository by inject()
    private val tagService: TagsService by inject()
    private val iconService: IconService by inject()

    private var loadJob: Job? = null
    private var session = 0L
    private var candidates by mutableStateOf(emptyList<TaggableItem>())
    internal var selection by mutableStateOf(TagPickerSelection())
        private set
    var query by mutableStateOf("")
    var onlyUntagged by mutableStateOf(false)
    var tagName by mutableStateOf("")
    var loading by mutableStateOf(true)
    var saving by mutableStateOf(false)
        private set
    var saveFailed by mutableStateOf(false)
        private set
    private var allTags by mutableStateOf(emptyList<String>())

    val visibleApps by derivedStateOf {
        filterTaggableItems(candidates, query, onlyUntagged).map {
            it.copy(isTagged = it.item.key in selection.keys)
        }
    }
    val suggestedTags by derivedStateOf {
        val text = normalizeTagPickerText(tagName.trim())
        allTags.filter { normalizeTagPickerText(it).contains(text) }.take(6)
    }

    fun init(initialApp: Application) {
        session++
        loadJob?.cancel()
        selection = TagPickerSelection(listOf(initialApp))
        query = ""
        onlyUntagged = false
        tagName = ""
        loading = true
        saving = false
        saveFailed = false
        loadJob = viewModelScope.launch {
            combine(
                appRepository.findMany().withCustomLabels(customAttributes),
                customAttributes.getTaggedKeys(),
                tagService.getAllTags(),
            ) { apps, taggedKeys, tags ->
                allTags = tags
                withContext(Dispatchers.Default) {
                    apps.filterNot { it.isPrivate }.sorted().map {
                        TaggableItem(it, false, it.key in taggedKeys)
                    }
                }
            }.collect {
                candidates = it
                loading = false
            }
        }
    }

    fun stopLoading() {
        session++
        loadJob?.cancel()
    }

    fun select(item: SavableSearchable, selected: Boolean) {
        if (!saving) selection.set(item, selected)
    }

    fun getIcon(item: SavableSearchable, size: Int) = iconService.getIcon(item, size)

    fun applyTag(onSaved: () -> Unit) {
        if (saving || tagName.isBlank() || selection.items.isEmpty()) return
        val items = selection.items
        val name = tagName.trim()
        val writeSession = session
        saving = true
        saveFailed = false
        viewModelScope.launch {
            val write = customAttributes.addTagToItems(items, name)
            write.join()
            if (session != writeSession) return@launch
            saving = false
            saveFailed = write.isCancelled
            if (!saveFailed) onSaved()
        }
    }
}
