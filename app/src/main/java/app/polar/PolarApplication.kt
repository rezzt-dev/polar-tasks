package app.polar

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import app.polar.util.NotificationHelper

@HiltAndroidApp
class PolarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize ThemeManager to apply saved theme preference immediately
        val themeManager = app.polar.util.ThemeManager(this)
        themeManager.applyTheme(themeManager.loadTheme())
        
        NotificationHelper.createNotificationChannel(this)
        
        // Schedule Recurrence Worker (runs periodically to check for tasks to reset)
        val workRequest = androidx.work.PeriodicWorkRequestBuilder<app.polar.worker.RecurrenceWorker>(
            12, java.util.concurrent.TimeUnit.HOURS
        ).build()
        
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "RecurrenceWorker",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
