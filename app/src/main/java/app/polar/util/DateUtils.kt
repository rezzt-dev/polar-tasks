package app.polar.util

import android.content.Context
import app.polar.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    private val dateFormat = SimpleDateFormat("d, MMM", Locale("es", "ES"))
    
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
            else -> dateFormat.format(Date(dueDateMs)).lowercase()
        }
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
    fun formatSmartDate(timeInMillis: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timeInMillis
        val hasTime = cal.get(Calendar.HOUR_OF_DAY) != 0 || cal.get(Calendar.MINUTE) != 0
        
        val format = if (hasTime) {
            SimpleDateFormat("d 'de' MMMM 'de' yyyy, HH:mm", Locale.getDefault())
        } else {
            SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale.getDefault())
        }
        
        return format.format(Date(timeInMillis))
    }
}
