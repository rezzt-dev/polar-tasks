package app.polar.util

import app.polar.data.AppDatabase
import app.polar.data.entity.Reminder
import app.polar.data.entity.Task
import javax.inject.Inject

/**
 * Acciones sobre el aviso de una tarea o de un recordatorio desde fuera de sus pantallas (botones
 * de la notificación y hoja de posponer). Cada una devuelve `false` si el elemento ya no está
 * pendiente: la notificación puede seguir visible aunque se haya completado o enviado a la
 * papelera desde la app, y entonces la acción no debe hacer nada.
 */
class AlertActions @Inject constructor(
    private val database: AppDatabase,
    private val alarmHelper: AlarmManagerHelper,
    private val geofenceHelper: GeofenceHelper
) {

    /** Misma regla que completar desde la app (recurrentes incluidas). */
    suspend fun completeTask(taskId: Long, now: Long = System.currentTimeMillis()): Boolean {
        val task = pendingTask(taskId) ?: return false
        database.taskDao().update(RecurrenceUtils.complete(task, now))
        database.subtaskDao().completeSubtasksForTask(taskId)
        alarmHelper.cancelTaskAlarm(taskId)
        return true
    }

    /** Pospone solo el aviso: la tarea conserva su fecha de vencimiento. */
    suspend fun snoozeTask(taskId: Long, until: Long): Boolean {
        val task = pendingTask(taskId) ?: return false
        val snoozed = task.copy(snoozedUntil = until)
        database.taskDao().update(snoozed)
        alarmHelper.syncTaskAlarm(snoozed)
        return true
    }

    suspend fun completeReminder(reminderId: Long): Boolean {
        val reminder = pendingReminder(reminderId) ?: return false
        val completed = reminder.copy(isCompleted = true)
        database.reminderDao().update(completed)
        alarmHelper.syncReminderAlarm(completed)
        geofenceHelper.sync(completed)
        return true
    }

    /** Un recordatorio no tiene más fecha que la del aviso: posponerlo la mueve. */
    suspend fun snoozeReminder(reminderId: Long, until: Long): Boolean {
        val reminder = pendingReminder(reminderId) ?: return false
        val snoozed = reminder.copy(dateTime = until)
        database.reminderDao().update(snoozed)
        alarmHelper.syncReminderAlarm(snoozed)
        return true
    }

    private suspend fun pendingTask(taskId: Long): Task? =
        database.taskDao().getTaskById(taskId)?.takeIf { !it.completed && !it.isDeleted }

    private suspend fun pendingReminder(reminderId: Long): Reminder? =
        database.reminderDao().getReminderById(reminderId)?.takeIf { !it.isCompleted && !it.isDeleted }
}
