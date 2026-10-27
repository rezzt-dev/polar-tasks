package app.polar.util

import android.content.Context
import app.polar.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    // ThreadLocal Calendars to avoid excessive allocations in onBindViewHolder while keeping thread safety
    private val calendarCache = object : ThreadLocal<Calendar>() {
        override fun initialValue(): Calendar = Calendar.getInstance()
    }

    fun formatTaskDate(context: Context, dueDateMs: Long): String {
        val now = calendarCache.get()!!
        now.timeInMillis = System.currentTimeMillis()
        
        val dueDate = Calendar.getInstance()
        dueDate.timeInMillis = dueDateMs
        
        val isToday = now.get(Calendar.YEAR) == dueDate.get(Calendar.YEAR) &&
                      now.get(Calendar.DAY_OF_YEAR) == dueDate.get(Calendar.DAY_OF_YEAR)
                      
        val isTomorrow = if (!isToday) {
            val tomorrow = Calendar.getInstance()
            tomorrow.timeInMillis = now.timeInMillis
            tomorrow.add(Calendar.DAY_OF_YEAR, 1)
            tomorrow.get(Calendar.YEAR) == dueDate.get(Calendar.YEAR) &&
            tomorrow.get(Calendar.DAY_OF_YEAR) == dueDate.get(Calendar.DAY_OF_YEAR)
        } else false

        return when {
            isToday -> context.getString(R.string.today)
            isTomorrow -> context.getString(R.string.tomorrow)
            // Formato creado por llamada: respeta el idioma activo y SimpleDateFormat no es thread-safe.
            // Patrón localizado ("22 oct", "oct 22"...) y con año solo si no es el actual.
            else -> {
                val locale = Locale.getDefault()
                val skeleton = if (now.get(Calendar.YEAR) == dueDate.get(Calendar.YEAR)) "dMMM" else "dMMMy"
                val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
                SimpleDateFormat(pattern, locale).format(Date(dueDateMs)).lowercase(locale)
            }
        }
    }

    /** "hoy · 10:30", "mañana · 7:30", "5 nov · 18:00": día como [formatTaskDate] y hora del sistema (12/24 h). */
    fun formatDateTime(context: Context, millis: Long): String {
        val time = android.text.format.DateFormat.getTimeFormat(context).format(Date(millis))
        return "${formatTaskDate(context, millis)} · $time".lowercase(Locale.getDefault())
    }

    fun isOverdue(dueDateMs: Long): Boolean {
        val now = calendarCache.get()!!
        now.timeInMillis = System.currentTimeMillis()
        
        val dueDate = Calendar.getInstance()
        dueDate.timeInMillis = dueDateMs
        
        val isToday = now.get(Calendar.YEAR) == dueDate.get(Calendar.YEAR) &&
                      now.get(Calendar.DAY_OF_YEAR) == dueDate.get(Calendar.DAY_OF_YEAR)
                      
        now.set(Calendar.HOUR_OF_DAY, 0)
        now.set(Calendar.MINUTE, 0)
        now.set(Calendar.SECOND, 0)
        now.set(Calendar.MILLISECOND, 0)
        
        return dueDateMs < now.timeInMillis && !isToday
    }
    
    fun isToday(dueDateMs: Long): Boolean {
        val now = calendarCache.get()!!
        now.timeInMillis = System.currentTimeMillis()
        
        val dueDate = Calendar.getInstance()
        dueDate.timeInMillis = dueDateMs
        
        return now.get(Calendar.YEAR) == dueDate.get(Calendar.YEAR) &&
               now.get(Calendar.DAY_OF_YEAR) == dueDate.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * Formats a date smartly. If the time is exactly 00:00, it omits the time component.
     * Useful for due dates where only the day was specified.
     */
    fun formatTimeEstimate(minutes: Int): String {
        return if (minutes < 60) {
            "${minutes} min"
        } else {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0) "${h}h" else "${h}h ${m}m"
        }
    }

    fun formatSmartDate(timeInMillis: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timeInMillis
        val hasTime = cal.get(Calendar.HOUR_OF_DAY) != 0 || cal.get(Calendar.MINUTE) != 0
        
        // Patrón propio de cada idioma ("5 de octubre de 2026" / "5 October 2026"...).
        val locale = Locale.getDefault()
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(
            locale, if (hasTime) "dMMMMyyyyHHmm" else "dMMMMyyyy"
        )
        return SimpleDateFormat(pattern, locale).format(Date(timeInMillis)).lowercase(locale)
    }
}
