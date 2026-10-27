package app.polar.util

import app.polar.data.AppDatabase
import javax.inject.Inject

/**
 * Sincroniza las alarmas de AlarmManager y los avisos por llegada con la base de datos. Ninguno
 * sobrevive a un reinicio y apuntan a ids concretos, así que hay que reconstruirlos al arrancar el
 * dispositivo o actualizar la app, al concederse el permiso de alarmas exactas (las anteriores
 * quedaron inexactas) y al restaurar una copia de seguridad (los ids antiguos pasan a ser de otros
 * elementos).
 */
class AlarmRescheduler @Inject constructor(
    private val database: AppDatabase,
    private val alarmHelper: AlarmManagerHelper,
    private val geofenceHelper: GeofenceHelper
) {

    suspend fun rescheduleAll(now: Long = System.currentTimeMillis()) {
        database.taskDao().getAlarmableTasks(now).forEach(alarmHelper::syncTaskAlarm)
        database.subtaskDao().getAlarmableSubtasks(now).forEach { subtask ->
            subtask.dueDate?.let { alarmHelper.scheduleSubtaskAlarm(subtask.id, it) }
        }
        database.reminderDao().getAlarmableReminders(now).forEach { reminder ->
            alarmHelper.scheduleReminderAlarm(reminder.id, reminder.dateTime)
        }
        rescheduleArrivalAlerts()
    }

    /** Vuelve a registrar los avisos por llegada (p. ej. tras conceder el permiso de ubicación). */
    suspend fun rescheduleArrivalAlerts() {
        database.reminderDao().getArrivalReminders().forEach(geofenceHelper::sync)
    }

    suspend fun cancelAll() {
        database.taskDao().getAllTaskIds().forEach(alarmHelper::cancelTaskAlarm)
        database.subtaskDao().getAllSubtaskIds().forEach(alarmHelper::cancelSubtaskAlarm)
        database.reminderDao().getAllReminderIds().forEach { id ->
            alarmHelper.cancelReminderAlarm(id)
            geofenceHelper.cancel(id)
        }
    }
}
