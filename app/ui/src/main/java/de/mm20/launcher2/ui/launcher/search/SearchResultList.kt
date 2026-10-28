package de.mm20.launcher2.ui.launcher.search

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The results must occupy the viewport even when only a single result remains. */
@Composable
internal fun SearchResultList(
    state: LazyListState,
    contentPadding: PaddingValues,
    reverseLayout: Boolean,
    userScrollEnabled: Boolean,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        // The overlay Box doesn't propagate its minimum height to the list. Without this,
        // reverse layout aligns short content to the bottom of a short list at the TOP of
        // the screen, rather than above the search bar.
        modifier = modifier.fillMaxSize(),
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        userScrollEnabled = userScrollEnabled,
        content = content,
    )
}
