package de.mm20.launcher2.ui.launcher.search.folders

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.data.customattrs.TagFoldersRepository
import de.mm20.launcher2.data.customattrs.utils.withCustomLabels
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.preferences.FolderStyle
import de.mm20.launcher2.preferences.search.FolderSettings
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.Tag
import de.mm20.launcher2.services.favorites.FavoritesService
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.component.LocalIconShape
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.search.common.grid.SearchResultGrid
import de.mm20.launcher2.ui.launcher.sheets.LocalBottomSheetManager
import de.mm20.launcher2.ui.locals.LocalGridSettings
import de.mm20.launcher2.ui.overlays.Overlay
import de.mm20.launcher2.ui.theme.transparency.transparency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * A tag that is shown as a folder on the search screen.
 */
class FolderVM(private val tag: String) : ViewModel(), KoinComponent {
    private val customAttributesRepository: CustomAttributesRepository by inject()
    private val tagFoldersRepository: TagFoldersRepository by inject()
    private val iconService: IconService by inject()
    private val favoritesService: FavoritesService by inject()
    private val folderSettings: FolderSettings by inject()

    private val folder = Tag(tag)

    val items: StateFlow<List<SavableSearchable>> = customAttributesRepository
        .getItemsForTag(tag)
        .withCustomLabels(customAttributesRepository)
        .map { it.sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customIcon = iconService.getCustomIcon(folder)
    val isPinned = favoritesService.isPinned(folder)
    val style = folderSettings.style

    fun getFolderIcon(size: Int): Flow<LauncherIcon?> = iconService.getIcon(folder, size)

    fun getIcon(item: SavableSearchable, size: Int): Flow<LauncherIcon?> {
        return iconService.getIcon(item, size)
    }

    fun pin() = favoritesService.pinItem(folder)

    fun unpin() = favoritesService.unpinItem(folder)

    fun ungroup() = tagFoldersRepository.setFolder(tag, false)
}

@Composable
private fun folderViewModel(folder: Tag): FolderVM {
    return viewModel(key = "folder-${folder.tag}") { FolderVM(folder.tag) }
}

/**
 * A folder in a grid, like [de.mm20.launcher2.ui.launcher.search.common.grid.GridItem].
 */
@Composable
fun FolderGridItem(
    modifier: Modifier = Modifier,
    folder: Tag,
    showLabels: Boolean = true,
) {
    val viewModel = folderViewModel(folder)
    var open by remember(folder.tag) { mutableStateOf(false) }
    var showMenu by remember(folder.tag) { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .padding(4.dp)
                .combinedClickable(
                    onClick = { open = true },
                    onLongClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMenu = true
                    },
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) then if (!showLabels) Modifier.aspectRatio(1f) else Modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            FolderIcon(
                viewModel,
                size = LocalGridSettings.current.iconSize.dp,
                modifier = Modifier
                    .padding(4.dp)
                    .then(
                        if (showLabels) Modifier
                        else Modifier.semantics { contentDescription = folder.label }
                    ),
            )
            if (showLabels) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    text = folder.label,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        FolderMenu(viewModel, folder, expanded = showMenu, onDismissRequest = { showMenu = false })
    }
    OpenFolder(viewModel, folder, open = open, onClose = { open = false })
}

/**
 * A folder in the list layout of the app list.
 */
@Composable
fun FolderListItem(
    modifier: Modifier = Modifier,
    folder: Tag,
) {
    val viewModel = folderViewModel(folder)
    var open by remember(folder.tag) { mutableStateOf(false) }
    var showMenu by remember(folder.tag) { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { open = true },
                    onLongClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMenu = true
                    },
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (LocalGridSettings.current.showListIcons) {
                FolderIcon(viewModel, size = 32.dp, modifier = Modifier.padding(end = 16.dp))
            }
            Text(
                text = folder.label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FolderMenu(viewModel, folder, expanded = showMenu, onDismissRequest = { showMenu = false })
    }
    OpenFolder(viewModel, folder, open = open, onClose = { open = false })
}

/**
 * The folder's icon: its custom icon, or the icons of its first items.
 */
@Composable
private fun FolderIcon(viewModel: FolderVM, size: Dp, modifier: Modifier = Modifier) {
    val customIcon by viewModel.customIcon.collectAsState(null)
    if (customIcon != null) {
        val sizePx = size.toPixels().toInt()
        val icon by remember(sizePx) { viewModel.getFolderIcon(sizePx) }.collectAsState(null)
        ShapedLauncherIcon(modifier = modifier, size = size, icon = { icon })
        return
    }

    val items by viewModel.items.collectAsState()
    val previewSize = size * 0.38f
    val previewSizePx = previewSize.toPixels().toInt()
    val spacing = size * 0.06f
    Box(
        modifier = modifier
            .size(size)
            .clip(LocalIconShape.current)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            for (row in 0 until 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    for (column in 0 until 2) {
                        val item = items.getOrNull(row * 2 + column)
                        if (item == null) {
                            Spacer(modifier = Modifier.size(previewSize))
                        } else {
                            key(item.key) {
                                val icon by remember(previewSizePx) {
                                    viewModel.getIcon(item, previewSizePx)
                                }.collectAsState(null)
                                ShapedLauncherIcon(size = previewSize, icon = { icon })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderMenu(
    viewModel: FolderVM,
    folder: Tag,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
) {
    val isPinned by viewModel.isPinned.collectAsState(false)
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        val sheetManager = LocalBottomSheetManager.current
        DropdownMenuItem(
            text = { Text(stringResource(R.string.edit)) },
            leadingIcon = { Icon(painterResource(R.drawable.edit_24px), null) },
            onClick = {
                onDismissRequest()
                sheetManager.showEditTagSheet(folder.tag)
            },
        )
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(
                        if (isPinned) R.string.menu_favorites_unpin else R.string.menu_favorites_pin
                    )
                )
            },
            leadingIcon = {
                Icon(
                    painterResource(if (isPinned) R.drawable.star_24px_filled else R.drawable.star_24px),
                    null
                )
            },
            onClick = {
                onDismissRequest()
                if (isPinned) viewModel.unpin() else viewModel.pin()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.folder_ungroup)) },
            leadingIcon = { Icon(painterResource(R.drawable.tag_24px), null) },
            onClick = {
                onDismissRequest()
                viewModel.ungroup()
            },
        )
    }
}

/**
 * Shows the open folder, as a popup or as a bottom sheet depending on the settings.
 */
@Composable
private fun OpenFolder(viewModel: FolderVM, folder: Tag, open: Boolean, onClose: () -> Unit) {
    val style by viewModel.style.collectAsState(FolderStyle.Popup)

    // e.g. after an app has been launched from the folder
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        onClose()
    }

    when (style) {
        FolderStyle.Popup -> if (open) {
            FolderPopup(viewModel, folder, onDismissRequest = onClose)
        }

        FolderStyle.BottomSheet -> DismissableBottomSheet(
            expanded = open,
            onDismissRequest = onClose,
        ) {
            FolderContent(
                viewModel,
                folder,
                onClose = onClose,
                modifier = Modifier.navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun FolderPopup(viewModel: FolderVM, folder: Tag, onDismissRequest: () -> Unit) {
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }

    LaunchedEffect(visibleState.isIdle, visibleState.currentState) {
        if (visibleState.isIdle && !visibleState.currentState && !visibleState.targetState) {
            onDismissRequest()
        }
    }

    BackHandler(visibleState.targetState) {
        visibleState.targetState = false
    }

    Overlay {
        val scrimAlpha by animateFloatAsState(if (visibleState.targetState) 0.32f else 0f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha))
                .pointerInput(Unit) {
                    detectTapGestures { visibleState.targetState = false }
                }
                .systemBarsPadding()
                .imePadding()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visibleState = visibleState,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f),
            ) {
                LauncherCard(
                    elevation = 8.dp,
                    backgroundOpacity = MaterialTheme.transparency.elevatedSurface,
                    // Taps on the folder's background don't close it
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures { } },
                ) {
                    FolderContent(
                        viewModel,
                        folder,
                        onClose = { visibleState.targetState = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderContent(
    viewModel: FolderVM,
    folder: Tag,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items by viewModel.items.collectAsState()
    val sheetManager = LocalBottomSheetManager.current

    Column(modifier = modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = folder.label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    onClose()
                    sheetManager.showEditTagSheet(folder.tag)
                }
            ) {
                Icon(painterResource(R.drawable.edit_24px), stringResource(R.string.edit))
            }
        }
        SearchResultGrid(
            items,
            modifier = Modifier
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState()),
        )
    }
}
