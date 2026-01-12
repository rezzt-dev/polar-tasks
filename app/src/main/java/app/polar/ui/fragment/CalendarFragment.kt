package app.polar.ui.fragment

import android.content.DialogInterface
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.polar.R
import app.polar.data.entity.Task
import app.polar.databinding.FragmentCalendarBinding
import app.polar.ui.adapter.CalendarAdapter
import app.polar.ui.adapter.TaskAdapter
import app.polar.ui.viewmodel.TaskViewModel
import java.util.Calendar
import java.util.Locale

class CalendarFragment : Fragment() {
    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TaskViewModel by activityViewModels()
    private lateinit var calendarAdapter: CalendarAdapter
    private lateinit var tasksAdapter: TaskAdapter
    
    private var currentCalendar = Calendar.getInstance()
    private var daysList = listOf<Long?>()
    private var monthTasksMap = mapOf<Long, List<Task>>()
    private var selectedDate: Long = System.currentTimeMillis() // Default to today

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        setupNavigation()
        observeTasks()
        
        // Initial load
        updateCalendar()
    }

    private fun setupRecyclerViews() {
        // 1. Calendar Grid
        calendarAdapter = CalendarAdapter { date ->
            selectDate(date)
        }
        binding.recyclerCalendar.layoutManager = GridLayoutManager(context, 7)
        binding.recyclerCalendar.adapter = calendarAdapter
        
        // 2. Bottom Task List
        tasksAdapter = TaskAdapter(
            lifecycleOwner = viewLifecycleOwner,
            viewModel = viewModel,
            onCheckChanged = { task, _ -> viewModel.toggleTaskCompletion(task) },
            onItemLongClick = { 
                // Optional: Show edit on long press?
                // For now return false or implement editing
                false 
            },
            onItemClick = { task ->
               val intent = android.content.Intent(requireContext(), app.polar.ui.activity.TaskDetailActivity::class.java)
               intent.putExtra(app.polar.ui.activity.TaskDetailActivity.EXTRA_TASK_ID, task.id)
               startActivity(intent)
            }
        )
        binding.recyclerDayTasks.layoutManager = LinearLayoutManager(context)
        binding.recyclerDayTasks.adapter = tasksAdapter
    }

    private fun setupNavigation() {
        binding.btnPrevMonth.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, -1)
            updateCalendar()
        }
        
        binding.btnNextMonth.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, 1)
            updateCalendar()
        }
    }
    
    private fun observeTasks() {
        viewModel.calendarTasks.observe(viewLifecycleOwner) { tasks ->
            // Map tasks to start-of-day timestamp
            monthTasksMap = tasks.groupBy { task ->
                task.dueDate?.let { getStartOfDay(it) } ?: -1L
            }
            // Update calendar grid (dots)
            calendarAdapter.submitData(daysList, monthTasksMap, selectedDate)
            
            // Update bottom list for currently selected date
            updateTasksForSelectedDate()
        }
    }

    private fun selectDate(date: Long) {
        selectedDate = date
        // Update Calendar UI (highlight selected)
        calendarAdapter.submitData(daysList, monthTasksMap, selectedDate)
        // Update Bottom List
        updateTasksForSelectedDate()
    }

    private fun updateTasksForSelectedDate() {
        val dayStart = getStartOfDay(selectedDate)
        val dayTasks = monthTasksMap[dayStart] ?: emptyList()
        
        // Map to TaskListItem
        val items = dayTasks.map { app.polar.ui.adapter.TaskListItem.Item(it) }
        tasksAdapter.submitList(items)
        
        if (dayTasks.isEmpty()) {
            binding.tvNoTasks.visibility = View.VISIBLE
            binding.recyclerDayTasks.visibility = View.INVISIBLE
        } else {
            binding.tvNoTasks.visibility = View.GONE
            binding.recyclerDayTasks.visibility = View.VISIBLE
        }
        
        // Update "Tasks for [Date]" title
        val format = java.text.SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
        val dateString = format.format(java.util.Date(selectedDate)).replaceFirstChar { it.uppercase() }
        // We can just set it to the date
        binding.tvSelectedDateTasks.text = dateString
    }

    private fun updateCalendar() {
        // Update Title (Spanish/Locale respected)
        val dateFormat = java.text.SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        binding.tvMonthYear.text = dateFormat.format(currentCalendar.time).replaceFirstChar { it.uppercase() }

        // Generate Days
        daysList = generateMonthDays(currentCalendar)
        
        // Calculate range for query
        val rangeStart = getMonthStart(currentCalendar)
        val rangeEnd = getMonthEnd(currentCalendar)
        
        // Trigger data load
        viewModel.setCalendarRange(rangeStart, rangeEnd)
        
        // Update adapter immediately
        calendarAdapter.submitData(daysList, monthTasksMap, selectedDate)
    }

    private fun generateMonthDays(cal: Calendar): List<Long?> {
        val days = mutableListOf<Long?>()
        val c = cal.clone() as Calendar
        
        c.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = c.get(Calendar.DAY_OF_WEEK) // Sun=1, Mon=2...
        
        // Determine start offset (Start on Monday = 2)
        // Mon(2) -> 0 offset
        // Sun(1) -> 6 offset
        // Tue(3) -> 1 offset
        val offset = if (firstDayOfWeek == Calendar.SUNDAY) 6 else firstDayOfWeek - 2
        
        for (i in 0 until offset) {
            days.add(null)
        }
        
        val maxDays = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (i in 1..maxDays) {
            days.add(c.timeInMillis)
            c.add(Calendar.DAY_OF_MONTH, 1)
        }
        
        return days
    }
    
    private fun getMonthStart(cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
    
    private fun getMonthEnd(cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH))
        c.set(Calendar.HOUR_OF_DAY, 23)
        c.set(Calendar.MINUTE, 59)
        c.set(Calendar.SECOND, 59)
        return c.timeInMillis
    }
    
    private fun getStartOfDay(date: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = date }
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
