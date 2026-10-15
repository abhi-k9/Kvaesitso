package de.mm20.launcher2.ui.launcher.search.folders

import android.view.WindowManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.flow.first

/**
 * Puts [item] in any number of folders, and can make a new one for it. Nothing is changed until
 * the dialog is confirmed.
 */
@Composable
fun AddToFolderDialog(
    item: SavableSearchable,
    onDismissRequest: () -> Unit,
) {
    val viewModel: FolderDialogsVM = viewModel()
    val folders by viewModel.folders.collectAsState(null)
    val allTags by viewModel.allTags.collectAsState(emptyList())

    // Apps in a folder are only shown there, unless they're shown in the app list too
    val canBeInAppList = item is Application
    var inAppList by remember(item.key) { mutableStateOf(false) }
    var savedInAppList by remember(item.key) { mutableStateOf(false) }

    // The tags of the item, with the folders that are checked; null until they're loaded
    var selected by remember(item.key) { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(item.key) {
        if (canBeInAppList) {
            savedInAppList = viewModel.isInAppList(item).first()
            inAppList = savedInAppList
        }
        selected = viewModel.getTags(item).first().toSet()
    }
    var newFolder by remember(item.key) { mutableStateOf("") }
    val newFolderName = newFolder.trim()

    val loadedFolders = folders
    val loadedSelection = selected

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.menu_add_to_folder)) },
        text = {
            // A dialog's window is moved up when the keyboard covers its focused text field. Each
            // typed letter moved it again, only as far as the cursor, and then back, so the dialog
            // shook. Resized instead, the window fits above the keyboard and stays still, and the
            // list of folders gets shorter.
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            LaunchedEffect(window) {
                @Suppress("DEPRECATION")
                window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
            Column {
                if (loadedFolders != null && loadedSelection != null) {
                    if (canBeInAppList) {
                        // Always first, apart from the folders: an app that isn't in any folder is
                        // in the app list anyway, then this can't be changed
                        val inAnyFolder = newFolderName.isNotEmpty() ||
                                loadedSelection.any { it in loadedFolders }
                        val checked = inAppList || !inAnyFolder
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = checked,
                                    enabled = inAnyFolder,
                                    role = Role.Checkbox,
                                    onValueChange = { inAppList = it },
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = null,
                                enabled = inAnyFolder,
                            )
                            Column(
                                modifier = Modifier
                                    .padding(start = 16.dp)
                                    .alpha(if (inAnyFolder) 1f else 0.38f)
                            ) {
                                Text(
                                    stringResource(R.string.add_to_folder_app_list),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    stringResource(R.string.add_to_folder_app_list_summary),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        for (folder in loadedFolders) {
                            val checked = folder in loadedSelection
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = checked,
                                        role = Role.Checkbox,
                                        onValueChange = {
                                            selected = if (it) loadedSelection + folder
                                            else loadedSelection - folder
                                        },
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null)
                                Text(
                                    folder,
                                    modifier = Modifier.padding(start = 16.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = newFolder,
                    onValueChange = { newFolder = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.new_folder_title)) },
                    placeholder = { Text(stringResource(R.string.folder_name)) },
                    supportingText = if (
                        newFolderName in allTags && loadedFolders?.contains(newFolderName) == false
                    ) {
                        { Text(stringResource(R.string.new_folder_existing_tag)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = loadedFolders != null && loadedSelection != null,
                onClick = {
                    if (loadedFolders != null && loadedSelection != null) {
                        viewModel.setFolders(
                            item,
                            folders = loadedFolders,
                            inFolders = loadedSelection.filterTo(mutableSetOf()) { it in loadedFolders },
                            newFolder = newFolderName,
                            inAppList = inAppList.takeIf {
                                canBeInAppList && it != savedInAppList
                            },
                        )
                    }
                    onDismissRequest()
                },
            ) {
                Text(stringResource(R.string.action_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
