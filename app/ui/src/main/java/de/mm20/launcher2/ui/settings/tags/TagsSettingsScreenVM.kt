package de.mm20.launcher2.ui.settings.tags

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Process
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.data.customattrs.TagFoldersRepository
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.ktx.getApplicationInfoOrNull
import de.mm20.launcher2.preferences.FolderStyle
import de.mm20.launcher2.preferences.search.FolderSettings
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.Tag
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.searchable.VisibilityLevel
import de.mm20.launcher2.services.tags.TagsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.Collator
import kotlin.time.Duration.Companion.seconds

class TagsSettingsScreenVM: ViewModel(), KoinComponent {
    private val tagsService: TagsService by inject()
    private val iconService: IconService by inject()
    private val folderSettings: FolderSettings by inject()
    private val tagFoldersRepository: TagFoldersRepository by inject()
    private val appRepository: AppRepository by inject()
    private val searchableRepository: SavableSearchableRepository by inject()

    val tags = tagsService.getAllTags()

    var editTag = mutableStateOf<String?>(null)
    var createTag = mutableStateOf(false)

    fun duplicateTag(tag: String) {
        viewModelScope.launch {
            val allTags = tags.first()
            var i = 2
            var newName = "$tag ($i)"
            while(allTags.contains(newName)) {
                i++
                newName = "$tag ($i)"
            }
            tagsService.cloneTag(tag, newName)
        }
    }

    val folderStyle = folderSettings.style

    fun isFolder(tag: String): Flow<Boolean> = tagFoldersRepository.isFolder(tag)

    fun setFolderStyle(style: FolderStyle) {
        folderSettings.setStyle(style)
    }

    val foldersInSearch = folderSettings.showInSearch

    fun setFoldersInSearch(showInSearch: Boolean) {
        folderSettings.setShowInSearch(showInSearch)
    }

    fun deleteTag(tag: String) {
        tagsService.deleteTag(tag)
    }

    fun getIcon(tag: String): Flow<LauncherIcon?> {
        return iconService.getIcon(Tag(tag), 1)
    }

    /**
     * Folders for the categories that apps declare, e.g. "Games", by the name that Android gives
     * the category. Only apps of the personal profile that aren't in a folder or hidden from the
     * app list yet are sorted into them, and only folders with at least two apps are made.
     */
    suspend fun getCategoryFolders(context: Context): Map<String, List<Application>> {
        return withContext(Dispatchers.Default) {
            val inFolders = tagFoldersRepository.folders.first().values
                .flatMapTo(mutableSetOf()) { items -> items.map { it.key } }
            val hiddenFromAppList = searchableRepository
                .getKeys(maxVisibility = VisibilityLevel.SearchOnly)
                .first()
                .toSet()
            val personalProfile = Process.myUserHandle()
            val packageManager = context.packageManager
            // Apps are loaded in the background after the repository is first used
            val apps = withTimeoutOrNull(10.seconds) {
                appRepository.findMany().first { loaded -> loaded.any { it.user == personalProfile } }
            } ?: return@withContext emptyMap()
            val folders = mutableMapOf<String, MutableList<Application>>()
            for (app in apps) {
                if (app.user != personalProfile || app.isPrivate) continue
                if (app.key in inFolders || app.key in hiddenFromAppList) continue
                val category = packageManager
                    .getApplicationInfoOrNull(app.componentName.packageName)
                    ?.category ?: continue
                val name = ApplicationInfo.getCategoryTitle(context, category)?.toString() ?: continue
                folders.getOrPut(name) { mutableListOf() }.add(app)
            }
            folders.filterValues { it.size >= 2 }.toSortedMap(Collator.getInstance())
        }
    }

    /**
     * Adds the apps to the folders, the tags are made if they don't exist yet
     */
    fun createFolders(folders: Map<String, List<Application>>) {
        viewModelScope.launch {
            for ((name, apps) in folders) {
                val items = tagsService.getTaggedItems(name).first()
                if (items.isEmpty()) {
                    tagsService.createTag(name, apps)
                } else {
                    tagsService.updateTag(name, items = (items + apps).distinctBy { it.key })
                }
                tagFoldersRepository.setFolder(name, true)
            }
        }
    }

}