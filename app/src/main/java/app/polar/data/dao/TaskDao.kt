package app.polar.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import app.polar.data.entity.Task

@Dao
interface TaskDao {
  @Query("SELECT * FROM tasks WHERE listId = :listId ORDER BY orderIndex ASC")
  fun getTasksForList(listId: Long): LiveData<List<Task>>
  
  @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
  fun getAllTasks(): LiveData<List<Task>>
  
  @Insert
  suspend fun insert(task: Task): Long
  
  @Update
  suspend fun update(task: Task)

  @Update
  suspend fun updateAll(tasks: List<Task>)
  
  @Delete
  suspend fun delete(task: Task)
  
  @Query("SELECT * FROM tasks WHERE id = :id")
  suspend fun getTaskById(id: Long): Task?
  
  @Query("""
    SELECT * FROM tasks 
    WHERE title LIKE '%' || :query || '%' 
    OR description LIKE '%' || :query || '%'
    OR tags LIKE '%' || :query || '%'
    ORDER BY createdAt DESC
  """)
  fun searchTasks(query: String): LiveData<List<Task>>
  
  @Query("SELECT * FROM tasks WHERE dueDate BETWEEN :start AND :end")
  suspend fun getTasksBetweenDates(start: Long, end: Long): List<Task>
  
  @Query("SELECT * FROM tasks WHERE dueDate BETWEEN :start AND :end ORDER BY dueDate ASC")
  fun getTasksForDateLive(start: Long, end: Long): LiveData<List<Task>>

  @Query("""
    SELECT 
        tasks.*, 
        task_lists.title as listTitle,
        (SELECT COUNT(*) FROM subtasks WHERE taskId = tasks.id) as totalSubtasks,
        (SELECT COUNT(*) FROM subtasks WHERE taskId = tasks.id AND completed = 1) as completedSubtasks
    FROM tasks 
    LEFT JOIN task_lists ON tasks.listId = task_lists.id 
    ORDER BY task_lists.orderIndex ASC, tasks.orderIndex ASC
  """)
  fun getTasksWithListTitles(): LiveData<List<app.polar.data.model.TaskWithList>>
}
