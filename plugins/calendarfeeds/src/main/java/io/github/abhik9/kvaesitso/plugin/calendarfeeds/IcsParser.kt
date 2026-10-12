package io.github.abhik9.kvaesitso.plugin.calendarfeeds

import java.time.DateTimeException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * A date (all-day) or a date and time of an iCalendar property
 */
internal sealed interface IcsTime {
    data class Date(val date: LocalDate) : IcsTime

    /**
     * @param zone null for a floating time, which is in the calendar's time zone
     */
    data class DateTime(val dateTime: LocalDateTime, val zone: ZoneId?) : IcsTime
}

internal data class IcsEvent(
    val uid: String?,
    val summary: String,
    val description: String? = null,
    val location: String? = null,
    val url: String? = null,
    val start: IcsTime,
    val end: IcsTime? = null,
    val duration: Duration? = null,
    val rrule: String? = null,
    val exdates: List<IcsTime> = emptyList(),
    val rdates: List<IcsTime> = emptyList(),
    /**
     * Set if this event replaces one occurrence of the recurring event with the same [uid]
     */
    val recurrenceId: IcsTime? = null,
    val cancelled: Boolean = false,
)

internal data class IcsCalendar(
    /**
     * X-WR-CALNAME, the name that the publisher gave the calendar
     */
    val name: String?,
    /**
     * X-WR-TIMEZONE, the time zone of floating times
     */
    val timeZone: ZoneId?,
    val events: List<IcsEvent>,
)

/**
 * A reader for iCalendar (.ics) files (RFC 5545), for the parts that a calendar subscription
 * needs: the events, their times, recurrence rules and exceptions. Other components (time zones,
 * to-dos, alarms) are skipped; time zones are taken from their IDs.
 */
internal object IcsParser {

    fun parse(text: String): IcsCalendar {
        var calendarName: String? = null
        var calendarZone: ZoneId? = null
        val events = mutableListOf<IcsEvent>()

        // The components that the current line is in, e.g. VCALENDAR, VEVENT, VALARM
        val components = ArrayDeque<String>()
        var event: MutableMap<String, MutableList<ContentLine>>? = null

        for (line in unfold(text)) {
            val contentLine = parseContentLine(line) ?: continue
            when (contentLine.name) {
                "BEGIN" -> {
                    val component = contentLine.value.uppercase()
                    components.addLast(component)
                    if (component == "VEVENT" && components.size == 2) {
                        event = mutableMapOf()
                    }
                }

                "END" -> {
                    val component = components.removeLastOrNull()
                    if (component == "VEVENT" && components.size == 1) {
                        event?.let { toEvent(it) }?.let { events += it }
                        event = null
                    }
                }

                else -> {
                    // Only properties of the event itself, not of e.g. its alarms
                    if (event != null && components.size == 2) {
                        event.getOrPut(contentLine.name) { mutableListOf() } += contentLine
                    } else if (components.size == 1 && components.last() == "VCALENDAR") {
                        when (contentLine.name) {
                            "X-WR-CALNAME" -> calendarName = unescapeText(contentLine.value).trim()
                                .takeIf { it.isNotEmpty() }

                            "X-WR-TIMEZONE" -> calendarZone = zoneOf(contentLine.value.trim())
                        }
                    }
                }
            }
        }
        return IcsCalendar(name = calendarName, timeZone = calendarZone, events = events)
    }

    private fun toEvent(properties: Map<String, List<ContentLine>>): IcsEvent? {
        fun first(name: String) = properties[name]?.firstOrNull()

        val start = first("DTSTART")?.let { parseTime(it) } ?: return null
        val exdates = properties["EXDATE"].orEmpty().flatMap { parseTimes(it) }
        val rdates = properties["RDATE"].orEmpty()
            // Periods (start/end) aren't supported
            .filter { it.params["VALUE"]?.uppercase() != "PERIOD" }
            .flatMap { parseTimes(it) }
        return IcsEvent(
            uid = first("UID")?.value?.trim(),
            summary = first("SUMMARY")?.let { unescapeText(it.value).trim() }.orEmpty(),
            description = first("DESCRIPTION")?.let { unescapeText(it.value).trim() }
                ?.takeIf { it.isNotEmpty() },
            location = first("LOCATION")?.let { unescapeText(it.value).trim() }
                ?.takeIf { it.isNotEmpty() },
            url = first("URL")?.value?.trim()?.takeIf { it.isNotEmpty() },
            start = start,
            end = first("DTEND")?.let { parseTime(it) },
            duration = first("DURATION")?.let { parseDuration(it.value.trim()) },
            rrule = first("RRULE")?.value?.trim()?.takeIf { it.isNotEmpty() },
            exdates = exdates,
            rdates = rdates,
            recurrenceId = first("RECURRENCE-ID")?.let { parseTime(it) },
            cancelled = first("STATUS")?.value?.trim()?.uppercase() == "CANCELLED",
        )
    }

    /**
     * Lines that are folded (continued on lines that start with a space or a tab) are joined
     */
    internal fun unfold(text: String): List<String> {
        val lines = mutableListOf<String>()
        val current = StringBuilder()
        for (rawLine in text.split('\n')) {
            val line = rawLine.removeSuffix("\r")
            if (line.startsWith(" ") || line.startsWith("\t")) {
                current.append(line, 1, line.length)
            } else {
                if (current.isNotEmpty()) lines += current.toString()
                current.setLength(0)
                current.append(line)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }

    internal data class ContentLine(
        val name: String,
        val params: Map<String, String>,
        val value: String,
    )

    /**
     * NAME;PARAM=value;PARAM="quoted value":VALUE
     */
    internal fun parseContentLine(line: String): ContentLine? {
        var inQuotes = false
        var colon = -1
        for (i in line.indices) {
            val c = line[i]
            if (c == '"') inQuotes = !inQuotes
            else if (c == ':' && !inQuotes) {
                colon = i
                break
            }
        }
        if (colon <= 0) return null
        val nameAndParams = splitOutsideQuotes(line.substring(0, colon), ';')
        val name = nameAndParams.first().trim().uppercase()
        if (name.isEmpty()) return null
        val params = nameAndParams.drop(1).mapNotNull {
            val equals = it.indexOf('=')
            if (equals <= 0) null
            else it.substring(0, equals).trim().uppercase() to
                    it.substring(equals + 1).trim().removeSurrounding("\"")
        }.toMap()
        return ContentLine(name, params, line.substring(colon + 1))
    }

    private fun splitOutsideQuotes(text: String, separator: Char): List<String> {
        val parts = mutableListOf<String>()
        var inQuotes = false
        var start = 0
        for (i in text.indices) {
            val c = text[i]
            if (c == '"') inQuotes = !inQuotes
            else if (c == separator && !inQuotes) {
                parts += text.substring(start, i)
                start = i + 1
            }
        }
        parts += text.substring(start)
        return parts
    }

    internal fun unescapeText(value: String): String {
        val result = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (val next = value[i + 1]) {
                    'n', 'N' -> result.append('\n')
                    else -> result.append(next)
                }
                i += 2
            } else {
                result.append(c)
                i++
            }
        }
        return result.toString()
    }

    private fun parseTimes(line: ContentLine): List<IcsTime> {
        return line.value.split(',').mapNotNull { value ->
            parseTime(value.trim(), line.params)
        }
    }

    private fun parseTime(line: ContentLine): IcsTime? = parseTime(line.value.trim(), line.params)

    private val dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val dateTimeFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    internal fun parseTime(value: String, params: Map<String, String> = emptyMap()): IcsTime? {
        return try {
            if (params["VALUE"]?.uppercase() == "DATE" || value.length == 8) {
                IcsTime.Date(LocalDate.parse(value.take(8), dateFormat))
            } else if (value.endsWith("Z") || value.endsWith("z")) {
                IcsTime.DateTime(
                    LocalDateTime.parse(value.dropLast(1).take(15), dateTimeFormat),
                    ZoneOffset.UTC
                )
            } else {
                IcsTime.DateTime(
                    LocalDateTime.parse(value.take(15), dateTimeFormat),
                    params["TZID"]?.let { zoneOf(it) },
                )
            }
        } catch (e: DateTimeParseException) {
            null
        }
    }

    /**
     * ISO 8601 durations as iCalendar uses them, e.g. PT1H30M, P1D or P2W, also negative
     */
    internal fun parseDuration(value: String): Duration? {
        val match = Regex("""([+-])?P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?""")
            .matchEntire(value.uppercase()) ?: return null
        val (sign, weeks, days, hours, minutes, seconds) = match.destructured
        val totalSeconds = (weeks.toLongOrNull() ?: 0) * 7 * 86400 +
                (days.toLongOrNull() ?: 0) * 86400 +
                (hours.toLongOrNull() ?: 0) * 3600 +
                (minutes.toLongOrNull() ?: 0) * 60 +
                (seconds.toLongOrNull() ?: 0)
        return Duration.ofSeconds(if (sign == "-") -totalSeconds else totalSeconds)
    }

    /**
     * A time zone by its ID. Besides the usual IDs (e.g. "America/New_York"), the Windows names
     * that Outlook and Exchange calendars use are known for the most common zones.
     */
    internal fun zoneOf(id: String): ZoneId? {
        val trimmed = id.trim().removePrefix("/")
        if (trimmed.isEmpty()) return null
        try {
            return ZoneId.of(trimmed)
        } catch (e: DateTimeException) {
            // Not an IANA or offset ID
        }
        val iana = windowsZones[trimmed] ?: return null
        return try {
            ZoneId.of(iana)
        } catch (e: DateTimeException) {
            null
        }
    }

    private val windowsZones = mapOf(
        "UTC" to "UTC",
        "Coordinated Universal Time" to "UTC",
        "GMT Standard Time" to "Europe/London",
        "Greenwich Standard Time" to "Atlantic/Reykjavik",
        "W. Europe Standard Time" to "Europe/Berlin",
        "Central Europe Standard Time" to "Europe/Budapest",
        "Central European Standard Time" to "Europe/Warsaw",
        "Romance Standard Time" to "Europe/Paris",
        "E. Europe Standard Time" to "Europe/Chisinau",
        "FLE Standard Time" to "Europe/Kiev",
        "GTB Standard Time" to "Europe/Bucharest",
        "Russian Standard Time" to "Europe/Moscow",
        "Turkey Standard Time" to "Europe/Istanbul",
        "Israel Standard Time" to "Asia/Jerusalem",
        "Arabian Standard Time" to "Asia/Dubai",
        "India Standard Time" to "Asia/Kolkata",
        "China Standard Time" to "Asia/Shanghai",
        "Singapore Standard Time" to "Asia/Singapore",
        "Tokyo Standard Time" to "Asia/Tokyo",
        "Korea Standard Time" to "Asia/Seoul",
        "AUS Eastern Standard Time" to "Australia/Sydney",
        "E. Australia Standard Time" to "Australia/Brisbane",
        "AUS Central Standard Time" to "Australia/Darwin",
        "W. Australia Standard Time" to "Australia/Perth",
        "New Zealand Standard Time" to "Pacific/Auckland",
        "Hawaiian Standard Time" to "Pacific/Honolulu",
        "Alaskan Standard Time" to "America/Anchorage",
        "Pacific Standard Time" to "America/Los_Angeles",
        "US Mountain Standard Time" to "America/Phoenix",
        "Mountain Standard Time" to "America/Denver",
        "Central Standard Time" to "America/Chicago",
        "Eastern Standard Time" to "America/New_York",
        "US Eastern Standard Time" to "America/Indiana/Indianapolis",
        "Atlantic Standard Time" to "America/Halifax",
        "Newfoundland Standard Time" to "America/St_Johns",
        "Canada Central Standard Time" to "America/Regina",
        "Central America Standard Time" to "America/Guatemala",
        "Central Standard Time (Mexico)" to "America/Mexico_City",
        "SA Pacific Standard Time" to "America/Bogota",
        "E. South America Standard Time" to "America/Sao_Paulo",
        "Argentina Standard Time" to "America/Buenos_Aires",
        "South Africa Standard Time" to "Africa/Johannesburg",
        "Egypt Standard Time" to "Africa/Cairo",
        "W. Central Africa Standard Time" to "Africa/Lagos",
        "E. Africa Standard Time" to "Africa/Nairobi",
    )
}
