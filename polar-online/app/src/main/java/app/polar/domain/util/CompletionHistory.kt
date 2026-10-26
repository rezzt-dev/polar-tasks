package app.polar.domain.util

import app.polar.util.RecurrenceUtils
import java.util.Calendar

/**
 * Tareas completadas por día (según su fecha de vencimiento), agrupadas en memoria a partir de una
 * sola consulta en lugar de una consulta por día. Sin dependencias de Android para poder probarla.
 */
class CompletionHistory(completedDueDates: List<Long>) {

    private val countsByDay: Map<Long, Int> =
        completedDueDates.groupingBy { RecurrenceUtils.startOfDay(it) }.eachCount()

    /** Completadas el día que contiene [time]. */
    fun countOn(time: Long): Int = countsByDay[RecurrenceUtils.startOfDay(time)] ?: 0

    /** Inicio de cada uno de los [days] días que terminan en [today] (incluido), del más antiguo al más reciente. */
    fun lastDays(today: Long, days: Int): List<Long> {
        val calendar = dayCalendar(today).apply { add(Calendar.DAY_OF_YEAR, -(days - 1)) }
        return List(days) {
            calendar.timeInMillis.also { calendar.add(Calendar.DAY_OF_YEAR, 1) }
        }
    }

    /**
     * Días seguidos con alguna tarea completada hasta [today]. Que hoy aún no haya ninguna no
     * rompe la racha: se cuenta desde ayer.
     */
    fun streak(today: Long, maxDays: Int = 365): Int {
        val calendar = dayCalendar(today)
        if (countOn(calendar.timeInMillis) == 0) calendar.add(Calendar.DAY_OF_YEAR, -1)

        var streak = 0
        while (streak < maxDays && countOn(calendar.timeInMillis) > 0) {
            streak++
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    /**
     * Día de la semana ([Calendar.DAY_OF_WEEK]) con más tareas completadas en los [days] días
     * anteriores a [today], o `null` si no se completó ninguna.
     */
    fun bestDayOfWeek(today: Long, days: Int = 30): Int? {
        val calendar = dayCalendar(today)
        val totals = mutableMapOf<Int, Int>()
        repeat(days) {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            totals[dayOfWeek] = (totals[dayOfWeek] ?: 0) + countOn(calendar.timeInMillis)
        }
        return totals.filterValues { it > 0 }.maxByOrNull { it.value }?.key
    }

    private fun dayCalendar(time: Long): Calendar =
        Calendar.getInstance().apply { timeInMillis = RecurrenceUtils.startOfDay(time) }
}
