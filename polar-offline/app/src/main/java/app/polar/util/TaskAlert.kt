package app.polar.util

import android.content.Context
import app.polar.R
import app.polar.data.entity.Task

/**
 * Aviso de una tarea con fecha. Suena [Task.reminderOffset] minutos antes del vencimiento (0, a la
 * hora; [OFF], nunca), salvo que se haya pospuesto desde la notificación: entonces suena en
 * [Task.snoozedUntil]. Posponer cambia solo el aviso, nunca la fecha de la tarea.
 */
object TaskAlert {
    const val AT_DUE_TIME = 0
    const val OFF = -1

    private const val HOUR = 60
    private const val DAY = 24 * HOUR
    private const val WEEK = 7 * DAY

    /** Opciones del selector del formulario, en minutos antes del vencimiento. */
    val PRESETS = listOf(AT_DUE_TIME, 5, 15, 30, HOUR, 2 * HOUR, DAY, 2 * DAY, WEEK, OFF)

    /** Momento en que debe sonar el aviso, o `null` si la tarea no avisa. */
    fun triggerAt(task: Task): Long? {
        if (task.completed || task.isDeleted) return null
        task.snoozedUntil?.let { return it }
        val due = task.dueDate ?: return null
        if (task.reminderOffset == OFF) return null
        return due - task.reminderOffset.coerceAtLeast(0) * 60_000L
    }

    /**
     * [task] tras editarla. Un aviso pospuesto pertenece a la fecha y a la antelación con las que se
     * pospuso: si cambia cualquiera de las dos, vuelve a sonar según la nueva configuración.
     */
    fun edited(original: Task, edited: Task): Task =
        if (edited.dueDate == original.dueDate && edited.reminderOffset == original.reminderOffset) edited
        else edited.copy(snoozedUntil = null)

    /** "avisar 1 día antes", "avisar a la hora", "sin aviso"... */
    fun describe(context: Context, offset: Int): String {
        val res = context.resources
        return when {
            offset == OFF -> context.getString(R.string.task_alert_off)
            offset <= AT_DUE_TIME -> context.getString(R.string.task_alert_at_due)
            offset % WEEK == 0 -> res.getQuantityString(R.plurals.task_alert_weeks_before, offset / WEEK, offset / WEEK)
            offset % DAY == 0 -> res.getQuantityString(R.plurals.task_alert_days_before, offset / DAY, offset / DAY)
            offset % HOUR == 0 -> res.getQuantityString(R.plurals.task_alert_hours_before, offset / HOUR, offset / HOUR)
            else -> res.getQuantityString(R.plurals.task_alert_minutes_before, offset, offset)
        }
    }
}
