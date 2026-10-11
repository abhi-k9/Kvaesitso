package de.mm20.launcher2.ui.settings.folders

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.preferences.FolderPosition
import de.mm20.launcher2.preferences.FolderStyle
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.settings.tags.TagsSettingsRoute
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object FoldersSettingsRoute : NavKey

/**
 * All settings of folders, which are tags that are shown as folders in the app list
 */
@Composable
fun FoldersSettingsScreen() {
    val viewModel: FoldersSettingsScreenVM = viewModel()
    val backStack = LocalBackStack.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val position by viewModel.position.collectAsState(FolderPosition.Mixed)
    val separate by viewModel.separate.collectAsState(false)
    val style by viewModel.style.collectAsState(FolderStyle.Popup)
    val showInSearch by viewModel.showInSearch.collectAsState(true)
    var categoryFolders by remember { mutableStateOf<Map<String, List<Application>>?>(null) }

    PreferenceScreen(title = stringResource(R.string.preference_screen_folders)) {
        item {
            PreferenceCategory {
                ListPreference(
                    title = stringResource(R.string.preference_folders_position),
                    items = listOf(
                        stringResource(R.string.preference_folders_position_mixed) to FolderPosition.Mixed,
                        stringResource(R.string.preference_folders_position_first) to FolderPosition.First,
                        stringResource(R.string.preference_folders_position_last) to FolderPosition.Last,
                    ),
                    value = position,
                    onValueChanged = {
                        if (it != null) viewModel.setPosition(it)
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_folders_separate),
                    summary = stringResource(R.string.preference_folders_separate_summary),
                    value = separate,
                    enabled = position != FolderPosition.Mixed,
                    onValueChanged = {
                        viewModel.setSeparate(it)
                    },
                )
                ListPreference(
                    title = stringResource(R.string.preference_folders_style),
                    items = listOf(
                        stringResource(R.string.preference_folders_style_popup) to FolderStyle.Popup,
                        stringResource(R.string.preference_folders_style_sheet) to FolderStyle.BottomSheet,
                    ),
                    value = style,
                    onValueChanged = {
                        if (it != null) viewModel.setStyle(it)
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_folders_in_search),
                    summary = stringResource(R.string.preference_folders_in_search_summary),
                    value = showInSearch,
                    onValueChanged = {
                        viewModel.setShowInSearch(it)
                    },
                )
            }
        }
        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(R.string.preference_folders_from_categories),
                    summary = stringResource(R.string.preference_folders_from_categories_summary),
                    onClick = {
                        scope.launch {
                            categoryFolders = viewModel.getCategoryFolders(context)
                        }
                    },
                )
                Preference(
                    title = stringResource(R.string.preference_screen_tags),
                    summary = stringResource(R.string.preference_folders_tags_summary),
                    icon = R.drawable.tag_24px,
                    onClick = {
                        backStack.add(TagsSettingsRoute)
                    },
                )
            }
        }
    }
    categoryFolders?.let { folders ->
        AlertDialog(
            onDismissRequest = { categoryFolders = null },
            title = { Text(stringResource(R.string.preference_folders_from_categories)) },
            text = {
                if (folders.isEmpty()) {
                    Text(stringResource(R.string.folders_from_categories_none))
                } else {
                    Column {
                        Text(
                            stringResource(R.string.folders_from_categories_message),
                            modifier = Modifier.padding(bottom = 16.dp),
                        )
                        for ((name, apps) in folders) {
                            Text(
                                pluralStringResource(
                                    R.plurals.folders_from_categories_folder,
                                    apps.size,
                                    name,
                                    apps.size
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createFolders(folders)
                        categoryFolders = null
                    },
                    enabled = folders.isNotEmpty(),
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryFolders = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}
