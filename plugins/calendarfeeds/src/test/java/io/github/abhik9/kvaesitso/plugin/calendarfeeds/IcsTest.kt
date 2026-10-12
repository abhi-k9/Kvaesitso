package io.github.abhik9.kvaesitso.plugin.calendarfeeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class IcsTest {

    private val newYork = ZoneId.of("America/New_York")

    private fun calendar(vararg events: String, header: String = ""): String =
        "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Test//EN\r\n$header" +
                events.joinToString("") { "BEGIN:VEVENT\r\n${it.trimIndent().replace("\n", "\r\n")}\r\nEND:VEVENT\r\n" } +
                "END:VCALENDAR\r\n"

    private fun millis(dateTime: String, zone: ZoneId = newYork): Long =
        LocalDateTime.parse(dateTime).atZone(zone).toInstant().toEpochMilli()

    private fun window(from: String, to: String, zone: ZoneId = newYork) = millis(from, zone) to millis(to, zone)

    private fun expand(ics: String, from: String, to: String, zone: ZoneId = newYork): List<Occurrence> {
        val (start, end) = window(from, to, zone)
        return occurrences(IcsParser.parse(ics), start, end, zone)
    }

    // Parsing

    @Test
    fun foldedLinesAndEscapesAreRead() {
        val ics = calendar(
            """
            UID:1
            DTSTART:20251012T140000Z
            SUMMARY:Team meeting\, weekly
            DESCRIPTION:First line\nSecond line with a long text that is fol
             ded onto the next line
            LOCATION:Room 1\; Building A
            """
        )
        val event = IcsParser.parse(ics).events.single()
        assertEquals("Team meeting, weekly", event.summary)
        assertEquals("First line\nSecond line with a long text that is folded onto the next line", event.description)
        assertEquals("Room 1; Building A", event.location)
        assertEquals(IcsTime.DateTime(LocalDateTime.parse("2025-10-12T14:00:00"), ZoneOffset.UTC), event.start)
    }

    @Test
    fun calendarNameAndZoneAreRead() {
        val ics = calendar(
            "UID:1\nDTSTART:20251012T090000\nSUMMARY:Floating",
            header = "X-WR-CALNAME:Holidays\r\nX-WR-TIMEZONE:Europe/Berlin\r\n"
        )
        val calendar = IcsParser.parse(ics)
        assertEquals("Holidays", calendar.name)
        assertEquals(ZoneId.of("Europe/Berlin"), calendar.timeZone)
    }

    @Test
    fun timeZonesAreRead() {
        assertEquals(
            IcsTime.DateTime(LocalDateTime.parse("2025-10-12T09:00:00"), newYork),
            IcsParser.parseTime("20251012T090000", mapOf("TZID" to "America/New_York"))
        )
        // Outlook's Windows time zone names
        assertEquals(
            IcsTime.DateTime(LocalDateTime.parse("2025-10-12T09:00:00"), ZoneId.of("America/Chicago")),
            IcsParser.parseTime("20251012T090000", mapOf("TZID" to "Central Standard Time"))
        )
        // Unknown zones are floating
        assertEquals(
            IcsTime.DateTime(LocalDateTime.parse("2025-10-12T09:00:00"), null),
            IcsParser.parseTime("20251012T090000", mapOf("TZID" to "Nowhere Standard Time"))
        )
        assertEquals(IcsTime.Date(LocalDate.parse("2025-10-12")), IcsParser.parseTime("20251012", mapOf("VALUE" to "DATE")))
        assertNull(IcsParser.parseTime("tomorrow"))
    }

    @Test
    fun durationsAreRead() {
        assertEquals(Duration.ofMinutes(90), IcsParser.parseDuration("PT1H30M"))
        assertEquals(Duration.ofDays(1), IcsParser.parseDuration("P1D"))
        assertEquals(Duration.ofDays(14), IcsParser.parseDuration("P2W"))
        assertEquals(Duration.ofMinutes(-15), IcsParser.parseDuration("-PT15M"))
        assertNull(IcsParser.parseDuration("one hour"))
    }

    @Test
    fun alarmPropertiesDontOverrideTheEvent() {
        val ics = calendar(
            """
            UID:1
            DTSTART:20251012T140000Z
            SUMMARY:Dentist
            DESCRIPTION:Bring the card
            BEGIN:VALARM
            ACTION:DISPLAY
            DESCRIPTION:Reminder
            TRIGGER:-PT15M
            END:VALARM
            """
        )
        assertEquals("Bring the card", IcsParser.parse(ics).events.single().description)
    }

    @Test
    fun eventsWithoutStartAreSkipped() {
        val ics = calendar("UID:1\nSUMMARY:No start", "UID:2\nDTSTART;VALUE=DATE:20251012\nSUMMARY:Holiday")
        assertEquals(listOf("Holiday"), IcsParser.parse(ics).events.map { it.summary })
    }

    // Single events

    @Test
    fun timedEventInWindow() {
        val ics = calendar(
            "UID:1\nDTSTART;TZID=America/New_York:20251012T090000\nDTEND;TZID=America/New_York:20251012T100000\nSUMMARY:Breakfast"
        )
        val occurrence = expand(ics, "2025-10-12T00:00", "2025-10-13T00:00").single()
        assertEquals(millis("2025-10-12T09:00"), occurrence.start)
        assertEquals(millis("2025-10-12T10:00"), occurrence.end)
        assertEquals(false, occurrence.allDay)
    }

    @Test
    fun eventsOutsideTheWindowAreLeftOut() {
        val ics = calendar("UID:1\nDTSTART:20251012T140000Z\nDURATION:PT1H\nSUMMARY:Past")
        assertTrue(expand(ics, "2025-10-13T00:00", "2025-10-20T00:00").isEmpty())
    }

    @Test
    fun eventThatStartedBeforeTheWindowIsIncluded() {
        val ics = calendar("UID:1\nDTSTART;VALUE=DATE:20251010\nDTEND;VALUE=DATE:20251015\nSUMMARY:Vacation")
        val occurrence = expand(ics, "2025-10-12T12:00", "2025-10-13T00:00").single()
        assertEquals(true, occurrence.allDay)
        assertEquals(millis("2025-10-10T00:00"), occurrence.start)
        // Until the end of the 14th, the end date is exclusive
        assertEquals(millis("2025-10-15T00:00") - 1, occurrence.end)
    }

    @Test
    fun allDayEventWithoutEndIsOneDay() {
        val ics = calendar("UID:1\nDTSTART;VALUE=DATE:20251012\nSUMMARY:Holiday")
        val occurrence = expand(ics, "2025-10-01T00:00", "2025-11-01T00:00").single()
        assertEquals(millis("2025-10-13T00:00") - 1, occurrence.end)
    }

    @Test
    fun floatingTimesUseTheCalendarZone() {
        val ics = calendar(
            "UID:1\nDTSTART:20251012T090000\nDURATION:PT1H\nSUMMARY:Floating",
            header = "X-WR-TIMEZONE:Europe/Berlin\r\n"
        )
        val occurrence = expand(ics, "2025-10-11T00:00", "2025-10-13T00:00").single()
        assertEquals(ZonedDateTime.of(2025, 10, 12, 9, 0, 0, 0, ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli(), occurrence.start)
    }

    @Test
    fun cancelledEventsAreLeftOut() {
        val ics = calendar("UID:1\nDTSTART:20251012T140000Z\nSTATUS:CANCELLED\nSUMMARY:Cancelled")
        assertTrue(expand(ics, "2025-10-01T00:00", "2025-11-01T00:00").isEmpty())
    }

    // Recurring events

    @Test
    fun weeklyOnSeveralDays() {
        val ics = calendar(
            "UID:1\nDTSTART;TZID=America/New_York:20251006T180000\nDURATION:PT1H\nRRULE:FREQ=WEEKLY;BYDAY=MO,WE\nSUMMARY:Gym"
        )
        val starts = expand(ics, "2025-10-06T00:00", "2025-10-20T00:00").map { it.start }
        assertEquals(
            listOf("2025-10-06T18:00", "2025-10-08T18:00", "2025-10-13T18:00", "2025-10-15T18:00").map { millis(it) },
            starts
        )
    }

    @Test
    fun recurrenceKeepsTheLocalTimeAcrossDaylightSavingTime() {
        // DST in New York ends on Nov 2, 2025
        val ics = calendar(
            "UID:1\nDTSTART;TZID=America/New_York:20251027T090000\nDURATION:PT30M\nRRULE:FREQ=WEEKLY\nSUMMARY:Standup"
        )
        val starts = expand(ics, "2025-10-27T00:00", "2025-11-04T00:00").map { it.start }
        assertEquals(listOf(millis("2025-10-27T09:00"), millis("2025-11-03T09:00")), starts)
    }

    @Test
    fun countLimitsTheOccurrences() {
        val ics = calendar("UID:1\nDTSTART;VALUE=DATE:20251001\nRRULE:FREQ=DAILY;COUNT=3\nSUMMARY:Course")
        assertEquals(3, expand(ics, "2025-09-01T00:00", "2025-12-01T00:00").size)
        // Occurrences before the window still count
        assertEquals(1, expand(ics, "2025-10-03T00:00", "2025-12-01T00:00").size)
    }

    @Test
    fun untilIsInclusive() {
        val ics = calendar(
            "UID:1\nDTSTART:20251001T120000Z\nRRULE:FREQ=DAILY;UNTIL=20251003T120000Z\nSUMMARY:Daily"
        )
        assertEquals(3, expand(ics, "2025-09-01T00:00", "2025-12-01T00:00", ZoneOffset.UTC).size)
    }

    @Test
    fun excludedDatesAreLeftOut() {
        val ics = calendar(
            """
            UID:1
            DTSTART;TZID=America/New_York:20251006T180000
            RRULE:FREQ=DAILY;COUNT=5
            EXDATE;TZID=America/New_York:20251007T180000,20251009T180000
            SUMMARY:Practice
            """
        )
        val starts = expand(ics, "2025-10-01T00:00", "2025-11-01T00:00").map { it.start }
        assertEquals(listOf("2025-10-06T18:00", "2025-10-08T18:00", "2025-10-10T18:00").map { millis(it) }, starts)
    }

    @Test
    fun changedOccurrenceReplacesTheOriginal() {
        val ics = calendar(
            "UID:abc\nDTSTART;TZID=America/New_York:20251006T180000\nDURATION:PT1H\nRRULE:FREQ=WEEKLY;COUNT=3\nSUMMARY:Club",
            "UID:abc\nRECURRENCE-ID;TZID=America/New_York:20251013T180000\nDTSTART;TZID=America/New_York:20251014T190000\nDURATION:PT1H\nSUMMARY:Club (moved)",
        )
        val result = expand(ics, "2025-10-01T00:00", "2025-11-01T00:00")
        assertEquals(
            listOf(
                "Club" to millis("2025-10-06T18:00"),
                "Club (moved)" to millis("2025-10-14T19:00"),
                "Club" to millis("2025-10-20T18:00"),
            ),
            result.map { it.event.summary to it.start }
        )
    }

    @Test
    fun cancelledOccurrenceIsLeftOut() {
        val ics = calendar(
            "UID:abc\nDTSTART;VALUE=DATE:20251006\nRRULE:FREQ=WEEKLY;COUNT=3\nSUMMARY:Trash day",
            "UID:abc\nRECURRENCE-ID;VALUE=DATE:20251013\nDTSTART;VALUE=DATE:20251013\nSTATUS:CANCELLED\nSUMMARY:Trash day",
        )
        assertEquals(2, expand(ics, "2025-10-01T00:00", "2025-11-01T00:00").size)
    }

    @Test
    fun unsupportedRuleShowsTheFirstOccurrence() {
        val ics = calendar("UID:1\nDTSTART:20251012T140000Z\nRRULE:FREQ=HOURLY\nSUMMARY:Hourly")
        assertEquals(1, expand(ics, "2025-10-01T00:00", "2025-11-01T00:00").size)
    }

    // Rules

    private fun dates(rule: String, start: String, end: String): List<LocalDate> {
        return RecurrenceRule.parse(rule) { null }!!
            .occurrences(LocalDate.parse(start).atStartOfDay(), LocalDate.parse(end).atStartOfDay())
            .map { it.toLocalDate() }.toList()
    }

    @Test
    fun thanksgiving() {
        assertEquals(
            listOf("2025-11-27", "2026-11-26", "2027-11-25").map { LocalDate.parse(it) },
            dates("FREQ=YEARLY;BYMONTH=11;BYDAY=4TH", "2025-11-27", "2028-01-01")
        )
    }

    @Test
    fun lastMondayOfMay() {
        assertEquals(
            listOf("2026-05-25", "2027-05-31").map { LocalDate.parse(it) },
            dates("FREQ=YEARLY;BYMONTH=5;BYDAY=-1MO", "2026-05-25", "2028-01-01")
        )
    }

    @Test
    fun lastWeekdayOfTheMonth() {
        assertEquals(
            listOf("2025-10-31", "2025-11-28", "2025-12-31").map { LocalDate.parse(it) },
            dates("FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-1", "2025-10-31", "2026-01-01")
        )
    }

    @Test
    fun monthlyOnTheThirtyFirstSkipsShortMonths() {
        assertEquals(
            listOf("2025-10-31", "2025-12-31", "2026-01-31").map { LocalDate.parse(it) },
            dates("FREQ=MONTHLY", "2025-10-31", "2026-02-15")
        )
    }

    @Test
    fun leapDayOnlyInLeapYears() {
        assertEquals(
            listOf("2024-02-29", "2028-02-29").map { LocalDate.parse(it) },
            dates("FREQ=YEARLY", "2024-02-29", "2029-01-01")
        )
    }

    @Test
    fun everyOtherWeek() {
        assertEquals(
            listOf("2025-10-07", "2025-10-21", "2025-11-04").map { LocalDate.parse(it) },
            dates("FREQ=WEEKLY;INTERVAL=2;BYDAY=TU", "2025-10-07", "2025-11-10")
        )
    }

    @Test
    fun ruleThatNeverMatchesEnds() {
        assertTrue(dates("FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=30", "2025-01-01", "2100-01-01").isEmpty())
    }

    @Test
    fun unsupportedRulesAreRejected() {
        assertNull(RecurrenceRule.parse("FREQ=HOURLY") { null })
        assertNull(RecurrenceRule.parse("FREQ=DAILY;BYHOUR=9,17") { null })
        assertNull(RecurrenceRule.parse("FREQ=WEEKLY;BYDAY=XX") { null })
    }
}
