package app.polar.util

import app.polar.data.AppDatabase
import javax.inject.Inject

/**
 * Sincroniza las alarmas de AlarmManager con la base de datos. Las alarmas no sobreviven a un
 * reinicio y apuntan a ids concretos, así que hay que reconstruirlas al arrancar el dispositivo,
 * al concederse el permiso de alarmas exactas (las anteriores quedaron inexactas) y al restaurar
 * una copia de seguridad (los ids antiguos pasan a ser de otros elementos).
 */
class AlarmRescheduler @Inject constructor(
    private val database: AppDatabase,
    private val alarmHelper: AlarmManagerHelper
) {

    suspend fun rescheduleAll(now: Long = System.currentTimeMillis()) {
        database.taskDao().getAlarmableTasks(now).forEach { task ->
            task.dueDate?.let { alarmHelper.scheduleTaskAlarm(task.id, it) }
        }
        database.subtaskDao().getAlarmableSubtasks(now).forEach { subtask ->
            subtask.dueDate?.let { alarmHelper.scheduleSubtaskAlarm(subtask.id, it) }
        }
        database.reminderDao().getAlarmableReminders(now).forEach { reminder ->
            alarmHelper.scheduleReminderAlarm(reminder.id, reminder.dateTime)
        }
    }

    suspend fun cancelAll() {
        database.taskDao().getAllTaskIds().forEach(alarmHelper::cancelTaskAlarm)
        database.subtaskDao().getAllSubtaskIds().forEach(alarmHelper::cancelSubtaskAlarm)
        database.reminderDao().getAllReminderIds().forEach(alarmHelper::cancelReminderAlarm)
    }
}
