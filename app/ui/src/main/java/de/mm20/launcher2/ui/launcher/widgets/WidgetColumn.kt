package de.mm20.launcher2.ui.launcher.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.unit.Dp
import de.mm20.launcher2.preferences.ui.WidgetWidth
import de.mm20.launcher2.preferences.ui.widgetRows
import de.mm20.launcher2.widgets.Widget
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.LocalAppWidgetHost
import de.mm20.launcher2.ui.ktx.animateTo
import de.mm20.launcher2.ui.launcher.sheets.WidgetPickerSheet
import de.mm20.launcher2.ui.locals.LocalSnackbarHostState
import de.mm20.launcher2.widgets.WidgetStack
import kotlinx.coroutines.launch

@Composable
fun WidgetColumn(
    modifier: Modifier = Modifier,
    editMode: Boolean = false,
    onEditModeChange: (Boolean) -> Unit,
    parentId: String,
) {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: WidgetsVM = viewModel(
        key = "widgets-column-$parentId",
        factory = WidgetsVM.Factory(parentId),
    )
    val snackbarHostState = LocalSnackbarHostState.current

    var addNewWidget by rememberSaveable { mutableStateOf(false) }


    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        val scope = rememberCoroutineScope()
        Column {
            val widgets by viewModel.widgets.collectAsState()
            val swapThresholds = remember(widgets) {
                Array(widgets.size) { floatArrayOf(0f, 0f) }
            }
            val widths by viewModel.widgetWidths.collectAsState(emptyMap())

            @Composable
            fun WidgetEntry(i: Int, widget: Widget, topPadding: Dp, modifier: Modifier) {
                key(widget.id) {
                    var dragOffsetAfterSwap = remember<Float?> { null }
                    val offsetY =
                        remember(widgets) { mutableFloatStateOf(dragOffsetAfterSwap ?: 0f) }

                    LaunchedEffect(widgets) {
                        dragOffsetAfterSwap = null
                    }

                    val widgetHost = LocalAppWidgetHost.current

                    WidgetItem(
                        widget = widget,
                        editMode = editMode,
                        onWidgetAdd = { widget, offset ->
                            viewModel.addWidget(widget, i + offset)
                        },
                        onWidgetRemove = {
                            lifecycleOwner.lifecycleScope.launch {
                                val removed = viewModel.removeWidget(widget)
                                finishWidgetRemoval(
                                    removed = removed,
                                    requestUndo = {
                                        snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.widget_removed),
                                            actionLabel = context.getString(R.string.action_undo),
                                            duration = SnackbarDuration.Short,
                                        ) == SnackbarResult.ActionPerformed
                                    },
                                    restore = viewModel::restoreWidgets,
                                    discard = { releaseWidgetIds(widgetHost, it) },
                                )
                            }
                        },
                        onWidgetUpdate = {
                            viewModel.updateWidget(it)
                        },
                        width = widths[widget.id.toString()] ?: WidgetWidth.Full,
                        onWidthChange = { viewModel.setWidth(widget, it) },
                        onStackWithNext = if (widgets.getOrNull(i + 1)?.let { it !is WidgetStack } == true) {
                            { viewModel.stackWithNext(i) }
                        } else null,
                        onUnstack = if (widget is WidgetStack) {
                            { viewModel.unstack(widget) }
                        } else null,
                        modifier = modifier
                            .onPlaced {
                                swapThresholds[i][0] = it.positionInParent().y
                                swapThresholds[i][1] = it.positionInParent().y + it.size.height
                            }
                            .padding(top = topPadding)
                            .offset {
                                IntOffset(0, offsetY.value.toInt())
                            },
                        draggableState = rememberDraggableState {
                            // This callback already runs on the UI thread and does not suspend.
                            // Avoid scheduling a coroutine for every pointer movement.
                            val newOffset = offsetY.value + it
                            offsetY.value = newOffset
                            if (i > 0 && newOffset < (swapThresholds[i - 1][0] - swapThresholds[i - 1][1])) {
                                if (dragOffsetAfterSwap == null) {
                                    dragOffsetAfterSwap =
                                        swapThresholds[i - 1][1] - swapThresholds[i - 1][0] + newOffset
                                    viewModel.moveUp(i)
                                }
                            }
                            if (i < widgets.lastIndex && newOffset > (swapThresholds[i + 1][1] - swapThresholds[i + 1][0])) {
                                if (dragOffsetAfterSwap == null) {
                                    dragOffsetAfterSwap =
                                        swapThresholds[i + 1][0] - swapThresholds[i + 1][1] + newOffset
                                    viewModel.moveDown(i)
                                }
                            }
                        },
                        onDragStopped = {
                            scope.launch {
                                offsetY.animateTo(0f)
                            }
                        },
                    )
                }
            }

            // Outside of edit mode, narrower widgets that follow each other are shown side by
            // side. In edit mode, each widget has a row of its own, so that it can be moved up and
            // down.
            val widgetWidths = remember(widgets, widths, editMode) {
                widgets.map {
                    if (editMode) WidgetWidth.Full
                    else widths[it.id.toString()] ?: WidgetWidth.Full
                }
            }
            val rows = remember(widgetWidths) { widgetRows(widgetWidths) }
            for ((rowIndex, row) in rows.withIndex()) {
                val topPadding = if (rowIndex > 0) 8.dp else 0.dp
                if (row.size == 1 && widgetWidths[row[0]] == WidgetWidth.Full) {
                    WidgetEntry(row[0], widgets[row[0]], topPadding, Modifier.fillMaxWidth())
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = topPadding),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (i in row) {
                            WidgetEntry(
                                i, widgets[i], 0.dp,
                                Modifier.weight(widgetWidths[i].twelfths.toFloat())
                            )
                        }
                        // Narrower widgets keep their width if the row isn't full
                        val free = 12 - row.sumOf { widgetWidths[it].twelfths }
                        if (free > 0) Spacer(modifier = Modifier.weight(free.toFloat()))
                    }
                }
            }
        }

        val editButton by viewModel.editButton.collectAsState()
        if (editMode || editButton == true) {
            val title = stringResource(
                if (editMode) R.string.widget_add_widget
                else R.string.menu_edit_widgets,
            )

            Button(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 8.dp),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                onClick = {
                    if (!editMode) {
                        onEditModeChange(true)
                    } else {
                        addNewWidget = true
                    }
                },
            ) {
                Icon(
                    modifier = Modifier
                        .padding(end = ButtonDefaults.IconSpacing)
                        .size(ButtonDefaults.IconSize),
                    painter = painterResource(
                        if (editMode) R.drawable.add_20px else R.drawable.edit_20px,
                    ),
                    contentDescription = null,
                )
                Text(title)
            }

        }
    }

    WidgetPickerSheet(
        expanded = addNewWidget,
        onDismiss = { addNewWidget = false },
        onWidgetSelected = {
            viewModel.addWidget(it)
            addNewWidget = false
        },
    )
}
