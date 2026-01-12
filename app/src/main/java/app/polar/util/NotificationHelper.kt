package app.polar.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.polar.R

object NotificationHelper {
    const val CHANNEL_ID = "task_reminders"
    const val CHANNEL_NAME = "Task Reminders"
    const val CHANNEL_DESC = "Notifications for task deadlines"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
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

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check_box) 
            .setContentTitle(title)
            .setContentText(description)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
           with(NotificationManagerCompat.from(context)) {
               notify(taskId.toInt(), builder.build())
           }
        } catch (e: SecurityException) {
            // Handle permission issue
        }
    }

    fun showReminderNotification(context: Context, reminder: app.polar.data.entity.Reminder) {
        // Intent to open Main Activity -> Reminders Fragment?
        // Or specific detail? Reminders are simple. Opening app is enough.
        // We can create an intent that triggers a specific navigation in MainActivity if we want.
        val intent = android.content.Intent(context, app.polar.MainActivity::class.java).apply {
             flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
             // Helper extra to navigate
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
            .setContentTitle("Reminder: ${reminder.title}")
            .setContentText(reminder.description)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            
        try {
           with(NotificationManagerCompat.from(context)) {
               notify((reminder.id + 1000000).toInt(), builder.build())
           }
        } catch (e: SecurityException) {
        }
    }
}
