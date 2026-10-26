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
import app.polar.receiver.NotificationActionReceiver
import app.polar.ui.activity.TaskDetailActivity

object NotificationHelper {
    const val CHANNEL_ID = "task_reminders"

    // Espacios de id de notificación separados para que tareas, recordatorios y subtareas no se pisen.
    private const val REMINDER_NOTIFICATION_OFFSET = 1_000_000
    private const val SUBTASK_NOTIFICATION_OFFSET = 2_000_000

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

    private fun taskActionIntent(context: Context, action: String, taskId: Long, requestCode: Int): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(NotificationActionReceiver.EXTRA_TASK_ID, taskId)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, taskId.toInt())
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun notify(context: Context, notificationId: Int, builder: NotificationCompat.Builder) {
        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            // Sin permiso de notificaciones: no hay nada que mostrar.
            e.printStackTrace()
        }
    }

    fun showNotification(context: Context, taskId: Long, title: String, description: String) {
        val localized = ThemeManager.localizedContext(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check_box)
            .setContentTitle(title)
            .setContentText(description)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(taskDetailIntent(context, taskId, taskId.toInt()))
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_check_box,
                localized.getString(R.string.notification_action_complete),
                taskActionIntent(context, NotificationActionReceiver.ACTION_COMPLETE, taskId, taskId.toInt() * 10)
            )
            .addAction(
                R.drawable.ic_schedule,
                localized.getString(R.string.notification_action_snooze),
                taskActionIntent(context, NotificationActionReceiver.ACTION_SNOOZE, taskId, taskId.toInt() * 10 + 1)
            )

        notify(context, taskId.toInt(), builder)
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

    fun showReminderNotification(context: Context, reminder: Reminder) {
        val localized = ThemeManager.localizedContext(context)
        val notificationId = REMINDER_NOTIFICATION_OFFSET + reminder.id.toInt()

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

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check_box)
            .setContentTitle(localized.getString(R.string.notification_reminder_title, reminder.title))
            .setContentText(reminder.description)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // Acción "ver ubicación" si el recordatorio tiene coordenadas
        if (reminder.latitude != null && reminder.longitude != null) {
            val coordinates = "${reminder.latitude},${reminder.longitude}"
            val query = if (!reminder.locationName.isNullOrBlank()) {
                "$coordinates(${reminder.locationName})"
            } else {
                coordinates
            }
            val locationIntent = Intent(Intent.ACTION_VIEW, "geo:$coordinates?q=${Uri.encode(query)}".toUri())
                .setPackage("com.google.android.apps.maps")
            val locationPendingIntent = PendingIntent.getActivity(
                context,
                notificationId * 10 + 2,
                locationIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(
                R.drawable.ic_location_notification,
                localized.getString(R.string.view_location),
                locationPendingIntent
            )
        }

        if (isDndActive(context)) {
            builder.setSilent(true)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        }

        notify(context, notificationId, builder)
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
