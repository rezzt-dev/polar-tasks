package app.polar.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.polar.data.AppDatabase
import app.polar.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.app.NotificationManagerCompat
import java.util.Calendar
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var alarmHelper: app.polar.util.AlarmManagerHelper


    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        if (taskId != -1L && notificationId != -1) {
            val goAsync = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    
                    if (action == ACTION_COMPLETE) {
                        val task = db.taskDao().getTaskById(taskId)
                        if (task != null) {
                            db.taskDao().update(task.copy(completed = true))
                            // Cancel notification
                            NotificationManagerCompat.from(context).cancel(notificationId)
                            // Maybe reschedule if recurring? Logic is in ViewModel mainly but we might duplicate or invoke VM...
                            // For now simple completion.
                        }
                    } else if (action == ACTION_SNOOZE) {
                        // Snooze for 1 hour
                        val calendar = Calendar.getInstance()
                        calendar.add(Calendar.HOUR_OF_DAY, 1)
                        val snoozeTime = calendar.timeInMillis
                        
                        // Reschedule alarm
                        alarmHelper.scheduleTaskAlarm(taskId, snoozeTime)
                        
                        // Cancel current notification
                        NotificationManagerCompat.from(context).cancel(notificationId)
                    }
                } finally {
                    goAsync.finish()
                }
            }
        }
    }



    companion object {
        const val ACTION_COMPLETE = "app.polar.ACTION_COMPLETE"
        const val ACTION_SNOOZE = "app.polar.ACTION_SNOOZE"
        const val EXTRA_TASK_ID = "EXTRA_TASK_ID"
        const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
    }
}
