package app.polar.data.repository

import androidx.lifecycle.LiveData
import app.polar.data.dao.SubtaskDao
import app.polar.data.dao.TaskDao
import app.polar.data.dao.TaskListDao
import app.polar.data.entity.Subtask
import app.polar.data.entity.Task
import app.polar.data.entity.TaskList

class TaskRepository(
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
  
  suspend fun deleteTaskList(taskList: TaskList) {
    taskListDao.delete(taskList)
  }
  
  suspend fun getTaskListById(id: Long): TaskList? {
    return taskListDao.getListById(id)
  }
  
  // Task operations
  fun getTasksForList(listId: Long): LiveData<List<Task>> {
    return taskDao.getTasksForList(listId)
  }
  
  fun getAllTasks(): LiveData<List<Task>> {
    return taskDao.getAllTasks()
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
  
  suspend fun deleteTask(task: Task) {
    taskDao.delete(task)
  }
  
  suspend fun getTaskById(taskId: Long): Task? {
    return taskDao.getTaskById(taskId)
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
  
  suspend fun deleteSubtask(subtask: Subtask) {
    subtaskDao.delete(subtask)
  }
  
  suspend fun deleteAllSubtasksForTask(taskId: Long) {
    subtaskDao.deleteAllForTask(taskId)
  }

  // Trash operations
  suspend fun softDeleteTask(taskId: Long) {
      taskDao.softDelete(taskId)
  }

  suspend fun restoreTask(taskId: Long) {
      taskDao.restore(taskId)
  }

  suspend fun permanentDeleteTask(taskId: Long) {
      taskDao.permanentDelete(taskId)
  }

  suspend fun emptyTrash() {
      taskDao.emptyTrash()
  }

  fun getDeletedTasks(): LiveData<List<Task>> {
      return taskDao.getDeletedTasks()
  }
}
