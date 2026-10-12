package de.mm20.launcher2.searchactions.builders

import android.content.Context
import de.mm20.launcher2.searchactions.R
import de.mm20.launcher2.searchactions.TextClassificationResult
import de.mm20.launcher2.searchactions.actions.AddTaskAction
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.actions.SearchActionIcon
import de.mm20.launcher2.tasks.TasksApp

class AddTaskActionBuilder(
    override val label: String,
) : SearchActionBuilder {

    constructor(context: Context) : this(context.getString(R.string.search_action_task))

    override val key: String
        get() = "task"

    override val icon: SearchActionIcon = SearchActionIcon.Task

    override fun build(context: Context, classifiedQuery: TextClassificationResult): SearchAction? {
        val title = classifiedQuery.text.trim()
        if (title.isEmpty() || !TasksApp.isInstalled(context)) return null
        return AddTaskAction(
            context.getString(R.string.search_action_task),
            title,
        )
    }
}
