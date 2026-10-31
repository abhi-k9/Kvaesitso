package de.mm20.launcher2.ui.launcher.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.mm20.launcher2.preferences.ui.WidgetWidth
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.launcher.sheets.ConfigureWidgetSheet
import de.mm20.launcher2.ui.launcher.widgets.calendar.CalendarWidget
import de.mm20.launcher2.ui.launcher.widgets.external.AppWidget
import de.mm20.launcher2.ui.launcher.widgets.favorites.AppsWidget
import de.mm20.launcher2.ui.launcher.widgets.music.MusicWidget
import de.mm20.launcher2.ui.launcher.widgets.notes.NotesWidget
import de.mm20.launcher2.ui.launcher.widgets.weather.WeatherWidget
import de.mm20.launcher2.ui.theme.transparency.transparency
import de.mm20.launcher2.widgets.AppWidget
import de.mm20.launcher2.widgets.CalendarWidget
import de.mm20.launcher2.widgets.AppsWidget
import de.mm20.launcher2.widgets.MusicWidget
import de.mm20.launcher2.widgets.NotesWidget
import de.mm20.launcher2.widgets.SpacerWidget
import de.mm20.launcher2.widgets.WeatherWidget
import de.mm20.launcher2.widgets.Widget
import de.mm20.launcher2.widgets.WidgetStack

@Composable
fun WidgetItem(
    widget: Widget,
    modifier: Modifier = Modifier,
    editMode: Boolean = false,
    onWidgetAdd: (widget: Widget, offset: Int) -> Unit = { _, _ -> },
    onWidgetUpdate: (widget: Widget) -> Unit = {},
    onWidgetRemove: () -> Unit = {},
    width: WidgetWidth = WidgetWidth.Full,
    onWidthChange: ((WidgetWidth) -> Unit)? = null,
    onStackWithNext: (() -> Unit)? = null,
    onUnstack: (() -> Unit)? = null,
    draggableState: DraggableState = rememberDraggableState {},
    onDragStopped: () -> Unit = {}
) {
    val context = LocalContext.current

    var configure by rememberSaveable { mutableStateOf(false) }

    var isDragged by remember { mutableStateOf(false) }
    val elevation by animateDpAsState(if (isDragged) 8.dp else 0.dp)

    val backgroundOpacity by animateFloatAsState(
        if ((widget is AppWidget && !widget.config.background || widget is SpacerWidget || widget is WidgetStack) && !editMode) 0f
        else MaterialTheme.transparency.surface,
        label = "widgetCardBackgroundOpacity",
    )

    LauncherCard(
        modifier = modifier.zIndex(if (isDragged) 1f else 0f),
        elevation = elevation,
        backgroundOpacity = backgroundOpacity,
    ) {
        Column {
            AnimatedVisibility(editMode) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painterResource(R.drawable.drag_indicator_24px),
                        contentDescription = null,
                        modifier = Modifier.draggable(
                            state = draggableState,
                            orientation = Orientation.Vertical,
                            startDragImmediately = true,
                            onDragStarted = {
                                isDragged = true
                            },
                            onDragStopped = {
                                isDragged = false
                                onDragStopped()
                            }
                        )
                    )
                    Text(
                        text = widget.getLabel(LocalContext.current),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                    if (onWidthChange != null) {
                        // Narrower widgets are shown side by side, outside of edit mode
                        Box {
                            var showWidths by remember { mutableStateOf(false) }
                            IconButton(onClick = { showWidths = true }) {
                                Icon(
                                    painterResource(R.drawable.splitscreen_right_20px),
                                    contentDescription = stringResource(R.string.widget_width),
                                )
                            }
                            DropdownMenu(
                                expanded = showWidths,
                                onDismissRequest = { showWidths = false },
                            ) {
                                for (option in WidgetWidth.entries) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    when (option) {
                                                        WidgetWidth.Full -> R.string.widget_width_full
                                                        WidgetWidth.Half -> R.string.widget_half_width
                                                        WidgetWidth.Third -> R.string.widget_width_third
                                                        WidgetWidth.Quarter -> R.string.widget_width_quarter
                                                    }
                                                )
                                            )
                                        },
                                        trailingIcon = if (option == width) {
                                            {
                                                Icon(
                                                    painterResource(R.drawable.check_24px),
                                                    contentDescription = null,
                                                )
                                            }
                                        } else null,
                                        onClick = {
                                            showWidths = false
                                            onWidthChange(option)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (onStackWithNext != null) {
                        Box {
                            var showStackAction by remember { mutableStateOf(false) }
                            IconButton(onClick = { showStackAction = true }) {
                                Icon(
                                    painterResource(R.drawable.widgets_24px),
                                    contentDescription = stringResource(
                                        if (widget is WidgetStack) R.string.stack_add_next else R.string.stack_with_next
                                    ),
                                )
                            }
                            DropdownMenu(expanded = showStackAction, onDismissRequest = { showStackAction = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (widget is WidgetStack) R.string.stack_add_next else R.string.stack_with_next)) },
                                    onClick = { showStackAction = false; onStackWithNext() },
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        configure = true
                    }) {
                        Icon(
                            painterResource(R.drawable.tune_24px),
                            contentDescription = stringResource(R.string.settings)
                        )
                    }
                    IconButton(onClick = { onWidgetRemove() }) {
                        Icon(
                            painterResource(R.drawable.delete_24px),
                            contentDescription = stringResource(R.string.widget_action_remove)
                        )
                    }
                }
            }
            AnimatedVisibility(!editMode) {
                WidgetContent(widget, onWidgetAdd, onWidgetUpdate, onWidgetRemove)
            }
        }
    }
    if (widget is WidgetStack) {
        EditWidgetStackSheet(
            expanded = configure,
            widget = widget,
            onWidgetUpdated = onWidgetUpdate,
            onUnstack = { onUnstack?.invoke(); configure = false },
            onDismiss = { configure = false },
        )
    } else {
        ConfigureWidgetSheet(
            expanded = configure,
            widget = widget,
            onWidgetUpdated = onWidgetUpdate,
            onDismiss = { configure = false },
        )
    }
}

@Composable
internal fun WidgetContent(
    widget: Widget,
    onWidgetAdd: (Widget, Int) -> Unit = { _, _ -> },
    onWidgetUpdate: (Widget) -> Unit = {},
    onWidgetRemove: () -> Unit = {},
) {
    when (widget) {
        is WidgetStack -> WidgetStackContent(widget)
        is WeatherWidget -> WeatherWidget(widget)
        is MusicWidget -> MusicWidget(widget)
        is CalendarWidget -> CalendarWidget(widget)
        is AppsWidget -> AppsWidget(widget)
        is NotesWidget -> NotesWidget(widget, onWidgetAdd = onWidgetAdd)
        is AppWidget -> AppWidget(widget, onWidgetUpdate = onWidgetUpdate, onWidgetRemove = onWidgetRemove)
        is SpacerWidget -> Spacer(Modifier.fillMaxWidth().height(widget.config.height.dp))
    }
}
