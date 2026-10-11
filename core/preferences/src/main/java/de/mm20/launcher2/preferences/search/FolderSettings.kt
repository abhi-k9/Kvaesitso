package de.mm20.launcher2.preferences.search

import de.mm20.launcher2.preferences.FolderPosition
import de.mm20.launcher2.preferences.FolderStyle
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class FolderSettings internal constructor(
    private val dataStore: LauncherDataStore,
) {
    val style: Flow<FolderStyle>
        get() = dataStore.data.map { it.foldersStyle }.distinctUntilChanged()

    fun setStyle(style: FolderStyle) {
        dataStore.update { it.copy(foldersStyle = style) }
    }

    /**
     * Whether folders whose name matches the search are shown in the search results
     */
    val showInSearch: Flow<Boolean>
        get() = dataStore.data.map { it.foldersInSearch }.distinctUntilChanged()

    fun setShowInSearch(showInSearch: Boolean) {
        dataStore.update { it.copy(foldersInSearch = showInSearch) }
    }

    /**
     * Whether folders are sorted in with the apps in the app list, or shown before or after them
     */
    val position: Flow<FolderPosition>
        get() = dataStore.data.map { it.foldersPosition }.distinctUntilChanged()

    fun setPosition(position: FolderPosition) {
        dataStore.update { it.copy(foldersPosition = position) }
    }

    /**
     * Whether there is a gap between the folders and the apps in the app list, when the folders
     * are shown before or after the apps
     */
    val separate: Flow<Boolean>
        get() = dataStore.data.map { it.foldersSeparate }.distinctUntilChanged()

    fun setSeparate(separate: Boolean) {
        dataStore.update { it.copy(foldersSeparate = separate) }
    }
}
