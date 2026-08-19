package app.polar.data.repository

import androidx.lifecycle.LiveData
import app.polar.data.dao.SubtaskDao
import app.polar.data.dao.TaskDao
import app.polar.data.dao.TaskListDao
import app.polar.data.entity.Subtask
import app.polar.data.entity.Task
import app.polar.data.entity.TaskList
import app.polar.data.sync.touched
import app.polar.data.sync.touchedDeleted
import app.polar.data.sync.touchedRestored

import javax.inject.Inject

class TaskRepository @Inject constructor(
  private val taskListDao: TaskListDao,
  private val taskDao: TaskDao,
  private val subtaskDao: SubtaskDao
) {
  // TaskList operations
  val allTaskLists: LiveData<List<TaskList>> = taskListDao.getAllLists()
  
  suspend fun getTaskListsSnapshot(): List<TaskList> {
      return taskListDao.getAllTaskListsSnapshot()
  }
  
  suspend fun insertTaskList(taskList: TaskList): Long {
    return taskListDao.insert(taskList)
  }
  
  suspend fun updateTaskList(taskList: TaskList) {
    taskListDao.update(taskList)
  }

  suspend fun updateTaskLists(taskLists: List<TaskList>) {
    taskListDao.updateAll(taskLists)
  }
  
  // Lists have no physical delete under sync: a deleted list, and every task/subtask that
  // hangs off it, must be soft-deleted so the tombstone can propagate to Supabase and the
  // other app (see agent-docs/supabase-sync/04-estrategia-sincronizacion.md, "Manejo de
  // borrado en cascada"). Room's ON DELETE CASCADE never fires here since we no longer issue
  // a physical DELETE.
  suspend fun deleteTaskList(taskList: TaskList) {
    taskListDao.update(taskList.touchedDeleted())
    taskDao.getAllTasksForListSnapshot(taskList.id)
      .filter { !it.isDeleted }
      .forEach { task ->
        taskDao.update(task.touchedDeleted())
        subtaskDao.getSubtasksForTaskDirect(task.id).forEach { subtask ->
          subtaskDao.update(subtask.touchedDeleted())
        }
      }
  }
  
  suspend fun getTaskListById(id: Long): TaskList? {
    return taskListDao.getListById(id)
  }
  
  // Task operations
  fun getTasksForList(listId: Long): LiveData<List<Task>> {
    return taskDao.getTasksForList(listId)
  }

  fun getTasksForListFlow(listId: Long): kotlinx.coroutines.flow.Flow<List<Task>> {
    return taskDao.getTasksForListFlow(listId)
  }
  
  fun getAllTasks(): LiveData<List<Task>> {
    return taskDao.getAllTasks()
  }

  fun getAllTasksFlow(): kotlinx.coroutines.flow.Flow<List<Task>> {
    return taskDao.getAllTasksFlow()
  }
  
  suspend fun insertTask(task: Task): Long {
    return taskDao.insert(task)
  }
  
  suspend fun updateTask(task: Task) {
    taskDao.update(task)
  }

  suspend fun updateTasks(tasks: List<Task>) {
    taskDao.updateAll(tasks)
  }
  
  suspend fun getTaskById(taskId: Long): Task? {
    return taskDao.getTaskById(taskId)
  }

  suspend fun cacheTaskImageUri(taskId: Long, imageUri: String) {
    taskDao.updateImageUriCache(taskId, imageUri)
  }
  
  fun searchTasks(query: String): LiveData<List<Task>> {
    return taskDao.searchTasks(query)
  }

  suspend fun getTasksBetweenDates(start: Long, end: Long): List<Task> {
      return taskDao.getTasksBetweenDates(start, end)
  }

  fun getTasksForDateLive(start: Long, end: Long): LiveData<List<Task>> {
      return taskDao.getTasksForDateLive(start, end)
  }

  fun getTasksWithListTitles(): LiveData<List<app.polar.data.model.TaskWithList>> {
      return taskDao.getTasksWithListTitles()
  }
  
  // Subtask operations
  fun getSubtasksForTask(taskId: Long): LiveData<List<Subtask>> {
    return subtaskDao.getSubtasksForTask(taskId)
  }
  
  suspend fun getSubtasksForTaskDirect(taskId: Long): List<Subtask> {
    return subtaskDao.getSubtasksForTaskDirect(taskId)
  }
  
  suspend fun insertSubtask(subtask: Subtask): Long {
    return subtaskDao.insert(subtask)
  }
  
  suspend fun updateSubtask(subtask: Subtask) {
    subtaskDao.update(subtask)
  }
  
  // Diffs the edited subtask list against what's stored instead of deleting everything and
  // reinserting it, so an edit that doesn't touch a given subtask never generates a spurious
  // tombstone + insert pair for it once sync is active (see agent-docs/supabase-sync/
  // 06-plan-implementacion-android.md, punto 3 "Refactor obligatorio").
  suspend fun replaceSubtasksForTask(taskId: Long, newSubtasks: List<Subtask>) {
    val existing = subtaskDao.getSubtasksForTaskDirect(taskId)
    val existingById = existing.associateBy { it.id }
    val incomingIds = newSubtasks.filter { it.id != 0L }.map { it.id }.toSet()

    existing.filter { it.id !in incomingIds }.forEach { subtaskDao.update(it.touchedDeleted()) }

    newSubtasks.forEachIndexed { index, incoming ->
      val current = existingById[incoming.id]
      if (current == null) {
        subtaskDao.insert(incoming.copy(id = 0, taskId = taskId, orderIndex = index).touched())
      } else if (
        current.title != incoming.title ||
        current.completed != incoming.completed ||
        current.dueDate != incoming.dueDate ||
        current.orderIndex != index
      ) {
        subtaskDao.update(
          current.copy(
            title = incoming.title,
            completed = incoming.completed,
            dueDate = incoming.dueDate,
            orderIndex = index
          ).touched()
        )
      }
    }
  }

  suspend fun completeSubtasksForTask(taskId: Long) {
    subtaskDao.completeSubtasksForTask(taskId, System.currentTimeMillis())
  }

  suspend fun resetSubtasksForTask(taskId: Long) {
    subtaskDao.resetSubtasksForTask(taskId, System.currentTimeMillis())
  }

  // Trash operations

  // Cascades the tombstone to every active subtask, mirroring deleteTaskList()'s cascade to
  // tasks/subtasks — otherwise a task's subtasks stay "active" while the task itself sits in the
  // trash, which is exactly the state that made 3.2 (orphaned subtasks resurrecting after purge)
  // easy to hit.
  suspend fun softDeleteTask(task: Task) {
      taskDao.update(task.touchedDeleted())
      subtaskDao.getSubtasksForTaskDirect(task.id).forEach { subtask ->
          subtaskDao.update(subtask.touchedDeleted())
      }
  }

  suspend fun restoreTask(task: Task) {
      taskDao.update(task.touchedRestored())
  }

  // Returns whether the row was actually purged. It can come back false if the tombstone (or one
  // of its subtasks') hasn't reached Supabase yet — see TaskDao.permanentDelete.
  suspend fun permanentDeleteTask(taskId: Long): Boolean {
      return taskDao.permanentDelete(taskId) > 0
  }

  // Returns how many trashed tasks are still stuck after the purge attempt (unsynced tombstone,
  // or an unsynced subtask blocking the cascade) so the caller can warn the user instead of
  // silently leaving them in the trash forever.
  suspend fun emptyTrash(): Int {
      taskDao.emptyTrash()
      return taskDao.getTrashCount()
  }

  fun getDeletedTasks(): LiveData<List<Task>> {
      return taskDao.getDeletedTasks()
  }

  // Statistics
  suspend fun getTotalTaskCount(): Int = taskDao.getTotalTaskCount()
  suspend fun getCompletedTaskCount(): Int = taskDao.getCompletedTaskCount()
  suspend fun getPendingTaskCount(): Int = taskDao.getPendingTaskCount()
  suspend fun getCompletedTaskCountBetween(start: Long, end: Long): Int = taskDao.getCompletedTaskCountBetween(start, end)
  suspend fun getTaskCountBetween(start: Long, end: Long): Int = taskDao.getTaskCountBetween(start, end)
  suspend fun getCreatedTaskCountBetween(start: Long, end: Long): Int = taskDao.getCreatedTaskCountBetween(start, end)
  suspend fun getOverdueTaskCount(now: Long): Int = taskDao.getOverdueTaskCount(now)
  suspend fun getAllCompletedTasksSnapshot(): List<Task> = taskDao.getAllCompletedTasksSnapshot()
  suspend fun getTaskCountByPriority(): List<app.polar.data.model.PriorityCount> = taskDao.getTaskCountByPriority()
  suspend fun getTaskCountByList(): List<app.polar.data.model.ListTaskCount> = taskDao.getTaskCountByList()
}
