package app.polar.ui.viewmodel

import app.polar.data.model.ListTaskCount

data class StatsUiState(
    val isEmpty: Boolean = false,
    val totalTasks: Int = 0,
    val completedTasks: Int = 0,
    val pendingTasks: Int = 0,
    val overdueTasks: Int = 0,
    val completionRate: Int = 0,
    val currentStreak: Int = 0,
    val bestDay: String = "-",
    val completedThisWeek: Int = 0,
    val createdThisWeek: Int = 0,
    val completedThisMonth: Int = 0,
    val createdThisMonth: Int = 0,
    val completedLastWeek: Int = 0,
    val weeklyCounts: List<Int> = emptyList(),
    val weeklyLabels: List<String> = emptyList(),
    val trendCounts: List<Int> = emptyList(),
    val trendLabels: List<String> = emptyList(),
    val priorityCounts: List<Int> = emptyList(),
    val listCounts: List<ListTaskCount> = emptyList()
)
