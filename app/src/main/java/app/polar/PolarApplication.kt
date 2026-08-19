package app.polar

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.HiltAndroidApp
import app.polar.util.NotificationHelper
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltAndroidApp
class PolarApplication : Application() {
  // Lives for the whole process, unlike a ViewModelScope which only exists while some screen is
  // open — needed so session changes are reacted to no matter what screen (or none) is visible.
  private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

    // Cloud sync (see agent-docs/supabase-sync/06-plan-implementacion-android.md). Safe to
    // schedule even when the user has never signed in: SyncManager.sync() no-ops without a
    // Supabase session.
    app.polar.worker.SyncWorker.schedulePeriodic(this)

    val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
      this, app.polar.di.SyncEntryPoint::class.java
    )

    // Fixes the "opening Polar shows stale data" gap (agent-docs/
    // analisis-implementacion-supabase-sync.md, hallazgo 2): without this, a pull only ever
    // happened as a side effect of the user editing something locally, or once one of the
    // background timers (3/15 min) happened to fire. ProcessLifecycleOwner.onStart() covers both
    // cold start and resuming from the recents list (unlike Application.onCreate(), which only
    // runs once per process), so every time the app comes to the foreground with a session
    // active it kicks off an immediate pull. triggerImmediateSync() uses REPLACE, so rapid
    // foreground/background flapping just collapses into the latest request instead of queuing
    // sync storms. appInForeground also drives the Realtime subscription below.
    val appInForeground = MutableStateFlow(false)
    ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
      override fun onStart(owner: LifecycleOwner) {
        appInForeground.value = true
        if (entryPoint.getSupabaseClient().auth.currentUserOrNull() != null) {
          app.polar.worker.SyncWorker.triggerImmediateSync(this@PolarApplication)
        }
      }

      override fun onStop(owner: LifecycleOwner) {
        appInForeground.value = false
      }
    })

    // Drives the 3-minute sync chain and the Realtime subscription (agent-docs/
    // analisis-implementacion-supabase-sync.md, hallazgo 4.11 and hallazgo 4.1/Fase 6) from
    // session state and foreground state combined, instead of a boolean re-read at process start:
    // any transition into Authenticated (fresh login, restored session on cold start, token
    // refresh) (re)arms the 3-minute chain, and any transition out (sign-out, revoked/expired
    // refresh) tears it down — reactively, from wherever in the app it happens, not just from
    // AuthViewModel.signIn()/signOut(). RefreshFailure counts as "not authenticated" here since
    // the session is no longer usable for sync even though Supabase hasn't cleared it outright.
    // Realtime additionally requires the app to be in the foreground — it's a live websocket
    // subscription, only worth holding open while the user could actually see an update land — so
    // it's started/stopped on the combination of both signals, whichever changes first.
    applicationScope.launch {
      combine(
        entryPoint.getSupabaseClient().auth.sessionStatus
          .map { it is SessionStatus.Authenticated }
          .distinctUntilChanged(),
        appInForeground
      ) { authenticated, foreground -> authenticated to foreground }
        .distinctUntilChanged()
        .collect { (authenticated, foreground) ->
          if (authenticated) {
            app.polar.worker.SyncWorker.scheduleFrequentSync(this@PolarApplication)
          } else {
            app.polar.worker.SyncWorker.cancelFrequentSync(this@PolarApplication)
          }
          if (authenticated && foreground) {
            entryPoint.getSyncManager().startRealtime()
          } else {
            entryPoint.getSyncManager().stopRealtime()
          }
        }
    }
  }
}
