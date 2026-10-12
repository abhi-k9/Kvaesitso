package io.github.abhik9.kvaesitso.plugin.calendarfeeds

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * One occurrence of an event
 */
internal data class Occurrence(
    val event: IcsEvent,
    /**
     * Unix time in milliseconds
     */
    val start: Long,
    /**
     * Unix time in milliseconds. For all-day events, the last millisecond of the last day, like the
     * launcher's own all-day events.
     */
    val end: Long,
    val allDay: Boolean,
)

/**
 * The occurrences of the events of [calendar] that take place between [from] and [to] (unix times
 * in milliseconds), sorted by their start. Recurring events are expanded, without the dates that
 * they exclude and with the occurrences that were moved or changed.
 * @param defaultZone the time zone of all-day events, and of floating times if the calendar
 * doesn't have one
 */
internal fun occurrences(
    calendar: IcsCalendar,
    from: Long,
    to: Long,
    defaultZone: ZoneId,
): List<Occurrence> {
    val floatingZone = calendar.timeZone ?: defaultZone
    val (overrides, masters) = calendar.events.partition { it.recurrenceId != null }

    // Occurrences that were changed or cancelled, by the event's UID and the original time
    val overrideKeys = overrides.mapNotNullTo(HashSet()) { override ->
        val uid = override.uid ?: return@mapNotNullTo null
        val master = masters.find { it.uid == uid }
        val masterZone = master?.let { zoneOf(it.start, floatingZone, defaultZone) } ?: floatingZone
        occurrenceKey(uid, override.recurrenceId!!, masterZone)
    }

    val result = mutableListOf<Occurrence>()
    for (event in masters) {
        if (event.cancelled) continue
        result += occurrencesOf(event, from, to, floatingZone, defaultZone, overrideKeys)
    }
    for (override in overrides) {
        if (override.cancelled) continue
        val single = override.copy(rrule = null, rdates = emptyList(), exdates = emptyList())
        result += occurrencesOf(single, from, to, floatingZone, defaultZone, emptySet())
    }
    return result.sortedBy { it.start }
}

private fun occurrencesOf(
    event: IcsEvent,
    from: Long,
    to: Long,
    floatingZone: ZoneId,
    defaultZone: ZoneId,
    overrideKeys: Set<String>,
): List<Occurrence> {
    val zone = zoneOf(event.start, floatingZone, defaultZone)
    val allDay = event.start is IcsTime.Date
    val localStart = localTimeOf(event.start, zone, floatingZone)

    // The length of each occurrence: days for all-day events, a duration otherwise
    val lengthDays: Long
    val length: Duration
    if (allDay) {
        val startDate = localStart.toLocalDate()
        val endDate = when (val end = event.end) {
            is IcsTime.Date -> end.date
            is IcsTime.DateTime -> localTimeOf(end, zone, floatingZone).toLocalDate()
            null -> event.duration?.let { startDate.plusDays(it.toDays()) } ?: startDate.plusDays(1)
        }
        lengthDays = daysBetween(startDate, endDate).coerceAtLeast(1)
        length = Duration.ZERO
    } else {
        lengthDays = 0
        val startInstant = localStart.atZone(zone).toInstant()
        length = when (val end = event.end) {
            is IcsTime.DateTime -> Duration.between(
                startInstant,
                end.dateTime.atZone(end.zone ?: zoneOf(end, floatingZone, defaultZone)).toInstant()
            )

            is IcsTime.Date -> Duration.between(startInstant, end.date.atStartOfDay(zone).toInstant())
            null -> event.duration ?: Duration.ZERO
        }.let { if (it.isNegative) Duration.ZERO else it }
    }

    // The local start times of all occurrences until the end of the window
    val windowEnd = LocalDateTime.ofInstant(Instant.ofEpochMilli(to), zone).plusDays(1)
    val rule = event.rrule?.let { rrule ->
        RecurrenceRule.parse(rrule) { localTimeOf(it, zone, floatingZone, untilOfDay = true) }
    }
    val starts = sortedSetOf<LocalDateTime>()
    if (rule != null) {
        starts += rule.occurrences(localStart, windowEnd)
    } else {
        // Without a (supported) rule, there's just the first occurrence
        starts += localStart
    }
    for (rdate in event.rdates) {
        starts += localTimeOf(rdate, zone, floatingZone)
    }
    val excludedTimes = event.exdates.filterIsInstance<IcsTime.DateTime>()
        .mapTo(HashSet()) { localTimeOf(it, zone, floatingZone) }
    val excludedDates = event.exdates.filterIsInstance<IcsTime.Date>().mapTo(HashSet()) { it.date }

    val result = mutableListOf<Occurrence>()
    for (start in starts) {
        if (start in excludedTimes || start.toLocalDate() in excludedDates) continue
        val uid = event.uid
        if (uid != null && overrideKeys.isNotEmpty()) {
            val original = if (allDay) IcsTime.Date(start.toLocalDate())
            else IcsTime.DateTime(start, zone)
            if (occurrenceKey(uid, original, zone) in overrideKeys) continue
        }
        val startMillis: Long
        val endMillis: Long
        if (allDay) {
            val date = start.toLocalDate()
            startMillis = date.atStartOfDay(defaultZone).toInstant().toEpochMilli()
            endMillis = date.plusDays(lengthDays).atStartOfDay(defaultZone).toInstant().toEpochMilli() - 1
        } else {
            val instant = start.atZone(zone).toInstant()
            startMillis = instant.toEpochMilli()
            endMillis = instant.plus(length).toEpochMilli()
        }
        if (startMillis >= to) continue
        if (endMillis <= from && startMillis < from) continue
        result += Occurrence(event, startMillis, endMillis, allDay)
    }
    return result
}

/**
 * The time zone that an event's times are in: their own, the calendar's for floating times, or
 * the device's for all-day events
 */
private fun zoneOf(time: IcsTime, floatingZone: ZoneId, defaultZone: ZoneId): ZoneId {
    return when (time) {
        is IcsTime.Date -> defaultZone
        is IcsTime.DateTime -> time.zone ?: floatingZone
    }
}

/**
 * [time] in the local time of [zone]
 * @param untilOfDay for dates, the end of the day instead of its start
 */
private fun localTimeOf(
    time: IcsTime,
    zone: ZoneId,
    floatingZone: ZoneId,
    untilOfDay: Boolean = false,
): LocalDateTime {
    return when (time) {
        is IcsTime.Date -> if (untilOfDay) time.date.atTime(LocalTime.MAX) else time.date.atStartOfDay()
        is IcsTime.DateTime -> {
            val timeZone = time.zone ?: floatingZone
            if (timeZone == zone) time.dateTime
            else time.dateTime.atZone(timeZone).withZoneSameInstant(zone).toLocalDateTime()
        }
    }
}

/**
 * Identifies an occurrence of a recurring event, to match it with the RECURRENCE-ID of a changed
 * occurrence: by date for all-day events, by the moment otherwise
 */
private fun occurrenceKey(uid: String, time: IcsTime, zone: ZoneId): String {
    return when (time) {
        is IcsTime.Date -> "$uid|${time.date}"
        is IcsTime.DateTime -> "$uid|${time.dateTime.atZone(time.zone ?: zone).toInstant().toEpochMilli()}"
    }
}
