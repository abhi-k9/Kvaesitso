package de.mm20.launcher2.ui.launcher.widgets

import android.appwidget.AppWidgetHost
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.widgets.AppWidget
import de.mm20.launcher2.widgets.Widget
import de.mm20.launcher2.widgets.WidgetPlacement
import de.mm20.launcher2.widgets.WidgetRepository
import de.mm20.launcher2.widgets.WidgetStack
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

internal class WidgetStackVM(private val stackId: UUID) : ViewModel(), KoinComponent {
    private val repository: WidgetRepository by inject()
    val children = repository.get(parent = stackId, limit = Int.MAX_VALUE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(widget: Widget, host: AppWidgetHost, index: Int? = null) {
        if (widget is WidgetStack) return
        viewModelScope.launch {
            if (!repository.insertIntoStack(stackId, widget, index) && widget is AppWidget) {
                // The stack may have been separated while the provider's configuration was open.
                host.deleteAppWidgetId(widget.config.widgetId)
            }
        }
    }

    fun update(widget: Widget) = repository.update(widget)

    fun move(widget: Widget, offset: Int) {
        viewModelScope.launch { repository.moveInStack(stackId, widget.id, offset) }
    }

    suspend fun takeOut(widget: Widget) = repository.removeWithChildren(widget.id)
    suspend fun restore(placements: List<WidgetPlacement>) = repository.restore(placements)

}

@Composable
internal fun widgetStackViewModel(id: UUID): WidgetStackVM =
    viewModel(key = "widget-stack-$id") { WidgetStackVM(id) }

internal fun releaseWidgetIds(host: AppWidgetHost, removed: List<WidgetPlacement>) {
    removed.forEach { placement ->
        (placement.widget as? AppWidget)?.let { host.deleteAppWidgetId(it.config.widgetId) }
    }
}

/** Keep provider IDs alive until Undo finishes, even when the snackbar's scope is cancelled. */
internal suspend fun finishWidgetRemoval(
    removed: List<WidgetPlacement>,
    requestUndo: suspend () -> Boolean,
    restore: suspend (List<WidgetPlacement>) -> Boolean,
    discard: (List<WidgetPlacement>) -> Unit,
) {
    if (removed.isEmpty()) return
    var restored = false
    try {
        if (requestUndo()) {
            withContext(NonCancellable) { restored = restore(removed) }
        }
    } finally {
        if (!restored) discard(removed)
    }
}
