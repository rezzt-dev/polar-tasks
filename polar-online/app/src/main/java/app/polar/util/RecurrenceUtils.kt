package app.polar.util

import app.polar.data.entity.Task
import java.util.Calendar

/**
 * Reglas de las tareas recurrentes, sin dependencias de Android para poder probarlas en JVM. Cada
 * valor de `Task.recurrence` se interpreta con [RecurrenceRule].
 *
 * Modelo: una tarea recurrente completada permanece marcada con la fecha de la ocurrencia que se
 * acaba de cumplir (`dueDate`). Cuando empieza el día de la siguiente ocurrencia, [startNextOccurrence]
 * la reabre con la nueva fecha de vencimiento y una nueva fecha de creación. En las reglas que
 * cuentan desde que se completa, esa fecha es el día en que se completó.
 */
object RecurrenceUtils {

    const val NONE = "NONE"
    const val DAILY = "DAILY"
    const val WEEKLY = "WEEKLY"
    const val MONTHLY = "MONTHLY"
    const val MON_WED = "MON_WED"
    const val FIRST_DAY_MONTH = "FIRST_DAY_MONTH"

    // Tope defensivo de saltos al ponerse al día tras muchos días sin abrir la app (~27 años diarios).
    private const val MAX_STEPS = 10_000

    fun isRecurring(recurrence: String): Boolean = RecurrenceRule.parse(recurrence) != null

    /** Primera ocurrencia estrictamente posterior a [from]; devuelve [from] si la regla no se conoce. */
    fun nextOccurrence(from: Long, recurrence: String): Long =
        RecurrenceRule.parse(recurrence)?.next(from) ?: from

    /**
     * [from] si cae en un día que permite la regla; si no, su siguiente ocurrencia (una tarea "cada
     * lunes y miércoles" con fecha en martes empieza el miércoles).
     */
    fun firstOccurrence(from: Long, recurrence: String): Long {
        val rule = RecurrenceRule.parse(recurrence) ?: return from
        return if (rule.matches(from)) from else rule.next(from)
    }

    fun startOfDay(timeInMillis: Long): Long = Calendar.getInstance().apply {
        this.timeInMillis = timeInMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /**
     * Última ocurrencia a partir de [from] cuyo día ya ha empezado en [now]. Si [from] ya es futura
     * (día aún no empezado) se devuelve tal cual.
     */
    fun latestOccurrenceStarted(from: Long, recurrence: String, now: Long): Long {
        var current = from
        repeat(MAX_STEPS) {
            val next = nextOccurrence(current, recurrence)
            if (next <= current || startOfDay(next) > now) return current
            current = next
        }
        return current
    }

    /**
     * Estado persistido al completar [task]. En una recurrente que se cumple con retraso (o tras
     * saltarse ocurrencias) la fecha se ajusta a la ocurrencia vigente, de modo que la siguiente se
     * calcule desde ella y no reaparezca al instante una ocurrencia ya pasada. Si la regla cuenta
     * desde que se completa, la fecha pasa a ser hoy, conservando la hora.
     */
    fun complete(task: Task, now: Long): Task {
        val due = task.dueDate
        val rule = RecurrenceRule.parse(task.recurrence)
        if (rule == null || due == null) return task.copy(completed = true)
        val fulfilled = if (rule.fromCompletion) onDayOf(now, keepingTimeOf = due)
        else latestOccurrenceStarted(due, task.recurrence, now)
        return task.copy(completed = true, dueDate = fulfilled)
    }

    /**
     * Si [task] es una recurrente completada cuya siguiente ocurrencia ya ha empezado, devuelve la
     * tarea reabierta con la nueva fecha de vencimiento y de creación; si no toca aún, `null`.
     */
    fun startNextOccurrence(task: Task, now: Long): Task? {
        val due = task.dueDate
        val rule = RecurrenceRule.parse(task.recurrence)
        if (!task.completed || task.isDeleted || rule == null || due == null) return null
        val next = rule.next(due)
        if (next <= due || startOfDay(next) > now) return null
        return task.copy(
            completed = false,
            // "N días después de completarla" vence justo N días después aunque se reabra tarde (el
            // dispositivo estuvo apagado): no hay ocurrencias intermedias a las que ponerse al día.
            dueDate = if (rule.fromCompletion) next else latestOccurrenceStarted(next, task.recurrence, now),
            createdAt = now
        )
    }

    /** El día de [day] a la hora de [keepingTimeOf]. */
    private fun onDayOf(day: Long, keepingTimeOf: Long): Long {
        val time = Calendar.getInstance().apply { timeInMillis = keepingTimeOf }
        return Calendar.getInstance().apply {
            timeInMillis = day
            set(Calendar.HOUR_OF_DAY, time.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, time.get(Calendar.MINUTE))
            set(Calendar.SECOND, time.get(Calendar.SECOND))
            set(Calendar.MILLISECOND, time.get(Calendar.MILLISECOND))
        }.timeInMillis
    }
}
