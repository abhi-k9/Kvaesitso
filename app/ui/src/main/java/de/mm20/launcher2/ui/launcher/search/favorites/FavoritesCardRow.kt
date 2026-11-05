package de.mm20.launcher2.ui.launcher.search.favorites

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import de.mm20.launcher2.preferences.search.FavoritesCardSettingsData
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.Tag
import de.mm20.launcher2.services.favorites.FavoritesCardItems
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.search.common.grid.GridItem
import de.mm20.launcher2.ui.launcher.search.folders.FolderGridItem

/** One row regardless of list direction: pins remain first, then recent and frequent apps. */
@Composable
internal fun FavoritesCardRow(
    items: List<SavableSearchable>,
    groups: FavoritesCardItems,
    settings: FavoritesCardSettingsData,
    selectedTag: String?,
    renderItem: @Composable (SavableSearchable, Modifier) -> Unit = { item, modifier ->
        if (item is Tag) {
            FolderGridItem(modifier, item, showLabels = settings.showLabels)
        } else {
            GridItem(modifier, item, showLabels = settings.showLabels)
        }
    },
) {
    val showHeadings = settings.showGroupLabels && selectedTag == null
    val headings = remember(groups, showHeadings) {
        buildMap {
            if (showHeadings) {
                groups.pinned.firstOrNull()?.let { put(it.key, R.string.favorites_card_pinned to groups.pinned.size) }
                groups.recent.firstOrNull()?.let { put(it.key, R.string.favorites_card_recent_short to groups.recent.size) }
                groups.frequent.firstOrNull()?.let { put(it.key, R.string.favorites_card_frequent_short to groups.frequent.size) }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // GridItem adds 8dp of outer padding. Keep the remaining hit area at least 48dp
        // and preserve the requested icon size instead of shrinking it to fit the screen.
        val minItemWidth = (settings.iconSize + 16).coerceAtLeast(56).dp
        val availableWidth = (maxWidth - 16.dp).coerceAtLeast(minItemWidth)
        // A glimpse of the next item makes overflow discoverable without another control.
        val slots = settings.columns.coerceIn(1, 10) + if (items.size > settings.columns) 0.35f else 0f
        val itemWidth = (availableWidth / slots).coerceAtLeast(minItemWidth)
        val trailingSpace = (availableWidth - itemWidth * items.size).coerceAtLeast(0.dp)
        // Returning to another tag starts at its first member; ordinary suggestion updates
        // retain the visible item using the row's stable item keys.
        key(selectedTag) {
            // Don't hand horizontal overscroll to the launcher's page gestures. Vertical
            // motion still belongs to the app drawer containing this row.
            val scrollConnection = remember {
                object : NestedScrollConnection {
                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) =
                        Offset(available.x, 0f)

                    override suspend fun onPostFling(consumed: Velocity, available: Velocity) =
                        Velocity(available.x, 0f)
                }
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth().nestedScroll(scrollConnection),
                state = rememberLazyListState(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                items(items, key = { it.key }, contentType = { "favorite" }) { item ->
                    Column(Modifier.width(itemWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (showHeadings) {
                            val title = headings[item.key]
                            // The caption belongs to the whole group, not just its first
                            // icon. Let it draw across neighboring cells without changing
                            // icon spacing, but stop before the next group's heading.
                            val span = title?.second ?: 1
                            val lastGroup = title != null && item.key == items.getOrNull(items.size - span)?.key
                            val titleWidth = (itemWidth * span + if (lastGroup) trailingSpace else 0.dp)
                                .coerceAtMost(availableWidth)
                            Text(
                                text = title?.let { stringResource(it.first) } ?: "",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentWidth(Alignment.Start, unbounded = true)
                                    .widthIn(max = titleWidth)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                    .then(if (title != null) Modifier.semantics { heading() } else Modifier),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // Unlabelled grid items are square. Limit their own width so a low
                        // items-in-view setting doesn't turn this strip into a tall card.
                        renderItem(item, Modifier.width(if (settings.showLabels) itemWidth else minItemWidth))
                    }
                }
            }
        }
    }
}
