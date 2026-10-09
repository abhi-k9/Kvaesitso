package de.mm20.launcher2.ui.settings.tags

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.preferences.FolderStyle
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.launcher.sheets.EditTagSheet
import de.mm20.launcher2.ui.settings.shapes.ShapeSchemeSettingsRoute
import kotlinx.serialization.Serializable

@Serializable
data object TagsSettingsRoute : NavKey

@Composable
fun TagsSettingsScreen() {
    val viewModel: TagsSettingsScreenVM = viewModel()

    val tags by remember { viewModel.tags }.collectAsState(emptyList())
    val folderStyle by viewModel.folderStyle.collectAsState(FolderStyle.Popup)

    PreferenceScreen(
        title = stringResource(R.string.preference_screen_tags),
        helpUrl = "https://kvaesitso.mm20.de/docs/user-guide/concepts/tags"
    ) {
        item {
            PreferenceCategory {
                for (tag in tags) {
                    var showMenu by remember { mutableStateOf(false) }

                    val icon by remember(tag) { viewModel.getIcon(tag) }.collectAsState(null)
                    val isFolder by remember(tag) { viewModel.isFolder(tag) }.collectAsState(false)

                    Preference(
                        icon = {
                            ShapedLauncherIcon(
                                size = 36.dp,
                                icon = { icon },
                            )
                        },
                        title = { Text(tag) },
                        summary = if (isFolder) {
                            { Text(stringResource(R.string.tag_is_folder)) }
                        } else null,
                        onClick = {
                            viewModel.editTag.value = tag
                        },
                        controls = {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(painterResource(R.drawable.more_vert_24px), null)
                            }
                            DropdownMenuPopup(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }) {
                                DropdownMenuGroup(
                                    shapes = MenuDefaults.groupShapes()
                                ) {
                                    DropdownMenuItem(
                                        shape = MenuDefaults.leadingItemShape,
                                        text = { Text(stringResource(R.string.duplicate)) },
                                        leadingIcon = {
                                            Icon(
                                                painterResource(R.drawable.content_copy_24px),
                                                null
                                            )
                                        },
                                        onClick = {
                                            viewModel.duplicateTag(tag)
                                            showMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        shape = MenuDefaults.trailingItemShape,
                                        text = { Text(stringResource(R.string.menu_delete)) },
                                        leadingIcon = {
                                            Icon(
                                                painterResource(R.drawable.delete_24px),
                                                null
                                            )
                                        },
                                        onClick = {
                                            viewModel.deleteTag(tag)
                                            showMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
        item {
            FilledTonalButton(
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                onClick = {
                    viewModel.createTag.value = true
                }) {
                Icon(
                    painterResource(R.drawable.add_20px),
                    null,
                    modifier = Modifier
                        .padding(end = ButtonDefaults.IconSpacing)
                        .size(ButtonDefaults.IconSize)
                )
                Text(stringResource(R.string.create_tag_title))
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.preference_category_folders)) {
                ListPreference(
                    title = stringResource(R.string.preference_folders_style),
                    items = listOf(
                        stringResource(R.string.preference_folders_style_popup) to FolderStyle.Popup,
                        stringResource(R.string.preference_folders_style_sheet) to FolderStyle.BottomSheet,
                    ),
                    value = folderStyle,
                    onValueChanged = {
                        if (it != null) viewModel.setFolderStyle(it)
                    },
                )
            }
        }
    }
    EditTagSheet(
        expanded = viewModel.editTag.value != null,
        tag = viewModel.editTag.value,
        onDismiss = {
            viewModel.editTag.value = null
            viewModel.createTag.value = false
        }
    )
    EditTagSheet(
        expanded = viewModel.createTag.value,
        tag = null,
        onDismiss = {
            viewModel.createTag.value = false
            viewModel.editTag.value = null
        }
    )
}