package de.mm20.launcher2.searchactions.actions

import android.content.Context
import android.widget.Toast
import de.mm20.launcher2.searchactions.R
import de.mm20.launcher2.tasks.TasksApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Adds a task to the Tasks app. Without the permission to change tasks (or with an older version
 * of Tasks), the task is opened in Tasks instead, to be saved there.
 */
data class AddTaskAction(
    override val label: String,
    val title: String,
) : SearchAction {
    override val icon: SearchActionIcon = SearchActionIcon.Task
    override val iconColor: Int = 0
    override val customIcon: String? = null

    override fun start(context: Context) {
        val appContext = context.applicationContext
        if (!TasksApp.canChangeTasks(appContext)) {
            TasksApp.openNewTask(context, title)
            return
        }
        scope.launch {
            val added = withContext(Dispatchers.IO) {
                TasksApp.addTask(appContext, title)
            }
            if (added) {
                Toast.makeText(
                    appContext,
                    appContext.getString(R.string.search_action_task_added, title),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                TasksApp.openNewTask(appContext, title)
            }
        }
    }

    companion object {
        // Outlives the search UI, which is closed when the action is started
        private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    }
}
