package de.mm20.launcher2.ui.launcher.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.LocalAppWidgetHost
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.launcher.sheets.ConfigureWidgetSheet
import de.mm20.launcher2.ui.launcher.sheets.WidgetPickerSheet
import de.mm20.launcher2.ui.locals.LocalSnackbarHostState
import de.mm20.launcher2.widgets.Widget
import de.mm20.launcher2.widgets.WidgetStack
import kotlinx.coroutines.launch

@Composable
internal fun EditWidgetStackSheet(
    expanded: Boolean,
    widget: WidgetStack,
    onWidgetUpdated: (Widget) -> Unit,
    onUnstack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val viewModel = widgetStackViewModel(widget.id)
    val children by viewModel.children.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val host = LocalAppWidgetHost.current
    val snackbar = LocalSnackbarHostState.current
    var addWidget by rememberSaveable { mutableStateOf(false) }
    var configureId by rememberSaveable { mutableStateOf<String?>(null) }
    val configuredWidget = children.find { it.id.toString() == configureId }

    DismissableBottomSheet(expanded, onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding()) {
            Text(stringResource(R.string.stack_edit), style = MaterialTheme.typography.titleLarge)
            SliderPreference(
                title = stringResource(R.string.stack_height),
                value = widget.config.height.coerceIn(120, 640),
                min = 120, max = 640, step = 8,
                onValueChanged = { onWidgetUpdated(widget.copy(config = widget.config.copy(height = it))) },
            )
            Text(stringResource(R.string.stack_swipe_hint), style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                itemsIndexed(children, key = { _, child -> child.id }) { index, child ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            child.getLabel(context).ifBlank { context.getString(R.string.widget_name_unknown) },
                            Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(enabled = index > 0, onClick = { viewModel.move(child, -1) }) {
                            Icon(painterResource(R.drawable.keyboard_arrow_up_24px), stringResource(R.string.stack_move_up))
                        }
                        IconButton(enabled = index < children.lastIndex, onClick = { viewModel.move(child, 1) }) {
                            Icon(painterResource(R.drawable.keyboard_arrow_down_24px), stringResource(R.string.stack_move_down))
                        }
                        IconButton(onClick = { configureId = child.id.toString() }) {
                            Icon(painterResource(R.drawable.tune_24px), stringResource(R.string.settings))
                        }
                        IconButton(onClick = {
                            lifecycleOwner.lifecycleScope.launch {
                                val removed = viewModel.takeOut(child)
                                finishWidgetRemoval(
                                    removed = removed,
                                    requestUndo = {
                                        snackbar.showSnackbar(
                                            context.getString(R.string.widget_removed),
                                            context.getString(R.string.action_undo),
                                            duration = SnackbarDuration.Short,
                                        ) == SnackbarResult.ActionPerformed
                                    },
                                    restore = viewModel::restore,
                                    discard = { releaseWidgetIds(host, it) },
                                )
                            }
                        }) {
                            Icon(painterResource(R.drawable.delete_24px), stringResource(R.string.stack_remove_from_stack))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { addWidget = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.widget_add_widget))
                }
                OutlinedButton(onClick = onUnstack, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.stack_unstack))
                }
            }
        }
    }
    WidgetPickerSheet(
        expanded = expanded && addWidget,
        onDismiss = { addWidget = false },
        onWidgetSelected = { viewModel.add(it, host); addWidget = false },
    )
    configuredWidget?.let { child ->
        ConfigureWidgetSheet(
            expanded = expanded,
            widget = child,
            onWidgetUpdated = viewModel::update,
            onDismiss = { configureId = null },
        )
    }
}
