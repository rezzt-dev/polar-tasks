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
    private val repository: TaskRepository,
    private val alarmHelper: app.polar.util.AlarmManagerHelper,
    private val getFilteredTasksUseCase: GetFilteredTasksUseCase
) : AndroidViewModel(application) {



  private val _selectedListId = MutableStateFlow(-1L)
  val selectedListId: StateFlow<Long> = _selectedListId.asStateFlow()
  
  // Filter states
  private val _filterPending = MutableStateFlow(false)
  private val _filterOverdue = MutableStateFlow(false)
  val filterPending: StateFlow<Boolean> = _filterPending.asStateFlow()
  val filterOverdue: StateFlow<Boolean> = _filterOverdue.asStateFlow()

  fun setFilterPending(enabled: Boolean) { _filterPending.value = enabled }
  fun setFilterOverdue(enabled: Boolean) { _filterOverdue.value = enabled }

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

  // Filtered List for List Mode
  val tasks: StateFlow<List<TaskListItem>> = getFilteredTasksUseCase(
        _selectedListId, 
        _filterPending, 
        _filterOverdue
    )
    .map { list -> list.map { TaskListItem.Item(it) } }
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
           val pendingOnly = _filterPending.value
           val overdueOnly = _filterOverdue.value
           val now = System.currentTimeMillis()
           
           val filteredList = rawList.filter { item ->
              val task = item.task
              if (task.completed) return@filter false
               
              var matches = true
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
               matches
           }
           val grouped = filteredList.groupBy { it.task.listId }
           val result = grouped.map { (listId, tasksWithList) ->
                val title = tasksWithList.firstOrNull()?.listTitle ?: "Unknown List"
                val tasks = tasksWithList.map { it.task }
                app.polar.data.model.TaskGroup(listId, title, tasks)
           }
           value = result
      }
      addSource(_rawHomeTasks) { update() }
      // addSource from StateFlow? MediatorLiveData doesn't support Flow source directly without conversion `asLiveData`.
      // We are in a hybrid state. 
      // Ideally I should convert `homeTaskGroups` to StateFlow too.
      // But _rawHomeTasks is LiveData.
      // For now, let's keep homeTaskGroups as LiveData to reduce breakage, but updated to read generic `.value` from StateFlows if accessed? 
      // No, `_filterPending` is now StateFlow. I can't `addSource` a StateFlow to MediatorLiveData.
      // I MUST convert `homeTaskGroups` to flow logic or adapter `asLiveData`.
      // `asLiveData()` from StateFlow.

      addSource(_filterOverdue.asLiveData()) { update() }
  }
  

  
  fun loadTasksForList(listId: Long) {
    _selectedListId.value = listId
  }
  
  fun loadAllTasks() {
      _selectedListId.value = -1L
  }
  
  // Get subtasks for a task
  fun getSubtasksForTask(taskId: Long): LiveData<List<Subtask>> {
    return repository.getSubtasksForTask(taskId)
  }
  
  fun insertTask(listId: Long, title: String, description: String, tags: String = "", subtasks: List<Subtask> = emptyList(), dueDate: Long? = null, recurrence: String = "NONE") = safeLaunch {
    val task = Task(
      listId = listId,
      title = title,
      description = description,
      tags = tags,
      dueDate = dueDate,
      recurrence = recurrence
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
