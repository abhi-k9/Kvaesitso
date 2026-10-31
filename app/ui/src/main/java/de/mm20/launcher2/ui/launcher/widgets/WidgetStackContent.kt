package de.mm20.launcher2.ui.launcher.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.LocalAppWidgetHost
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.locals.LocalSnackbarHostState
import de.mm20.launcher2.ui.theme.transparency.transparency
import de.mm20.launcher2.widgets.AppWidget
import de.mm20.launcher2.widgets.SpacerWidget
import de.mm20.launcher2.widgets.Widget
import de.mm20.launcher2.widgets.WidgetStack
import kotlinx.coroutines.launch

@Composable
internal fun WidgetStackContent(widget: WidgetStack) {
    val viewModel = widgetStackViewModel(widget.id)
    val children by viewModel.children.collectAsStateWithLifecycle()
    val host = LocalAppWidgetHost.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbar = LocalSnackbarHostState.current
    WidgetStackPager(children, widget.config.height.coerceIn(120, 640).dp) { child ->
        val background = if (child is AppWidget && !child.config.background || child is SpacerWidget) {
            0f
        } else MaterialTheme.transparency.surface
        LauncherCard(modifier = Modifier.fillMaxSize(), backgroundOpacity = background) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                WidgetContent(
                    widget = child,
                    onWidgetUpdate = viewModel::update,
                    onWidgetRemove = {
                        lifecycleOwner.lifecycleScope.launch {
                            val removed = viewModel.takeOut(child)
                            finishWidgetRemoval(
                                removed = removed,
                                requestUndo = {
                                    snackbar.showSnackbar(
                                        message = context.getString(R.string.widget_removed),
                                        actionLabel = context.getString(R.string.action_undo),
                                        duration = SnackbarDuration.Short,
                                    ) == SnackbarResult.ActionPerformed
                                },
                                restore = viewModel::restore,
                                discard = { releaseWidgetIds(host, it) },
                            )
                        }
                    },
                    onWidgetAdd = { added, offset -> viewModel.add(added, host, children.indexOf(child) + offset) },
                )
            }
        }
    }
}

/** Only the current page and pages needed by an in-progress swipe are composed. */
@Composable
internal fun WidgetStackPager(
    widgets: List<Widget>,
    height: Dp,
    modifier: Modifier = Modifier,
    content: @Composable (Widget) -> Unit,
) {
    val pager = rememberPagerState(pageCount = { widgets.size })
    val scope = rememberCoroutineScope()
    val stackLabel = stringResource(R.string.stack_label)
    LaunchedEffect(widgets.map { it.id }) {
        if (widgets.isNotEmpty() && pager.currentPage > widgets.lastIndex) {
            pager.scrollToPage(widgets.lastIndex)
        }
    }
    Column(modifier.fillMaxWidth().semantics { paneTitle = stackLabel }) {
        if (widgets.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.stack_empty), Modifier.padding(16.dp))
            }
        } else {
            HorizontalPager(
                state = pager,
                key = { widgets[it].id.toString() },
                beyondViewportPageCount = 0,
                modifier = Modifier.fillMaxWidth().height(height),
                verticalAlignment = Alignment.Top,
            ) { page ->
                widgets.getOrNull(page)?.let { child ->
                    Box(Modifier.fillMaxSize()) { content(child) }
                }
            }
            val page = pager.currentPage.coerceIn(0, widgets.lastIndex)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.Center,
            ) {
                IconButton(
                    enabled = page > 0,
                    onClick = { scope.launch { pager.animateScrollToPage(page - 1) } },
                ) {
                    Icon(painterResource(R.drawable.chevron_backward_24px), stringResource(R.string.stack_previous))
                }
                Box(Modifier.height(48.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.stack_page, page + 1, widgets.size), style = MaterialTheme.typography.labelMedium)
                }
                IconButton(
                    enabled = page < widgets.lastIndex,
                    onClick = { scope.launch { pager.animateScrollToPage(page + 1) } },
                ) {
                    Icon(painterResource(R.drawable.chevron_forward_24px), stringResource(R.string.stack_next))
                }
            }
        }
    }
}
