package app.polar.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import app.polar.data.entity.Subtask

@Dao
interface SubtaskDao {
  @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY orderIndex ASC")
  fun getSubtasksForTask(taskId: Long): LiveData<List<Subtask>>

  @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY orderIndex ASC")
  fun getSubtasksForTaskFlow(taskId: Long): kotlinx.coroutines.flow.Flow<List<Subtask>>

  @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY orderIndex ASC")
  suspend fun getSubtasksForTaskDirect(taskId: Long): List<Subtask>

  @Query("SELECT * FROM subtasks WHERE id = :subtaskId LIMIT 1")
  suspend fun getSubtaskById(subtaskId: Long): Subtask?

  @Query("SELECT * FROM subtasks")
  suspend fun getAllSubtasksSnapshot(): List<Subtask>

  // Subtarea a la que tiene sentido avisar: pendiente, con fecha y cuya tarea sigue activa.
  @Query("""
    SELECT subtasks.* FROM subtasks
    INNER JOIN tasks ON tasks.id = subtasks.taskId
    WHERE subtasks.id = :subtaskId AND subtasks.completed = 0 AND subtasks.dueDate IS NOT NULL
      AND tasks.completed = 0 AND tasks.isDeleted = 0
  """)
  suspend fun getAlarmableSubtask(subtaskId: Long): Subtask?

  @Query("""
    SELECT subtasks.* FROM subtasks
    INNER JOIN tasks ON tasks.id = subtasks.taskId
    WHERE subtasks.completed = 0 AND subtasks.dueDate > :now
      AND tasks.completed = 0 AND tasks.isDeleted = 0
  """)
  suspend fun getAlarmableSubtasks(now: Long): List<Subtask>

  @Query("SELECT id FROM subtasks")
  suspend fun getAllSubtaskIds(): List<Long>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(subtask: Subtask): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(subtasks: List<Subtask>)

  @Update
  suspend fun update(subtask: Subtask)

  @Delete
  suspend fun delete(subtask: Subtask)

  @Query("DELETE FROM subtasks")
  suspend fun deleteAll()

  @Query("UPDATE subtasks SET completed = 0 WHERE taskId = :taskId")
  suspend fun resetSubtasksForTask(taskId: Long)

  @Query("UPDATE subtasks SET completed = 1 WHERE taskId = :taskId")
  suspend fun completeSubtasksForTask(taskId: Long)
}
