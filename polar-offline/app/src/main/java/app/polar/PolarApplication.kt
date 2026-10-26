package app.polar

import android.app.Application
import androidx.room.InvalidationTracker
import app.polar.data.AppDatabase
import app.polar.widget.TaskWidgetProvider
import dagger.hilt.android.HiltAndroidApp
import app.polar.util.LegacyCloudDataCleaner
import app.polar.util.NotificationHelper

@HiltAndroidApp
class PolarApplication : Application() {

  override fun onCreate() {
    super.onCreate()

    // Initialize ThemeManager to apply saved theme preference immediately
    val themeManager = app.polar.util.ThemeManager(this)
    themeManager.applyTheme(themeManager.loadTheme())

    NotificationHelper.createNotificationChannel(this)

    LegacyCloudDataCleaner.cleanIfNeeded(this)

    scheduleRecurrenceWorker()
    observeTasksForWidget()
  }

  // Cualquier escritura en `tasks` (app, notificaciones, workers, copias) refresca el widget, sin
  // que cada punto de escritura tenga que acordarse de avisarle.
  private fun observeTasksForWidget() {
    AppDatabase.getDatabase(this).invalidationTracker.addObserver(
      object : InvalidationTracker.Observer("tasks") {
        override fun onInvalidated(tables: Set<String>) {
          TaskWidgetProvider.notifyDataChanged(this@PolarApplication)
        }
      }
    )
  }

  // Se revisa cada hora (mínimo razonable de WorkManager) para que una tarea recurrente reaparezca
  // poco después de medianoche, y una vez al abrir la app para ponerse al día de inmediato.
  private fun scheduleRecurrenceWorker() {
    val workManager = androidx.work.WorkManager.getInstance(this)

    val periodicRequest = androidx.work.PeriodicWorkRequestBuilder<app.polar.worker.RecurrenceWorker>(
      1, java.util.concurrent.TimeUnit.HOURS
    ).build()
    // UPDATE (no KEEP) para que las instalaciones previas dejen atrás el intervalo antiguo de 12 h.
    workManager.enqueueUniquePeriodicWork(
      "RecurrenceWorker",
      androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
      periodicRequest
    )

    workManager.enqueueUniqueWork(
      "RecurrenceWorkerOnStart",
      androidx.work.ExistingWorkPolicy.REPLACE,
      androidx.work.OneTimeWorkRequestBuilder<app.polar.worker.RecurrenceWorker>().build()
    )
  }
}
