package app.polar.util

import app.polar.data.entity.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class TaskAlertTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int = 7, minute: Int = 30): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    private fun task(
        due: Long? = at(2026, 1, 14, 10, 0),
        offset: Int = TaskAlert.AT_DUE_TIME,
        snoozedUntil: Long? = null,
        completed: Boolean = false,
        deleted: Boolean = false
    ) = Task(
        id = 1, listId = 1, title = "t", dueDate = due, reminderOffset = offset,
        snoozedUntil = snoozedUntil, completed = completed, isDeleted = deleted
    )

    @Test
    fun triggerAt_atDueTimeByDefault() {
        assertEquals(at(2026, 1, 14, 10, 0), TaskAlert.triggerAt(task()))
    }

    @Test
    fun triggerAt_relativeToDueDate() {
        assertEquals(at(2026, 1, 14, 9, 45), TaskAlert.triggerAt(task(offset = 15)))
        assertEquals(at(2026, 1, 13, 10, 0), TaskAlert.triggerAt(task(offset = 24 * 60)))
        assertEquals(at(2026, 1, 7, 10, 0), TaskAlert.triggerAt(task(offset = 7 * 24 * 60)))
    }

    @Test
    fun triggerAt_noAlertWithoutDateOrWhenOff() {
        assertNull(TaskAlert.triggerAt(task(due = null)))
        assertNull(TaskAlert.triggerAt(task(offset = TaskAlert.OFF)))
    }

    @Test
    fun triggerAt_noAlertWhenCompletedOrTrashed() {
        assertNull(TaskAlert.triggerAt(task(completed = true)))
        assertNull(TaskAlert.triggerAt(task(deleted = true)))
        assertNull(TaskAlert.triggerAt(task(snoozedUntil = at(2026, 1, 14, 11, 0), completed = true)))
    }

    @Test
    fun triggerAt_snoozeReplacesTheAlertButNotTheDueDate() {
        val snoozed = task(offset = 24 * 60, snoozedUntil = at(2026, 1, 13, 11, 0))
        assertEquals(at(2026, 1, 13, 11, 0), TaskAlert.triggerAt(snoozed))
        assertEquals(at(2026, 1, 14, 10, 0), snoozed.dueDate)
        // Pospuesto aunque la tarea no avisara: el usuario pidió que se lo recordaran
        assertEquals(at(2026, 1, 14, 11, 0), TaskAlert.triggerAt(task(offset = TaskAlert.OFF, snoozedUntil = at(2026, 1, 14, 11, 0))))
    }

    @Test
    fun edited_keepsSnoozeOnlyIfDateAndOffsetAreUnchanged() {
        val original = task(offset = 60, snoozedUntil = at(2026, 1, 14, 9, 30))
        assertEquals(original.snoozedUntil, TaskAlert.edited(original, original.copy(title = "otro")).snoozedUntil)
        assertNull(TaskAlert.edited(original, original.copy(dueDate = at(2026, 1, 15, 10, 0))).snoozedUntil)
        assertNull(TaskAlert.edited(original, original.copy(reminderOffset = 15)).snoozedUntil)
    }

    @Test
    fun complete_clearsSnooze() {
        val done = RecurrenceUtils.complete(task(snoozedUntil = at(2026, 1, 14, 11, 0)), at(2026, 1, 14, 12, 0))
        assertNull(done.snoozedUntil)
        val recurring = task(snoozedUntil = at(2026, 1, 14, 11, 0)).copy(recurrence = RecurrenceUtils.DAILY)
        assertNull(RecurrenceUtils.complete(recurring, at(2026, 1, 14, 12, 0)).snoozedUntil)
    }
}
