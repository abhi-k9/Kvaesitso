package de.mm20.launcher2.preferences.search

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
     * Whether apps that are in a folder are shown in the app list too, not only in the folder
     */
    val appsInList: Flow<Boolean>
        get() = dataStore.data.map { it.foldersAppsInList }.distinctUntilChanged()

    fun setAppsInList(appsInList: Boolean) {
        dataStore.update { it.copy(foldersAppsInList = appsInList) }
    }
}
