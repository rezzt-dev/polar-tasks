package app.polar.ui.model

import app.polar.data.entity.Reminder
import java.util.Calendar
import java.util.TimeZone

enum class ReminderFilter { ALL, PENDING, COMPLETED }
enum class ReminderSection { OVERDUE, TODAY, TOMORROW, UPCOMING, COMPLETED }

sealed class ReminderRow {
    data class Header(val section: ReminderSection, val count: Int) : ReminderRow()
    data class Item(val reminder: Reminder, val section: ReminderSection? = null) : ReminderRow()
}

/** Agrupa usando días locales (también en cambios de horario de verano). */
object ReminderPresentation {
    fun section(reminder: Reminder, now: Long, zone: TimeZone = TimeZone.getDefault()): ReminderSection {
        if (reminder.isCompleted) return ReminderSection.COMPLETED
        if (reminder.dateTime < now) return ReminderSection.OVERDUE
        val calendar = Calendar.getInstance(zone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        if (reminder.dateTime < calendar.timeInMillis) return ReminderSection.TODAY
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        return if (reminder.dateTime < calendar.timeInMillis) ReminderSection.TOMORROW else ReminderSection.UPCOMING
    }

    fun rows(
        reminders: List<Reminder>,
        filter: ReminderFilter,
        now: Long,
        zone: TimeZone = TimeZone.getDefault()
    ): List<ReminderRow> {
        val groups = reminders.filter { reminder ->
            !reminder.isDeleted && when (filter) {
                ReminderFilter.ALL -> true
                ReminderFilter.PENDING -> !reminder.isCompleted
                ReminderFilter.COMPLETED -> reminder.isCompleted
            }
        }.groupBy { section(it, now, zone) }
        return ReminderSection.entries.flatMap { section ->
            val items = groups[section].orEmpty()
            if (items.isEmpty()) emptyList() else {
                val sorted = if (section == ReminderSection.COMPLETED) items.sortedByDescending { it.dateTime }
                    else items.sortedBy { it.dateTime }
                listOf(ReminderRow.Header(section, items.size)) + sorted.map { ReminderRow.Item(it, section) }
            }
        }
    }
}
