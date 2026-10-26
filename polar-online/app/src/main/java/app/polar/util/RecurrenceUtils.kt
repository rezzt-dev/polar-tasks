package app.polar.util

import app.polar.data.entity.Task
import java.util.Calendar

/**
 * Reglas de las tareas recurrentes, sin dependencias de Android para poder probarlas en JVM.
 *
 * Modelo: una tarea recurrente completada permanece marcada con la fecha de la ocurrencia que se
 * acaba de cumplir (`dueDate`). Cuando empieza el día de la siguiente ocurrencia, [startNextOccurrence]
 * la reabre con la nueva fecha de vencimiento y una nueva fecha de creación.
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

    private val KNOWN = setOf(DAILY, WEEKLY, MONTHLY, MON_WED, FIRST_DAY_MONTH)

    fun isRecurring(recurrence: String): Boolean = recurrence in KNOWN

    /** Primera ocurrencia estrictamente posterior a [from]; devuelve [from] si la regla no se conoce. */
    fun nextOccurrence(from: Long, recurrence: String): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = from }
        when (recurrence) {
            DAILY -> calendar.add(Calendar.DAY_OF_YEAR, 1)
            WEEKLY -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
            MONTHLY -> calendar.add(Calendar.MONTH, 1)
            MON_WED -> do {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            } while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY &&
                calendar.get(Calendar.DAY_OF_WEEK) != Calendar.WEDNESDAY)
            FIRST_DAY_MONTH -> {
                // Se fija el día 1 tras avanzar de mes para no desbordar (p. ej. desde el día 31).
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.add(Calendar.MONTH, 1)
            }
        }
        return calendar.timeInMillis
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
     * calcule desde ella y no reaparezca al instante una ocurrencia ya pasada.
     */
    fun complete(task: Task, now: Long): Task {
        val due = task.dueDate
        if (!isRecurring(task.recurrence) || due == null) return task.copy(completed = true)
        return task.copy(completed = true, dueDate = latestOccurrenceStarted(due, task.recurrence, now))
    }

    /**
     * Si [task] es una recurrente completada cuya siguiente ocurrencia ya ha empezado, devuelve la
     * tarea reabierta con la nueva fecha de vencimiento y de creación; si no toca aún, `null`.
     */
    fun startNextOccurrence(task: Task, now: Long): Task? {
        val due = task.dueDate
        if (!task.completed || task.isDeleted || !isRecurring(task.recurrence) || due == null) return null
        val next = nextOccurrence(due, task.recurrence)
        if (next <= due || startOfDay(next) > now) return null
        return task.copy(
            completed = false,
            dueDate = latestOccurrenceStarted(next, task.recurrence, now),
            createdAt = now
        )
    }
}
