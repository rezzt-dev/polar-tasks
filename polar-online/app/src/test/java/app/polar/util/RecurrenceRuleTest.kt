package app.polar.util

import app.polar.util.RecurrenceRule.Frequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RecurrenceRuleTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int = 7, minute: Int = 30): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    @Test
    fun encode_equivalentRulesKeepLegacyValues() {
        assertEquals("DAILY", RecurrenceRule(Frequency.DAILY).encode())
        assertEquals("WEEKLY", RecurrenceRule(Frequency.WEEKLY).encode())
        assertEquals("MONTHLY", RecurrenceRule(Frequency.MONTHLY).encode())
        assertEquals("MON_WED", RecurrenceRule(Frequency.WEEKLY, weekdays = setOf(Calendar.WEDNESDAY, Calendar.MONDAY)).encode())
        assertEquals("FIRST_DAY_MONTH", RecurrenceRule(Frequency.MONTHLY, monthDay = 1).encode())
    }

    @Test
    fun encode_customRulesUseRrule() {
        assertEquals("RRULE:FREQ=WEEKLY;INTERVAL=2", RecurrenceRule(Frequency.WEEKLY, interval = 2).encode())
        assertEquals(
            "RRULE:FREQ=WEEKLY;BYDAY=MO,FR,SU",
            RecurrenceRule(Frequency.WEEKLY, weekdays = setOf(Calendar.SUNDAY, Calendar.FRIDAY, Calendar.MONDAY)).encode()
        )
        assertEquals("RRULE:FREQ=MONTHLY;BYMONTHDAY=-1", RecurrenceRule(Frequency.MONTHLY, monthDay = -1).encode())
        assertEquals("RRULE:FREQ=YEARLY", RecurrenceRule(Frequency.YEARLY).encode())
        assertEquals(
            "RRULE:FREQ=DAILY;INTERVAL=3;X-FROM=COMPLETION",
            RecurrenceRule(Frequency.DAILY, interval = 3, fromCompletion = true).encode()
        )
    }

    @Test
    fun encode_dropsFieldsThatDoNotApply() {
        // Días concretos solo en semanales y no al contar desde que se completa
        assertEquals("DAILY", RecurrenceRule(Frequency.DAILY, weekdays = setOf(Calendar.MONDAY)).encode())
        assertEquals(
            "RRULE:FREQ=WEEKLY;X-FROM=COMPLETION",
            RecurrenceRule(Frequency.WEEKLY, weekdays = setOf(Calendar.MONDAY), fromCompletion = true).encode()
        )
        assertEquals("WEEKLY", RecurrenceRule(Frequency.WEEKLY, monthDay = 1).encode())
    }

    @Test
    fun parse_roundTrips() {
        listOf(
            RecurrenceRule(Frequency.DAILY),
            RecurrenceRule(Frequency.WEEKLY, interval = 2, weekdays = setOf(Calendar.TUESDAY, Calendar.THURSDAY)),
            RecurrenceRule(Frequency.MONTHLY, interval = 3, monthDay = 15),
            RecurrenceRule(Frequency.MONTHLY, monthDay = RecurrenceRule.LAST_DAY_OF_MONTH),
            RecurrenceRule(Frequency.YEARLY, interval = 2),
            RecurrenceRule(Frequency.MONTHLY, interval = 6, fromCompletion = true)
        ).forEach { rule -> assertEquals(rule, RecurrenceRule.parse(rule.encode())) }
    }

    @Test
    fun parse_rejectsUnknownOrInvalidValues() {
        assertNull(RecurrenceRule.parse("NONE"))
        assertNull(RecurrenceRule.parse(""))
        assertNull(RecurrenceRule.parse(null))
        assertNull(RecurrenceRule.parse("HOURLY"))
        assertNull(RecurrenceRule.parse("RRULE:FREQ=HOURLY"))
        assertNull(RecurrenceRule.parse("RRULE:FREQ=DAILY;INTERVAL=0"))
        assertNull(RecurrenceRule.parse("RRULE:FREQ=DAILY;INTERVAL=abc"))
        assertNull(RecurrenceRule.parse("RRULE:FREQ=WEEKLY;BYDAY=XX"))
        assertNull(RecurrenceRule.parse("RRULE:FREQ=MONTHLY;BYMONTHDAY=40"))
    }

    @Test
    fun parse_ignoresUnknownKeysAndCase() {
        assertEquals(
            RecurrenceRule(Frequency.WEEKLY, interval = 2),
            RecurrenceRule.parse("RRULE:freq=weekly;interval=2;WKST=MO")
        )
    }

    @Test
    fun next_everyTwoWeeks() {
        assertEquals(at(2026, 1, 28), RecurrenceRule(Frequency.WEEKLY, interval = 2).next(at(2026, 1, 14)))
    }

    @Test
    fun next_everyThreeDaysAndYearly() {
        assertEquals(at(2026, 1, 17), RecurrenceRule(Frequency.DAILY, interval = 3).next(at(2026, 1, 14)))
        assertEquals(at(2027, 1, 14), RecurrenceRule(Frequency.YEARLY).next(at(2026, 1, 14)))
    }

    @Test
    fun next_weekdaysWithinWeekThenSkipsInterval() {
        // martes y jueves cada 2 semanas; 2026-01-13 es martes
        val rule = RecurrenceRule(Frequency.WEEKLY, interval = 2, weekdays = setOf(Calendar.TUESDAY, Calendar.THURSDAY))
        assertEquals(at(2026, 1, 15), rule.next(at(2026, 1, 13)))
        assertEquals(at(2026, 1, 27), rule.next(at(2026, 1, 15)))
    }

    @Test
    fun next_weeksStartOnMonday() {
        // domingo y lunes cada 2 semanas: el domingo cierra la semana del lunes anterior
        val rule = RecurrenceRule(Frequency.WEEKLY, interval = 2, weekdays = setOf(Calendar.SUNDAY, Calendar.MONDAY))
        assertEquals(at(2026, 1, 18), rule.next(at(2026, 1, 12)))
        assertEquals(at(2026, 1, 26), rule.next(at(2026, 1, 18)))
    }

    @Test
    fun next_monthDayClampsToShortMonths() {
        val last = RecurrenceRule(Frequency.MONTHLY, monthDay = RecurrenceRule.LAST_DAY_OF_MONTH)
        assertEquals(at(2026, 1, 31), last.next(at(2026, 1, 5)))
        assertEquals(at(2026, 2, 28), last.next(at(2026, 1, 31)))
        assertEquals(at(2026, 3, 31), last.next(at(2026, 2, 28)))

        val day31 = RecurrenceRule(Frequency.MONTHLY, interval = 2, monthDay = 31)
        assertEquals(at(2026, 3, 31), day31.next(at(2026, 1, 31)))
        assertEquals(at(2026, 4, 30), RecurrenceRule(Frequency.MONTHLY, monthDay = 31).next(at(2026, 3, 31)))
    }

    @Test
    fun matches_onlyAllowedDays() {
        val rule = RecurrenceRule(Frequency.WEEKLY, weekdays = setOf(Calendar.MONDAY))
        assertTrue(rule.matches(at(2026, 1, 12)))
        assertFalse(rule.matches(at(2026, 1, 13)))
        assertTrue(RecurrenceRule(Frequency.MONTHLY, monthDay = -1).matches(at(2026, 2, 28)))
        assertTrue(RecurrenceRule(Frequency.DAILY, interval = 4).matches(at(2026, 2, 28)))
    }
}
