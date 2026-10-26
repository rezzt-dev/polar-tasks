package app.polar.domain.usecase

import app.polar.data.entity.Task
import app.polar.data.repository.TaskRepository
import app.polar.domain.util.TaskFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

class GetFilteredTasksUseCase @Inject constructor(
    private val repository: TaskRepository
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(
        listIdFlow: Flow<Long>,
        filterTodayFlow: Flow<Boolean>,
        filterPendingFlow: Flow<Boolean>,
        filterOverdueFlow: Flow<Boolean>,
        filterRecurrentFlow: Flow<Boolean>
    ): Flow<List<Task>> {
        // -1L = todas las tareas (inicio); cualquier otro id, las de esa lista.
        val tasksFlow = listIdFlow.flatMapLatest { listId ->
            if (listId == -1L) repository.getAllTasksFlow() else repository.getTasksForListFlow(listId)
        }

        return combine(
            tasksFlow, filterTodayFlow, filterPendingFlow, filterOverdueFlow, filterRecurrentFlow
        ) { tasks, todayOnly, pendingOnly, overdueOnly, recurrentOnly ->
            val filter = TaskFilter(todayOnly, pendingOnly, overdueOnly, recurrentOnly)
            val now = System.currentTimeMillis()
            tasks.filter { filter.matches(it, now) }
        }
    }
}
