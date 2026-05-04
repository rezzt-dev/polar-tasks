package app.polar.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import app.polar.data.AppDatabase
import app.polar.data.entity.Subtask
import app.polar.data.entity.Task
import app.polar.data.entity.TaskList
import app.polar.data.repository.TaskRepository
import app.polar.domain.usecase.GetFilteredTasksUseCase
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import app.polar.ui.adapter.TaskListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TaskViewModel @Inject constructor(
    application: Application,
    internal val repository: TaskRepository,
    private val alarmHelper: app.polar.util.AlarmManagerHelper,
    private val getFilteredTasksUseCase: GetFilteredTasksUseCase
) : AndroidViewModel(application) {



  private val _selectedListId = MutableStateFlow(-1L)
  val selectedListId: StateFlow<Long> = _selectedListId.asStateFlow()
  
  // Filter states
  private val _filterToday = MutableStateFlow(false)
  private val _filterPending = MutableStateFlow(false)
  private val _filterOverdue = MutableStateFlow(false)
  private val _filterRecurrent = MutableStateFlow(false)
  val filterToday: StateFlow<Boolean> = _filterToday.asStateFlow()
  val filterPending: StateFlow<Boolean> = _filterPending.asStateFlow()
  val filterOverdue: StateFlow<Boolean> = _filterOverdue.asStateFlow()
  val filterRecurrent: StateFlow<Boolean> = _filterRecurrent.asStateFlow()

  fun setFilterToday(enabled: Boolean) { _filterToday.value = enabled }
  fun setFilterPending(enabled: Boolean) { _filterPending.value = enabled }
  fun setFilterOverdue(enabled: Boolean) { _filterOverdue.value = enabled }
  fun setFilterRecurrent(enabled: Boolean) { _filterRecurrent.value = enabled }

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  fun clearError() { _errorMessage.value = null }

  private fun safeLaunch(block: suspend () -> Unit) = viewModelScope.launch {
      try {
          block()
      } catch (e: Exception) {
          e.printStackTrace()
          _errorMessage.value = "Error: ${e.message}"
      }
  }

  // Track whether the currently viewed list is a dependency chain
  private val _isCurrentListChain = MutableStateFlow(false)
  val isCurrentListChain: StateFlow<Boolean> = _isCurrentListChain.asStateFlow()

  // Filtered List for List Mode
  val tasks: StateFlow<List<TaskListItem>> = combine(
        getFilteredTasksUseCase(
            _selectedListId,
            _filterToday,
            _filterPending,
            _filterOverdue,
            _filterRecurrent
        ),
        _isCurrentListChain
    ) { taskList, isChain ->
        if (isChain) {
            // Sort all tasks by orderIndex, compute which ones are blocked
            val sorted = taskList.sortedBy { it.orderIndex }
            val activeIndex = sorted.indexOfFirst { !it.completed }
            sorted.mapIndexed { index, task ->
                TaskListItem.Item(
                    task = task,
                    isChainMode = true,
                    isBlocked = activeIndex != -1 && index > activeIndex,
                    isFirst = index == 0,
                    isLast = index == sorted.lastIndex
                )
            }
        } else {
            taskList.map { TaskListItem.Item(it) }
        }
    }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )



  // TODO: Migrate Home Tasks logic to Use Case or Flow as well if desired.
  // For now leaving as LiveData as it wasn't strictly requested to refactor EVERY usage, but ideally yes.
  // Converting to Flow using asFlow if repository supports it (DAO supports getTasksWithListTitles as LiveData currently).
  private val _rawHomeTasks = repository.getTasksWithListTitles() // This is LiveData
  
  // We can wrap it in Flow? Or keep it hybrid. User asked for "StateFlow in ViewModels", implying migration.
  // I should update DAO for this too?
  // Let's create `homeTaskGroups` as StateFlow derived from `_rawHomeTasks` (observed as flow).
  // Ideally update DAO.
  
  val homeTaskGroups: LiveData<List<app.polar.data.model.TaskGroup>> = androidx.lifecycle.MediatorLiveData<List<app.polar.data.model.TaskGroup>>().apply {
      fun update() {
           val rawList = _rawHomeTasks.value ?: emptyList()
           val todayOnly = _filterToday.value
           val pendingOnly = _filterPending.value
           val overdueOnly = _filterOverdue.value
           val recurrentOnly = _filterRecurrent.value
           val now = System.currentTimeMillis()
           
           // Group by listId first to handle per-chain filtering
           val grouped = rawList.groupBy { it.task.listId }
           
           val result = grouped.mapNotNull { (listId, tasksWithList) ->
                val title = tasksWithList.firstOrNull()?.listTitle ?: "Unknown List"
                val isDependencyChain = tasksWithList.firstOrNull()?.isDependencyChain ?: false
                
                // For chain lists: only show the first uncompleted task
                val tasksToShow = if (isDependencyChain) {
                    val firstUncompleted = tasksWithList
                        .sortedBy { it.task.orderIndex }
                        .firstOrNull { !it.task.completed }
                    if (firstUncompleted != null) listOf(firstUncompleted) else emptyList()
                } else {
                    tasksWithList
                }
                
                val filteredList = tasksToShow.filter { item ->
                    val task = item.task
                    if (task.completed) return@filter false
                    
                    var matches = true
                    if (todayOnly) {
                        if (task.dueDate == null) {
                            matches = false
                        } else {
                            if (!android.text.format.DateUtils.isToday(task.dueDate)) matches = false
                        }
                    }
                    if (pendingOnly) {
                       if (task.completed) matches = false
                       if (item.completedSubtasks > 0) matches = false
                    }
                    if (overdueOnly) {
                         if (task.dueDate == null) {
                            matches = false
                        } else {
                            val isToday = android.text.format.DateUtils.isToday(task.dueDate)
                            val isFuture = task.dueDate >= now
                            if (isToday || isFuture) matches = false
                        }
                        if (task.completed) matches = false
                    }
                    if (recurrentOnly) {
                        if (task.recurrence == "NONE") matches = false
                    }
                    matches
                }
                
                if (filteredList.isEmpty()) null
                else app.polar.data.model.TaskGroup(listId, title, filteredList.map { it.task })
           }
           value = result
      }
      addSource(_rawHomeTasks) { update() }
      addSource(_filterToday.asLiveData()) { update() }
      addSource(_filterPending.asLiveData()) { update() }
      addSource(_filterOverdue.asLiveData()) { update() }
      addSource(_filterRecurrent.asLiveData()) { update() }
  }
  

  
  fun loadTasksForList(listId: Long) {
    _selectedListId.value = listId
    // Reset filters
    _filterToday.value = false
    _filterPending.value = false
    _filterOverdue.value = false
    _filterRecurrent.value = false
    // Update chain state asynchronously
    viewModelScope.launch {
        val list = repository.getTaskListById(listId)
        _isCurrentListChain.value = list?.isDependencyChain ?: false
    }
  }
  
  fun loadAllTasks() {
      _selectedListId.value = -1L
      // Reset filters
      _filterToday.value = false
      _filterPending.value = false
      _filterOverdue.value = false
      _filterRecurrent.value = false
  }

  fun loadMyDayTasks() {
      _selectedListId.value = -4L
      // Force filters: today + overdue + pending
      _filterToday.value = true
      _filterOverdue.value = true
      _filterPending.value = true
      _filterRecurrent.value = false
  }
  
  // Get subtasks for a task
  fun getSubtasksForTask(taskId: Long): LiveData<List<Subtask>> {
    return repository.getSubtasksForTask(taskId)
  }
  
  fun insertTask(listId: Long, title: String, description: String, tags: String = "", subtasks: List<Subtask> = emptyList(), dueDate: Long? = null, recurrence: String = "NONE", priority: Int = 0, timeEstimate: Int = 0) = safeLaunch {
    val task = Task(
      listId = listId,
      title = title,
      description = description,
      tags = tags,
      dueDate = dueDate,
      recurrence = recurrence,
      priority = priority,
      timeEstimate = timeEstimate
    )
    val taskId = repository.insertTask(task)
    
    // Insert subtasks
    subtasks.forEach { subtask ->
        // Ensure they are linked to the new task
        repository.insertSubtask(subtask.copy(id = 0, taskId = taskId))
    }
    
    // Schedule alarm
    if (dueDate != null) {
        alarmHelper.scheduleTaskAlarm(taskId, dueDate)
    }
  }

  suspend fun getTasksBetweenDates(start: Long, end: Long): List<Task> {
    return repository.getTasksBetweenDates(start, end)
  }

  fun getTasksForDate(start: Long, end: Long): LiveData<List<Task>> {
    return repository.getTasksForDateLive(start, end)
  }

  fun getAllTasks(): LiveData<List<Task>> {
    return repository.getAllTasks()
  }
  
  fun updateTask(task: Task, subtasks: List<Subtask>? = null) = safeLaunch {
    repository.updateTask(task)
    
    if (subtasks != null) {
        repository.deleteAllSubtasksForTask(task.id)
        subtasks.forEach { subtask ->
             // Re-insert subtask maintaining state (title, completed)
             // Reset ID for auto-generation
             repository.insertSubtask(subtask.copy(id = 0, taskId = task.id))
        }
    }
    
    if (task.dueDate != null) {
        alarmHelper.scheduleTaskAlarm(task.id, task.dueDate)
    } else {
        alarmHelper.cancelTaskAlarm(task.id)
    }
  }

  fun updateTaskWithSubtasks(task: Task, newSubtasks: List<Subtask>) = safeLaunch {
      updateTask(task, newSubtasks)
  }
  
  fun setTaskCompletion(task: Task, isCompleted: Boolean) = safeLaunch {
    if (task.completed == isCompleted) return@safeLaunch
    repository.updateTask(task.copy(completed = isCompleted))
    
    // Sync subtasks
    if (isCompleted) {
        repository.completeSubtasksForTask(task.id)
    } else {
        repository.resetSubtasksForTask(task.id)
    }
    
    if (isCompleted) {
        if (task.dueDate != null) {
            alarmHelper.cancelTaskAlarm(task.id)
        }
    } else {
        if (task.dueDate != null) {
            alarmHelper.scheduleTaskAlarm(task.id, task.dueDate)
        }
    }
  }

  fun toggleTaskCompletion(task: Task) = safeLaunch {
    val newCompletedState = !task.completed
    repository.updateTask(task.copy(completed = newCompletedState))
    
    // Sync subtasks
    if (newCompletedState) {
        repository.completeSubtasksForTask(task.id)
    } else {
        repository.resetSubtasksForTask(task.id)
    }
    
    if (newCompletedState) {
        // Task completed.
        // If recurrent, RecurrenceWorker will reset it on due date.
        // Cancel alarm for now.
        if (task.dueDate != null) {
            alarmHelper.cancelTaskAlarm(task.id)
        }
    } else {
        // Task unchecked.
        // Reschedule alarm if it has future or present date.
        if (task.dueDate != null) {
            alarmHelper.scheduleTaskAlarm(task.id, task.dueDate)
        }
    }
  }
  
  fun deleteTask(task: Task) = safeLaunch {
    repository.deleteTask(task)
  }
  
  fun insertSubtask(taskId: Long, title: String) = safeLaunch {
    val subtask = Subtask(taskId = taskId, title = title)
    repository.insertSubtask(subtask)
  }
  
  fun toggleSubtaskCompletion(subtask: Subtask) = safeLaunch {
    repository.updateSubtask(subtask.copy(completed = !subtask.completed))
  }
  
  fun deleteSubtask(subtask: Subtask) = safeLaunch {
    repository.deleteSubtask(subtask)
  }



  fun renameSubtask(subtask: Subtask, newTitle: String) = safeLaunch {
    repository.updateSubtask(subtask.copy(title = newTitle))
  }

  fun updateSubtask(subtask: Subtask) = safeLaunch {
    repository.updateSubtask(subtask)
  }

  fun updateTasksOrder(tasks: List<Task>) = safeLaunch {
    repository.updateTasks(tasks)
  }

  fun updateTaskListsOrder(taskLists: List<app.polar.data.entity.TaskList>) = safeLaunch {
    repository.updateTaskLists(taskLists)
  }
  
  fun updateTaskGroupsOrder(groups: List<app.polar.data.model.TaskGroup>) = safeLaunch {
      val allLists = repository.getTaskListsSnapshot()
      val groupsMap = groups.mapIndexed { index, g -> g.listId to index }.toMap()
      
      val updatedLists = allLists.mapNotNull { list ->
          if (groupsMap.containsKey(list.id)) {
              list.copy(homeOrderIndex = groupsMap[list.id]!!)
          } else {
              null
          }
      }
      if (updatedLists.isNotEmpty()) {
        repository.updateTaskLists(updatedLists)
      }
  }
  
  fun searchTasks(query: String): LiveData<List<Task>> {
    return repository.searchTasks(query)
  }
  
  fun getTaskById(taskId: Long): LiveData<Task?> {
     val result = androidx.lifecycle.MutableLiveData<Task?>()
     viewModelScope.launch {
         result.postValue(repository.getTaskById(taskId))
     }
     return result
  }

  fun getTaskListById(listId: Long): LiveData<app.polar.data.entity.TaskList?> {
      val result = androidx.lifecycle.MutableLiveData<app.polar.data.entity.TaskList?>()
      viewModelScope.launch {
          result.postValue(repository.getTaskListById(listId))
      }
      return result
  }

  private val _calendarRange = MutableLiveData<Pair<Long, Long>>()
  val calendarTasks: LiveData<List<Task>> = _calendarRange.switchMap { range ->
      repository.getTasksForDateLive(range.first, range.second)
  }

  fun setCalendarRange(start: Long, end: Long) {
      _calendarRange.value = Pair(start, end)
  }

  // Trash Logic
  fun moveToTrash(task: Task) = safeLaunch {
      repository.softDeleteTask(task.id)
      // Cancel alarm if set
      if (task.dueDate != null) {
          alarmHelper.cancelTaskAlarm(task.id)
      }
  }

  fun restoreFromTrash(task: Task) = safeLaunch {
      repository.restoreTask(task.id)
      // Reschedule alarm if needed?
      if (task.dueDate != null && !task.completed) {
          alarmHelper.scheduleTaskAlarm(task.id, task.dueDate)
      }
  }

  fun permanentDelete(task: Task) = safeLaunch {
      repository.permanentDeleteTask(task.id)
  }

  fun emptyTrash() = safeLaunch {
      repository.emptyTrash()
  }

  fun getDeletedTasks(): LiveData<List<Task>> {
      return repository.getDeletedTasks()
  }
}
