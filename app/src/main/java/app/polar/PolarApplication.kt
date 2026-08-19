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

    runSyncLeftoverCleanup()
  }

  /**
   * Limpieza única de restos de la sincronización Supabase en dispositivos que ejecutaron
   * una build 1.6: trabajos de WorkManager cuya clase ya no existe, preferencias de sync,
   * sesión persistida del SDK (fichero "<packageName>_preferences", el nombre que usa por
   * defecto `Settings()` de multiplatform-settings en Android) y caché de imágenes de Storage.
   * Ver agent-docs/eliminacion-supabase/ (Fase 5).
   */
  private fun runSyncLeftoverCleanup() {
    val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
    if (prefs.getBoolean("sync_cleanup_done", false)) return

    androidx.work.WorkManager.getInstance(this).apply {
      cancelUniqueWork("SyncWorkerPeriodic")
      cancelUniqueWork("SyncWorkerOneTime")
      cancelUniqueWork("SyncWorkerFrequent")
    }
    deleteSharedPreferences("sync_prefs")
    deleteSharedPreferences("${packageName}_preferences")
    java.io.File(cacheDir, "task_images").deleteRecursively()

    prefs.edit().putBoolean("sync_cleanup_done", true).apply()
  }
}
