package app.polar.util

import android.content.Context
import app.polar.data.AppDatabase
import app.polar.data.entity.Reminder
import app.polar.data.entity.Task
import app.polar.data.sync.touched
import app.polar.worker.SyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Acciones sobre el aviso de una tarea o de un recordatorio desde fuera de sus pantallas (botones
 * de la notificación y hoja de posponer). Cada una devuelve `false` si el elemento ya no está
 * pendiente: la notificación puede seguir visible aunque se haya completado o enviado a la
 * papelera (aquí o en otro dispositivo), y entonces la acción no debe hacer nada.
 *
 * Los cambios que viajan a Supabase marcan la fila (touched) y piden sincronizar; posponer una
 * tarea solo toca [Task.snoozedUntil], que es local del dispositivo.
 */
class AlertActions @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val alarmHelper: AlarmManagerHelper,
    private val geofenceHelper: GeofenceHelper
) {

    /** Misma regla que completar desde la app (recurrentes incluidas). */
    suspend fun completeTask(taskId: Long, now: Long = System.currentTimeMillis()): Boolean {
        val task = pendingTask(taskId) ?: return false
        val completed = RecurrenceUtils.complete(task, now).touched()
        database.taskDao().update(completed)
        database.subtaskDao().completeSubtasksForTask(taskId, completed.updatedAt)
        alarmHelper.cancelTaskAlarm(taskId)
        SyncWorker.triggerImmediateSync(context)
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
        val completed = reminder.copy(isCompleted = true).touched()
        database.reminderDao().update(completed)
        alarmHelper.syncReminderAlarm(completed)
        geofenceHelper.sync(completed)
        SyncWorker.triggerImmediateSync(context)
        return true
    }

    /** Un recordatorio no tiene más fecha que la del aviso: posponerlo la mueve. */
    suspend fun snoozeReminder(reminderId: Long, until: Long): Boolean {
        val reminder = pendingReminder(reminderId) ?: return false
        val snoozed = reminder.copy(dateTime = until).touched()
        database.reminderDao().update(snoozed)
        alarmHelper.syncReminderAlarm(snoozed)
        SyncWorker.triggerImmediateSync(context)
        return true
    }

    private suspend fun pendingTask(taskId: Long): Task? =
        database.taskDao().getTaskById(taskId)?.takeIf { !it.completed && !it.isDeleted }

    private suspend fun pendingReminder(reminderId: Long): Reminder? =
        database.reminderDao().getReminderById(reminderId)?.takeIf { !it.isCompleted && !it.isDeleted }
}
