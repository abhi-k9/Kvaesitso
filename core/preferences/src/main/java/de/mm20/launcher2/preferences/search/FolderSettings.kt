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
}
