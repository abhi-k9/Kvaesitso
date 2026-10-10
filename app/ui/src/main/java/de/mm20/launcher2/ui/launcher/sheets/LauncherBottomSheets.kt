package de.mm20.launcher2.ui.launcher.sheets

import androidx.compose.runtime.Composable
import de.mm20.launcher2.ui.launcher.search.folders.AddToFolderDialog

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
    val folderDialogItem = bottomSheetManager.addToFolderDialogShown.value
    if (folderDialogItem != null) {
        AddToFolderDialog(
            item = folderDialogItem,
            onDismissRequest = { bottomSheetManager.dismissAddToFolderDialog() },
        )
    }
}