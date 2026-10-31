package de.mm20.launcher2.ui.launcher.search.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.preferences.search.FavoritesCardSettingsData
import de.mm20.launcher2.preferences.ui.GridSettings
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.services.favorites.FavoritesCardItems
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.search.common.grid.SearchResultGrid
import de.mm20.launcher2.ui.launcher.search.ListItemViewModelStore
import de.mm20.launcher2.ui.launcher.search.LocalListItemViewModelStore
import de.mm20.launcher2.ui.layout.BottomReversed
import de.mm20.launcher2.ui.locals.LocalGridSettings

/** Fit dense layouts without allowing icons to overlap neighboring columns. */
internal fun favoritesCardGridSettings(settings: FavoritesCardSettingsData, width: Float): GridSettings {
    val available = (width - 8f).coerceAtLeast(40f)
    val columns = settings.columns.coerceIn(1, (available / 40f).toInt().coerceIn(1, 10))
    val icon = settings.iconSize.coerceIn(24, (available / columns - 16f).toInt().coerceAtLeast(24))
    return GridSettings(columnCount = columns, iconSize = icon, showLabels = settings.showLabels)
}

@Composable
internal fun FavoritesCardGrid(
    items: List<SavableSearchable>,
    groups: FavoritesCardItems,
    settings: FavoritesCardSettingsData,
    selectedTag: String?,
    reverse: Boolean,
    // A renderer slot also allows verifying group layout independently of icon/provider loading.
    renderGrid: @Composable (List<SavableSearchable>) -> Unit = {
        SearchResultGrid(it, transitionKey = selectedTag, reverse = reverse)
    },
) {
    // An app may appear here and in the main grid with different icon sizes. Separate
    // presentation models prevent the two renderers from overwriting each other's icon size.
    val itemStore = remember { ListItemViewModelStore() }
    DisposableEffect(itemStore) { onDispose { itemStore.clear() } }
    BoxWithConstraints {
        val grid = favoritesCardGridSettings(settings, maxWidth.value)
        CompositionLocalProvider(LocalGridSettings provides grid, LocalListItemViewModelStore provides itemStore) {
            if (selectedTag != null || !settings.showGroupLabels) {
                renderGrid(items)
            } else {
                Column(verticalArrangement = if (reverse) Arrangement.BottomReversed else Arrangement.Top) {
                    for ((title, section) in listOf(
                        R.string.favorites_card_pinned to groups.pinned,
                        R.string.favorites_card_recent to groups.recent,
                        R.string.favorites_card_frequent to groups.frequent,
                    )) {
                        if (section.isEmpty()) continue
                        Column {
                            Text(
                                stringResource(title),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            renderGrid(section)
                        }
                    }
                }
            }
        }
    }
}
