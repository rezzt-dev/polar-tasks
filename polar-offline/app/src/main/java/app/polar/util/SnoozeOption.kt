package app.polar.util

import java.util.Calendar

/**
 * Opciones rápidas para posponer un aviso. [at] devuelve el momento al que se pospone, redondeado
 * al minuto, o `null` si la opción no tiene sentido ahora ("esta tarde" cuando ya es tarde).
 */
enum class SnoozeOption {
    MINUTES_15,
    HOUR_1,
    HOURS_3,
    THIS_EVENING,
    TOMORROW;

    /**
     * @param defaultHour hora de aviso por defecto de los ajustes, usada para "mañana".
     */
    fun at(now: Long, defaultHour: Int, defaultMinute: Int): Long? {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        when (this) {
            MINUTES_15 -> calendar.add(Calendar.MINUTE, 15)
            HOUR_1 -> calendar.add(Calendar.HOUR_OF_DAY, 1)
            HOURS_3 -> calendar.add(Calendar.HOUR_OF_DAY, 3)
            THIS_EVENING -> {
                // Solo mientras queda al menos una hora hasta entonces: si no, "en 1 hora" ya lo cubre
                if (calendar.get(Calendar.HOUR_OF_DAY) >= EVENING_HOUR - 1) return null
                calendar.set(Calendar.HOUR_OF_DAY, EVENING_HOUR)
                calendar.set(Calendar.MINUTE, 0)
            }
            TOMORROW -> {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, defaultHour)
                calendar.set(Calendar.MINUTE, defaultMinute)
            }
        }
        return calendar.timeInMillis
    }

    companion object {
        const val EVENING_HOUR = 18
    }
}
