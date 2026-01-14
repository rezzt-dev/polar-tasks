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

    fun scheduleTaskAlarm(taskId: Long, timeInMillis: Long) {
        if (timeInMillis <= System.currentTimeMillis()) return
        
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_TYPE, AlarmReceiver.TYPE_TASK)
            putExtra("TASK_ID", taskId)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        scheduleExact(timeInMillis, pendingIntent)
    }

    fun cancelTaskAlarm(taskId: Long) {
        val intent = Intent(context, AlarmReceiver::class.java)
        // PendingIntent must match the original RequestCode and Class to be found for cancellation
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        
        pendingIntent?.let { alarmManager.cancel(it) }
    }

    fun scheduleReminderAlarm(reminderId: Long, timeInMillis: Long) {
        if (timeInMillis <= System.currentTimeMillis()) return

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_TYPE, AlarmReceiver.TYPE_REMINDER)
            putExtra(AlarmReceiver.EXTRA_REMINDER_ID, reminderId)
        }

        // Reminders use an offset for RequestCode to avoid collision with Tasks
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1000000 + reminderId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        scheduleExact(timeInMillis, pendingIntent)
    }

    fun cancelReminderAlarm(reminderId: Long) {
         val intent = Intent(context, AlarmReceiver::class.java)
         val pendingIntent = PendingIntent.getBroadcast(
            context,
            1000000 + reminderId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        pendingIntent?.let { alarmManager.cancel(it) }

    }

    private fun scheduleExact(timeInMillis: Long, pendingIntent: PendingIntent) {
         try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
