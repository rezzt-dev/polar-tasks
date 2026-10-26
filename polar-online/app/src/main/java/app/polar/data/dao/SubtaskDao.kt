package app.polar.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import app.polar.data.entity.Subtask

@Dao
interface SubtaskDao {
  @Query("SELECT * FROM subtasks WHERE taskId = :taskId AND deletedAt IS NULL ORDER BY orderIndex ASC")
  fun getSubtasksForTask(taskId: Long): LiveData<List<Subtask>>

  @Query("SELECT * FROM subtasks WHERE taskId = :taskId AND deletedAt IS NULL ORDER BY orderIndex ASC")
  fun getSubtasksForTaskFlow(taskId: Long): kotlinx.coroutines.flow.Flow<List<Subtask>>

  @Query("SELECT * FROM subtasks WHERE taskId = :taskId AND deletedAt IS NULL ORDER BY orderIndex ASC")
  suspend fun getSubtasksForTaskDirect(taskId: Long): List<Subtask>

  @Query("SELECT * FROM subtasks WHERE id = :subtaskId LIMIT 1")
  suspend fun getSubtaskById(subtaskId: Long): Subtask?

  @Query("SELECT * FROM subtasks WHERE dirty = 1")
  suspend fun getDirtySubtasks(): List<Subtask>

  @Query("SELECT * FROM subtasks WHERE uuid = :uuid LIMIT 1")
  suspend fun getByUuid(uuid: String): Subtask?

  @Query("SELECT * FROM subtasks")
  suspend fun getAllSubtasksSnapshot(): List<Subtask>

  // Subtarea a la que tiene sentido avisar: pendiente, no borrada, con fecha y cuya tarea sigue activa.
  @Query("""
    SELECT subtasks.* FROM subtasks
    INNER JOIN tasks ON tasks.id = subtasks.taskId
    WHERE subtasks.id = :subtaskId AND subtasks.completed = 0 AND subtasks.deletedAt IS NULL
      AND subtasks.dueDate IS NOT NULL AND tasks.completed = 0 AND tasks.isDeleted = 0
  """)
  suspend fun getAlarmableSubtask(subtaskId: Long): Subtask?

  @Query("""
    SELECT subtasks.* FROM subtasks
    INNER JOIN tasks ON tasks.id = subtasks.taskId
    WHERE subtasks.completed = 0 AND subtasks.deletedAt IS NULL AND subtasks.dueDate > :now
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

  @Update
  suspend fun updateAll(subtasks: List<Subtask>)

  @Query("DELETE FROM subtasks")
  suspend fun deleteAll()

  @Query("UPDATE subtasks SET completed = 0, updatedAt = :updatedAt, dirty = 1 WHERE taskId = :taskId AND deletedAt IS NULL")
  suspend fun resetSubtasksForTask(taskId: Long, updatedAt: Long)

  @Query("UPDATE subtasks SET completed = 1, updatedAt = :updatedAt, dirty = 1 WHERE taskId = :taskId AND deletedAt IS NULL")
  suspend fun completeSubtasksForTask(taskId: Long, updatedAt: Long)
}
