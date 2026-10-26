package app.polar.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.polar.data.model.ListTaskCount
import app.polar.data.repository.TaskRepository
import app.polar.domain.util.CompletionHistory
import app.polar.util.RecurrenceUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
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

            // Una sola consulta para series, racha y mejor día (antes, una por cada día del año).
            val historyStart = Calendar.getInstance().apply {
                timeInMillis = RecurrenceUtils.startOfDay(now)
                add(Calendar.DAY_OF_YEAR, -HISTORY_DAYS)
            }.timeInMillis
            val history = CompletionHistory(repository.getCompletedDueDatesSince(historyStart))

            val (weeklyCounts, weeklyLabels) = buildWeeklySeries(history, now)
            val (trendCounts, trendLabels) = buildTrendSeries(history, now)

            val priorityCounts = buildPriorityCounts()
            val listCounts = buildListCounts()

            val streak = history.streak(now)
            val bestDay = bestDayName(history, now)

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

    private fun buildWeeklySeries(history: CompletionHistory, now: Long): Pair<List<Int>, List<String>> {
        val days = history.lastDays(now, 7)
        // Inicial del día en el idioma de la app ("EEEEE": l, m, x... / m, t, w...)
        val format = SimpleDateFormat("EEEEE", Locale.getDefault())
        return days.map(history::countOn) to days.map { format.format(Date(it)).lowercase() }
    }

    private fun buildTrendSeries(history: CompletionHistory, now: Long): Pair<List<Int>, List<String>> {
        val days = history.lastDays(now, TREND_DAYS)
        val labels = days.mapIndexed { index, dayStart -> trendLabel(dayStart, index) }
        return days.map(history::countOn) to labels
    }

    private fun trendLabel(timestamp: Long, index: Int): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return if (index == 0 || index == TREND_DAYS - 1 || cal.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY) {
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

    private fun bestDayName(history: CompletionHistory, now: Long): String {
        val dayOfWeek = history.bestDayOfWeek(now) ?: return "-"
        val day = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, dayOfWeek) }
        return SimpleDateFormat("EEEE", Locale.getDefault()).format(day.time).lowercase()
    }

    private companion object {
        const val TREND_DAYS = 30
        // La racha mira hasta un año atrás (más el día de hoy).
        const val HISTORY_DAYS = 366
    }
}
