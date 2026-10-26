package app.polar.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.polar.receiver.AlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AlarmManagerHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleTaskAlarm(taskId: Long, timeInMillis: Long) =
        schedule(TASK_REQUEST_OFFSET, taskId, timeInMillis) {
            putExtra(AlarmReceiver.EXTRA_TYPE, AlarmReceiver.TYPE_TASK)
            putExtra(AlarmReceiver.EXTRA_TASK_ID, taskId)
        }

    fun cancelTaskAlarm(taskId: Long) = cancel(TASK_REQUEST_OFFSET, taskId)

    fun scheduleReminderAlarm(reminderId: Long, timeInMillis: Long) =
        schedule(REMINDER_REQUEST_OFFSET, reminderId, timeInMillis) {
            putExtra(AlarmReceiver.EXTRA_TYPE, AlarmReceiver.TYPE_REMINDER)
            putExtra(AlarmReceiver.EXTRA_REMINDER_ID, reminderId)
        }

    fun cancelReminderAlarm(reminderId: Long) = cancel(REMINDER_REQUEST_OFFSET, reminderId)

    fun scheduleSubtaskAlarm(subtaskId: Long, timeInMillis: Long) =
        schedule(SUBTASK_REQUEST_OFFSET, subtaskId, timeInMillis) {
            putExtra(AlarmReceiver.EXTRA_TYPE, AlarmReceiver.TYPE_SUBTASK)
            putExtra(AlarmReceiver.EXTRA_SUBTASK_ID, subtaskId)
        }

    fun cancelSubtaskAlarm(subtaskId: Long) = cancel(SUBTASK_REQUEST_OFFSET, subtaskId)

    // Programar con el mismo request code sustituye la alarma anterior del elemento.
    private fun schedule(offset: Int, id: Long, timeInMillis: Long, extras: Intent.() -> Unit) {
        if (timeInMillis <= System.currentTimeMillis()) {
            // Una fecha ya pasada no debe dejar sonando la alarma de la fecha anterior.
            cancel(offset, id)
            return
        }
        val intent = Intent(context, AlarmReceiver::class.java).apply(extras)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            offset + id.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        scheduleExact(timeInMillis, pendingIntent)
    }

    // El PendingIntent se localiza por request code y componente; los extras no cuentan.
    private fun cancel(offset: Int, id: Long) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            offset + id.toInt(),
            Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun scheduleExact(timeInMillis: Long, pendingIntent: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                // Sin permiso de alarmas exactas: aviso aproximado. Se reprograma como exacta al
                // concederse el permiso (BootReceiver).
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private companion object {
        // Espacios de request code separados para que tareas, recordatorios y subtareas no choquen.
        const val TASK_REQUEST_OFFSET = 0
        const val REMINDER_REQUEST_OFFSET = 1_000_000
        const val SUBTASK_REQUEST_OFFSET = 2_000_000
    }
}
