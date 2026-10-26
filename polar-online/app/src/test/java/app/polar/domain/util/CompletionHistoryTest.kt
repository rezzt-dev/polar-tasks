package app.polar.domain.util

import app.polar.util.RecurrenceUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class CompletionHistoryTest {

    // Martes 2026-03-10 15:00 (hora local)
    private val today = day(10, 15)

    private fun day(dayOfMonth: Int, hour: Int = 12): Long = Calendar.getInstance().apply {
        set(2026, Calendar.MARCH, dayOfMonth, hour, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun `counts completions per calendar day`() {
        val history = CompletionHistory(listOf(day(10, 0), day(10, 23), day(9, 23)))

        assertEquals(2, history.countOn(today))
        assertEquals(1, history.countOn(day(9)))
        assertEquals(0, history.countOn(day(8)))
    }

    @Test
    fun `lastDays ends today and goes oldest first`() {
        val days = CompletionHistory(emptyList()).lastDays(today, 3)

        assertEquals(
            listOf(day(8), day(9), day(10)).map(RecurrenceUtils::startOfDay),
            days
        )
    }

    @Test
    fun `streak counts consecutive days up to today`() {
        val history = CompletionHistory(listOf(day(10), day(9), day(8), day(6)))

        assertEquals(3, history.streak(today))
    }

    @Test
    fun `an empty today does not break the streak`() {
        val history = CompletionHistory(listOf(day(9), day(8)))

        assertEquals(2, history.streak(today))
    }

    @Test
    fun `no completions yesterday or today means no streak`() {
        val history = CompletionHistory(listOf(day(8)))

        assertEquals(0, history.streak(today))
    }

    @Test
    fun `best day of week ignores today and picks the busiest weekday`() {
        // 2 lunes (9 y 2 de marzo) frente a 1 domingo (8) y lo de hoy, que no cuenta.
        val history = CompletionHistory(listOf(day(9), day(2), day(8), day(10), day(10), day(10)))

        assertEquals(Calendar.MONDAY, history.bestDayOfWeek(today))
    }

    @Test
    fun `best day of week is null without completions`() {
        assertNull(CompletionHistory(listOf(day(10))).bestDayOfWeek(today))
    }
}
