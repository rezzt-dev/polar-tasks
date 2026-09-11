package app.polar.ui.model

import app.polar.data.entity.Reminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ReminderPresentationTest {
    private val zone = TimeZone.getTimeZone("Europe/Madrid")
    private fun date(day: Int, hour: Int, minute: Int = 0): Long = Calendar.getInstance(zone).apply {
        clear()
        set(2026, Calendar.MARCH, day, hour, minute)
    }.timeInMillis
    private fun reminder(id: Long, time: Long, completed: Boolean = false, deleted: Boolean = false) =
        Reminder(id = id, title = "Reminder $id", dateTime = time, isCompleted = completed, isDeleted = deleted)

    @Test
    fun `group upcoming reminders by local midnight across daylight saving`() {
        val now = date(28, 12)
        assertEquals(ReminderSection.TODAY, ReminderPresentation.section(reminder(1, date(28, 23, 59)), now, zone))
        assertEquals(ReminderSection.TOMORROW, ReminderPresentation.section(reminder(2, date(29, 0)), now, zone))
        assertEquals(ReminderSection.TOMORROW, ReminderPresentation.section(reminder(3, date(29, 23, 59)), now, zone))
        assertEquals(ReminderSection.UPCOMING, ReminderPresentation.section(reminder(4, date(30, 0)), now, zone))
    }

    @Test
    fun `completed reminders never appear overdue and deleted reminders stay hidden`() {
        val now = date(28, 12)
        val rows = ReminderPresentation.rows(listOf(
            reminder(1, date(27, 10), completed = true),
            reminder(2, date(27, 10), deleted = true)
        ), ReminderFilter.ALL, now, zone)
        assertEquals(ReminderRow.Header(ReminderSection.COMPLETED, 1), rows.first())
        assertEquals(listOf(1L), rows.filterIsInstance<ReminderRow.Item>().map { it.reminder.id })
    }

    @Test
    fun `filters retain only matching reminders with correct counts and date order`() {
        val now = date(28, 12)
        val reminders = listOf(reminder(1, date(28, 16)), reminder(2, date(28, 14)), reminder(3, date(27, 10), true))
        val pending = ReminderPresentation.rows(reminders, ReminderFilter.PENDING, now, zone)
        assertEquals(ReminderRow.Header(ReminderSection.TODAY, 2), pending.first())
        assertEquals(listOf(2L, 1L), pending.filterIsInstance<ReminderRow.Item>().map { it.reminder.id })
        val completed = ReminderPresentation.rows(reminders, ReminderFilter.COMPLETED, now, zone)
        assertEquals(listOf(3L), completed.filterIsInstance<ReminderRow.Item>().map { it.reminder.id })
        assertFalse(completed.filterIsInstance<ReminderRow.Header>().any { it.section == ReminderSection.OVERDUE })
    }

    @Test
    fun `clock advancement moves a reminder to overdue without a database change`() {
        val reminders = listOf(reminder(1, date(28, 12)))
        val before = ReminderPresentation.rows(reminders, ReminderFilter.ALL, date(28, 12), zone)
        val after = ReminderPresentation.rows(reminders, ReminderFilter.ALL, date(28, 12, 1), zone)
        assertEquals(ReminderSection.TODAY, (before.last() as ReminderRow.Item).section)
        assertEquals(ReminderSection.OVERDUE, (after.last() as ReminderRow.Item).section)
        assertEquals(ReminderRow.Header(ReminderSection.OVERDUE, 1), after.first())
    }
}
