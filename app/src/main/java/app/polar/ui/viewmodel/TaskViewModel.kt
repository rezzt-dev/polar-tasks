package app.polar.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import app.polar.data.AppDatabase
import app.polar.data.entity.Subtask
import app.polar.data.entity.Task
import app.polar.data.entity.TaskList
import app.polar.data.repository.TaskRepository
import kotlinx.coroutines.launch
import app.polar.ui.adapter.TaskListItem
import androidx.lifecycle.map

class TaskViewModel(application: Application) : AndroidViewModel(application) {
  private val repository: TaskRepository = TaskRepository(
      AppDatabase.getDatabase(application).taskListDao(),
      AppDatabase.getDatabase(application).taskDao(),
      AppDatabase.getDatabase(application).subtaskDao()
  )


  private val _selectedListId = MutableLiveData<Long>()
  val selectedListId: LiveData<Long> = _selectedListId
  
  // Filter states
  private val _filterPending = MutableLiveData(false)
  private val _filterOverdue = MutableLiveData(false)
  val filterPending: LiveData<Boolean> = _filterPending
  val filterOverdue: LiveData<Boolean> = _filterOverdue

  fun setFilterPending(enabled: Boolean) { _filterPending.value = enabled }
  fun setFilterOverdue(enabled: Boolean) { _filterOverdue.value = enabled }

  // Combined source for filtering
  private val _tasksSource: LiveData<List<Task>> = _selectedListId.switchMap { listId ->
    if (listId == -1L) {
        repository.getAllTasks()
    } else {
        repository.getTasksForList(listId)
    }
  }

  // Filtered List for List Mode
  private val _filteredTasks = androidx.lifecycle.MediatorLiveData<List<TaskListItem>>().apply {
      fun update() {
          val currentTasks = _tasksSource.value ?: emptyList()
          val pendingOnly = _filterPending.value ?: false
          val overdueOnly = _filterOverdue.value ?: false
          val now = System.currentTimeMillis()

          val filtered = currentTasks.filter { task ->
              var matches = true
              if (pendingOnly && task.completed) matches = false
              if (overdueOnly && (task.dueDate == null || task.dueDate >= now || task.completed)) matches = false
              matches
          }
          value = filtered.map { TaskListItem.Item(it) }
      }

      addSource(_tasksSource) { update() }
      addSource(_filterPending) { update() }
      addSource(_filterOverdue) { update() }
  }

  val tasks: LiveData<List<TaskListItem>> = _filteredTasks


  private val _rawHomeTasks = repository.getTasksWithListTitles()
  
  val homeTaskGroups = androidx.lifecycle.MediatorLiveData<List<app.polar.data.model.TaskGroup>>().apply {
      fun update() {
          val rawList = _rawHomeTasks.value ?: emptyList()
          val pendingOnly = _filterPending.value ?: false
          val overdueOnly = _filterOverdue.value ?: false
          val now = System.currentTimeMillis()
          
          val filteredList = rawList.filter { item ->
              val task = item.task
              var matches = true
              if (pendingOnly && task.completed) matches = false
              if (overdueOnly && (task.dueDate == null || task.dueDate >= now || task.completed)) matches = false
              matches
          }
          
          val grouped = filteredList.groupBy { it.task.listId }
          
          val result = grouped.map { (listId, tasksWithList) ->
            val title = tasksWithList.firstOrNull()?.listTitle ?: "Unknown List"
            val tasks = tasksWithList.map { it.task }
            app.polar.data.model.TaskGroup(
                listId = listId, 
                title = title, 
                tasks = tasks
            )
          }
          
          // Sort groups if needed? e.g. by homeOrderIndex if available in lists, but listTitle query might order them.
          // For now rely on query order preservation.
          value = result
      }
      
      addSource(_rawHomeTasks) { update() }
      addSource(_filterPending) { update() }
      addSource(_filterOverdue) { update() }
  }
  

  
  fun loadTasksForList(listId: Long) {
    _selectedListId.value = listId
  }
  
  fun loadAllTasks() {
      _selectedListId.value = -1L
  }
  
  // obtener subtareas de una tarea
  fun getSubtasksForTask(taskId: Long): LiveData<List<Subtask>> {
    return repository.getSubtasksForTask(taskId)
  }
  
  fun insertTask(listId: Long, title: String, description: String, tags: String = "", subtasks: List<Subtask> = emptyList(), dueDate: Long? = null, recurrence: String = "NONE") = viewModelScope.launch {
    val task = Task(
      listId = listId,
      title = title,
      description = description,
      tags = tags,
      dueDate = dueDate,
      recurrence = recurrence
    )
    val taskId = repository.insertTask(task)
    
    // insertar subtareas
    subtasks.forEach { subtask ->
        // asegurarnos de que se vinculan a la nueva tarea
        repository.insertSubtask(subtask.copy(id = 0, taskId = taskId))
    }
    
    // programar alarma
    if (dueDate != null) {
        scheduleAlarm(taskId, dueDate)
    }
  }

  suspend fun getTasksBetweenDates(start: Long, end: Long): List<Task> {
    return repository.getTasksBetweenDates(start, end)
  }

  fun getTasksForDate(start: Long, end: Long): LiveData<List<Task>> {
    return repository.getTasksForDateLive(start, end)
  }
  
  fun updateTask(task: Task, subtasks: List<Subtask>? = null) = viewModelScope.launch {
    repository.updateTask(task)
    
    if (subtasks != null) {
        repository.deleteAllSubtasksForTask(task.id)
        subtasks.forEach { subtask ->
             // reinsertar subtarea manteniendo su estado (title, completed)
             // reseteamos id para autogenerar uno nuevo
             repository.insertSubtask(subtask.copy(id = 0, taskId = task.id))
        }
    }
    
    if (task.dueDate != null) {
        scheduleAlarm(task.id, task.dueDate)
    } else {
        cancelAlarm(task.id)
    }
  }

  fun updateTaskWithSubtasks(task: Task, newSubtasks: List<Subtask>) = viewModelScope.launch {
      updateTask(task, newSubtasks)
  }
  
  fun toggleTaskCompletion(task: Task) = viewModelScope.launch {
    val newCompletedState = !task.completed
    repository.updateTask(task.copy(completed = newCompletedState))
    
    if (newCompletedState && task.recurrence != "NONE" && task.dueDate != null) {
        // crear instancia de recurrencia siguiente
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = task.dueDate
        
        when (task.recurrence) {
            "DAILY" -> calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
            "WEEKLY" -> calendar.add(java.util.Calendar.WEEK_OF_YEAR, 1)
            "MONTHLY" -> calendar.add(java.util.Calendar.MONTH, 1)
        }
        
        val nextDueDate = calendar.timeInMillis
        
        // clonar tarea
        val nextTask = task.copy(
            id = 0, // nuevo id
            completed = false,
            dueDate = nextDueDate,
            createdAt = System.currentTimeMillis()
        )
        
        val newTaskId = repository.insertTask(nextTask)
        
        // clonar subtareas?
        // idealmente si, resetear a incompleto
        val existingSubtasks = repository.getSubtasksForTaskDirect(task.id)
        existingSubtasks.forEach { sub ->
            repository.insertSubtask(sub.copy(id = 0, taskId = newTaskId, completed = false))
        }
        
        // programar alarma para nueva tarea
        scheduleAlarm(newTaskId, nextDueDate)
    }
  }
  
  fun deleteTask(task: Task) = viewModelScope.launch {
    repository.deleteTask(task)
  }
  
  fun insertSubtask(taskId: Long, title: String) = viewModelScope.launch {
    val subtask = Subtask(taskId = taskId, title = title)
    repository.insertSubtask(subtask)
  }
  
  fun toggleSubtaskCompletion(subtask: Subtask) = viewModelScope.launch {
    repository.updateSubtask(subtask.copy(completed = !subtask.completed))
  }
  
  fun deleteSubtask(subtask: Subtask) = viewModelScope.launch {
    repository.deleteSubtask(subtask)
  }

  private fun scheduleAlarm(taskId: Long, timeInMillis: Long) {
      if (timeInMillis <= System.currentTimeMillis()) return
      
      val alarmManager = getApplication<Application>().getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
      val intent = android.content.Intent(getApplication(), app.polar.receiver.AlarmReceiver::class.java).apply {
          putExtra("TASK_ID", taskId)
      }
      val pendingIntent = android.app.PendingIntent.getBroadcast(
          getApplication(),
          taskId.toInt(),
          intent,
          android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
      )
      
      try {
          if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
              if (alarmManager.canScheduleExactAlarms()) {
                  alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
              } else {
                  alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
              }
          } else {
               alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
          }
      } catch (e: SecurityException) {
          e.printStackTrace()
      }
  }

  private fun cancelAlarm(taskId: Long) {
      val alarmManager = getApplication<Application>().getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
      val intent = android.content.Intent(getApplication(), app.polar.receiver.AlarmReceiver::class.java)
      val pendingIntent = android.app.PendingIntent.getBroadcast(
          getApplication(),
          taskId.toInt(),
          intent,
          android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_NO_CREATE
      )
      if (pendingIntent != null) {
          alarmManager.cancel(pendingIntent)
      }
  }

  fun renameSubtask(subtask: Subtask, newTitle: String) = viewModelScope.launch {
    repository.updateSubtask(subtask.copy(title = newTitle))
  }

  fun updateTasksOrder(tasks: List<Task>) = viewModelScope.launch {
    repository.updateTasks(tasks)
  }

  fun updateTaskListsOrder(taskLists: List<app.polar.data.entity.TaskList>) = viewModelScope.launch {
    repository.updateTaskLists(taskLists)
  }
  
  fun updateTaskGroupsOrder(groups: List<app.polar.data.model.TaskGroup>) = viewModelScope.launch {
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
  fun moveToTrash(task: Task) = viewModelScope.launch {
      repository.softDeleteTask(task.id)
      // Cancel alarm if set
      if (task.dueDate != null) {
          cancelAlarm(task.id)
      }
  }

  fun restoreFromTrash(task: Task) = viewModelScope.launch {
      repository.restoreTask(task.id)
      // Reschedule alarm if needed?
      if (task.dueDate != null && !task.completed) {
          scheduleAlarm(task.id, task.dueDate)
      }
  }

  fun permanentDelete(task: Task) = viewModelScope.launch {
      repository.permanentDeleteTask(task.id)
  }

  fun emptyTrash() = viewModelScope.launch {
      repository.emptyTrash()
  }

  fun getDeletedTasks(): LiveData<List<Task>> {
      return repository.getDeletedTasks()
  }
}
