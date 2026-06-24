package app.polar.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.polar.data.AppDatabase
import app.polar.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    
    @Inject
    lateinit var alarmHelper: app.polar.util.AlarmManagerHelper
    
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
                             alarmHelper.scheduleTaskAlarm(task.id, task.dueDate!!)
                        }
                    }
                    
                    // Reschedule reminder alarms on boot
                    val reminders = database.reminderDao().getAllRemindersSnapshot()
                    reminders.forEach { reminder ->
                        if (!reminder.isCompleted && !reminder.isDeleted && reminder.dateTime > now) {
                            alarmHelper.scheduleReminderAlarm(reminder.id, reminder.dateTime)
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }


}
