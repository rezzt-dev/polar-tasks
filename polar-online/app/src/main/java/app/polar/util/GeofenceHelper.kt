package app.polar.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.location.LocationManagerCompat
import app.polar.data.entity.Reminder
import app.polar.receiver.AlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Aviso al llegar al lugar de un recordatorio. Usa las alertas de proximidad de [LocationManager]
 * (sin Google Play Services ni red): el sistema avisa a [AlarmReceiver] al entrar en el radio. Las
 * alertas no sobreviven a un reinicio, así que [AlarmRescheduler] las reconstruye junto a las alarmas.
 *
 * Registrar una alerta estando ya dentro de la zona avisa al momento, así que [sync] solo la
 * registra si no existe o si la zona ha cambiado: se puede llamar en cada edición o al volver a la
 * pantalla sin repetir el aviso.
 */
class GeofenceHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    // Zona con la que se registró cada alerta (clave: id del recordatorio)
    private val registered = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Registra la alerta de [reminder] si debe avisar al llegar; si no, la retira. */
    fun sync(reminder: Reminder) {
        if (isEligible(reminder)) register(reminder) else cancel(reminder.id)
    }

    @SuppressLint("MissingPermission") // comprobado en hasLocationPermission()
    private fun register(reminder: Reminder) {
        val latitude = reminder.latitude ?: return
        val longitude = reminder.longitude ?: return
        // Sin permiso no se puede registrar; se reintenta al volver a la pantalla de recordatorios.
        if (!hasLocationPermission(context)) return
        val radius = reminder.radius ?: DEFAULT_RADIUS_METERS
        val zone = "$latitude,$longitude,$radius"
        val key = reminder.id.toString()
        if (registered.getString(key, null) == zone && existingPendingIntent(reminder.id) != null) return
        try {
            // Registrar con el mismo PendingIntent sustituye la alerta anterior del recordatorio.
            locationManager.addProximityAlert(latitude, longitude, radius, NO_EXPIRATION, pendingIntent(reminder.id))
            registered.edit { putString(key, zone) }
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: IllegalArgumentException) {
            // Coordenadas o radio fuera de rango (introducidos a mano).
            e.printStackTrace()
        }
    }

    fun cancel(reminderId: Long) {
        registered.edit { remove(reminderId.toString()) }
        val pendingIntent = existingPendingIntent(reminderId) ?: return
        try {
            locationManager.removeProximityAlert(pendingIntent)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
        pendingIntent.cancel()
    }

    // El PendingIntent se localiza por request code y componente; los extras no cuentan. Existe
    // mientras la alerta está registrada (no sobrevive a un reinicio ni a forzar la detención).
    private fun existingPendingIntent(reminderId: Long): PendingIntent? = PendingIntent.getBroadcast(
        context, REQUEST_OFFSET + reminderId.toInt(), Intent(context, AlarmReceiver::class.java),
        pendingIntentFlags(PendingIntent.FLAG_NO_CREATE)
    )

    private fun pendingIntent(reminderId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_TYPE, AlarmReceiver.TYPE_REMINDER_ARRIVAL)
            putExtra(AlarmReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context, REQUEST_OFFSET + reminderId.toInt(), intent, pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
        )
    }

    // El sistema añade KEY_PROXIMITY_ENTERING al intent, así que debe ser mutable (explícito, a un
    // receptor no exportado de la propia app).
    private fun pendingIntentFlags(flags: Int): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags or PendingIntent.FLAG_MUTABLE else flags

    companion object {
        const val DEFAULT_RADIUS_METERS = 200f
        /** Radios que ofrece el formulario, en metros. */
        val RADIUS_OPTIONS = listOf(100f, 200f, 500f, 1000f)

        // Mismo esquema de request codes que AlarmManagerHelper, en un espacio propio.
        private const val REQUEST_OFFSET = 3_000_000
        private const val NO_EXPIRATION = -1L
        private const val PREFS_NAME = "arrival_alerts"

        fun isEligible(reminder: Reminder): Boolean =
            reminder.notifyOnArrival && reminder.latitude != null && reminder.longitude != null &&
                !reminder.isCompleted && !reminder.isDeleted

        fun hasLocationPermission(context: Context): Boolean =
            isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
                isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

        /** Desde Android 10 el aviso solo llega con la app cerrada si la ubicación se permite "todo el tiempo". */
        fun hasBackgroundPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                isGranted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

        fun canNotifyOnArrival(context: Context): Boolean =
            hasLocationPermission(context) && hasBackgroundPermission(context)

        fun isLocationEnabled(context: Context): Boolean {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            return LocationManagerCompat.isLocationEnabled(manager)
        }

        private fun isGranted(context: Context, permission: String): Boolean =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
