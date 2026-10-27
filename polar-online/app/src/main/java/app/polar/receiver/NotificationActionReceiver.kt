package app.polar.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import app.polar.util.AlertActions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Botones de la notificación de una tarea o de un recordatorio. "posponer" abre la hoja de
 * [app.polar.ui.activity.SnoozeActivity] para elegir hasta cuándo; aquí solo llega
 * [ACTION_SNOOZE] desde notificaciones creadas antes de poder elegir, que posponían una hora.
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var alertActions: AlertActions

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if ((taskId == -1L && reminderId == -1L) || notificationId == -1) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_COMPLETE ->
                        if (reminderId != -1L) alertActions.completeReminder(reminderId)
                        else alertActions.completeTask(taskId)
                    ACTION_SNOOZE -> {
                        val until = System.currentTimeMillis() + LEGACY_SNOOZE_MILLIS
                        if (reminderId != -1L) alertActions.snoozeReminder(reminderId, until)
                        else alertActions.snoozeTask(taskId, until)
                    }
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
        const val EXTRA_REMINDER_ID = "EXTRA_REMINDER_ID"
        const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
        private const val LEGACY_SNOOZE_MILLIS = 60 * 60 * 1000L
    }
}
