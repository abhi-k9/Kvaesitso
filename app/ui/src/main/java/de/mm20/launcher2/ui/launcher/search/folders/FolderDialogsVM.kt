package de.mm20.launcher2.ui.launcher.search.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.data.customattrs.TagFoldersRepository
import de.mm20.launcher2.search.SavableSearchable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.Collator

/**
 * For the folder dialogs that are shown over the launcher. Unlike an item's own view model, it
 * stays while the item that the dialog was opened for leaves the screen, e.g. when the keyboard
 * makes the list smaller.
 */
class FolderDialogsVM : ViewModel(), KoinComponent {
    private val customAttributesRepository: CustomAttributesRepository by inject()
    private val tagFoldersRepository: TagFoldersRepository by inject()

    val allTags = customAttributesRepository.getAllTags()

    /**
     * The names of all folders, sorted
     */
    val folders = tagFoldersRepository.folderTags.map { folders ->
        folders.sortedWith(Collator.getInstance().apply { strength = Collator.SECONDARY })
    }

    fun getTags(item: SavableSearchable): Flow<List<String>> {
        return customAttributesRepository.getTags(item)
    }

    /**
     * Puts [item] in the folders [inFolders] and takes it out of the other [folders]. Its other
     * tags stay. If [newFolder] isn't empty, the item is put in a new folder with that name too;
     * if there already is a tag with that name, it's shown as a folder from now on.
     */
    fun setFolders(
        item: SavableSearchable,
        folders: List<String>,
        inFolders: Set<String>,
        newFolder: String,
    ) {
        viewModelScope.launch {
            val tags = customAttributesRepository.getTags(item).first()
            val selected = if (newFolder.isNotEmpty()) inFolders + newFolder else inFolders
            val newTags = (tags.filterNot { it in folders } + selected).distinct()
            if (newTags.toSet() != tags.toSet()) {
                customAttributesRepository.setTags(item, newTags)
            }
            if (newFolder.isNotEmpty()) {
                tagFoldersRepository.setFolder(newFolder, true)
            }
        }
    }
}
