package app.polar.data.sync

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

// Sync cursor + status storage. Plain SharedPreferences, consistent with how TaskViewModel already
// persists sort_mode (see agent-docs/supabase-sync/06-plan-implementacion-android.md, punto 6).
class SyncPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    var lastSyncAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_AT, value).apply()

    // Last time sync() actually completed successfully (push+pull both went through), regardless
    // of whether anything changed — distinct from lastSyncAt, which is the pull cursor. Drives the
    // "última sincronización" indicator in Settings
    // (agent-docs/analisis-implementacion-supabase-sync.md, hallazgo 4.9).
    var lastSyncSuccessAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC_SUCCESS_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_SUCCESS_AT, value).apply()

    // Message of the most recent failed sync() attempt, or null once a sync succeeds again. Lets
    // Settings surface a persistent "sync has been failing" state instead of the silent
    // Result.retry() loop SyncWorker used to leave the user with (hallazgo 4.9).
    var lastSyncError: String?
        get() = prefs.getString(KEY_LAST_SYNC_ERROR, null)
        set(value) = prefs.edit().putString(KEY_LAST_SYNC_ERROR, value).apply()

    // Cumulative count of local rows that lost a Last-Write-Wins conflict and got silently
    // overwritten by the server's version (hallazgo 4.3), since the user last dismissed the
    // warning in Settings. SyncManager adds to this on every push(); Settings resets it to 0 once
    // shown and dismissed.
    var lostConflictsCount: Int
        get() = prefs.getInt(KEY_LOST_CONFLICTS_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_LOST_CONFLICTS_COUNT, value).apply()

    // Emits once on collection and again on every write to any key in this prefs file, so UI can
    // react to background sync updates (e.g. SyncWorker finishing) the same way SettingsFragment
    // already reacts to supabaseClient.auth.sessionStatus (hallazgo 4.11) instead of only
    // re-reading on onResume().
    fun changes(): Flow<Unit> = callbackFlow {
        trySend(Unit)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val KEY_LAST_SYNC_SUCCESS_AT = "last_sync_success_at"
        private const val KEY_LAST_SYNC_ERROR = "last_sync_error"
        private const val KEY_LOST_CONFLICTS_COUNT = "lost_conflicts_count"
    }
}
