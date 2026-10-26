package app.polar.domain.util

import app.polar.data.entity.Task
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TaskFilterTest {

    // 2026-03-10 15:00 (hora local)
    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.MARCH, 10, 15, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun at(day: Int, hour: Int): Long = Calendar.getInstance().apply {
        set(2026, Calendar.MARCH, day, hour, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun task(dueDate: Long? = null, completed: Boolean = false, recurrence: String = "NONE") =
        Task(listId = 1, title = "t", dueDate = dueDate, completed = completed, recurrence = recurrence)

    @Test
    fun `no filter matches every task`() {
        assertTrue(TaskFilter().matches(task(), now))
        assertTrue(TaskFilter().matches(task(at(1, 9), completed = true), now))
    }

    @Test
    fun `today matches any time of the current day only`() {
        val filter = TaskFilter(todayOnly = true)
        assertTrue(filter.matches(task(at(10, 0)), now))
        assertTrue(filter.matches(task(at(10, 23)), now))
        assertFalse(filter.matches(task(at(11, 0)), now))
        assertFalse(filter.matches(task(at(9, 23)), now))
        assertFalse(filter.matches(task(null), now))
    }

    @Test
    fun `overdue is a pending task due before today`() {
        val filter = TaskFilter(overdueOnly = true)
        assertTrue(filter.matches(task(at(9, 23)), now))
        // Lo que vence hoy, aunque la hora ya haya pasado, todavía está a tiempo.
        assertFalse(filter.matches(task(at(10, 8)), now))
        assertFalse(filter.matches(task(at(9, 23), completed = true), now))
        assertFalse(filter.matches(task(null), now))
    }

    @Test
    fun `pending excludes completed tasks`() {
        val filter = TaskFilter(pendingOnly = true)
        assertTrue(filter.matches(task(), now))
        assertFalse(filter.matches(task(completed = true), now))
    }

    @Test
    fun `recurrent matches only known repetitions`() {
        val filter = TaskFilter(recurrentOnly = true)
        assertTrue(filter.matches(task(at(10, 9), recurrence = "DAILY"), now))
        assertTrue(filter.matches(task(at(10, 9), recurrence = "MON_WED"), now))
        assertFalse(filter.matches(task(at(10, 9), recurrence = "NONE"), now))
    }

    @Test
    fun `filters combine with and`() {
        val filter = TaskFilter(todayOnly = true, pendingOnly = true)
        assertTrue(filter.matches(task(at(10, 18)), now))
        assertFalse(filter.matches(task(at(10, 18), completed = true), now))
    }
}
