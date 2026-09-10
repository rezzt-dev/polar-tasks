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

    // Feeds the combine() below, which is the single place that decides when to fire an
    // immediate sync (see its comment) — every foreground transition changes the (authenticated,
    // foreground) pair it collects on, so a plain onStart()/onStop() toggle here is enough;
    // triggering a sync directly from onStart() too (an earlier version of this code did) raced
    // the combine flow's own trigger on cold start, since both fire around the same moment once
    // the session finishes restoring — enqueueUniqueWork's REPLACE policy would then cancel and
    // restart an already-running sync worker, roughly doubling how long the user actually waited
    // to see fresh data after opening the app.
    val appInForeground = MutableStateFlow(false)
    ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
      override fun onStart(owner: LifecycleOwner) {
        appInForeground.value = true
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
            // Sole trigger for an immediate sync (see the appInForeground observer above for why
            // it's not also fired from onStart() directly): fires once per genuine transition
            // into "authenticated" — cold start once the async Supabase session restore
            // completes, a fresh login, a token-refresh recovery — and also every time the app
            // comes back to the foreground while already authenticated, since a foreground toggle
            // changes the (authenticated, foreground) pair this collector runs on.
            app.polar.worker.SyncWorker.triggerImmediateSync(this@PolarApplication)
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
