package app.polar.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.polar.data.AppDatabase
import app.polar.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val database = AppDatabase.getDatabase(context)
                    val tasks = database.taskDao().getAllTasksSnapshot()
                    val now = System.currentTimeMillis()
                    
                    tasks.forEach { task ->
                        if (!task.completed && task.dueDate != null && task.dueDate > now) {
                            // Schedule alarm
                             // We need a helper or copy logic from ViewModel. 
                             // Ideally logic should be in a shared helper, but for now we can duplicate or create a Utils class.
                             // Let's create a minimal scheduler here or reuse if possible.
                             // NotificationHelper is UI focused. 
                             // We'll reimplement simple scheduling here to avoid heavy dependencies, 
                             // or better, extract scheduleAlarm to a global utility. 
                             
                             scheduleAlarm(context, task.id, task.dueDate)
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun scheduleAlarm(context: Context, taskId: Long, timeInMillis: Long) {
          val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
          val intent = Intent(context, AlarmReceiver::class.java).apply {
              putExtra("TASK_ID", taskId)
          }
          val pendingIntent = android.app.PendingIntent.getBroadcast(
              context,
              taskId.toInt(),
              intent,
              android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
          )
          
          try {
              if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                  if (alarmManager.canScheduleExactAlarms()) {
                      alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
                  } else {
                      alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
                  }
              } else {
                   alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
              }
          } catch (e: SecurityException) {
              e.printStackTrace()
          }
    }
}
