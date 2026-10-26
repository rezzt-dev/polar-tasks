package app.polar.util

import app.polar.data.entity.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RecurrenceUtilsTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int = 7, minute: Int = 30): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    private fun task(
        due: Long?,
        recurrence: String = RecurrenceUtils.DAILY,
        completed: Boolean = true,
        deleted: Boolean = false
    ) = Task(
        id = 1, listId = 1, title = "t", completed = completed, dueDate = due,
        recurrence = recurrence, isDeleted = deleted, createdAt = 1L
    )

    @Test
    fun nextOccurrence_perRule() {
        assertEquals(at(2026, 1, 15), RecurrenceUtils.nextOccurrence(at(2026, 1, 14), "DAILY"))
        assertEquals(at(2026, 1, 21), RecurrenceUtils.nextOccurrence(at(2026, 1, 14), "WEEKLY"))
        assertEquals(at(2026, 2, 14), RecurrenceUtils.nextOccurrence(at(2026, 1, 14), "MONTHLY"))
        // 2026-01-14 es miércoles -> lunes 19; lunes 19 -> miércoles 21
        assertEquals(at(2026, 1, 19), RecurrenceUtils.nextOccurrence(at(2026, 1, 14), "MON_WED"))
        assertEquals(at(2026, 1, 21), RecurrenceUtils.nextOccurrence(at(2026, 1, 19), "MON_WED"))
        assertEquals(at(2026, 2, 1), RecurrenceUtils.nextOccurrence(at(2026, 1, 31), "FIRST_DAY_MONTH"))
        assertEquals(at(2026, 3, 1), RecurrenceUtils.nextOccurrence(at(2026, 2, 1), "FIRST_DAY_MONTH"))
    }

    @Test
    fun nextOccurrence_unknownRuleReturnsSameInstant() {
        assertEquals(at(2026, 1, 14), RecurrenceUtils.nextOccurrence(at(2026, 1, 14), "NONE"))
        assertFalse(RecurrenceUtils.isRecurring("NONE"))
        assertTrue(RecurrenceUtils.isRecurring("MON_WED"))
    }

    @Test
    fun complete_nonRecurringKeepsDate() {
        val t = task(at(2026, 1, 10), recurrence = "NONE", completed = false)
        val done = RecurrenceUtils.complete(t, at(2026, 1, 14))
        assertTrue(done.completed)
        assertEquals(t.dueDate, done.dueDate)
    }

    @Test
    fun complete_lateDailyJumpsToCurrentOccurrence() {
        val done = RecurrenceUtils.complete(task(at(2026, 1, 10), completed = false), at(2026, 1, 14, 10, 0))
        assertEquals(at(2026, 1, 14), done.dueDate)
    }

    @Test
    fun complete_earlyKeepsFutureDate() {
        val done = RecurrenceUtils.complete(task(at(2026, 1, 20), completed = false), at(2026, 1, 14))
        assertEquals(at(2026, 1, 20), done.dueDate)
    }

    @Test
    fun complete_lateWeeklyKeepsWeekdayAnchor() {
        // vencía el jueves 8; se completa el miércoles 14 -> la ocurrencia vigente sigue siendo la del 8
        val done = RecurrenceUtils.complete(task(at(2026, 1, 8), "WEEKLY", completed = false), at(2026, 1, 14))
        assertEquals(at(2026, 1, 8), done.dueDate)
    }

    @Test
    fun startNextOccurrence_notYet() {
        // completada el 14: no reaparece el mismo día
        assertNull(RecurrenceUtils.startNextOccurrence(task(at(2026, 1, 14)), at(2026, 1, 14, 23, 59)))
    }

    @Test
    fun startNextOccurrence_atMidnightOfNextDay() {
        val reopened = RecurrenceUtils.startNextOccurrence(task(at(2026, 1, 14)), at(2026, 1, 15, 0, 1))
        assertNotNull(reopened)
        assertFalse(reopened!!.completed)
        assertEquals(at(2026, 1, 15), reopened.dueDate)
        assertEquals(at(2026, 1, 15, 0, 1), reopened.createdAt)
    }

    @Test
    fun startNextOccurrence_catchesUpAfterMissedDays() {
        val reopened = RecurrenceUtils.startNextOccurrence(task(at(2026, 1, 14)), at(2026, 1, 20, 9, 0))
        assertEquals(at(2026, 1, 20), reopened!!.dueDate)
    }

    @Test
    fun startNextOccurrence_ignoresInvalidTasks() {
        val now = at(2026, 2, 1)
        assertNull(RecurrenceUtils.startNextOccurrence(task(at(2026, 1, 14), completed = false), now))
        assertNull(RecurrenceUtils.startNextOccurrence(task(at(2026, 1, 14), deleted = true), now))
        assertNull(RecurrenceUtils.startNextOccurrence(task(at(2026, 1, 14), recurrence = "NONE"), now))
        assertNull(RecurrenceUtils.startNextOccurrence(task(null), now))
    }
}
