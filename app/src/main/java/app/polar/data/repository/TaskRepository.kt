package app.polar.data.repository

import androidx.lifecycle.LiveData
import app.polar.data.dao.SubtaskDao
import app.polar.data.dao.TaskDao
import app.polar.data.dao.TaskListDao
import app.polar.data.entity.Subtask
import app.polar.data.entity.Task
import app.polar.data.entity.TaskList

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

  // Borra la lista junto a todas sus tareas y subtareas (ON DELETE CASCADE). Devuelve las
  // tareas que se han eliminado para que quien llama pueda cancelar sus alarmas.
  suspend fun deleteTaskList(taskList: TaskList): List<Task> {
    val tasks = taskDao.getAllTasksForListSnapshot(taskList.id)
    taskListDao.delete(taskList)
    return tasks
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

  // Compara la lista editada con lo guardado en vez de borrar y reinsertar todas las
  // subtareas: solo se escriben las que cambian, conservando ids y fechas de creación.
  //
  // `newSubtasks` es una foto que el diálogo de edición tomó al abrirse. Si mientras sigue
  // abierto cambia el estado `completed` de alguna subtarea por otra vía (por ejemplo, al
  // completar la tarea desde una notificación), guardar no debe devolverla al valor antiguo:
  // `touchedCompletedIds` limita ese campo a las subtareas cuyo checkbox tocó el usuario en esta
  // sesión del diálogo; el resto conserva el valor actual de la base de datos.
  suspend fun replaceSubtasksForTask(
    taskId: Long,
    newSubtasks: List<Subtask>,
    touchedCompletedIds: Set<Long> = emptySet()
  ) {
    val existing = subtaskDao.getSubtasksForTaskDirect(taskId)
    val existingById = existing.associateBy { it.id }
    val incomingIds = newSubtasks.filter { it.id != 0L }.map { it.id }.toSet()

    existing.filter { it.id !in incomingIds }.forEach { subtaskDao.delete(it) }

    newSubtasks.forEachIndexed { index, incoming ->
      val current = existingById[incoming.id]
      if (current == null) {
        subtaskDao.insert(incoming.copy(id = 0, taskId = taskId, orderIndex = index))
      } else {
        val completed = if (incoming.id in touchedCompletedIds) incoming.completed else current.completed
        if (
          current.title != incoming.title ||
          current.completed != completed ||
          current.dueDate != incoming.dueDate ||
          current.orderIndex != index
        ) {
          subtaskDao.update(
            current.copy(
              title = incoming.title,
              completed = completed,
              dueDate = incoming.dueDate,
              orderIndex = index
            )
          )
        }
      }
    }
  }

  suspend fun completeSubtasksForTask(taskId: Long) {
    subtaskDao.completeSubtasksForTask(taskId)
  }

  suspend fun resetSubtasksForTask(taskId: Long) {
    subtaskDao.resetSubtasksForTask(taskId)
  }

  // Trash operations
  suspend fun softDeleteTask(task: Task) {
      taskDao.softDelete(task.id)
  }

  suspend fun restoreTask(task: Task) {
      taskDao.restore(task.id)
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
