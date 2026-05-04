package app.polar.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.polar.util.NotificationHelper
import app.polar.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra(EXTRA_TYPE) ?: TYPE_TASK
        
        if (type == TYPE_TASK) {
            val taskId = intent.getLongExtra("TASK_ID", -1L)
            if (taskId != -1L) {
                 // Trigger Task Notification
                 // We need to fetch task in async
                 val goAsync = goAsync()
                 kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                     try {
                         val database = app.polar.data.AppDatabase.getDatabase(context)
                         val task = database.taskDao().getTaskById(taskId)
                         if (task != null && !task.completed) {
                             app.polar.util.NotificationHelper.showNotification(context, task.id, task.title, task.description)
                         }
                     } finally {
                         goAsync.finish()
                     }
                 }
            }
        } else if (type == TYPE_REMINDER) {
            val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
            if (reminderId != -1L) {
                 val goAsync = goAsync()
                 kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                     try {
                         val database = app.polar.data.AppDatabase.getDatabase(context)
                         val reminder = database.reminderDao().getReminderById(reminderId)
                         if (reminder != null && !reminder.isCompleted) {
                             app.polar.util.NotificationHelper.showReminderNotification(context, reminder)
                         }
                     } finally {
                         goAsync.finish()
                     }
                 }
            }
        } else if (type == TYPE_SUBTASK) {
            val subtaskId = intent.getLongExtra(EXTRA_SUBTASK_ID, -1L)
            if (subtaskId != -1L) {
                 val goAsync = goAsync()
                 kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                     try {
                         val database = app.polar.data.AppDatabase.getDatabase(context)
                         val subtask = database.subtaskDao().getSubtaskById(subtaskId)
                         if (subtask != null && !subtask.completed) {
                             app.polar.util.NotificationHelper.showNotification(
                                 context,
                                 // Use negative ID space for subtasks to avoid collision
                                 (subtaskId + 2_000_000L).toInt().toLong(),
                                 subtask.title,
                                 ""
                             )
                         }
                     } finally {
                         goAsync.finish()
                     }
                 }
            }
        }
    }

    companion object {
        const val EXTRA_TYPE = "EXTRA_TYPE"
        const val TYPE_TASK = "TYPE_TASK"
        const val TYPE_REMINDER = "TYPE_REMINDER"
        const val TYPE_SUBTASK = "TYPE_SUBTASK"
        const val EXTRA_REMINDER_ID = "EXTRA_REMINDER_ID"
        const val EXTRA_SUBTASK_ID = "EXTRA_SUBTASK_ID"
    }
}
