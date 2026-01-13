package app.polar.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import app.polar.data.entity.Task

@Dao
interface TaskDao {
  @Query("SELECT * FROM tasks WHERE listId = :listId AND isDeleted = 0 ORDER BY orderIndex ASC")
  fun getTasksForList(listId: Long): LiveData<List<Task>>
  
  @Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY createdAt DESC")
  fun getAllTasks(): LiveData<List<Task>>

  @Query("SELECT * FROM tasks")
  suspend fun getAllTasksSnapshot(): List<Task>
  
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(task: Task): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(tasks: List<Task>)
  
  @Update
  suspend fun update(task: Task)

  @Update
  suspend fun updateAll(tasks: List<Task>)
  
  @Delete
  suspend fun delete(task: Task)

  @Query("DELETE FROM tasks")
  suspend fun deleteAll()

  @Query("UPDATE tasks SET isDeleted = 1 WHERE id = :taskId")
  suspend fun softDelete(taskId: Long)

  @Query("UPDATE tasks SET isDeleted = 0 WHERE id = :taskId")
  suspend fun restore(taskId: Long)

  @Query("DELETE FROM tasks WHERE id = :taskId")
  suspend fun permanentDelete(taskId: Long)

  @Query("DELETE FROM tasks WHERE isDeleted = 1")
  suspend fun emptyTrash()

  @Query("SELECT * FROM tasks WHERE isDeleted = 1 ORDER BY createdAt DESC")
  fun getDeletedTasks(): LiveData<List<Task>>
  
  @Query("SELECT * FROM tasks WHERE id = :id")
  suspend fun getTaskById(id: Long): Task?
  
  @Query("""
    SELECT * FROM tasks 
    WHERE (title LIKE '%' || :query || '%' 
    OR description LIKE '%' || :query || '%'
    OR tags LIKE '%' || :query || '%')
    AND isDeleted = 0
    ORDER BY createdAt DESC
  """)
  fun searchTasks(query: String): LiveData<List<Task>>
  
  @Query("SELECT * FROM tasks WHERE dueDate BETWEEN :start AND :end AND isDeleted = 0")
  suspend fun getTasksBetweenDates(start: Long, end: Long): List<Task>
  
  @Query("SELECT * FROM tasks WHERE dueDate BETWEEN :start AND :end AND isDeleted = 0 ORDER BY dueDate ASC")
  fun getTasksForDateLive(start: Long, end: Long): LiveData<List<Task>>

  @Query("""
    SELECT 
        tasks.*, 
        task_lists.title as listTitle,
        (SELECT COUNT(*) FROM subtasks WHERE taskId = tasks.id) as totalSubtasks,
        (SELECT COUNT(*) FROM subtasks WHERE taskId = tasks.id AND completed = 1) as completedSubtasks
    FROM tasks 
    LEFT JOIN task_lists ON tasks.listId = task_lists.id 
    WHERE tasks.isDeleted = 0
    ORDER BY task_lists.homeOrderIndex ASC, tasks.orderIndex ASC
  """)
  fun getTasksWithListTitles(): LiveData<List<app.polar.data.model.TaskWithList>>
}
