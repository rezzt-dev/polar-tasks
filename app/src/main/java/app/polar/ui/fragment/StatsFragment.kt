package app.polar.ui.fragment

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import app.polar.R
import app.polar.databinding.FragmentStatsBinding
import app.polar.ui.viewmodel.TaskViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class StatsFragment : Fragment() {
    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TaskViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadStats()
    }

    private fun loadStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            val repository = viewModel.repository

            // Basic counts
            val totalTasks = repository.getTotalTaskCount()
            val completedTasks = repository.getCompletedTaskCount()
            val pendingTasks = repository.getPendingTaskCount()
            val overdueTasks = repository.getOverdueTaskCount(System.currentTimeMillis())

            // Summary cards
            binding.tvTotalCount.text = "$totalTasks"
            binding.tvCompletedCount.text = "$completedTasks"
            binding.tvPendingCount.text = "$pendingTasks"
            binding.tvOverdueCount.text = "$overdueTasks"

            // Completion rate
            val rate = if (totalTasks > 0) (completedTasks * 100) / totalTasks else 0
            binding.tvCompletionRate.text = "$rate%"
            binding.progressCompletion.setProgressCompat(rate, true)

            // Week dates
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            val weekStart = cal.timeInMillis
            cal.add(Calendar.DAY_OF_WEEK, 7)
            val weekEnd = cal.timeInMillis

            // Month dates
            cal.timeInMillis = System.currentTimeMillis()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            val monthStart = cal.timeInMillis
            cal.add(Calendar.MONTH, 1)
            val monthEnd = cal.timeInMillis

            // Week/Month metrics
            val completedWeek = repository.getCompletedTaskCountBetween(weekStart, weekEnd)
            val createdWeek = repository.getCreatedTaskCountBetween(weekStart, weekEnd)
            val completedMonth = repository.getCompletedTaskCountBetween(monthStart, monthEnd)
            val createdMonth = repository.getCreatedTaskCountBetween(monthStart, monthEnd)

            binding.tvCompletedWeek.text = "$completedWeek"
            binding.tvCreatedWeek.text = "$createdWeek"
            binding.tvCompletedMonth.text = "$completedMonth"
            binding.tvCreatedMonth.text = "$createdMonth"

            // Weekly activity chart (last 7 days)
            buildWeeklyChart(repository)

            // Streak calculation
            calculateStreak(repository)
        }
    }

    private suspend fun buildWeeklyChart(repository: app.polar.data.repository.TaskRepository) {
        val dayNames = arrayOf("L", "M", "X", "J", "V", "S", "D")
        val counts = mutableListOf<Int>()

        val cal = Calendar.getInstance()
        // Go back to start of today
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        // Go to 6 days ago (start of the 7-day window)
        cal.add(Calendar.DAY_OF_YEAR, -6)

        val dayLabelsOrdered = mutableListOf<String>()

        for (i in 0 until 7) {
            val dayStart = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val dayEnd = cal.timeInMillis

            val count = repository.getCompletedTaskCountBetween(dayStart, dayEnd)
            counts.add(count)

            // Get day of week (Calendar SUNDAY=1, MONDAY=2, etc.)
            val tempCal = Calendar.getInstance()
            tempCal.timeInMillis = dayStart
            val dayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK)
            val label = when (dayOfWeek) {
                Calendar.MONDAY -> "L"
                Calendar.TUESDAY -> "M"
                Calendar.WEDNESDAY -> "X"
                Calendar.THURSDAY -> "J"
                Calendar.FRIDAY -> "V"
                Calendar.SATURDAY -> "S"
                Calendar.SUNDAY -> "D"
                else -> "?"
            }
            dayLabelsOrdered.add(label)
        }

        binding.weeklyBarChart.setData(counts, dayLabelsOrdered)
    }

    private suspend fun calculateStreak(repository: app.polar.data.repository.TaskRepository) {
        // Calculate streak: consecutive days with at least 1 completed task, counting backwards from today
        var streak = 0
        val cal = Calendar.getInstance()

        // Start from today
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        for (i in 0 until 365) { // Max 1 year lookback
            val dayStart = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val dayEnd = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, -1) // Reset

            val completed = repository.getCompletedTaskCountBetween(dayStart, dayEnd)
            if (completed > 0) {
                streak++
                cal.add(Calendar.DAY_OF_YEAR, -1) // Go to previous day
            } else {
                // If it's today and nothing completed yet, check yesterday
                if (i == 0) {
                    cal.add(Calendar.DAY_OF_YEAR, -1)
                    continue
                }
                break
            }
        }

        binding.tvStreak.text = "$streak"

        // Best day: find the day of the week with most completions
        // Look at last 30 days
        val bestDayCal = Calendar.getInstance()
        bestDayCal.set(Calendar.HOUR_OF_DAY, 0)
        bestDayCal.set(Calendar.MINUTE, 0)
        bestDayCal.set(Calendar.SECOND, 0)
        bestDayCal.set(Calendar.MILLISECOND, 0)
        bestDayCal.add(Calendar.DAY_OF_YEAR, -30)

        val dayTotals = mutableMapOf<Int, Int>() // dayOfWeek -> total completions

        for (i in 0 until 30) {
            val dayStart = bestDayCal.timeInMillis
            bestDayCal.add(Calendar.DAY_OF_YEAR, 1)
            val dayEnd = bestDayCal.timeInMillis

            val completed = repository.getCompletedTaskCountBetween(dayStart, dayEnd)
            val dow = Calendar.getInstance().apply { timeInMillis = dayStart }.get(Calendar.DAY_OF_WEEK)
            dayTotals[dow] = (dayTotals[dow] ?: 0) + completed
        }

        val bestDow = dayTotals.maxByOrNull { it.value }
        val bestDayName = when (bestDow?.key) {
            Calendar.MONDAY -> "lunes"
            Calendar.TUESDAY -> "martes"
            Calendar.WEDNESDAY -> "miércoles"
            Calendar.THURSDAY -> "jueves"
            Calendar.FRIDAY -> "viernes"
            Calendar.SATURDAY -> "sábado"
            Calendar.SUNDAY -> "domingo"
            else -> "-"
        }

        binding.tvBestDay.text = if ((bestDow?.value ?: 0) > 0) bestDayName else "-"
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
