package app.polar.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.polar.data.model.ListTaskCount
import app.polar.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val repository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    fun loadStats() {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()

            val totalTasks = repository.getTotalTaskCount()
            val completedTasks = repository.getCompletedTaskCount()
            val pendingTasks = repository.getPendingTaskCount()
            val overdueTasks = repository.getOverdueTaskCount(now)
            val completionRate = if (totalTasks > 0) (completedTasks * 100) / totalTasks else 0

            val weekRange = currentWeekRange()
            val monthRange = currentMonthRange()
            val lastWeekRange = previousWeekRange()

            val completedThisWeek = repository.getCompletedTaskCountBetween(weekRange.first, weekRange.second)
            val createdThisWeek = repository.getCreatedTaskCountBetween(weekRange.first, weekRange.second)
            val completedThisMonth = repository.getCompletedTaskCountBetween(monthRange.first, monthRange.second)
            val createdThisMonth = repository.getCreatedTaskCountBetween(monthRange.first, monthRange.second)
            val completedLastWeek = repository.getCompletedTaskCountBetween(lastWeekRange.first, lastWeekRange.second)

            val (weeklyCounts, weeklyLabels) = buildWeeklySeries()
            val (trendCounts, trendLabels) = buildTrendSeries()

            val priorityCounts = buildPriorityCounts()
            val listCounts = buildListCounts()

            val streak = calculateStreak()
            val bestDay = calculateBestDay()

            _uiState.value = StatsUiState(
                isEmpty = totalTasks == 0,
                totalTasks = totalTasks,
                completedTasks = completedTasks,
                pendingTasks = pendingTasks,
                overdueTasks = overdueTasks,
                completionRate = completionRate,
                currentStreak = streak,
                bestDay = bestDay,
                completedThisWeek = completedThisWeek,
                createdThisWeek = createdThisWeek,
                completedThisMonth = completedThisMonth,
                createdThisMonth = createdThisMonth,
                completedLastWeek = completedLastWeek,
                weeklyCounts = weeklyCounts,
                weeklyLabels = weeklyLabels,
                trendCounts = trendCounts,
                trendLabels = trendLabels,
                priorityCounts = priorityCounts,
                listCounts = listCounts
            )
        }
    }

    private fun currentWeekRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_WEEK, 7)
        return start to cal.timeInMillis
    }

    private fun previousWeekRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            add(Calendar.DAY_OF_WEEK, -7)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_WEEK, 7)
        return start to cal.timeInMillis
    }

    private fun currentMonthRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        return start to cal.timeInMillis
    }

    private suspend fun buildWeeklySeries(): Pair<List<Int>, List<String>> {
        val counts = mutableListOf<Int>()
        val labels = mutableListOf<String>()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -6)
        }

        for (i in 0 until 7) {
            val dayStart = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val dayEnd = cal.timeInMillis
            counts.add(repository.getCompletedTaskCountBetween(dayStart, dayEnd))
            labels.add(dayShortLabel(dayStart))
        }
        return counts to labels
    }

    private suspend fun buildTrendSeries(): Pair<List<Int>, List<String>> {
        val counts = mutableListOf<Int>()
        val labels = mutableListOf<String>()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -29)
        }

        for (i in 0 until 30) {
            val dayStart = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val dayEnd = cal.timeInMillis
            counts.add(repository.getCompletedTaskCountBetween(dayStart, dayEnd))
            labels.add(trendLabel(dayStart, i))
        }
        return counts to labels
    }

    private fun dayShortLabel(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "l"
            Calendar.TUESDAY -> "m"
            Calendar.WEDNESDAY -> "x"
            Calendar.THURSDAY -> "j"
            Calendar.FRIDAY -> "v"
            Calendar.SATURDAY -> "s"
            Calendar.SUNDAY -> "d"
            else -> "?"
        }
    }

    private fun trendLabel(timestamp: Long, index: Int): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return if (index == 0 || index == 29 || cal.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY) {
            "${cal.get(Calendar.DAY_OF_MONTH)}"
        } else {
            ""
        }
    }

    private suspend fun buildPriorityCounts(): List<Int> {
        val fromDb = repository.getTaskCountByPriority().associate { it.priority to it.count }
        return (0..3).map { priority -> fromDb[priority] ?: 0 }
    }

    private suspend fun buildListCounts(): List<ListTaskCount> {
        return repository.getTaskCountByList()
            .filter { it.total > 0 }
            .sortedByDescending { it.total }
            .take(6)
    }

    private suspend fun calculateStreak(): Int {
        var streak = 0
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        for (i in 0 until 365) {
            val dayStart = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val dayEnd = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, -1)

            val completed = repository.getCompletedTaskCountBetween(dayStart, dayEnd)
            if (completed > 0) {
                streak++
                cal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                if (i == 0) {
                    cal.add(Calendar.DAY_OF_YEAR, -1)
                    continue
                }
                break
            }
        }
        return streak
    }

    private suspend fun calculateBestDay(): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -30)
        }

        val dayTotals = mutableMapOf<Int, Int>()
        for (i in 0 until 30) {
            val dayStart = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val dayEnd = cal.timeInMillis
            val completed = repository.getCompletedTaskCountBetween(dayStart, dayEnd)
            val dow = Calendar.getInstance().apply { timeInMillis = dayStart }.get(Calendar.DAY_OF_WEEK)
            dayTotals[dow] = (dayTotals[dow] ?: 0) + completed
        }

        val best = dayTotals.maxByOrNull { it.value }
        return if ((best?.value ?: 0) > 0) {
            when (best?.key) {
                Calendar.MONDAY -> "lunes"
                Calendar.TUESDAY -> "martes"
                Calendar.WEDNESDAY -> "miercoles"
                Calendar.THURSDAY -> "jueves"
                Calendar.FRIDAY -> "viernes"
                Calendar.SATURDAY -> "sabado"
                Calendar.SUNDAY -> "domingo"
                else -> "-"
            }
        } else "-"
    }
}
