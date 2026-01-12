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
    private var selectedDate: Long? = null

    fun submitData(days: List<Long?>, tasks: Map<Long, List<Task>>, selected: Long?) {
        this.days = days
        this.tasks = tasks
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

            // Reset styles
            binding.bgDay.visibility = View.INVISIBLE
            binding.tvDay.setTextColor(colorOnSurface) // Set correctly for Light/Dark
            binding.tvDay.alpha = 1.0f
            binding.tvDay.typeface = android.graphics.Typeface.DEFAULT

            if (isToday) {
                 binding.bgDay.visibility = View.VISIBLE
                 binding.bgDay.background.setTint(colorPrimary)
                 binding.bgDay.alpha = 1.0f
                 
                 binding.tvDay.setTextColor(colorOnPrimary)
                 binding.tvDay.typeface = android.graphics.Typeface.DEFAULT_BOLD
            } else if (isSelected) {
                 binding.bgDay.visibility = View.VISIBLE
                 binding.bgDay.background.setTint(colorPrimary)
                 binding.bgDay.alpha = 0.2f 
                 binding.tvDay.setTextColor(colorPrimary) // Colored text for selection
                 binding.tvDay.typeface = android.graphics.Typeface.DEFAULT_BOLD
            } else {
                 // Normal day
                 binding.tvDay.alpha = 0.8f
            }

            // Dots
            val dayTasks = tasks[getStartOfDay(date)]
            if (!dayTasks.isNullOrEmpty()) {
                binding.dotIndicator.visibility = View.VISIBLE
                
                // Dot color should match text color usually, or be accent
                if (isToday) {
                     binding.dotIndicator.background.setTint(colorOnPrimary)
                } else if (isSelected) {
                     binding.dotIndicator.background.setTint(colorPrimary)
                } else {
                     binding.dotIndicator.background.setTint(colorOnSurface)
                }
                
                val allCompleted = dayTasks.all { it.completed }
                binding.dotIndicator.alpha = if (allCompleted) 0.3f else 1.0f
            } else {
                binding.dotIndicator.visibility = View.GONE
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
