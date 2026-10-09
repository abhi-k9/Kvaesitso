package de.mm20.launcher2.ui.launcher.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.badges.BadgeIcon
import de.mm20.launcher2.icons.CustomIconWithPreview
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.searchable.VisibilityLevel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.common.IconPicker
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.ui.component.OutlinedTagsInputField
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.ktx.toPixels
import kotlinx.coroutines.flow.first

@Composable
fun CustomizeSearchableSheet(
    searchable: SavableSearchable?,
    onDismiss: () -> Unit,
) {

    DismissableBottomSheet(
        state = searchable,
        expanded = { it != null },
        onDismissRequest = { onDismiss() }) { searchable ->

        searchable ?: return@DismissableBottomSheet

        val viewModel: CustomizeSearchableSheetVM =
            remember(searchable.key) { CustomizeSearchableSheetVM(searchable) }

        val pickIcon by viewModel.isIconPickerOpen

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val iconSize = 64.dp
            val iconSizePx = iconSize.toPixels()
            val icon by remember { viewModel.getIcon(iconSizePx.toInt()) }.collectAsState(null)

            ShapedLauncherIcon(
                size = iconSize,
                icon = { icon },
                badge = {
                    Badge(
                        icon = BadgeIcon(R.drawable.edit_20px)
                    )
                },
                modifier = Modifier.clickable {
                    viewModel.openIconPicker()
                }
            )

            var customLabelValue by remember {
                mutableStateOf(searchable.labelOverride ?: "")
            }
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 16.dp),
                value = customLabelValue,
                onValueChange = {
                    customLabelValue = it
                },
                singleLine = true,
                label = {
                    Text(stringResource(R.string.customize_item_label))
                },
                placeholder = {
                    Text(searchable.label)
                },
                leadingIcon = {
                    Icon(painterResource(R.drawable.label_24px), null)
                }
            )

            var tags by remember { mutableStateOf(emptyList<String>()) }
            // null until the saved tags and visibility have been loaded
            var savedTags by remember { mutableStateOf<List<String>?>(null) }
            var visibility by remember { mutableStateOf(VisibilityLevel.Default) }
            var inAppList by remember { mutableStateOf(false) }
            var savedInAppList by remember { mutableStateOf(false) }

            LaunchedEffect(searchable.key) {
                visibility = viewModel.getVisibility().first()
                inAppList = viewModel.isInAppList().first()
                savedInAppList = inAppList
                val saved = viewModel.getTags().first()
                tags = saved
                savedTags = saved
            }

            val tagChoices by remember { viewModel.getTagChoices() }.collectAsState(null)
            val hasTagChoices = tagChoices?.isEmpty() == false
            var showTagsDropdown by remember {
                mutableStateOf(false)
            }

            ExposedDropdownMenuBox(
                expanded = showTagsDropdown && hasTagChoices,
                onExpandedChange = { showTagsDropdown = it },
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
            ) {
                OutlinedTagsInputField(
                    modifier = Modifier.fillMaxWidth(),
                    tags = tags, onTagsChange = { tags = it.distinct() },
                    label = {
                        Text(stringResource(R.string.customize_item_tags))
                    },
                    onAutocomplete = {
                        viewModel.autocompleteTags(it).minus(tags.toSet())
                    },
                    leadingIcon = {
                        Icon(painterResource(R.drawable.tag_24px), null)
                    },
                    trailingIcon = if (hasTagChoices) {
                        {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = showTagsDropdown,
                                // Opens the list without focusing the text field
                                modifier = Modifier.menuAnchor(
                                    ExposedDropdownMenuAnchorType.SecondaryEditable
                                ),
                            )
                        }
                    } else null,
                )
                ExposedDropdownMenu(
                    expanded = showTagsDropdown && hasTagChoices,
                    onDismissRequest = {
                        showTagsDropdown = false
                    }
                ) {
                    val choices = tagChoices ?: return@ExposedDropdownMenu
                    // Several tags can be selected, so the list stays open
                    val onCheckedChange = { tag: String, checked: Boolean ->
                        tags = if (checked) tags + tag else tags - tag
                    }
                    if (choices.recent.isNotEmpty()) {
                        Text(
                            stringResource(R.string.customize_item_tags_recent),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        for (tag in choices.recent) {
                            TagChoice(tag, checked = tag in tags, onCheckedChange = onCheckedChange)
                        }
                        if (choices.others.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                    for (tag in choices.others) {
                        TagChoice(tag, checked = tag in tags, onCheckedChange = onCheckedChange)
                    }
                }
            }

            var showDropdown by remember {
                mutableStateOf(false)
            }

            ExposedDropdownMenuBox(
                expanded = showDropdown,
                onExpandedChange = { showDropdown = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            ) {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    value = when (visibility) {
                        VisibilityLevel.Default -> {
                            when (searchable) {
                                is Application -> stringResource(R.string.item_visibility_app_default)
                                is CalendarEvent -> stringResource(R.string.item_visibility_calendar_default)
                                else -> stringResource(R.string.item_visibility_search_only)
                            }
                        }

                        VisibilityLevel.SearchOnly -> stringResource(R.string.item_visibility_search_only)
                        VisibilityLevel.Hidden -> stringResource(R.string.item_visibility_hidden)
                    },
                    label = {
                        Text(stringResource(R.string.customize_item_visibility))
                    },
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showDropdown) },
                    leadingIcon = {
                        Icon(
                            painterResource(
                                when (visibility) {
                                    VisibilityLevel.Default -> R.drawable.visibility_24px_filled
                                    VisibilityLevel.SearchOnly -> R.drawable.visibility_24px
                                    VisibilityLevel.Hidden -> R.drawable.visibility_off_24px
                                }
                            ),
                            null
                        )
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                )
                ExposedDropdownMenu(
                    expanded = showDropdown,
                    onDismissRequest = {
                        showDropdown = false
                    }
                ) {
                    if (searchable is Application) {
                        DropdownMenuItem(
                            onClick = {
                                visibility = VisibilityLevel.Default
                                showDropdown = false
                            },
                            text = {
                                Text(stringResource(R.string.item_visibility_app_default))
                            },
                            leadingIcon = {
                                Icon(painterResource(R.drawable.visibility_24px_filled), null)
                            }
                        )
                    } else if (searchable is CalendarEvent) {
                        DropdownMenuItem(
                            onClick = {
                                visibility = VisibilityLevel.Default
                                showDropdown = false
                            },
                            text = {
                                Text(stringResource(R.string.item_visibility_calendar_default))
                            },
                            leadingIcon = {
                                Icon(painterResource(R.drawable.visibility_24px_filled), null)
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            onClick = {
                                visibility = VisibilityLevel.Default
                                showDropdown = false
                            },
                            text = {
                                Text(stringResource(R.string.item_visibility_search_only))
                            },
                            leadingIcon = {
                                Icon(painterResource(R.drawable.visibility_24px_filled), null)
                            }
                        )
                    }
                    if (searchable is Application || searchable is CalendarEvent) {
                        DropdownMenuItem(
                            onClick = {
                                visibility = VisibilityLevel.SearchOnly
                                showDropdown = false
                            },
                            text = {
                                Text(stringResource(R.string.item_visibility_search_only))
                            },
                            leadingIcon = {
                                Icon(
                                    painterResource(R.drawable.visibility_24px),
                                    null
                                )
                            }
                        )
                    }
                    DropdownMenuItem(
                        onClick = {
                            visibility = VisibilityLevel.Hidden
                            showDropdown = false
                        },
                        text = {
                            Text(stringResource(R.string.item_visibility_hidden))
                        },
                        leadingIcon = {
                            Icon(painterResource(R.drawable.visibility_off_24px), null)
                        }
                    )
                }
            }

            // Apps in a folder are only shown there, unless they're shown in the app list too
            val folderTags by remember { viewModel.folderTags }.collectAsState(emptySet())
            if (searchable is Application && visibility == VisibilityLevel.Default &&
                tags.any { it in folderTags }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = inAppList,
                            role = Role.Switch,
                            onValueChange = { inAppList = it },
                        )
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.apps_24px),
                        null,
                        modifier = Modifier.padding(start = 12.dp, end = 16.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.customize_item_in_app_list),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            stringResource(R.string.customize_item_in_app_list_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = inAppList,
                        onCheckedChange = null,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }

            DisposableEffect(searchable.key) {
                onDispose {
                    viewModel.setCustomLabel(customLabelValue)
                    // If the sheet is closed before they are loaded, they must not be overwritten
                    val saved = savedTags ?: return@onDispose
                    viewModel.setTags(tags, saved)
                    viewModel.setVisibility(visibility)
                    if (inAppList != savedInAppList) viewModel.setInAppList(inAppList)
                }
            }
        }
        DismissableBottomSheet(
            expanded = pickIcon,
            onDismissRequest = { viewModel.closeIconPicker() },
        ) {
            IconPicker(
                searchable = searchable,
                onSelect = {
                        viewModel.pickIcon(it)
                        viewModel.closeIconPicker()
                },
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                ),
            )
        }
    }
}

@Composable
private fun TagChoice(
    tag: String,
    checked: Boolean,
    onCheckedChange: (tag: String, checked: Boolean) -> Unit,
) {
    DropdownMenuItem(
        checked = checked,
        onCheckedChange = { onCheckedChange(tag, it) },
        shapes = MenuDefaults.itemShapes(),
        text = { Text(tag) },
        leadingIcon = {
            Icon(painterResource(R.drawable.tag_24px), null)
        },
        checkedLeadingIcon = {
            Icon(painterResource(R.drawable.check_24px), null)
        },
    )
}

@Composable
fun IconPreview(
    item: CustomIconWithPreview?,
    iconSize: Dp,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        ShapedLauncherIcon(
            size = iconSize,
            icon = { item?.preview },
            modifier = Modifier.clickable(onClick = onClick),
        )
    }
}

@Composable
fun Separator(label: String) {
    Text(
        label,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier
            .padding(top = 16.dp, bottom = 8.dp)
            .fillMaxWidth()
    )
}