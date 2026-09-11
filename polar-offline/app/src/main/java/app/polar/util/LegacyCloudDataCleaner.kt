package app.polar.util

import android.content.Context
import androidx.work.WorkManager

/**
 * Elimina, una única vez, el rastro que la sincronización en la nube de la v1.6 dejó en el
 * dispositivo al actualizar a una versión 100 % local:
 * - los trabajos de sincronización que WorkManager mantiene persistidos y que ya no tienen
 *   ninguna clase que los ejecute;
 * - las preferencias con el estado de la sincronización;
 * - la sesión de la cuenta (tokens de acceso) guardada en las preferencias por defecto.
 */
object LegacyCloudDataCleaner {

    private const val APP_PREFS = "app_prefs"
    private const val KEY_CLEANED = "legacy_cloud_data_cleaned"

    private val LEGACY_WORK_NAMES = listOf("SyncWorkerPeriodic", "SyncWorkerOneTime", "SyncWorkerFrequent")
    private const val LEGACY_SYNC_PREFS = "sync_prefs"
    // Claves `sb-<proyecto>-session` y `sb-<proyecto>-code-verifier` de la sesión antigua.
    private const val LEGACY_SESSION_KEY_PREFIX = "sb-"

    fun cleanIfNeeded(context: Context) {
        val appPrefs = context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
        if (appPrefs.getBoolean(KEY_CLEANED, false)) return

        try {
            val workManager = WorkManager.getInstance(context)
            LEGACY_WORK_NAMES.forEach { workManager.cancelUniqueWork(it) }

            context.deleteSharedPreferences(LEGACY_SYNC_PREFS)

            val defaultPrefs = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
            val sessionKeys = defaultPrefs.all.keys.filter { it.startsWith(LEGACY_SESSION_KEY_PREFIX) }
            if (sessionKeys.isNotEmpty()) {
                defaultPrefs.edit().apply { sessionKeys.forEach { remove(it) } }.apply()
            }

            appPrefs.edit().putBoolean(KEY_CLEANED, true).apply()
        } catch (e: Exception) {
            // Sin marcar como hecho: se vuelve a intentar en el siguiente arranque.
            e.printStackTrace()
        }
    }
}
