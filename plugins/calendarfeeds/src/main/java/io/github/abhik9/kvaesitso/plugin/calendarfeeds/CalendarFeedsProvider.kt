package io.github.abhik9.kvaesitso.plugin.calendarfeeds

import android.content.Context
import android.content.Intent
import android.net.Uri
import de.mm20.launcher2.plugin.config.QueryPluginConfig
import de.mm20.launcher2.plugin.config.StorageStrategy
import de.mm20.launcher2.sdk.PluginState
import de.mm20.launcher2.sdk.base.SearchParams
import de.mm20.launcher2.sdk.calendar.CalendarEvent
import de.mm20.launcher2.sdk.calendar.CalendarList
import de.mm20.launcher2.sdk.calendar.CalendarProvider
import de.mm20.launcher2.search.calendar.CalendarListType
import de.mm20.launcher2.search.calendar.CalendarQuery
import java.time.ZoneId

class CalendarFeedsProvider : CalendarProvider(
    QueryPluginConfig(storageStrategy = StorageStrategy.StoreCopy)
) {

    override suspend fun getPluginState(): PluginState {
        val context = context ?: return PluginState.Ready()
        val feeds = FeedStore(context).getFeeds()
        if (feeds.isEmpty()) {
            return PluginState.SetupRequired(
                setupActivity = Intent(context, FeedsActivity::class.java),
                message = context.getString(R.string.setup_message),
            )
        }
        return PluginState.Ready(
            context.resources.getQuantityString(R.plurals.state_ready, feeds.size, feeds.size)
        )
    }

    override suspend fun getCalendarLists(): List<CalendarList> {
        val context = context ?: return emptyList()
        return FeedStore(context).getFeeds().map {
            CalendarList(
                id = it.id,
                name = it.name,
                contentTypes = listOf(CalendarListType.Calendar),
                color = it.color,
            )
        }
    }

    /**
     * The feeds are updated like a synced account, also when the launcher only allows offline
     * results: the calendar widget always does, and it would never be updated otherwise.
     */
    override suspend fun search(query: CalendarQuery, params: SearchParams): List<CalendarEvent> {
        val context = context ?: return emptyList()
        val feeds = FeedStore(context).getFeeds().filter { it.id !in query.excludedCalendars }
        if (feeds.isEmpty()) return emptyList()

        val from = query.start ?: System.currentTimeMillis()
        val to = query.end ?: (from + 730L * 24 * 60 * 60 * 1000)
        val text = query.query?.trim()?.takeIf { it.isNotEmpty() }
        val zone = ZoneId.systemDefault()

        return feeds.flatMap { feed ->
            val calendar = FeedRepository.getCalendar(context, feed) ?: return@flatMap emptyList()
            occurrences(calendar, from, to, zone)
                .filter { text == null || it.event.matches(text) }
                .map { it.toCalendarEvent(context, feed) }
        }
            .sortedBy { it.startTime ?: it.endTime }
            // Daily events would otherwise fill the search results for two years
            .take(MaxResults)
    }

    private fun IcsEvent.matches(text: String): Boolean {
        return summary.contains(text, ignoreCase = true) ||
                location?.contains(text, ignoreCase = true) == true ||
                description?.contains(text, ignoreCase = true) == true
    }

    private fun Occurrence.toCalendarEvent(context: Context, feed: Feed): CalendarEvent {
        val uidHash = Integer.toHexString((event.uid ?: event.summary).hashCode())
        return CalendarEvent(
            id = "${feed.id}:$uidHash:$start",
            title = event.summary.ifEmpty { context.getString(R.string.untitled_event) },
            calendarName = feed.name,
            description = event.description,
            location = event.location,
            color = feed.color,
            startTime = start,
            endTime = end,
            includeTime = !allDay,
            uri = eventUri(),
        )
    }

    /**
     * The event's web page if it has one, otherwise the user's calendar app on that day
     */
    private fun Occurrence.eventUri(): Uri {
        val url = event.url
        if (url != null && (url.startsWith("https://", ignoreCase = true) ||
                    url.startsWith("http://", ignoreCase = true))
        ) {
            return Uri.parse(url)
        }
        return Uri.parse("content://com.android.calendar/time/$start")
    }

    companion object {
        private const val MaxResults = 300
    }
}
