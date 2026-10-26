package app.polar.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.polar.data.AppDatabase
import app.polar.data.sync.touched
import app.polar.util.RecurrenceUtils
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
        if (taskId == -1L || notificationId == -1) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                // La notificación puede seguir visible aunque la tarea ya se haya completado o
                // enviado a la papelera (aquí o en otro dispositivo): en ese caso no se hace nada.
                val task = db.taskDao().getTaskById(taskId)?.takeIf { !it.completed && !it.isDeleted }

                if (task != null && action == ACTION_COMPLETE) {
                    // Misma regla que completar desde la app (recurrentes incluidas)
                    val updatedAt = System.currentTimeMillis()
                    db.taskDao().update(RecurrenceUtils.complete(task, updatedAt).touched())
                    db.subtaskDao().completeSubtasksForTask(taskId, updatedAt)
                    alarmHelper.cancelTaskAlarm(taskId)
                    app.polar.worker.SyncWorker.triggerImmediateSync(context)
                } else if (task != null && action == ACTION_SNOOZE) {
                    val snoozeTime = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }.timeInMillis
                    // Persist the new due date so it marks dirty/updatedAt like any other
                    // mutation and reaches Supabase — otherwise a snooze from a notification
                    // never leaves this device (agent-docs/analisis-implementacion-supabase-
                    // sync.md, hallazgo 4.4).
                    db.taskDao().update(task.copy(dueDate = snoozeTime).touched())
                    alarmHelper.scheduleTaskAlarm(taskId, snoozeTime)
                    app.polar.worker.SyncWorker.triggerImmediateSync(context)
                }

                NotificationManagerCompat.from(context).cancel(notificationId)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
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
