package de.mm20.launcher2.widgets

import android.content.Context
import de.mm20.launcher2.database.entities.PartialWidgetEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class CalendarWidgetConfig(
    val allDayEvents: Boolean = true,
    @Deprecated("Use excludedCalendars instead")
    @SerialName("excludedCalendarIds")
    val legacyExcludedCalendarIds: List<Long>? = null,
    @SerialName("excludedCalendars")
    val excludedCalendarIds: List<String>? = null,
    val completedTasks: Boolean = true,
    /**
     * Show open tasks that are overdue on today's page
     */
    val overdueTasks: Boolean = true,
    /**
     * Show open tasks without a due date on today's page
     */
    val undatedTasks: Boolean = false,
    val upcomingEventsCount: Int = 3,
    val upcomingTaskCount: Int = 3,
)
data class CalendarWidget(
    override val id: UUID,
    val config: CalendarWidgetConfig = CalendarWidgetConfig(),
) : Widget() {
    override fun toDatabaseEntity(): PartialWidgetEntity {
        return PartialWidgetEntity(
            id = id,
            type = Type,
            config = Json.encodeToString(config),
        )
    }

    override fun getLabel(context: Context): String {
        return context.getString(R.string.widget_name_calendar)
    }

    companion object {
        const val Type = "calendar"
    }
}