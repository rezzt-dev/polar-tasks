package app.polar.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.polar.di.SyncEntryPoint
import dagger.hilt.android.EntryPointAccessors
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

// Pushes dirty rows then pulls remote changes (see SyncManager). No-ops if the user isn't signed
// in, so it's safe to schedule unconditionally. Uses the same manual EntryPointAccessors pattern
// as RecurrenceWorker rather than @HiltWorker, for consistency with the rest of the project.
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, SyncEntryPoint::class.java)
        try {
            val result = entryPoint.getSyncManager().sync()
            // Re-arms the next 3-minute tick from inside the worker itself rather than a
            // PeriodicWorkRequest, since WorkManager enforces a 15-minute floor on those — see
            // scheduleFrequentSync(). Only kept alive while a session exists, so a signed-out
            // device isn't woken up every 3 minutes for nothing.
            if (entryPoint.getSupabaseClient().auth.currentUserOrNull() != null) {
                scheduleFrequentSync(applicationContext)
            }
            if (result.isSuccess) Result.success() else Result.retry()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "SyncWorkerPeriodic"
        private const val ONE_TIME_WORK_NAME = "SyncWorkerOneTime"
        private const val FREQUENT_WORK_NAME = "SyncWorkerFrequent"
        private const val FREQUENT_SYNC_INTERVAL = 3L

        private fun networkConstraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // 15-minute safety net: always scheduled (even signed out, sync() no-ops then), and
        // self-heals the 3-minute chain below if it were ever dropped (e.g. WorkManager state
        // loss), since doWork() re-arms scheduleFrequentSync() on every run while signed in.
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun triggerImmediateSync(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        // Approximates a 3-minute sync cadence while signed in. PeriodicWorkRequest can't go
        // below 15 minutes (Android platform floor), so instead a short-delay OneTimeWorkRequest
        // re-enqueues itself (via REPLACE) from doWork() each time it completes. Safe to call
        // repeatedly/concurrently — REPLACE keeps only the latest scheduled tick.
        fun scheduleFrequentSync(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInitialDelay(FREQUENT_SYNC_INTERVAL, TimeUnit.MINUTES)
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                FREQUENT_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        // Stops the 3-minute chain (called on sign-out); the 15-minute safety net keeps running
        // regardless since it's harmless while signed out.
        fun cancelFrequentSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(FREQUENT_WORK_NAME)
        }
    }
}
