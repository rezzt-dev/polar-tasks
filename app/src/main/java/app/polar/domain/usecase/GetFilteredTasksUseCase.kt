package app.polar.domain.usecase

import app.polar.data.entity.Task
import app.polar.data.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

class GetFilteredTasksUseCase @Inject constructor(
    private val repository: TaskRepository
) {
    operator fun invoke(
        listIdFlow: Flow<Long>,
        filterTodayFlow: Flow<Boolean>,
        filterPendingFlow: Flow<Boolean>,
        filterOverdueFlow: Flow<Boolean>,
        filterRecurrentFlow: Flow<Boolean>
    ): Flow<List<Task>> {
        // 1. Get raw tasks based on listId
        val tasksFlow = listIdFlow.flatMapLatest { listId ->
            when {
                // -1L = All tasks (Home)
                listId == -1L -> repository.getAllTasksFlow()
                else -> repository.getTasksForListFlow(listId)
            }
        }

        // 2. Combine and filter
        return combine(tasksFlow, filterTodayFlow, filterPendingFlow, filterOverdueFlow, filterRecurrentFlow) { tasks, todayOnly, pendingOnly, overdueOnly, recurrentOnly ->
            val now = System.currentTimeMillis()
            tasks.filter { task ->
                var matches = true

                if (todayOnly) {
                    if (task.dueDate == null) {
                        matches = false
                    } else {
                        if (!android.text.format.DateUtils.isToday(task.dueDate)) matches = false
                    }
                }

                if (pendingOnly) {
                    // Strict Pending: Task not completed
                    if (task.completed) matches = false
                }

                if (overdueOnly) {
                    // Strict Overdue: Due date is BEFORE today (Yesterday or older).
                     if (task.dueDate == null) {
                        matches = false
                    } else {
                        val isToday = android.text.format.DateUtils.isToday(task.dueDate)
                        val isFuture = task.dueDate >= now
                        // If it is Today or Future, it is NOT overdue
                        if (isToday || isFuture) matches = false
                    }
                    // Overdue tasks must not be completed
                    if (task.completed) matches = false
                }

                if (recurrentOnly) {
                    if (task.recurrence == "NONE") matches = false
                }

                matches
            }
        }
    }
}
