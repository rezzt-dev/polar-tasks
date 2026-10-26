package app.polar.ui.adapter

import android.graphics.Color
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.polar.data.entity.Task
import app.polar.databinding.ItemCalendarDayBinding
import app.polar.R
import java.util.Calendar
import com.google.android.material.R as MaterialR

class CalendarAdapter(
    private val onDayClick: (Long) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder>() {

    private var days = listOf<Long?>()
    private var tasks = mapOf<Long, List<Task>>()
    private var reminders = mapOf<Long, List<app.polar.data.entity.Reminder>>()
    private var selectedDate: Long? = null

    fun submitData(
        days: List<Long?>, 
        tasks: Map<Long, List<Task>>, 
        reminders: Map<Long, List<app.polar.data.entity.Reminder>>,
        selected: Long?
    ) {
        this.days = days
        this.tasks = tasks
        this.reminders = reminders
        this.selectedDate = selected
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarViewHolder {
        val binding = ItemCalendarDayBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CalendarViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) {
        holder.bind(days[position])
    }

    override fun getItemCount(): Int = days.size

    inner class CalendarViewHolder(private val binding: ItemCalendarDayBinding) : RecyclerView.ViewHolder(binding.root) {
        
    fun bind(date: Long?) {
            if (date == null) {
                binding.root.visibility = View.INVISIBLE
                binding.root.setOnClickListener(null)
                return
            }

            binding.root.visibility = View.VISIBLE
            val calendar = Calendar.getInstance().apply { timeInMillis = date }
            val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
            binding.tvDay.text = dayOfMonth.toString()

            val isToday = DateUtils.isToday(date)
            val isSelected = selectedDate != null && isSameDay(date, selectedDate!!)

            // Resolve Theme Colors
            val typedValue = android.util.TypedValue()
            val theme = binding.root.context.theme
            
            // colorOnSurface (Default Text)
            // Use Material R explicitly
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
            val colorOnSurface = typedValue.data
            
            // colorPrimary (Today/Selected Background)
            // Use Android Framework R for colorPrimary
            theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
            val colorPrimary = typedValue.data
            
            // colorOnPrimary (Text on Primary)
            // Use Material R explicitly
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
            val colorOnPrimary = typedValue.data

            // Seleccionado: círculo relleno. Hoy sin seleccionar: anillo y número en negrita.
            // El tipo de letra se conserva (solo cambia el estilo) para respetar la fuente elegida.
            val baseTypeface = android.graphics.Typeface.create(binding.tvDay.typeface, android.graphics.Typeface.NORMAL)
            binding.bgDay.visibility = View.INVISIBLE
            binding.bgDay.alpha = 1.0f
            binding.tvDay.alpha = 1.0f
            binding.tvDay.setTextColor(colorOnSurface)
            binding.tvDay.setTypeface(baseTypeface, android.graphics.Typeface.NORMAL)

            when {
                isSelected -> {
                    binding.bgDay.setBackgroundResource(R.drawable.bg_calendar_circle)
                    binding.bgDay.background.setTint(colorPrimary)
                    binding.bgDay.visibility = View.VISIBLE
                    binding.tvDay.setTextColor(colorOnPrimary)
                    binding.tvDay.setTypeface(baseTypeface, android.graphics.Typeface.BOLD)
                }
                isToday -> {
                    binding.bgDay.setBackgroundResource(R.drawable.bg_calendar_ring)
                    binding.bgDay.background.setTint(colorPrimary)
                    binding.bgDay.visibility = View.VISIBLE
                    binding.tvDay.setTextColor(colorPrimary)
                    binding.tvDay.setTypeface(baseTypeface, android.graphics.Typeface.BOLD)
                }
            }

            // Punto de actividad: tenue si todo lo del día está completado
            val dayTasks = tasks[getStartOfDay(date)]
            val dayReminders = reminders[getStartOfDay(date)]

            if (!dayTasks.isNullOrEmpty() || !dayReminders.isNullOrEmpty()) {
                binding.dotIndicator.visibility = View.VISIBLE
                binding.dotIndicator.background.setTint(colorOnSurface)

                val allTasksCompleted = dayTasks?.all { it.completed } ?: true
                val allRemindersCompleted = dayReminders?.all { it.isCompleted } ?: true
                binding.dotIndicator.alpha = if (allTasksCompleted && allRemindersCompleted) 0.3f else 0.9f
            } else {
                binding.dotIndicator.visibility = View.INVISIBLE
            }

            binding.root.setOnClickListener {
                onDayClick(date)
            }
        }
    }
    
    private fun isSameDay(date1: Long, date2: Long): Boolean {
        val c1 = Calendar.getInstance().apply { timeInMillis = date1 }
        val c2 = Calendar.getInstance().apply { timeInMillis = date2 }
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
               c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }
    
    private fun getStartOfDay(date: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = date }
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
}
