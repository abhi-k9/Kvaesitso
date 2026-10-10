package de.mm20.launcher2.ui.launcher.search.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.data.customattrs.TagFoldersRepository
import de.mm20.launcher2.search.SavableSearchable
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

    /**
     * Adds [item] to a new folder. If there already is a tag called [name], it's shown as a
     * folder from now on.
     */
    fun addToNewFolder(item: SavableSearchable, name: String) {
        viewModelScope.launch {
            val tags = customAttributesRepository.getTags(item).first()
            customAttributesRepository.setTags(item, (tags + name).distinct())
            tagFoldersRepository.setFolder(name, true)
        }
    }
}
