package app.polar.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.polar.R

object NotificationHelper {
    const val CHANNEL_ID = "task_reminders"
    const val CHANNEL_NAME = "task reminders"
    const val CHANNEL_DESC = "notifications for task deadlines"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                setSound(soundUri, audioAttributes)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun isDndActive(context: Context): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        } else {
            false
        }
    }

    fun showNotification(context: Context, taskId: Long, title: String, description: String) {
        // Create an explicit intent for an Activity in your app
        val intent = android.content.Intent(context, app.polar.ui.activity.TaskDetailActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(app.polar.ui.activity.TaskDetailActivity.EXTRA_TASK_ID, taskId)
        }
        val pendingIntent: android.app.PendingIntent = android.app.PendingIntent.getActivity(
            context, taskId.toInt(), intent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )



        // Action: Complete
        val completeIntent = android.content.Intent(context, app.polar.receiver.NotificationActionReceiver::class.java).apply {
            action = app.polar.receiver.NotificationActionReceiver.ACTION_COMPLETE
            putExtra(app.polar.receiver.NotificationActionReceiver.EXTRA_TASK_ID, taskId)
            putExtra(app.polar.receiver.NotificationActionReceiver.EXTRA_NOTIFICATION_ID, taskId.toInt())
        }
        val completePendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            taskId.toInt() * 10, // Unique Request Code
            completeIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Action: Snooze (1 Hour)
        val snoozeIntent = android.content.Intent(context, app.polar.receiver.NotificationActionReceiver::class.java).apply {
            action = app.polar.receiver.NotificationActionReceiver.ACTION_SNOOZE
            putExtra(app.polar.receiver.NotificationActionReceiver.EXTRA_TASK_ID, taskId)
            putExtra(app.polar.receiver.NotificationActionReceiver.EXTRA_NOTIFICATION_ID, taskId.toInt())
        }
        val snoozePendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            taskId.toInt() * 10 + 1, // Unique Request Code
            snoozeIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check_box) 
            .setContentTitle(title)
            .setContentText(description)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_check_box, "completar", completePendingIntent)
            .addAction(R.drawable.ic_schedule, "posponer 1h", snoozePendingIntent)

        try {
           with(NotificationManagerCompat.from(context)) {
               notify(taskId.toInt(), builder.build())
           }
        } catch (e: SecurityException) {
            // Handle permission issue
        }
    }

    fun showReminderNotification(context: Context, reminder: app.polar.data.entity.Reminder) {
        val intent = android.content.Intent(context, app.polar.MainActivity::class.java).apply {
             flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
             putExtra("NAVIGATE_TO_REMINDERS", true)
        }

        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            (reminder.id + 1000000).toInt(),
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check_box)
            .setContentTitle("recordatorio: ${reminder.title}")
            .setContentText(reminder.description)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // Add "View Location" action if coordinates are available
        if (reminder.latitude != null && reminder.longitude != null) {
            val locationUri = if (!reminder.locationName.isNullOrBlank()) {
                android.net.Uri.parse("geo:${reminder.latitude},${reminder.longitude}?q=${reminder.latitude},${reminder.longitude}(${reminder.locationName})")
            } else {
                android.net.Uri.parse("geo:${reminder.latitude},${reminder.longitude}?q=${reminder.latitude},${reminder.longitude}")
            }
            val locationIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, locationUri)
            locationIntent.setPackage("com.google.android.apps.maps")
            val locationPendingIntent = android.app.PendingIntent.getActivity(
                context,
                (reminder.id + 1000000).toInt() * 10 + 2,
                locationIntent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(R.drawable.ic_location_notification, context.getString(R.string.view_location), locationPendingIntent)
        }

        if (isDndActive(context)) {
            builder.setSilent(true)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        }

        try {
           with(NotificationManagerCompat.from(context)) {
               notify((reminder.id + 1000000).toInt(), builder.build())
           }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }


    fun showCreationConfirmation(context: Context, title: String, dateTime: Long) {
        val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val dateObj = java.util.Date(dateTime)
        
        val dateStr = dateFormat.format(dateObj)
        val timeStr = timeFormat.format(dateObj)
        
        // "se ha puesto un recordatorio el dia X a la hora X con el motivo de: [texto en negrita]"
        // Using BigTextStyle to ensure full text is visible and styling
        val message = "se ha puesto un recordatorio el dia $dateStr a la hora $timeStr con el motivo de: <b>$title</b>"
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check_box) 
            .setContentTitle("recordatorio creado")
             // setContentText usually strips HTML, use BigText for better support or Html.fromHtml if supported
            .setContentText("se ha puesto un recordatorio el dia $dateStr...")
            .setStyle(NotificationCompat.BigTextStyle().bigText(androidx.core.text.HtmlCompat.fromHtml(message, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            
        try {
           with(NotificationManagerCompat.from(context)) {
               // Use a unique ID for confirmation, e.g., current time
               notify(System.currentTimeMillis().toInt(), builder.build())
           }
        } catch (e: SecurityException) {
        }
    }
}
