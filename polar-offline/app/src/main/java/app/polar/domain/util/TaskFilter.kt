package app.polar.domain.util

import app.polar.data.entity.Task
import app.polar.util.RecurrenceUtils
import java.util.Calendar

/**
 * Chips de filtro de las pantallas de tareas. Es la única fuente de las reglas de "hoy",
 * "pendientes", "vencidas" y "recurrentes", compartida por la vista de una lista
 * ([app.polar.domain.usecase.GetFilteredTasksUseCase]) y la de inicio. Sin dependencias de
 * Android para poder probarla en JVM.
 */
data class TaskFilter(
    val todayOnly: Boolean = false,
    val pendingOnly: Boolean = false,
    val overdueOnly: Boolean = false,
    val recurrentOnly: Boolean = false
) {

    fun matches(task: Task, now: Long = System.currentTimeMillis()): Boolean {
        val due = task.dueDate
        val todayStart = RecurrenceUtils.startOfDay(now)

        if (todayOnly && (due == null || due < todayStart || due >= startOfNextDay(todayStart))) return false
        if (pendingOnly && task.completed) return false
        // Vencida: pendiente y con fecha anterior a hoy (lo que vence hoy aún está a tiempo).
        if (overdueOnly && (task.completed || due == null || due >= todayStart)) return false
        if (recurrentOnly && !RecurrenceUtils.isRecurring(task.recurrence)) return false
        return true
    }

    private fun startOfNextDay(dayStart: Long): Long = Calendar.getInstance().apply {
        timeInMillis = dayStart
        add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis
}
