package de.mm20.launcher2.ui.launcher.sheets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.launcher.search.folders.FolderDialogsVM
import de.mm20.launcher2.ui.launcher.search.folders.NewFolderDialog

@Composable
fun LauncherBottomSheets() {
    val bottomSheetManager = LocalBottomSheetManager.current
    CustomizeSearchableSheet(
        searchable = bottomSheetManager.customizeSearchableSheetShown.value,
        onDismiss = { bottomSheetManager.dismissCustomizeSearchableModal() })
    EditFavoritesSheet(
        expanded = bottomSheetManager.editFavoritesSheetShown.value,
        onDismiss = { bottomSheetManager.dismissEditFavoritesSheet() })
    EditTagSheet(
        expanded = bottomSheetManager.editTagSheetShown.value != null,
        tag = bottomSheetManager.editTagSheetShown.value,
        onDismiss = { bottomSheetManager.dismissEditTagSheet() }
    )
    FailedGestureSheet(
        bottomSheetManager.failedGestureSheetShown.value,
        onDismiss = { bottomSheetManager.dismissFailedGestureSheet() }
    )
    // Shown here rather than with the item it's for, which can leave the screen when the keyboard
    // opens, e.g. if the list is anchored at the bottom
    val newFolderItem = bottomSheetManager.newFolderDialogShown.value
    if (newFolderItem != null) {
        val viewModel: FolderDialogsVM = viewModel()
        val tags by viewModel.allTags.collectAsState(emptyList())
        val folders by viewModel.folders.collectAsState(emptyList())
        NewFolderDialog(
            tags = tags,
            folders = folders,
            onDismissRequest = { bottomSheetManager.dismissNewFolderDialog() },
            onCreate = {
                bottomSheetManager.dismissNewFolderDialog()
                viewModel.addToNewFolder(newFolderItem, it)
            },
        )
    }
}