package io.github.abhik9.kvaesitso.plugin.calendarfeeds

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * A recurrence rule (RRULE) with the parts that calendar feeds commonly use: FREQ (daily to
 * yearly), INTERVAL, COUNT, UNTIL, BYDAY (with ordinals like 2MO or -1FR), BYMONTHDAY, BYMONTH,
 * BYSETPOS and WKST. Rules that repeat more often than daily, or by hour, minute, week number or
 * day of the year aren't supported.
 */
internal data class RecurrenceRule(
    val frequency: Frequency,
    val interval: Int = 1,
    val count: Int? = null,
    /**
     * The last possible occurrence, inclusive. For rules of timed events in local time of the
     * event's zone.
     */
    val until: LocalDateTime? = null,
    val byDay: List<WeekdayNum> = emptyList(),
    val byMonthDay: List<Int> = emptyList(),
    val byMonth: List<Int> = emptyList(),
    val bySetPos: List<Int> = emptyList(),
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
) {
    enum class Frequency { Daily, Weekly, Monthly, Yearly }

    /**
     * A day of the week, e.g. MO, or with an ordinal, e.g. 2MO (the second Monday of the month or
     * year) or -1FR (the last Friday)
     */
    data class WeekdayNum(val ordinal: Int?, val day: DayOfWeek)

    companion object {
        /**
         * @param untilToLocal turns an UNTIL value into the local time of the event
         * @return null if the rule can't be read or isn't supported
         */
        fun parse(rule: String, untilToLocal: (IcsTime) -> LocalDateTime?): RecurrenceRule? {
            val parts = rule.removePrefix("RRULE:").split(';').mapNotNull {
                val equals = it.indexOf('=')
                if (equals <= 0) null
                else it.substring(0, equals).trim().uppercase() to it.substring(equals + 1).trim()
            }.toMap()

            val frequency = when (parts["FREQ"]?.uppercase()) {
                "DAILY" -> Frequency.Daily
                "WEEKLY" -> Frequency.Weekly
                "MONTHLY" -> Frequency.Monthly
                "YEARLY" -> Frequency.Yearly
                else -> return null
            }
            // Rules that this implementation would get wrong
            if (listOf("BYHOUR", "BYMINUTE", "BYSECOND", "BYWEEKNO", "BYYEARDAY").any { it in parts }) {
                return null
            }
            val interval = parts["INTERVAL"]?.toIntOrNull()?.takeIf { it > 0 } ?: 1
            val count = parts["COUNT"]?.toIntOrNull()?.takeIf { it > 0 }
            val until = parts["UNTIL"]?.let { IcsParser.parseTime(it) }?.let(untilToLocal)
            val byDay = parts["BYDAY"]?.split(',')?.map {
                parseWeekdayNum(it.trim()) ?: return null
            } ?: emptyList()
            val byMonthDay = parts["BYMONTHDAY"]?.split(',')?.map {
                it.trim().toIntOrNull()?.takeIf { day -> day != 0 && day in -31..31 } ?: return null
            } ?: emptyList()
            val byMonth = parts["BYMONTH"]?.split(',')?.map {
                it.trim().toIntOrNull()?.takeIf { month -> month in 1..12 } ?: return null
            } ?: emptyList()
            val bySetPos = parts["BYSETPOS"]?.split(',')?.map {
                it.trim().toIntOrNull()?.takeIf { pos -> pos != 0 } ?: return null
            } ?: emptyList()
            val weekStart = parts["WKST"]?.let { parseDay(it.trim()) } ?: DayOfWeek.MONDAY
            return RecurrenceRule(
                frequency = frequency,
                interval = interval,
                count = count,
                until = until,
                byDay = byDay,
                byMonthDay = byMonthDay,
                byMonth = byMonth,
                bySetPos = bySetPos,
                weekStart = weekStart,
            )
        }

        private fun parseWeekdayNum(value: String): WeekdayNum? {
            if (value.length < 2) return null
            val day = parseDay(value.takeLast(2)) ?: return null
            val ordinal = value.dropLast(2)
            if (ordinal.isEmpty()) return WeekdayNum(null, day)
            val number = ordinal.removePrefix("+").toIntOrNull() ?: return null
            if (number == 0 || number !in -53..53) return null
            return WeekdayNum(number, day)
        }

        private fun parseDay(value: String): DayOfWeek? {
            return when (value.uppercase()) {
                "MO" -> DayOfWeek.MONDAY
                "TU" -> DayOfWeek.TUESDAY
                "WE" -> DayOfWeek.WEDNESDAY
                "TH" -> DayOfWeek.THURSDAY
                "FR" -> DayOfWeek.FRIDAY
                "SA" -> DayOfWeek.SATURDAY
                "SU" -> DayOfWeek.SUNDAY
                else -> null
            }
        }
    }

    /**
     * The start times of the occurrences, beginning with [start] (the event's start), in order,
     * until [end] (exclusive), the rule's COUNT or UNTIL, whichever comes first.
     */
    fun occurrences(start: LocalDateTime, end: LocalDateTime): Sequence<LocalDateTime> = sequence {
        var produced = 0
        val time = start.toLocalTime()
        // Rules that never match (e.g. BYMONTHDAY=30 with BYMONTH=2) must not loop forever
        var emptyPeriods = 0
        var period = 0L
        while (emptyPeriods < 1000) {
            val dates = datesOfPeriod(start.toLocalDate(), period)
            period++
            if (dates.isEmpty()) {
                emptyPeriods++
                continue
            }
            emptyPeriods = 0
            for (date in dates) {
                val occurrence = date.atTime(time)
                if (occurrence < start) continue
                if (occurrence >= end) return@sequence
                if (until != null && occurrence > until) return@sequence
                if (count != null && produced >= count) return@sequence
                produced++
                yield(occurrence)
            }
        }
    }

    /**
     * The dates of the [index]th period (day, week, month or year, times the interval) after the
     * one of [start], sorted
     */
    private fun datesOfPeriod(start: LocalDate, index: Long): List<LocalDate> {
        val step = index * interval
        val candidates: List<LocalDate> = when (frequency) {
            Frequency.Daily -> {
                val date = start.plusDays(step)
                if (matchesFilters(date)) listOf(date) else emptyList()
            }

            Frequency.Weekly -> {
                val weekStartDate = start.with(TemporalAdjusters.previousOrSame(weekStart))
                    .plusWeeks(step)
                val days = byDay.map { it.day }.ifEmpty { listOf(start.dayOfWeek) }
                (0L until 7L).map { weekStartDate.plusDays(it) }
                    .filter { it.dayOfWeek in days }
                    .filter { byMonth.isEmpty() || it.monthValue in byMonth }
            }

            Frequency.Monthly -> {
                val month = start.withDayOfMonth(1).plusMonths(step)
                if (byMonth.isNotEmpty() && month.monthValue !in byMonth) emptyList()
                else datesInMonth(month, start)
            }

            Frequency.Yearly -> {
                val year = start.withDayOfYear(1).plusYears(step)
                datesInYear(year, start)
            }
        }
        return applySetPos(candidates.distinct().sorted())
    }

    private fun matchesFilters(date: LocalDate): Boolean {
        if (byMonth.isNotEmpty() && date.monthValue !in byMonth) return false
        if (byMonthDay.isNotEmpty() && !byMonthDay.any { monthDay(date.withDayOfMonth(1), it) == date }) {
            return false
        }
        if (byDay.isNotEmpty() && byDay.none { it.day == date.dayOfWeek }) return false
        return true
    }

    private fun datesInMonth(firstOfMonth: LocalDate, start: LocalDate): List<LocalDate> {
        val byMonthDayDates = byMonthDay.mapNotNull { monthDay(firstOfMonth, it) }
        val byDayDates = byDay.flatMap { weekdaysInMonth(firstOfMonth, it) }
        return when {
            byMonthDay.isNotEmpty() && byDay.isNotEmpty() -> byMonthDayDates.filter { it in byDayDates }
            byMonthDay.isNotEmpty() -> byMonthDayDates
            byDay.isNotEmpty() -> byDayDates
            // The same day as the start; months without it are skipped
            else -> listOfNotNull(monthDay(firstOfMonth, start.dayOfMonth))
        }
    }

    private fun datesInYear(firstOfYear: LocalDate, start: LocalDate): List<LocalDate> {
        if (byMonth.isEmpty() && byMonthDay.isEmpty() && byDay.isNotEmpty()) {
            // E.g. the 20th Monday of the year
            return byDay.flatMap { weekdaysInYear(firstOfYear, it) }
        }
        val months = byMonth.ifEmpty { listOf(start.monthValue) }
        return months.flatMap { month ->
            val firstOfMonth = firstOfYear.withMonth(month)
            if (byMonthDay.isEmpty() && byDay.isEmpty()) {
                // The same day as the start, e.g. Feb 29 only in leap years
                listOfNotNull(monthDay(firstOfMonth, start.dayOfMonth))
            } else {
                datesInMonth(firstOfMonth, start)
            }
        }
    }

    /**
     * The [day]th day of the month (negative: counted from the end), null if the month doesn't
     * have it
     */
    private fun monthDay(firstOfMonth: LocalDate, day: Int): LocalDate? {
        val length = firstOfMonth.lengthOfMonth()
        val dayOfMonth = if (day > 0) day else length + day + 1
        if (dayOfMonth !in 1..length) return null
        return firstOfMonth.withDayOfMonth(dayOfMonth)
    }

    private fun weekdaysInMonth(firstOfMonth: LocalDate, weekday: WeekdayNum): List<LocalDate> {
        val all = generateSequence(firstOfMonth.with(TemporalAdjusters.nextOrSame(weekday.day))) {
            it.plusWeeks(1)
        }.takeWhile { it.month == firstOfMonth.month }.toList()
        return pick(all, weekday.ordinal)
    }

    private fun weekdaysInYear(firstOfYear: LocalDate, weekday: WeekdayNum): List<LocalDate> {
        val all = generateSequence(firstOfYear.with(TemporalAdjusters.nextOrSame(weekday.day))) {
            it.plusWeeks(1)
        }.takeWhile { it.year == firstOfYear.year }.toList()
        return pick(all, weekday.ordinal)
    }

    private fun pick(dates: List<LocalDate>, ordinal: Int?): List<LocalDate> {
        if (ordinal == null) return dates
        val index = if (ordinal > 0) ordinal - 1 else dates.size + ordinal
        return listOfNotNull(dates.getOrNull(index))
    }

    private fun applySetPos(dates: List<LocalDate>): List<LocalDate> {
        if (bySetPos.isEmpty() || frequency == Frequency.Daily) return dates
        return bySetPos.mapNotNull {
            dates.getOrNull(if (it > 0) it - 1 else dates.size + it)
        }.distinct().sorted()
    }
}

/**
 * The number of days between two dates, e.g. for the length of all-day events
 */
internal fun daysBetween(start: LocalDate, end: LocalDate): Long = ChronoUnit.DAYS.between(start, end)
