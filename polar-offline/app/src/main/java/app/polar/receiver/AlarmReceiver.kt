package app.polar.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.polar.data.AppDatabase
import app.polar.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Muestra la notificación de una tarea, subtarea o recordatorio cuando suena su alarma. Antes de
 * avisar se comprueba el estado actual en la base de datos: el elemento puede haberse completado,
 * borrado o enviado a la papelera después de programar la alarma.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val database = AppDatabase.getDatabase(context)

        when (intent.getStringExtra(EXTRA_TYPE) ?: TYPE_TASK) {
            TYPE_TASK -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
                if (taskId == -1L) return
                runAsync {
                    val task = database.taskDao().getTaskById(taskId)
                    if (task != null && !task.completed && !task.isDeleted) {
                        NotificationHelper.showNotification(context, task.id, task.title, task.description)
                    }
                }
            }
            TYPE_REMINDER -> {
                val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
                if (reminderId == -1L) return
                runAsync {
                    val reminder = database.reminderDao().getReminderById(reminderId)
                    if (reminder != null && !reminder.isCompleted && !reminder.isDeleted) {
                        NotificationHelper.showReminderNotification(context, reminder)
                    }
                }
            }
            TYPE_SUBTASK -> {
                val subtaskId = intent.getLongExtra(EXTRA_SUBTASK_ID, -1L)
                if (subtaskId == -1L) return
                runAsync {
                    database.subtaskDao().getAlarmableSubtask(subtaskId)?.let { subtask ->
                        val parentTitle = database.taskDao().getTaskById(subtask.taskId)?.title
                        NotificationHelper.showSubtaskNotification(context, subtask, parentTitle)
                    }
                }
            }
        }
    }

    private fun runAsync(block: suspend () -> Unit) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                block()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TYPE = "EXTRA_TYPE"
        const val TYPE_TASK = "TYPE_TASK"
        const val TYPE_REMINDER = "TYPE_REMINDER"
        const val TYPE_SUBTASK = "TYPE_SUBTASK"
        const val EXTRA_TASK_ID = "TASK_ID"
        const val EXTRA_REMINDER_ID = "EXTRA_REMINDER_ID"
        const val EXTRA_SUBTASK_ID = "EXTRA_SUBTASK_ID"
    }
}
