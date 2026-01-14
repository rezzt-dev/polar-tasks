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
                            // Schedule alarm
                             // We need a helper or copy logic from ViewModel. 
                             // Ideally logic should be in a shared helper, but for now we can duplicate or create a Utils class.
                             // Let's create a minimal scheduler here or reuse if possible.
                             // NotificationHelper is UI focused. 
                             // We'll reimplement simple scheduling here to avoid heavy dependencies, 
                             // or better, extract scheduleAlarm to a global utility. 
                             
                             alarmHelper.scheduleTaskAlarm(task.id, task.dueDate!!)
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }


}
