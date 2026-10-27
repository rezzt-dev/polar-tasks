package app.polar.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import app.polar.MainActivity
import app.polar.R
import app.polar.data.entity.Reminder
import app.polar.data.entity.Subtask
import app.polar.data.entity.Task
import app.polar.receiver.NotificationActionReceiver
import app.polar.ui.activity.SnoozeActivity
import app.polar.ui.activity.TaskDetailActivity

object NotificationHelper {
    const val CHANNEL_ID = "task_reminders"

    // Espacios de id de notificación separados para que tareas, recordatorios y subtareas no se pisen.
    private const val REMINDER_NOTIFICATION_OFFSET = 1_000_000
    private const val SUBTASK_NOTIFICATION_OFFSET = 2_000_000

    // Request codes de las acciones de cada notificación: id de notificación * 10 + acción.
    private const val REQUEST_COMPLETE = 0
    private const val REQUEST_SNOOZE = 1
    private const val REQUEST_LOCATION = 2

    // Un aviso anticipado ("1 día antes") indica cuándo vence; a la hora del vencimiento no hace falta.
    private const val EARLY_ALERT_MARGIN_MILLIS = 60_000L

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val localized = ThemeManager.localizedContext(context)
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            // Volver a crear el canal solo actualiza su nombre y descripción (p. ej. al cambiar el idioma).
            val channel = NotificationChannel(
                CHANNEL_ID,
                localized.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = localized.getString(R.string.notification_channel_desc)
                setSound(soundUri, audioAttributes)
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun taskNotificationId(taskId: Long): Int = taskId.toInt()

    fun reminderNotificationId(reminderId: Long): Int = REMINDER_NOTIFICATION_OFFSET + reminderId.toInt()

    private fun isDndActive(context: Context): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    }

    private fun taskDetailIntent(context: Context, taskId: Long, requestCode: Int): PendingIntent {
        val intent = Intent(context, TaskDetailActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(TaskDetailActivity.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getActivity(
            context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** Botones "completar" y "posponer" de la notificación de una tarea o de un recordatorio. */
    private fun NotificationCompat.Builder.addAlertActions(
        context: Context,
        notificationId: Int,
        title: String,
        idExtra: Intent.() -> Unit
    ): NotificationCompat.Builder {
        val localized = ThemeManager.localizedContext(context)
        val completeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_COMPLETE
            idExtra()
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val snoozeIntent = Intent(context, SnoozeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            idExtra()
            putExtra(SnoozeActivity.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(SnoozeActivity.EXTRA_TITLE, title)
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        addAction(
            R.drawable.ic_check_box,
            localized.getString(R.string.notification_action_complete),
            PendingIntent.getBroadcast(context, notificationId * 10 + REQUEST_COMPLETE, completeIntent, flags)
        )
        addAction(
            R.drawable.ic_schedule,
            localized.getString(R.string.snooze),
            PendingIntent.getActivity(context, notificationId * 10 + REQUEST_SNOOZE, snoozeIntent, flags)
        )
        return this
    }

    private fun notify(context: Context, notificationId: Int, builder: NotificationCompat.Builder) {
        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            // Sin permiso de notificaciones: no hay nada que mostrar.
            e.printStackTrace()
        }
    }

    fun showTaskNotification(context: Context, task: Task, now: Long = System.currentTimeMillis()) {
        val localized = ThemeManager.localizedContext(context)
        val notificationId = taskNotificationId(task.id)
        val due = task.dueDate
        val dueText = if (due != null && due - now > EARLY_ALERT_MARGIN_MILLIS) {
            localized.getString(R.string.notification_task_due, DateUtils.formatDateTime(localized, due))
        } else null
        val text = listOfNotNull(dueText, task.description.takeIf { it.isNotBlank() }).joinToString("\n")

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check_box)
            .setContentTitle(task.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(taskDetailIntent(context, task.id, notificationId))
            .setAutoCancel(true)
            .addAlertActions(context, notificationId, task.title) {
                putExtra(NotificationActionReceiver.EXTRA_TASK_ID, task.id)
            }

        notify(context, notificationId, builder)
    }

    // Las subtareas abren el detalle de su tarea y no llevan acciones: completar o posponer
    // actúan sobre tareas, no sobre subtareas.
    fun showSubtaskNotification(context: Context, subtask: Subtask, parentTitle: String?) {
        val notificationId = SUBTASK_NOTIFICATION_OFFSET + subtask.id.toInt()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check_box)
            .setContentTitle(subtask.title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(taskDetailIntent(context, subtask.taskId, notificationId))
            .setAutoCancel(true)

        if (!parentTitle.isNullOrBlank()) {
            builder.setContentText(
                ThemeManager.localizedContext(context).getString(R.string.notification_subtask_of, parentTitle)
            )
        }

        notify(context, notificationId, builder)
    }

    /**
     * @param arrived el aviso es por llegar al lugar del recordatorio, no por su hora. Si la
     *   notificación sigue visible, entrar de nuevo en la zona no vuelve a sonar.
     */
    fun showReminderNotification(context: Context, reminder: Reminder, arrived: Boolean = false) {
        val localized = ThemeManager.localizedContext(context)
        val notificationId = reminderNotificationId(reminder.id)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(MainActivity.EXTRA_NAVIGATE_TO_REMINDERS, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val arrivalText = if (arrived) {
            reminder.locationName?.takeIf { it.isNotBlank() }
                ?.let { localized.getString(R.string.notification_arrived_at, it) }
                ?: localized.getString(R.string.notification_arrived)
        } else null
        val text = listOfNotNull(arrivalText, reminder.description.takeIf { it.isNotBlank() }).joinToString("\n")

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check_box)
            .setContentTitle(localized.getString(R.string.notification_reminder_title, reminder.title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(arrived)
            .addAlertActions(context, notificationId, reminder.title) {
                putExtra(NotificationActionReceiver.EXTRA_REMINDER_ID, reminder.id)
            }

        // Acción "ver ubicación" si el recordatorio tiene coordenadas; se abre con cualquier app de
        // mapas instalada, no solo con google maps.
        if (reminder.latitude != null && reminder.longitude != null) {
            val coordinates = "${reminder.latitude},${reminder.longitude}"
            val query = if (!reminder.locationName.isNullOrBlank()) {
                "$coordinates(${reminder.locationName})"
            } else {
                coordinates
            }
            val locationIntent = Intent(Intent.ACTION_VIEW, "geo:$coordinates?q=${Uri.encode(query)}".toUri())
            if (locationIntent.resolveActivity(context.packageManager) != null) {
                val locationPendingIntent = PendingIntent.getActivity(
                    context,
                    notificationId * 10 + REQUEST_LOCATION,
                    locationIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                builder.addAction(
                    R.drawable.ic_location_notification,
                    localized.getString(R.string.view_location),
                    locationPendingIntent
                )
            }
        }

        if (isDndActive(context)) {
            builder.setSilent(true)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        }

        notify(context, notificationId, builder)
    }

    fun cancel(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    fun showCreationConfirmation(context: Context, title: String, dateTime: Long) {
        val localized = ThemeManager.localizedContext(context)
        val date = java.util.Date(dateTime)
        val dateStr = android.text.format.DateFormat.getDateFormat(localized).format(date)
        val timeStr = android.text.format.DateFormat.getTimeFormat(localized).format(date)

        // El título lo escribe el usuario: se añade como texto en negrita, nunca como HTML.
        val message = buildSpannedString {
            append(localized.getString(R.string.reminder_created_message, dateStr, timeStr))
            append(" ")
            bold { append(title) }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check_box)
            .setContentTitle(localized.getString(R.string.reminder_created_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notify(context, System.currentTimeMillis().toInt(), builder)
    }
}
